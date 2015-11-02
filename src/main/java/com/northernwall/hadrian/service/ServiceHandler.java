/*
 * Copyright 2014 Richard Thurston.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.northernwall.hadrian.service;

import com.northernwall.hadrian.maven.MavenHelper;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.northernwall.hadrian.Const;
import com.northernwall.hadrian.Util;
import com.northernwall.hadrian.access.Access;
import com.northernwall.hadrian.access.AccessException;
import com.northernwall.hadrian.db.DataAccess;
import com.northernwall.hadrian.domain.CustomFunction;
import com.northernwall.hadrian.domain.DataStore;
import com.northernwall.hadrian.domain.Vip;
import com.northernwall.hadrian.domain.VipRef;
import com.northernwall.hadrian.domain.Service;
import com.northernwall.hadrian.domain.Host;
import com.northernwall.hadrian.domain.ServiceRef;
import com.northernwall.hadrian.service.dao.GetCustomFunctionData;
import com.northernwall.hadrian.service.dao.GetDataStoreData;
import com.northernwall.hadrian.service.dao.GetHostData;
import com.northernwall.hadrian.service.dao.GetNotUsesData;
import com.northernwall.hadrian.service.dao.GetServiceData;
import com.northernwall.hadrian.service.dao.GetServiceRefData;
import com.northernwall.hadrian.service.dao.GetVipData;
import com.northernwall.hadrian.service.dao.GetVipRefData;
import com.northernwall.hadrian.service.dao.PostServiceData;
import com.northernwall.hadrian.service.dao.PostServiceRefData;
import com.northernwall.hadrian.service.dao.PutServiceData;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Richard Thurston
 */
public class ServiceHandler extends AbstractHandler {

    private final static Logger logger = LoggerFactory.getLogger(ServiceHandler.class);

    private final Access access;
    private final DataAccess dataAccess;
    private final MavenHelper mavenhelper;
    private final InfoHelper infoHelper;
    private final Gson gson;
    private final ExecutorService es;

    public ServiceHandler(Access access, DataAccess dataAccess, MavenHelper mavenhelper, InfoHelper infoHelper) {
        this.access = access;
        this.dataAccess = dataAccess;
        this.mavenhelper = mavenhelper;
        this.infoHelper = infoHelper;
        gson = new Gson();

        es = Executors.newFixedThreadPool(20);
    }

    @Override
    public void handle(String target, Request request, HttpServletRequest httpRequest, HttpServletResponse response) throws IOException, ServletException {
        try {
            if (target.startsWith("/v1/service/")) {
                switch (request.getMethod()) {
                    case "GET":
                        if (target.matches("/v1/service/\\w+-\\w+-\\w+-\\w+-\\w+/notuses")) {
                            logger.info("Handling {} request {}", request.getMethod(), target);
                            getServiceNotUses(response, target.substring(12, target.length() - 8));
                            response.setStatus(200);
                            request.setHandled(true);
                        } else if (target.matches("/v1/service/\\w+-\\w+-\\w+-\\w+-\\w+")) {
                            logger.info("Handling {} request {}", request.getMethod(), target);
                            getService(request, response, target.substring(12, target.length()));
                            response.setStatus(200);
                            request.setHandled(true);
                        }
                        break;
                    case "POST":
                        if (target.matches("/v1/service/service")) {
                            logger.info("Handling {} request {}", request.getMethod(), target);
                            createService(request);
                            response.setStatus(200);
                            request.setHandled(true);
                        } else if (target.matches("/v1/service/\\w+-\\w+-\\w+-\\w+-\\w+/ref")) {
                            logger.info("Handling {} request {}", request.getMethod(), target);
                            createServiceRef(request, target.substring(12, target.length() - 4));
                            response.setStatus(200);
                            request.setHandled(true);
                        }
                        break;
                    case "PUT":
                        if (target.matches("/v1/service/\\w+-\\w+-\\w+-\\w+-\\w+")) {
                            logger.info("Handling {} request {}", request.getMethod(), target);
                            updateService(request, target.substring(12, target.length()));
                            response.setStatus(200);
                            request.setHandled(true);
                        }
                        break;
                    case "DELETE":
                        if (target.matches("/v1/service/\\w+-\\w+-\\w+-\\w+-\\w+/uses/\\w+-\\w+-\\w+-\\w+-\\w+")) {
                            logger.info("Handling {} request {}", request.getMethod(), target);
                            deleteServiceRef(request, target.substring(12, target.length() - 42), target.substring(54, target.length()));
                            response.setStatus(200);
                            request.setHandled(true);
                        }
                        break;
                }
            }
        } catch (AccessException e) {
            logger.error("Exception {} while handling request for {}", e.getMessage(), target);
            response.setStatus(401);
            request.setHandled(true);
        } catch (Exception e) {
            logger.error("Exception {} while handling request for {}", e.getMessage(), target, e);
            response.setStatus(400);
            request.setHandled(true);
        }
    }

    private void getService(Request request, HttpServletResponse response, String id) throws IOException {
        response.setContentType(Const.JSON);
        Service service = dataAccess.getService(id);
        if (service == null) {
            throw new RuntimeException("Could not find service with id '" + id + "'");
        }

        GetServiceData getServiceData = GetServiceData.create(service);
        getServiceData.canModify = access.canUserModify(request, service.getTeamId());

        List<Future> futures = new LinkedList<>();
        for (Host host : dataAccess.getHosts(id)) {
            GetHostData getHostData = GetHostData.create(host);
            futures.add(es.submit(new ReadVersionRunnable(getHostData, getServiceData)));
            futures.add(es.submit(new ReadAvailabilityRunnable(getHostData, getServiceData)));
            for (VipRef vipRef : dataAccess.getVipRefsByHost(getHostData.hostId)) {
                GetVipRefData getVipRefData = GetVipRefData.create(vipRef);
                for (GetVipData vip : getServiceData.vips) {
                    if (vip.vipId.equals(getVipRefData.vipId)) {
                        getVipRefData.vipName = vip.vipName;
                    }
                }
                getHostData.vipRefs.add(getVipRefData);
            }
            getServiceData.hosts.add(getHostData);
        }

        for (Vip vip : dataAccess.getVips(id)) {
            GetVipData getVipData = GetVipData.create(vip);
            getServiceData.vips.add(getVipData);

        }

        for (DataStore dataStore : dataAccess.getDataStores(id)) {
            GetDataStoreData getDataStoreData = GetDataStoreData.create(dataStore);
            getServiceData.dataStores.add(getDataStoreData);

        }

        for (ServiceRef ref : dataAccess.getServiceRefsByClient(id)) {
            GetServiceRefData tempRef = GetServiceRefData.create(ref);
            tempRef.serviceName = dataAccess.getService(ref.getServerServiceId()).getServiceName();
            getServiceData.uses.add(tempRef);
        }

        for (ServiceRef ref : dataAccess.getServiceRefsByServer(id)) {
            GetServiceRefData tempRef = GetServiceRefData.create(ref);
            tempRef.serviceName = dataAccess.getService(ref.getClientServiceId()).getServiceName();
            getServiceData.usedBy.add(tempRef);
        }

        for (CustomFunction customFunction : dataAccess.getCustomFunctions(id)) {
            GetCustomFunctionData getCustomFunctionData = GetCustomFunctionData.create(customFunction);
            getServiceData.customFunctions.add(getCustomFunctionData);
        }

        //TODO: make this a future also
        getServiceData.versions.addAll(mavenhelper.readMavenVersions(getServiceData.mavenGroupId, getServiceData.mavenArtifactId));

        waitForFutures(futures);

        try (JsonWriter jw = new JsonWriter(new OutputStreamWriter(response.getOutputStream()))) {
            gson.toJson(getServiceData, GetServiceData.class, jw);
        }
    }

    private void waitForFutures(List<Future> futures) {
        for (int i = 0; i < 20; i++) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
            }
            futures.removeIf(new Predicate<Future>() {
                @Override
                public boolean test(Future t) {
                    return t.isDone();
                }
            });
            if (futures.isEmpty()) {
                return;
            }
        }
    }

    class ReadVersionRunnable implements Runnable {

        private final GetHostData getHostData;
        private final GetServiceData getServiceData;

        public ReadVersionRunnable(GetHostData getHostData, GetServiceData getServiceData) {
            this.getHostData = getHostData;
            this.getServiceData = getServiceData;
        }

        @Override
        public void run() {
            try {
                getHostData.version = infoHelper.readVersion(getHostData.hostName, getServiceData.versionUrl);
            } catch (IOException ex) {
            }
        }
    }

    class ReadAvailabilityRunnable implements Runnable {

        private final GetHostData getHostData;
        private final GetServiceData getServiceData;

        public ReadAvailabilityRunnable(GetHostData getHostData, GetServiceData getServiceData) {
            this.getHostData = getHostData;
            this.getServiceData = getServiceData;
        }

        @Override
        public void run() {
            try {
                getHostData.availability = infoHelper.readAvailability(getHostData.hostName, getServiceData.availabilityUrl);
            } catch (IOException ex) {
            }
        }
    }

    private void getServiceNotUses(HttpServletResponse response, String id) throws IOException {
        logger.info("got here {}", id);
        List<Service> services = dataAccess.getServices();
        List<ServiceRef> refs = dataAccess.getServiceRefsByClient(id);

        GetNotUsesData notUses = new GetNotUsesData();
        for (Service service : services) {
            if (!service.getServiceId().equals(id)) {
                boolean found = false;
                for (ServiceRef ref : refs) {
                    if (service.getServiceId().equals(ref.getServerServiceId())) {
                        found = true;
                    }
                }
                if (!found) {
                    GetServiceRefData ref = new GetServiceRefData();
                    ref.clientServiceId = id;
                    ref.serverServiceId = service.getServiceId();
                    ref.serviceName = service.getServiceName();
                    notUses.refs.add(ref);
                }
            }
        }

        try (JsonWriter jw = new JsonWriter(new OutputStreamWriter(response.getOutputStream()))) {
            gson.toJson(notUses, GetNotUsesData.class, jw);
        }
    }

    private void createService(Request request) throws IOException {
        PostServiceData postServiceData = Util.fromJson(request, PostServiceData.class);
        access.checkIfUserCanModify(request, postServiceData.teamId, "create a service");
        postServiceData.serviceAbbr = postServiceData.serviceAbbr.toLowerCase();

        for (Service temp : dataAccess.getServices(postServiceData.teamId)) {
            if (temp.getServiceAbbr().equals(postServiceData.serviceAbbr)) {
                logger.warn("A service already exists with that abbreviation, {}", postServiceData.serviceAbbr);
                return;
            }
        }

        Service service = new Service(
                postServiceData.serviceAbbr,
                postServiceData.serviceName,
                postServiceData.teamId,
                postServiceData.description,
                postServiceData.mavenGroupId,
                postServiceData.mavenArtifactId,
                postServiceData.versionUrl,
                postServiceData.availabilityUrl);

        dataAccess.saveService(service);
    }

    private void updateService(Request request, String id) throws IOException {
        PutServiceData putServiceData = Util.fromJson(request, PutServiceData.class);
        Service service = dataAccess.getService(id);
        if (service == null) {
            throw new RuntimeException("Could not find service");
        }
        access.checkIfUserCanModify(request, service.getTeamId(), "modify a service");

        service.setServiceAbbr(putServiceData.serviceAbbr);
        service.setServiceName(putServiceData.serviceName);
        service.setDescription(putServiceData.description);
        service.setMavenGroupId(putServiceData.mavenGroupId);
        service.setMavenArtifactId(putServiceData.mavenArtifactId);
        service.setVersionUrl(putServiceData.versionUrl);
        service.setAvailabilityUrl(putServiceData.availabilityUrl);

        dataAccess.updateService(service);
    }

    private void createServiceRef(Request request, String id) throws IOException {
        PostServiceRefData postServiceRefData = Util.fromJson(request, PostServiceRefData.class);
        Service service = dataAccess.getService(id);
        if (service == null) {
            throw new RuntimeException("Could not find service");
        }
        access.checkIfUserCanModify(request, service.getTeamId(), "add a service ref");
        for (Entry<String, String> entry : postServiceRefData.uses.entrySet()) {
            if (entry.getValue().equalsIgnoreCase("true")) {
                ServiceRef ref = new ServiceRef(id, entry.getKey());
                dataAccess.saveServiceRef(ref);
            }
        }
    }

    private void deleteServiceRef(Request request, String clientId, String serviceId) {
        Service service = dataAccess.getService(clientId);
        if (service == null) {
            return;
        }
        access.checkIfUserCanModify(request, service.getTeamId(), "delete a service ref");
        dataAccess.deleteServiceRef(clientId, serviceId);
    }

}
