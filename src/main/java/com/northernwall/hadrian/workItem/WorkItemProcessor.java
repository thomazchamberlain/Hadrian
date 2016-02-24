/*
 * Copyright 2015 Richard Thurston.
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
package com.northernwall.hadrian.workItem;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.gson.Gson;
import com.northernwall.hadrian.Const;
import com.northernwall.hadrian.Util;
import com.northernwall.hadrian.db.DataAccess;
import com.northernwall.hadrian.domain.Audit;
import com.northernwall.hadrian.domain.Host;
import com.northernwall.hadrian.domain.Service;
import com.northernwall.hadrian.domain.Vip;
import com.northernwall.hadrian.domain.VipRef;
import com.northernwall.hadrian.domain.WorkItem;
import com.northernwall.hadrian.workItem.dao.CallbackData;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkItemProcessor {

    private final static Logger logger = LoggerFactory.getLogger(WorkItemProcessor.class);

    private final DataAccess dataAccess;
    private final WorkItemSender webHookSender;
    private final Timer timerProcess;
    private final Timer timerCalback;
    private final Meter meterSuccess;
    private final Meter meterFail;
    private final Gson gson;


    public WorkItemProcessor(DataAccess dataAccess, WorkItemSender webHookSender, MetricRegistry metricRegistry) {
        this.dataAccess = dataAccess;
        this.webHookSender = webHookSender;

        timerProcess = metricRegistry.timer("workItem.sendWorkItem");
        timerCalback = metricRegistry.timer("workItem.callback.process");
        meterSuccess = metricRegistry.meter("workItem.callback.success");
        meterFail = metricRegistry.meter("workItem.callback.fail");
        gson = new Gson();
    }

    public void sendWorkItem(WorkItem workItem) throws IOException {
        boolean done = false;
        Timer.Context context = timerProcess.time();
        try {
            done = webHookSender.sendWorkItem(workItem);
        } finally {
            context.stop();
        }
        if (done) {
            logger.info("Work item sender says work item  {} has been process, no callback expected.", workItem.getId());
            CallbackData callbackData = new CallbackData();
            callbackData.requestId = workItem.getId();
            callbackData.errorCode = 0;
            callbackData.errorDescription = " ";
            callbackData.status = Const.WORK_ITEM_STATUS_SUCCESS;
            callbackData.output = "no output";
            processCallback(callbackData);
        }
    }

    public void processCallback(CallbackData callbackData) throws IOException {
        Timer.Context context = timerCalback.time();
        try {
            WorkItem workItem = dataAccess.getWorkItem(callbackData.requestId);
            if (workItem == null) {
                throw new RuntimeException("Could not find work item " + callbackData.requestId);
            }
            dataAccess.deleteWorkItem(callbackData.requestId);

            boolean status = callbackData.status.equalsIgnoreCase(Const.WORK_ITEM_STATUS_SUCCESS);
            if (status) {
                meterSuccess.mark();
            } else {
                meterFail.mark();
            }

            Map<String, String> notes = new HashMap<>();
            if (workItem.getType().equalsIgnoreCase(Const.TYPE_MODULE)) {
                if (workItem.getOperation().equalsIgnoreCase(Const.OPERATION_CREATE)) {
                    notes.put("template", workItem.getMainModule().template);
                    notes.put("type", workItem.getMainModule().moduleType);
                } else if (workItem.getOperation().equalsIgnoreCase(Const.OPERATION_UPDATE)) {
                } else if (workItem.getOperation().equalsIgnoreCase(Const.OPERATION_DELETE)) {
                } else {
                    throw new RuntimeException("Unknown callback " + workItem.getType() + " " + workItem.getOperation());
                }
            } else if (workItem.getType().equalsIgnoreCase(Const.TYPE_HOST)) {
                if (workItem.getOperation().equalsIgnoreCase(Const.OPERATION_CREATE)) {
                    createHost(workItem, status);
                    notes.put("env", workItem.getHost().env);
                    notes.put("size",workItem.getHost().size);
                    notes.put("reason",workItem.getHost().reason);
                } else if (workItem.getOperation().equalsIgnoreCase(Const.OPERATION_DEPLOY)) {
                    deploySoftware(workItem, status);
                    notes.put("version", workItem.getHost().version);
                    notes.put("reason", workItem.getHost().reason);
                } else if (workItem.getOperation().equalsIgnoreCase(Const.OPERATION_RESTART)) {
                    restartHost(workItem, status);
                } else if (workItem.getOperation().equalsIgnoreCase(Const.OPERATION_DELETE)) {
                    deleteHost(workItem, status);
                } else {
                    throw new RuntimeException("Unknown callback " + workItem.getType() + " " + workItem.getOperation());
                }
            } else if (workItem.getType().equalsIgnoreCase(Const.TYPE_VIP)) {
                if (workItem.getOperation().equalsIgnoreCase(Const.OPERATION_CREATE)) {
                    notes.put("protocol", workItem.getVip().protocol);
                    notes.put("vip_port", Integer.toString(workItem.getVip().vipPort));
                    notes.put("service_port", Integer.toString(workItem.getVip().servicePort));
                    notes.put("external", Boolean.toString(workItem.getVip().external));
                    createVip(workItem, status);
                } else if (workItem.getOperation().equalsIgnoreCase(Const.OPERATION_UPDATE)) {
                    notes.put("protocol", workItem.getVip().protocol);
                    notes.put("vip_port", Integer.toString(workItem.getVip().vipPort));
                    notes.put("service_port", Integer.toString(workItem.getVip().servicePort));
                    notes.put("external", Boolean.toString(workItem.getVip().external));
                    updateVip(workItem, status);
                } else if (workItem.getOperation().equalsIgnoreCase(Const.OPERATION_DELETE)) {
                    deleteVip(workItem, status);
                } else {
                    throw new RuntimeException("Unknown callback " + workItem.getType() + " " + workItem.getOperation());
                }
            } else if (workItem.getType().equalsIgnoreCase(Const.TYPE_HOST_VIP)) {
                if (workItem.getOperation().equalsIgnoreCase("add")) {
                    addHostVip(workItem, status);
                } else if (workItem.getOperation().equalsIgnoreCase(Const.OPERATION_DELETE)) {
                    deleteHostVip(workItem, status);
                } else {
                    throw new RuntimeException("Unknown callback " + workItem.getType() + " " + workItem.getOperation());
                }
            } else {
                throw new RuntimeException("Unknown callback " + workItem.getType() + " " + workItem.getOperation());
            }
            if (status) {
                Audit audit = new Audit();
                audit.serviceId = workItem.getService().serviceId;
                audit.timePerformed = Util.getGmt();
                audit.timeRequested = workItem.getRequestDate();
                audit.requestor = workItem.getUsername();
                audit.type = workItem.getType();
                audit.operation = workItem.getOperation();
                if (workItem.getMainModule() != null) {
                    audit.moduleName = workItem.getMainModule().moduleName;
                }
                if (workItem.getHost() != null) {
                    audit.hostName = workItem.getHost().hostName;
                }
                if (workItem.getVip() != null) {
                    audit.vipName = workItem.getVip().vipName;
                }
                if (notes == null || notes.isEmpty()) {
                    audit.notes = "";
                } else {
                    audit.notes = gson.toJson(notes);
                }
                dataAccess.saveAudit(audit, callbackData.output);
            } else {
                deleteNextWorkItem(workItem.getNextId());
            }
        } finally {
            context.stop();
        }
    }

    private void deleteNextWorkItem(String nextId) {
        if (nextId != null) {
            WorkItem nextWorkItem = dataAccess.getWorkItem(nextId);
            deleteNextWorkItem(nextWorkItem.getNextId());
            dataAccess.deleteWorkItem(nextId);
        }
    }

    private void createHost(WorkItem workItem, boolean status) throws IOException {
        Host host = dataAccess.getHost(workItem.getService().serviceId, workItem.getHost().hostId);
        if (host == null) {
            logger.warn("Could not find host {} being created", workItem.getHost().hostId);
            return;
        }
        if (status) {
            if (workItem.getNextId() != null) {
                WorkItem nextWorkItem = dataAccess.getWorkItem(workItem.getNextId());
                if (nextWorkItem != null) {
                    host.setStatus("Deploying...");
                    dataAccess.updateHost(host);
                    sendWorkItem(nextWorkItem);
                } else {
                    logger.warn("Odd, the deploy work item {} for create host {} could not be found", workItem.getNextId(), host.getHostName());
                    host.setStatus(Const.NO_STATUS);
                    dataAccess.updateHost(host);
                }
            } else {
                logger.warn("Odd, create host {} work item has no deploy work item id", host.getHostName());
            }
        } else {
            logger.warn("Callback for {} failed with status {}", host.getHostId(), status);
            dataAccess.deleteHost(host.getServiceId(), host.getHostId());
        }
    }

    private void deploySoftware(WorkItem workItem, boolean status) throws IOException {
        Host host = dataAccess.getHost(workItem.getService().serviceId, workItem.getHost().hostId);
        if (host == null) {
            logger.warn("Could not find host {} being updated", workItem.getHost().hostId);
            return;
        }
        if (status) {
            host.setStatus(Const.NO_STATUS);
            dataAccess.updateHost(host);

            if (workItem.getNextId() == null) {
                //No more hosts to update in the chain
                return;
            }

            WorkItem nextWorkItem = dataAccess.getWorkItem(workItem.getNextId());
            Host nextHost = dataAccess.getHost(nextWorkItem.getService().serviceId, nextWorkItem.getHost().hostId);
            if (nextHost == null) {
                logger.error("Finished updating {}, next work item is {}, but could not find it.", workItem.getHost().hostId, nextWorkItem.getHost().hostId);
                return;
            }
            nextHost.setStatus("Deploying...");
            dataAccess.saveHost(nextHost);

            sendWorkItem(nextWorkItem);
        } else {
            logger.warn("Callback for {} failed with status {}", workItem.getHost().hostId, status);
        }
    }

    private void restartHost(WorkItem workItem, boolean status) throws IOException {
        Host host = dataAccess.getHost(workItem.getService().serviceId, workItem.getHost().hostId);
        if (host == null) {
            logger.warn("Could not find host {} being restarted", workItem.getHost().hostId);
            return;
        }
        if (status) {
            host.setStatus(Const.NO_STATUS);
            dataAccess.updateHost(host);

            if (workItem.getNextId() == null) {
                //No more hosts to restart in the chain
                return;
            }

            WorkItem nextWorkItem = dataAccess.getWorkItem(workItem.getNextId());
            Host nextHost = dataAccess.getHost(nextWorkItem.getService().serviceId, nextWorkItem.getHost().hostId);
            if (nextHost == null) {
                logger.error("Finished restarting {}, next work item is {}, but could not find it.", workItem.getHost().hostId, nextWorkItem.getHost().hostId);
                return;
            }
            nextHost.setStatus("Restarting...");
            dataAccess.saveHost(nextHost);

            sendWorkItem(nextWorkItem);
        } else {
            logger.warn("Callback for {} failed with status {}", workItem.getHost().hostId, status);
        }
    }

    private void deleteHost(WorkItem workItem, boolean status) throws IOException {
        Host host = dataAccess.getHost(workItem.getService().serviceId, workItem.getHost().hostId);
        if (host == null) {
            logger.warn("Could not find host {} to delete.", workItem.getHost().hostId);
            return;
        }
        if (status) {
            dataAccess.deleteHost(host.getServiceId(), host.getHostId());
        } else {
            logger.warn("Callback for {} failed with status {}", host.getHostId(), status);
            host.setStatus(Const.NO_STATUS);
            dataAccess.updateHost(host);
        }
    }

    private void createVip(WorkItem workItem, boolean status) throws IOException {
        Vip vip = dataAccess.getVip(workItem.getService().serviceId, workItem.getVip().vipId);
        if (vip == null) {
            logger.warn("Could not find vip {} being created", workItem.getVip().vipId);
            return;
        }
        if (status) {
            vip.setStatus(Const.NO_STATUS);
            dataAccess.updateVip(vip);
        } else {
            logger.warn("Callback for {} failed with status {}", vip.getVipId(), status);
            dataAccess.deleteVip(vip.getServiceId(), vip.getVipId());
        }
    }

    private void updateVip(WorkItem workItem, boolean status) throws IOException {
        Vip vip = dataAccess.getVip(workItem.getService().serviceId, workItem.getVip().vipId);
        if (vip == null) {
            logger.warn("Could not find vip {} being updated", workItem.getVip().vipId);
            return;
        }
        if (status) {
            vip.setStatus(Const.NO_STATUS);
            vip.setExternal(workItem.getVip().external);
            vip.setServicePort(workItem.getVip().servicePort);
            dataAccess.updateVip(vip);
        } else {
            logger.warn("Callback for {} failed with status {}", workItem.getVip().vipId, status);
        }
    }

    private void deleteVip(WorkItem workItem, boolean status) throws IOException {
        Vip vip = dataAccess.getVip(workItem.getService().serviceId, workItem.getVip().vipId);
        if (vip == null) {
            logger.error("Could not find end point {} to delete.", workItem.getVip().vipId);
            return;
        }
        if (status) {
            dataAccess.deleteVipRefs(vip.getVipId());
            dataAccess.deleteVip(vip.getServiceId(), vip.getVipId());
        } else {
            logger.warn("Callback for {} failed with status {}", vip.getVipId(), status);
            vip.setStatus(Const.NO_STATUS);
            dataAccess.updateVip(vip);
        }
    }

    private void addHostVip(WorkItem workItem, boolean status) {
        VipRef vipRef = dataAccess.getVipRef(workItem.getHost().hostId, workItem.getVip().vipId);
        if (vipRef == null) {
            logger.error("Could not find end point ref {} {} to create.", workItem.getHost().hostId, workItem.getVip().vipId);
            return;
        }
        if (status) {
            vipRef.setStatus(Const.NO_STATUS);
            dataAccess.updateVipRef(vipRef);
        } else {
            logger.warn("Callback for {} {} failed with status {}", vipRef.getHostId(), vipRef.getVipId(), status);
            dataAccess.deleteVipRef(vipRef.getHostId(), vipRef.getVipId());
        }
    }

    private void deleteHostVip(WorkItem workItem, boolean status) {
        VipRef vipRef = dataAccess.getVipRef(workItem.getHost().hostId, workItem.getVip().vipId);
        if (vipRef == null) {
            logger.error("Could not find end point ref {} {} to delete.", workItem.getHost().hostId, workItem.getVip().vipId);
            return;
        }
        if (status) {
            dataAccess.deleteVipRef(vipRef.getHostId(), vipRef.getVipId());
        } else {
            logger.warn("Callback for {} {} failed with status {}", vipRef.getHostId(), vipRef.getVipId(), status);
            vipRef.setStatus(Const.NO_STATUS);
            dataAccess.updateVipRef(vipRef);
        }
    }

}
