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
package com.northernwall.hadrian.webhook;

import com.google.gson.Gson;
import com.northernwall.hadrian.Const;
import com.northernwall.hadrian.domain.Vip;
import com.northernwall.hadrian.domain.Host;
import com.northernwall.hadrian.domain.Service;
import com.northernwall.hadrian.domain.User;
import com.northernwall.hadrian.domain.WorkItem;
import com.northernwall.hadrian.webhook.dao.HostData;
import com.northernwall.hadrian.webhook.dao.CreateVipContainer;
import com.northernwall.hadrian.webhook.dao.UpdateHostContainer;
import com.northernwall.hadrian.webhook.dao.CreateHostVipContainer;
import com.northernwall.hadrian.webhook.dao.CreateHostContainer;
import com.northernwall.hadrian.webhook.dao.CreateServiceContainer;
import com.northernwall.hadrian.webhook.dao.UpdateVipContainer;
import com.northernwall.hadrian.webhook.dao.ServiceData;
import com.northernwall.hadrian.webhook.dao.VipData;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import java.io.IOException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Richard Thurston
 */
public class WebHookSender {
    private final static Logger logger = LoggerFactory.getLogger(WebHookSender.class);

    private final String callbackUrl;
    private final String serviceUrl;
    private final String hostUrl;
    private final String vipUrl;
    private final String hostVipUrl;

    private final Gson gson;
    private final OkHttpClient client;

    public WebHookSender(Properties properties, OkHttpClient client) {
        this.client = client;
        gson = new Gson();

        int port = Integer.parseInt(properties.getProperty(Const.JETTY_PORT, Const.JETTY_PORT_DEFAULT));

        callbackUrl = Const.HTTP + properties.getProperty(Const.WEB_HOOK_CALLBACK_HOST, Const.WEB_HOOK_CALLBACK_HOST_DEFAULT) + ":" + port + "/webhook/callback";
        serviceUrl = Const.HTTP + properties.getProperty(Const.WEB_HOOK_SERVICE_URL, Const.WEB_HOOK_SERVICE_URL_DEFAULT);
        hostUrl = Const.HTTP + properties.getProperty(Const.WEB_HOOK_HOST_URL, Const.WEB_HOOK_HOST_URL_DEFAULT);
        vipUrl = Const.HTTP + properties.getProperty(Const.WEB_HOOK_HOST_VIP_URL, Const.WEB_HOOK_VIP_URL_DEFAULT);
        hostVipUrl = Const.HTTP + properties.getProperty(Const.WEB_HOOK_HOST_VIP_URL, Const.WEB_HOOK_HOST_VIP_URL_DEFAULT);
    }

    public void createService(Service service, User user) throws IOException {
        CreateServiceContainer data = new CreateServiceContainer();
        data.operation = "create";
        data.service = ServiceData.create(service);

        post(serviceUrl, data, user);
    }

    public void createHost(Service service, Host host, User user) throws IOException {
        CreateHostContainer data = new CreateHostContainer();
        data.operation = "create";
        data.service = ServiceData.create(service);
        data.host = HostData.create(host);

        post(hostUrl, data, user);
    }

    public void updateHost(Service service, Host host, WorkItem workItem, User user) throws IOException {
        UpdateHostContainer data = new UpdateHostContainer();
        data.operation = "update";
        data.service = ServiceData.create(service);
        data.host = HostData.create(host);
        data.newEnv = workItem.getEnv();
        data.newSize = workItem.getSize();
        data.newVersion = workItem.getVersion();

        post(hostUrl, data, user);
    }

    public void deleteHost(Service service, Host host, User user) throws IOException {
        CreateHostContainer data = new CreateHostContainer();
        data.operation = "delete";
        data.service = ServiceData.create(service);
        data.host = HostData.create(host);

        post(hostUrl, data, user);
    }

    public void createVip(Service service, Vip vip, User user) throws IOException {
        CreateVipContainer data = new CreateVipContainer();
        data.operation = "create";
        data.service = ServiceData.create(service);
        data.vip = VipData.create(vip);

        post(vipUrl, data, user);
    }

    public void updateVip(Service service, Vip vip, WorkItem workItem, User user) throws IOException {
        UpdateVipContainer data = new UpdateVipContainer();
        data.operation = "update";
        data.service = ServiceData.create(service);
        data.vip = VipData.create(vip);
        data.newExternal = workItem.getExternal();
        data.newServicePort = workItem.getServicePort();

        post(vipUrl, data, user);
    }

    public void deleteVip(Service service, Vip vip, User user) throws IOException {
        CreateVipContainer data = new CreateVipContainer();
        data.operation = "delete";
        data.service = ServiceData.create(service);
        data.vip = VipData.create(vip);

        post(vipUrl, data, user);
    }

    public void addHostVip(Service service, Host host, Vip vip, User user) throws IOException {
        CreateHostVipContainer data = new CreateHostVipContainer();
        data.operation = "add";
        data.service = ServiceData.create(service);
        data.host = HostData.create(host);
        data.vip = VipData.create(vip);

        post(hostVipUrl, data, user);
    }

    public void deleteHostVip(Service service, Host host, Vip vip, User user) throws IOException {
        CreateHostVipContainer data = new CreateHostVipContainer();
        data.operation = "delete";
        data.service = ServiceData.create(service);
        data.host = HostData.create(host);
        data.vip = VipData.create(vip);

        post(hostVipUrl, data, user);
    }

    private void post(String url, CreateServiceContainer data, User user) throws IOException {
        data.callbackUrl = callbackUrl;
        data.username = user.getUsername();
        data.fullname = user.getFullName();
        RequestBody body = RequestBody.create(Const.JSON_MEDIA_TYPE, gson.toJson(data));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        client.newCall(request).execute();
    }

}
