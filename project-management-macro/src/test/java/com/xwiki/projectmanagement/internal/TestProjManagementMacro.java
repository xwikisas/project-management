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
package com.xwiki.projectmanagement.internal;

import javax.inject.Named;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.macro.descriptor.DefaultContentDescriptor;

import com.xwiki.projectmanagement.internal.macro.AbstractProjectManagementMacro;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;

/**
 * Implementation of the abstract project management macro for testing purposes.
 *
 * @version $Id
 * @since 1.0-rc-4
 */
@Component
@Named("test")
public class TestProjManagementMacro extends AbstractProjectManagementMacro<ProjectManagementMacroParameters>
{
    public TestProjManagementMacro()
    {
        super("test", "test", new DefaultContentDescriptor(), String.class);
    }

    @Override
    public void processParameters(ProjectManagementMacroParameters parameters)
    {
        addToSourceParams(parameters, "smth", "smth2");
        addToSourceParams(parameters, "smth3", "smth4");
    }
}
