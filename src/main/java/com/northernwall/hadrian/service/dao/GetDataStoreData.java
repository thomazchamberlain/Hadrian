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
package com.northernwall.hadrian.service.dao;

import com.northernwall.hadrian.domain.DataStore;

public class GetDataStoreData {
    public String dataStoreId;
    public String name;
    public String serviceId;
    public String type;
    public String network;
    
    public static GetDataStoreData create(DataStore dataStore) {
        GetDataStoreData temp = new GetDataStoreData();
        temp.dataStoreId = dataStore.getDataStoreId();
        temp.name = dataStore.getName();
        temp.serviceId = dataStore.getServiceId();
        temp.type = dataStore.getType();
        temp.network = dataStore.getNetwork();
        return temp;
    }

}
