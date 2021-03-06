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
package com.northernwall.hadrian.workItem.noop;

import com.northernwall.hadrian.domain.WorkItem;
import com.northernwall.hadrian.parameters.Parameters;
import com.northernwall.hadrian.workItem.Result;
import com.northernwall.hadrian.workItem.WorkItemProcessor;
import com.northernwall.hadrian.workItem.WorkItemSender;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoopWorkItemSender extends WorkItemSender {

    private final static Logger logger = LoggerFactory.getLogger(NoopWorkItemSender.class);

    public NoopWorkItemSender(Parameters parameters) {
        super(parameters);
    }

    @Override
    public void setWorkItemProcessor(WorkItemProcessor workItemProcessor) {
    }

    @Override
    public Result sendWorkItem(WorkItem workItem) throws IOException {
        logger.info(workItem.toString());
        return Result.success;
    }

}
