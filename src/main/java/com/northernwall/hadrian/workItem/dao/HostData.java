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
package com.northernwall.hadrian.workItem.dao;

import com.northernwall.hadrian.domain.Host;

public class HostData {
    public String hostId;
    public String hostName;
    public String dataCenter;
    public String network;
    public String env;
    public String size;
    public String version;

    public static HostData create(Host host) {
        if (host == null) {
            return null;
        }
        HostData temp = new HostData();
        temp.hostId = host.getHostId();
        temp.hostName = host.getHostName();
        temp.dataCenter = host.getDataCenter();
        temp.network = host.getNetwork();
        temp.env = host.getEnv();
        temp.size = host.getSize();
        temp.version = null;
        return temp;
    }

}