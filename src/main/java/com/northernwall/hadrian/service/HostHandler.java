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

import com.northernwall.hadrian.service.helper.HostDetailsHelper;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.northernwall.hadrian.Const;
import com.northernwall.hadrian.Util;
import com.northernwall.hadrian.access.AccessException;
import com.northernwall.hadrian.access.AccessHelper;
import com.northernwall.hadrian.db.DataAccess;
import com.northernwall.hadrian.domain.Audit;
import com.northernwall.hadrian.domain.Config;
import com.northernwall.hadrian.domain.Vip;
import com.northernwall.hadrian.domain.VipRef;
import com.northernwall.hadrian.domain.Host;
import com.northernwall.hadrian.domain.Module;
import com.northernwall.hadrian.domain.Service;
import com.northernwall.hadrian.domain.Team;
import com.northernwall.hadrian.domain.User;
import com.northernwall.hadrian.domain.WorkItem;
import com.northernwall.hadrian.workItem.WorkItemProcessor;
import com.northernwall.hadrian.service.dao.GetHostDetailsData;
import com.northernwall.hadrian.service.dao.PostHostData;
import com.northernwall.hadrian.service.dao.PostHostVipData;
import com.northernwall.hadrian.service.dao.PutDeploySoftwareData;
import com.northernwall.hadrian.service.dao.PutRestartHostData;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class HostHandler extends AbstractHandler {

    private final static Logger logger = LoggerFactory.getLogger(HostHandler.class);

    private final AccessHelper accessHelper;
    private final Config config;
    private final DataAccess dataAccess;
    private final WorkItemProcessor workItemProcess;
    private final HostDetailsHelper hostDetailsHelper;
    private final Gson gson;

    public HostHandler(AccessHelper accessHelper, Config config, DataAccess dataAccess, WorkItemProcessor workItemProcess, HostDetailsHelper hostDetailsHelper) {
        this.accessHelper = accessHelper;
        this.config = config;
        this.dataAccess = dataAccess;
        this.workItemProcess = workItemProcess;
        this.hostDetailsHelper = hostDetailsHelper;
        gson = new Gson();
    }

    @Override
    public void handle(String target, Request request, HttpServletRequest httpRequest, HttpServletResponse response) throws IOException, ServletException {
        try {
            if (target.startsWith("/v1/host/")) {
                switch (request.getMethod()) {
                    case Const.HTTP_GET:
                        if (target.matches("/v1/host/\\w+-\\w+-\\w+-\\w+-\\w+/\\w+-\\w+-\\w+-\\w+-\\w+/details")) {
                            logger.info("Handling {} request {}", request.getMethod(), target);
                            String serviceId = target.substring(9, 45);
                            String hostId = target.substring(46, 82);
                            getHostDetails(response, serviceId, hostId);
                        } else {
                            throw new RuntimeException("Unknown host operation");
                        }
                        break;
                    case "POST":
                        if (target.matches("/v1/host/host")) {
                            logger.info("Handling {} request {}", request.getMethod(), target);
                            createHosts(request);
                        } else if (target.matches("/v1/host/vips")) {
                            logger.info("Handling {} request {}", request.getMethod(), target);
                            addVIPs(request);
                        } else if (target.matches("/v1/host/backfill")) {
                            logger.info("Handling {} request {}", request.getMethod(), target);
                            backfillHosts(request);
                        } else {
                            throw new RuntimeException("Unknown host operation");
                        }
                        break;
                    case "PUT":
                        if (target.matches("/v1/host/host")) {
                            logger.info("Handling {} request {}", request.getMethod(), target);
                            deploySoftware(request);
                        } else if (target.matches("/v1/host/restart")) {
                            logger.info("Handling {} request {}", request.getMethod(), target);
                            restartHost(request);
                        } else {
                            throw new RuntimeException("Unknown host operation");
                        }
                        break;
                    case "DELETE":
                        if (target.matches("/v1/host/\\w+-\\w+-\\w+-\\w+-\\w+/\\w+-\\w+-\\w+-\\w+-\\w+")) {
                            logger.info("Handling {} request {}", request.getMethod(), target);
                            String serviceId = target.substring(9, 45);
                            String hostId = target.substring(46);
                            deleteHost(request, serviceId, hostId);
                        } else if (target.matches("/v1/host/\\w+-\\w+-\\w+-\\w+-\\w+/\\w+-\\w+-\\w+-\\w+-\\w+/\\w+-\\w+-\\w+-\\w+-\\w+")) {
                            logger.info("Handling {} request {}", request.getMethod(), target);
                            String serviceId = target.substring(9, 45);
                            String hostId = target.substring(46, 82);
                            String vipId = target.substring(83);
                            deleteVIP(request, serviceId, hostId, vipId);
                        } else {
                            throw new RuntimeException("Unknown host operation");
                        }
                        break;
                    default:
                        throw new RuntimeException("Unknown host operation");
                }
                response.setStatus(200);
                request.setHandled(true);
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

    private void getHostDetails(HttpServletResponse response, String serviceId, String hostId) throws IOException {
        Host host = dataAccess.getHost(serviceId, hostId);
        if (host == null) {
            throw new RuntimeException("Could not find host");
        }

        GetHostDetailsData details = hostDetailsHelper.getDetails(host);

        response.setContentType(Const.JSON);
        try (JsonWriter jw = new JsonWriter(new OutputStreamWriter(response.getOutputStream()))) {
            gson.toJson(details, GetHostDetailsData.class, jw);
        }
    }

    private void createHosts(Request request) throws IOException {
        PostHostData postHostData = Util.fromJson(request, PostHostData.class);
        Service service = dataAccess.getService(postHostData.serviceId);
        if (service == null) {
            throw new RuntimeException("Could not find service");
        }
        User user = accessHelper.checkIfUserCanModify(request, service.getTeamId(), "add a host");
        Team team = dataAccess.getTeam(service.getTeamId());

        if (postHostData.count < 1) {
            throw new RuntimeException("count must to at least 1");
        } else if (postHostData.count > 10) {
            logger.warn("Reducing count to 10, was {}", postHostData.count);
            postHostData.count = 10;
        }

        if (!config.dataCenters.contains(postHostData.dataCenter)) {
            throw new RuntimeException("Unknown data center");
        }
        if (!config.networks.contains(postHostData.network)) {
            throw new RuntimeException("Unknown network");
        }
        if (!config.envs.contains(postHostData.env)) {
            throw new RuntimeException("Unknown env");
        }
        if (!config.sizes.contains(postHostData.size)) {
            throw new RuntimeException("Unknown size");
        }
        
        List<Module> modules = dataAccess.getModules(postHostData.serviceId);
        Module module = null;
        for (Module temp : modules) {
            if (temp.getModuleId().equals(postHostData.moduleId)) {
                module = temp;
            }
        }
        if (module == null) {
            throw new RuntimeException("Unknown module");
        }

        //calc host name
        String prefix = postHostData.dataCenter + "-" + postHostData.network + "-" + module.getHostAbbr() + "-";
        int len = prefix.length();
        int num = 0;
        List<Host> hosts = dataAccess.getHosts(postHostData.serviceId);
        for (Host existingHost : hosts) {
            String existingHostName = existingHost.getHostName();
            if (existingHostName.startsWith(prefix) && existingHostName.length() > len) {
                String numPart = existingHostName.substring(len);
                try {
                    int temp = Integer.parseInt(numPart);
                    if (temp > num) {
                        num = temp;
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing int from last part of {}", existingHostName);
                }
            }
        }
        num++;
        for (int c = 0; c < postHostData.count; c++) {
            String numStr = Integer.toString(num + c);
            numStr = "000".substring(numStr.length()) + numStr;

            Host host = new Host(prefix + numStr,
                    postHostData.serviceId,
                    "Creating...",
                    postHostData.moduleId,
                    postHostData.dataCenter,
                    postHostData.network,
                    postHostData.env,
                    postHostData.size);
            dataAccess.saveHost(host);

            WorkItem workItemCreate = new WorkItem(Const.TYPE_HOST, Const.OPERATION_CREATE, user, team, service, module, host, null);
            WorkItem workItemDeploy = new WorkItem(Const.TYPE_HOST, Const.OPERATION_DEPLOY, user, team, service, module, host, null);

            workItemCreate.getHost().version = postHostData.version;
            workItemCreate.getHost().reason = postHostData.reason;
            workItemCreate.setNextId(workItemDeploy.getId());
            workItemDeploy.getHost().version = postHostData.version;
            workItemDeploy.getHost().reason = postHostData.reason;

            dataAccess.saveWorkItem(workItemCreate);
            dataAccess.saveWorkItem(workItemDeploy);

            workItemProcess.sendWorkItem(workItemCreate);
        }
    }

    private void deploySoftware(Request request) throws IOException {
        PutDeploySoftwareData putHostData = Util.fromJson(request, PutDeploySoftwareData.class);
        Service service = null;
        Module module = null;
        List<WorkItem> workItems = new ArrayList<>(putHostData.hosts.size());
        User user = null;

        Team team = null;

        for (Map.Entry<String, String> entry : putHostData.hosts.entrySet()) {
            if (entry.getValue().equalsIgnoreCase("true")) {
                Host host = dataAccess.getHost(putHostData.serviceId, entry.getKey());
                if (host != null && 
                        host.getServiceId().equals(putHostData.serviceId) && 
                        host.getStatus().equals(Const.NO_STATUS) &&
                        host.getNetwork().equals(putHostData.network) &&
                        host.getModuleId().equals(putHostData.moduleId)) {
                    if (service == null) {
                        service = dataAccess.getService(host.getServiceId());
                        if (service == null) {
                            throw new RuntimeException("Could not find service");
                        }
                        user = accessHelper.checkIfUserCanModify(request, service.getTeamId(), "deploy software to host");
                        team = dataAccess.getTeam(service.getTeamId());
                    }
                    if (module == null || !module.getModuleId().equals(host.getModuleId())) {
                        module = dataAccess.getModule(host.getServiceId(), host.getModuleId());
                    }
                    WorkItem workItem = new WorkItem(Const.TYPE_HOST, Const.OPERATION_DEPLOY, user, team, service, module, host, null);
                    workItem.getHost().version = putHostData.version;
                    workItem.getHost().reason = putHostData.reason;
                    if (workItems.isEmpty()) {
                        host.setStatus("Deploying...");
                    } else {
                        host.setStatus("Deploy Queued");
                    }
                    dataAccess.updateHost(host);
                    workItems.add(workItem);
                }
            }
        }
        String prevId = null;
        int size = workItems.size();
        if (size > 0) {
            for (int i = 0; i < size; i++) {
                WorkItem workItem = workItems.get(size - i - 1);
                workItem.setNextId(prevId);
                prevId = workItem.getId();
                dataAccess.saveWorkItem(workItem);
            }
            workItemProcess.sendWorkItem(workItems.get(0));
        }
    }

    private void restartHost(Request request) throws IOException {
        PutRestartHostData putRestartHostData = Util.fromJson(request, PutRestartHostData.class);
        Service service = null;
        Module module = null;
        List<WorkItem> workItems = new ArrayList<>(putRestartHostData.hosts.size());
        User user = null;

        Team team = null;

        for (Map.Entry<String, String> entry : putRestartHostData.hosts.entrySet()) {
            if (entry.getValue().equalsIgnoreCase("true")) {
                Host host = dataAccess.getHost(putRestartHostData.serviceId, entry.getKey());
                if (host != null && 
                        host.getServiceId().equals(putRestartHostData.serviceId) && 
                        host.getStatus().equals(Const.NO_STATUS) &&
                        host.getNetwork().equals(putRestartHostData.network) &&
                        host.getModuleId().equals(putRestartHostData.moduleId)) {
                    if (service == null) {
                        service = dataAccess.getService(host.getServiceId());
                        if (service == null) {
                            throw new RuntimeException("Could not find service");
                        }
                        user = accessHelper.checkIfUserCanModify(request, service.getTeamId(), "restart host");
                        team = dataAccess.getTeam(service.getTeamId());
                    }
                    if (module == null || !module.getModuleId().equals(host.getModuleId())) {
                        module = dataAccess.getModule(host.getServiceId(), host.getModuleId());
                    }
                    WorkItem workItem = new WorkItem(Const.TYPE_HOST, Const.OPERATION_RESTART, user, team, service, module, host, null);
                    if (workItems.isEmpty()) {
                        host.setStatus("Restarting...");
                    } else {
                        host.setStatus("Restart Queued");
                    }
                    dataAccess.updateHost(host);
                    workItems.add(workItem);
                }
            }
        }
        String prevId = null;
        int size = workItems.size();
        if (size > 0) {
            for (int i = 0; i < size; i++) {
                WorkItem workItem = workItems.get(size - i - 1);
                workItem.setNextId(prevId);
                prevId = workItem.getId();
                dataAccess.saveWorkItem(workItem);
            }
            workItemProcess.sendWorkItem(workItems.get(0));
        }
    }

    private void deleteHost(Request request, String serviceId, String hostId) throws IOException {
        Host host = dataAccess.getHost(serviceId, hostId);
        if (host == null) {
            logger.info("Could not find host with id {}", hostId);
            return;
        }
        Service service = dataAccess.getService(host.getServiceId());
        if (service == null) {
            throw new RuntimeException("Could not find service");
        }
        User user = accessHelper.checkIfUserCanModify(request, service.getTeamId(), "deleting a host");
        Team team = dataAccess.getTeam(service.getTeamId());
        host.setStatus("Deleting...");
        dataAccess.updateHost(host);
        WorkItem workItem = new WorkItem(Const.TYPE_HOST, Const.OPERATION_DELETE, user, team, service, null, host, null);
        dataAccess.saveWorkItem(workItem);
        workItemProcess.sendWorkItem(workItem);
    }

    private void backfillHosts(Request request) throws IOException {
        User user = accessHelper.checkIfUserIsOps(request, "Backfill");
        BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()));
        String s = reader.readLine();
        while (s != null && !s.isEmpty()) {
            String[] parts = s.split(",");
            if (parts.length == 7) {
                backfillHost(
                        parts[0].trim(), 
                        parts[1].trim(), 
                        parts[2].trim(), 
                        parts[3].trim(), 
                        parts[4].trim(), 
                        parts[5].trim(), 
                        parts[6].trim(), 
                        user);
            }
            s = reader.readLine();
        }
    }

    private void backfillHost(String serviceAbbr, String moduleName, String hostName, String dataCenter, String network, String env, String size, User user) {
        if (config.dataCenters.contains(dataCenter)
                && config.networks.contains(network)
                && config.envs.contains(env)
                && config.sizes.contains(size)) {
            for (Service service : dataAccess.getServices()) {
                if (service.getServiceAbbr().equalsIgnoreCase(serviceAbbr)) {
                    List<Host> hosts = dataAccess.getHosts(service.getServiceId());
                    for (Host host : hosts) {
                        if (host.getHostName().equalsIgnoreCase(hostName)) {
                            logger.warn("There already exists host '{}' on service '{}'", hostName, serviceAbbr);
                            return;
                        }
                    }
                    Module module = null;
                    List<Module> modules = dataAccess.getModules(service.getServiceId());
                    for (Module temp : modules) {
                        if (temp.getModuleName().equalsIgnoreCase(moduleName)) {
                            module = temp;
                        }
                    };
                    if (module == null) {
                        logger.warn("Could not find module with name {} in service {}", moduleName, serviceAbbr);
                        return;
                    }
                    Host host = new Host(hostName,
                            service.getServiceId(),
                            Const.NO_STATUS,
                            module.getModuleId(),
                            dataCenter,
                            network,
                            env,
                            size);
                    dataAccess.saveHost(host);

                    Audit audit = new Audit();
                    audit.serviceId = service.getServiceId();
                    audit.timePerformed = new Date();
                    audit.timeRequested = new Date();
                    audit.requestor = user.getUsername();
                    audit.type = Const.TYPE_HOST;
                    audit.operation = Const.OPERATION_CREATE;
                    audit.moduleName = module.getModuleName();
                    audit.hostName = hostName;
                    Map<String, String> notes = new HashMap<>();
                    notes.put("reason", "Backfill via OPS tool.");
                    audit.notes = gson.toJson(notes);
                    dataAccess.saveAudit(audit, "");

                    return;
                }
            }
            logger.warn("Could not find a service with the abbr '{}'", serviceAbbr);
        }
    }

    private void addVIPs(Request request) throws IOException {
        PostHostVipData data = Util.fromJson(request, PostHostVipData.class);
        Service service = dataAccess.getService(data.serviceId);
        if (service == null) {
            throw new RuntimeException("Could not find service");
        }
        User user = accessHelper.checkIfUserCanModify(request, service.getTeamId(), "add a host vip");
        Team team = dataAccess.getTeam(service.getTeamId());
        List<Host> hosts = dataAccess.getHosts(data.serviceId);
        List<Vip> vips = dataAccess.getVips(data.serviceId);
        Module module = null;
        for (Map.Entry<String, String> entry : data.hosts.entrySet()) {
            if (entry.getValue().equalsIgnoreCase("true")) {
                boolean found = false;
                for (Host host : hosts) {
                    if (entry.getKey().equals(host.getHostId())) {
                        found = true;
                        for (Map.Entry<String, String> entry2 : data.vips.entrySet()) {
                            if (entry2.getValue().equalsIgnoreCase("true")) {
                                boolean found2 = false;
                                for (Vip vip : vips) {
                                    if (entry2.getKey().equals(vip.getVipId())) {
                                        found2 = true;
                                        if (host.getNetwork().equals(vip.getNetwork())) {
                                            if (module == null || host.getModuleId().equals(module.getModuleId())) {
                                                for (Module temp : dataAccess.getModules(host.getServiceId())) {
                                                    if (temp.getModuleId().equals(host.getModuleId())) {
                                                        module = temp;
                                                    }
                                                }
                                            }
                                            dataAccess.saveVipRef(new VipRef(host.getHostId(), vip.getVipId(), "Adding..."));
                                            WorkItem workItem = new WorkItem(Const.TYPE_HOST_VIP, "add", user, team, service, module, host, vip);
                                            dataAccess.saveWorkItem(workItem);
                                            workItemProcess.sendWorkItem(workItem);
                                        } else {
                                            logger.warn("Request to add {} to {} reject because they are not on the same network", host.getHostName(), vip.getVipName());
                                        }
                                    }
                                }
                                if (!found2) {
                                    logger.error("Asked to add host(s) to vip {}, but vip is not on service {}", entry2.getKey(), data.serviceId);
                                }
                            }
                        }
                    }
                }
                if (!found) {
                    logger.error("Asked to add vip(s) to host {}, but host is not on service {}", entry.getKey(), data.serviceId);
                }
            }
        }
    }

    private void deleteVIP(Request request, String serviceId, String hostId, String vipId) throws IOException {
        Service service = dataAccess.getService(serviceId);
        if (service == null) {
            throw new RuntimeException("Could not find service");
        }
        User user = accessHelper.checkIfUserCanModify(request, service.getTeamId(), "delete host vip");
        Team team = dataAccess.getTeam(service.getTeamId());
        VipRef vipRef = dataAccess.getVipRef(hostId, vipId);
        if (vipRef == null) {
            return;
        }
        Host host = dataAccess.getHost(serviceId, hostId);
        if (host == null) {
            throw new RuntimeException("Could not find host");
        }
        Vip vip = dataAccess.getVip(serviceId, vipId);
        if (vip == null) {
            throw new RuntimeException("Could not find vip");
        }
        vipRef.setStatus("Removing...");
        dataAccess.updateVipRef(vipRef);
        WorkItem workItem = new WorkItem(Const.TYPE_HOST_VIP, Const.OPERATION_DELETE, user, team, service, null, host, vip);
        dataAccess.saveWorkItem(workItem);
        workItemProcess.sendWorkItem(workItem);
    }

}
