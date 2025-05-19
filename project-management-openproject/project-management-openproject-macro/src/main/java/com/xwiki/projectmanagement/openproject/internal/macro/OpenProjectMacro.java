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
import org.xwiki.projectmanagement.internal.macro.AbstractProjectManagementMacro;
import org.xwiki.rendering.macro.descriptor.DefaultContentDescriptor;

import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;

/**
 * Open project macro.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("openproject")
public class OpenProjectMacro extends AbstractProjectManagementMacro<OpenProjectMacroParameters>
{
    /**
     * Default constructor.
     */
    public OpenProjectMacro()
    {
        super("Open Project", "Retrieve work items from open project.",
            new DefaultContentDescriptor("Some description", false), OpenProjectMacroParameters.class);
    }

    @Override
    public void processParameters(OpenProjectMacroParameters parameters)
    {
        String instance = parameters.getInstance();
        if (instance == null || instance.isEmpty()) {
            return;
        }
        String sourceParameters = parameters.getSourceParameters();
        if (sourceParameters == null || sourceParameters.isEmpty()) {
            parameters.setSourceParameters(String.format("instance=%s", instance));
        } else {
            parameters.setSourceParameters(String.format("%s&instance=%s", sourceParameters, instance));
        }
    }
}
