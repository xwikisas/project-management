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
package com.xwiki.projectmanagement.presets.script;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.script.service.ScriptService;

import com.xwiki.projectmanagement.presets.Preset;
import com.xwiki.projectmanagement.presets.PresetsManager;
import com.xwiki.projectmanagement.script.ProjectManagementScriptService;

/**
 * Script Service exposing utility methods for handling Filter Presets.
 *
 * @version $Id$
 * @since 1.3.0
 */
@Component
@Named(ProjectManagementScriptService.ROLE_HINT + "." + PresetsScriptService.ROLE_HINT)
@Singleton
public class PresetsScriptService implements ScriptService
{
    /**
     * The HINT for this script service.
     */
    public static final String ROLE_HINT = "presets";

    @Inject
    private PresetsManager manager;

    /**
     * @return the next available Preset id.
     */
    public int getNextPresetId()
    {
        return manager.getNextId();
    }

    /**
     * @param client the id of the Project Management client for which we want the presets.
     * @param chart whether to retrieve the chart presets or the normal ones.
     * @param offset the offset of the result set.
     * @param limit the limit of the results.
     * @return a list of presets for the given client id.
     */
    public List<Preset> getPresetsForClient(String client, Boolean chart, int offset, int limit)
    {
        return manager.getClientPresets(client, chart, offset, limit);
    }

    /**
     * @param chart whether to retrieve the chart presets or the normal ones.
     * @param offset the offset of the result set.
     * @param limit the limit of the results.
     * @return a list of presets.
     */
    public List<Preset> getPresets(Boolean chart, int offset, int limit)
    {
        return manager.getPresets(chart, offset, limit);
    }
}
