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
package com.xwiki.projectmanagement.openproject.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.script.service.ScriptService;

import com.xwiki.projectmanagement.exception.AuthenticationException;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;

/**
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("openproject")
@Singleton
public class OpenProjectScriptService implements ScriptService
{
    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    /**
     * Retrieves a list of available OpenProject connections.
     *
     * @return a list of {@code OpenProjectConnection} instances;
     */
    public List<Map<String, String>> getConnectionOptions() throws AuthenticationException
    {
        List<OpenProjectConnection> openProjectConnections = openProjectConfiguration.getOpenProjectConnections();

        List<Map<String, String>> options = new ArrayList<>();

        for (OpenProjectConnection openProjectConnection : openProjectConnections) {
            Map<String, String> option = new HashMap<>();
            option.put("name", openProjectConnection.getConnectionName());
            option.put("value", openProjectConnection.getConnectionName());
            options.add(option);
        }
        return options;
    }
}
