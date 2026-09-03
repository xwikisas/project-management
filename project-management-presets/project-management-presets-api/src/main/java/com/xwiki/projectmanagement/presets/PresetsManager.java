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
package com.xwiki.projectmanagement.presets;

import java.util.List;

import org.xwiki.component.annotation.Role;
import org.xwiki.stability.Unstable;

/**
 * Manages Presets within the wiki.
 *
 * @version $Id$
 * @since 1.3.0
 */
@Role
@Unstable
public interface PresetsManager
{
    /**
     * @param chart true if the chart presets should be retrieved; false if the normal presets should be retrieved.
     * @param offset the offset of the returned list relative to the superset.
     * @param limit the maximum number of preset objects returned.
     * @return a list of presets that matches the offset and limit.
     */
    List<Preset> getPresets(Boolean chart, int offset, int limit);

    /**
     * @param client the identifier of the ProjectManagement client. i.e. openproject.
     * @param chart true if the chart presets should be retrieved; false if the normal presets should be retrieved.
     * @param offset the offset of the result set.
     * @param limit the maximum size that will be returned.
     * @return a list of presets that were configured for a specific ProjectManagement client.
     */
    List<Preset> getClientPresets(String client, Boolean chart, int offset, int limit);

    /**
     * @param id the id of the Preset.
     * @return the first Preset that matches the name.
     */
    Preset getPreset(Integer id);

    /**
     * @return an id that is not assigned to any existing Preset.
     */
    int getNextId();
}
