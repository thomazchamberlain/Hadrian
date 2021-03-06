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
package com.northernwall.hadrian.service;

import com.northernwall.hadrian.Const;
import com.northernwall.hadrian.access.AccessHelper;
import com.northernwall.hadrian.db.DataAccess;
import com.northernwall.hadrian.domain.Team;
import com.northernwall.hadrian.service.dao.PutTeamData;
import com.northernwall.hadrian.utilityHandlers.routingHandler.Http400BadRequestException;
import com.northernwall.hadrian.utilityHandlers.routingHandler.Http405NotAllowedException;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;

public class TeamModifyHandler extends BasicHandler {

    private final AccessHelper accessHelper;

    public TeamModifyHandler(AccessHelper accessHelper, DataAccess dataAccess) {
        super(dataAccess);
        this.accessHelper = accessHelper;
    }

    @Override
    public void handle(String target, Request request, HttpServletRequest httpRequest, HttpServletResponse response) throws IOException, ServletException {
        PutTeamData data = fromJson(request, PutTeamData.class);
        Team team = getTeam(data.teamId, null);
        accessHelper.checkIfUserCanModify(request, data.teamId, "update team");
        data.teamName = data.teamName.trim();
        if (data.teamName.isEmpty()) {
            throw new Http400BadRequestException("Team Name is mising or empty");
        }
        if (data.teamName.length() > 30) {
            throw new Http400BadRequestException("Team Name is to long, max is 30");
        }

        if (data.gitGroup == null || data.gitGroup.isEmpty()) {
            throw new Http400BadRequestException("Git Group is mising or empty");
        }
        if (data.gitGroup.length() > 30) {
            throw new Http400BadRequestException("Git Group is to long, max is 30");
        }

        for (Team temp : getDataAccess().getTeams()) {
            if (!temp.getTeamId().equals(data.teamId)) {
                if (temp.getTeamName().equals(data.teamName)) {
                    throw new Http405NotAllowedException("Can not chnage team name, as a team with name " + data.teamName + " already exists");
                }
                if (temp.getGitGroup().equals(data.gitGroup)) {
                    throw new Http405NotAllowedException("Can not chnage team name, as a team with name " + data.teamName + " already exists");
                }
            }
        }

        if (data.teamPage != null && data.teamPage.isEmpty()) {
            if (!data.teamPage.toLowerCase().startsWith(Const.HTTP)
                    && !data.teamPage.toLowerCase().startsWith(Const.HTTPS)) {
                data.teamPage = Const.HTTP + data.teamPage;
            }
        }

        team.setTeamName(data.teamName);
        team.setTeamEmail(data.teamEmail);
        team.setTeamIrc(data.teamIrc);
        team.setTeamSlack(data.teamSlack);
        team.setGitGroup(data.gitGroup);
        team.setTeamPage(data.teamPage);
        team.setCalendarId(data.calendarId);
        team.setColour(data.colour);

        getDataAccess().saveTeam(team);
        response.setStatus(200);
        request.setHandled(true);
    }

}
