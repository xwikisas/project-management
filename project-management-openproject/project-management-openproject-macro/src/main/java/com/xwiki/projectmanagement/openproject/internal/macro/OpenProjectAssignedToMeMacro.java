package com.xwiki.projectmanagement.openproject.internal.macro;

/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;

import com.xwiki.projectmanagement.openproject.macro.OpenProjectAssignedToMeMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;

/**
 * OpenProject macro that displays only work packages assigned to the current user. The "assigned to me" filter is
 * always enforced and cannot be overridden.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("openproject-assigned-to-me")
public class OpenProjectAssignedToMeMacro extends OpenProjectMacro
{
    private static final String ASSIGNED_TO_ME_FILTERS =
        "{\"query\":{\"filters\":[{\"property\":\"assignees\","
            + "\"constraints\":[{\"operator\":\"=\",\"value\":\"me\"}]}]}}";

    /**
     * Default constructor.
     */
    public OpenProjectAssignedToMeMacro()
    {
        super("OpenProject Assigned to Me",
            "Retrieve work packages from OpenProject assigned to the current user.",
            OpenProjectAssignedToMeMacroParameters.class);
    }

    @Override
    public void processParameters(OpenProjectMacroParameters parameters)
    {
        parameters.setFilters(ASSIGNED_TO_ME_FILTERS);
        addToSourceParams(parameters, "client", "openproject");
        addToSourceParams(parameters, "instance", parameters.getInstance());
        addToSourceParams(parameters, "translationPrefix", "openproject.");
    }
}
