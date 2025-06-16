package com.xwiki.projectmanagement.openproject.macro;

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

import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;
import org.xwiki.properties.annotation.PropertyDisplayType;

import com.xwiki.projectmanagement.openproject.OpenProjectInstance;
import com.xwiki.projectmanagement.openproject.OpenProjectProperties;

/**
 * Open project macro params.
 *
 * @version $Id$.
 */
public class OpenProjectMacroParameters extends ProjectManagementMacroParameters
{
    private String instance;

    /**
     * @return the instance from where work items should be retrieved.
     */
    public String getInstance()
    {
        return instance;
    }

    /**
     * @param instance see {@link #getInstance()}.
     */
    @PropertyDisplayType(OpenProjectInstance.class)
    public void setInstance(String instance)
    {
        this.instance = instance;
    }

    @PropertyDisplayType(OpenProjectProperties.class)
    @Override
    public void setProperties(String properties)
    {
        super.setProperties(properties);
    }
}
