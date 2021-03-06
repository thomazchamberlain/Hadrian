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
package com.northernwall.hadrian.workItem.simple;

import com.codahale.metrics.MetricRegistry;
import com.google.gson.Gson;
import com.northernwall.hadrian.Const;
import com.northernwall.hadrian.domain.WorkItem;
import com.northernwall.hadrian.parameters.Parameters;
import com.northernwall.hadrian.workItem.Result;
import com.northernwall.hadrian.workItem.WorkItemProcessor;
import com.northernwall.hadrian.workItem.WorkItemSender;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Richard Thurston
 */
public class SimpleWorkItemSender extends WorkItemSender {
    private final static Logger logger = LoggerFactory.getLogger(SimpleWorkItemSender.class);

    private final String url;

    private final Gson gson;
    private final OkHttpClient client;

    public SimpleWorkItemSender(Parameters parameters, OkHttpClient client, MetricRegistry metricRegistry) {
        super(parameters);
        this.client = client;
        gson = new Gson();

        url = parameters.getString(Const.SIMPLE_WORK_ITEM_URL, Const.SIMPLE_WORK_ITEM_URL_DEFAULT);
    }

    @Override
    public void setWorkItemProcessor(WorkItemProcessor workItemProcessor) {
    }

    @Override
    public Result sendWorkItem(WorkItem workItem) throws IOException {
        RequestBody body = RequestBody.create(Const.JSON_MEDIA_TYPE, gson.toJson(workItem));
        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-Request-Id", workItem.getId())
                .post(body)
                .build();
        Response response = client.newCall(request).execute();
        logger.info("Sent workitem {} and got response {}", workItem.getId(), response.code());
        if (response.isSuccessful()) {
            return Result.wip;
        }
        return Result.error;
    }

}
