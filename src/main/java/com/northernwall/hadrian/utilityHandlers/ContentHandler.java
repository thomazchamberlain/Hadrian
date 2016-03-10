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
package com.northernwall.hadrian.utilityHandlers;

import com.northernwall.hadrian.Const;
import java.io.IOException;
import java.io.InputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 *
 * @author Richard Thurston
 */
public class ContentHandler extends AbstractHandler {

    private final String rootPath;

    public ContentHandler(String rootPath) {
        this.rootPath = rootPath;
    }

    @Override
    public void handle(String target, Request request, HttpServletRequest httpRequest, HttpServletResponse response) throws IOException, ServletException {
        String path;
        if (target.equals("/ui/")) {
            path = rootPath + "/index.html";
        } else {
            path = rootPath + target.substring(3);
        }
        if (getContent(response, path)) {
            response.setStatus(200);
            request.setHandled(true);
        }
    }

    private boolean getContent(HttpServletResponse response, String resource) throws IOException {
        byte[] buffer = new byte[50 * 1024];
        try (InputStream is = this.getClass().getResourceAsStream(resource)) {
            if (is == null) {
                return false;
            }
            if (resource.toLowerCase().endsWith(".html")) {
                response.addHeader("X-Frame-Options", "DENY");
                response.setContentType(Const.HTML);
            }
            int len = is.read(buffer);
            while (len != -1) {
                response.getOutputStream().write(buffer, 0, len);
                len = is.read(buffer);
            }
        }
        return true;
    }

}
