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

import com.northernwall.hadrian.access.AccessHelper;
import com.northernwall.hadrian.db.DataAccess;
import com.northernwall.hadrian.domain.User;
import com.northernwall.hadrian.utilityHandlers.routingHandler.Http400BadRequestException;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;

/**
 *
 * @author Richard Thurston
 */
public class UserModifyHandler extends BasicHandler {

    private final AccessHelper accessHelper;

    public UserModifyHandler(AccessHelper accessHelper, DataAccess dataAccess) {
        super(dataAccess);
        this.accessHelper = accessHelper;
    }

    @Override
    public void handle(String target, Request request, HttpServletRequest httpRequest, HttpServletResponse response) throws IOException, ServletException {
        accessHelper.checkIfUserIsAdmin(request, "update user");

        User temp = fromJson(request, User.class);
        if (temp.getUsername() == null || temp.getUsername().isEmpty()) {
            throw new Http400BadRequestException("User Name is mising or empty");
        }
        if (temp.getFullName() == null) {
            throw new Http400BadRequestException("Full Name is mising or empty");
        }
        temp.setFullName(temp.getFullName().trim());
        if (temp.getFullName().isEmpty()) {
            throw new Http400BadRequestException("Full Name is mising or empty");
        }
        if (temp.getFullName().length() > 30) {
            throw new Http400BadRequestException("Full Name is to long, max is 30");
        }
        getDataAccess().updateUser(temp);
        
        response.setStatus(200);
        request.setHandled(true);
    }

}
