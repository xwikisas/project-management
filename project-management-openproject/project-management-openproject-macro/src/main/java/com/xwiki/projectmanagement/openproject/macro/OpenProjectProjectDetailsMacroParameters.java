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
package com.xwiki.projectmanagement.openproject.macro;

import org.xwiki.properties.annotation.PropertyMandatory;
import org.xwiki.properties.annotation.PropertyDisplayType;

import com.xwiki.projectmanagement.openproject.OpenProjectInstance;
import com.xwiki.projectmanagement.openproject.OpenProjectInstanceHolder;
import com.xwiki.projectmanagement.openproject.OpenProjectProject;

/**
 * Parameters for the OpenProject Project Details macro.
 *
 * @version $Id$
 * @since 1.3
 */
public class OpenProjectProjectDetailsMacroParameters implements OpenProjectInstanceHolder
{
    private String instance;

    private String project;

    @Override
    public String getInstance()
    {
        return instance;
    }

    @Override
    @PropertyMandatory
    @PropertyDisplayType(OpenProjectInstance.class)
    public void setInstance(String instance)
    {
        this.instance = instance;
    }

    /**
     * @return the identifier of the project within the OpenProject instance.
     */
    public String getProject()
    {
        return project;
    }

    /**
     * @param project see {@link #getProject()}.
     */
    @PropertyMandatory
    @PropertyDisplayType(OpenProjectProject.class)
    public void setProject(String project)
    {
        this.project = project;
    }
}
