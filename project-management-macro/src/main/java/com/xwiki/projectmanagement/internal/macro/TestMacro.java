package com.xwiki.projectmanagement.internal.macro;

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
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.descriptor.DefaultContentDescriptor;

/**
 * Some description.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("openproj")
public class TestMacro extends AbstractProjectManagementMacro<ProjectManagementMacroParameters>
{
    /**
     * Default constructor.
     */
    public TestMacro()
    {
        super("Open Proj Test Macro", "Some description", new DefaultContentDescriptor("Some content description",
            false, Block.LIST_BLOCK_TYPE), ProjectManagementMacroParameters.class);
    }

    @Override
    public void processParameters(ProjectManagementMacroParameters parameters)
    {
        parameters.setSourceParameters("client=test&translationPrefix=test.");

        // TODO: Maybe also change the content to update the livedata configuration? In JIRA implementation wants to
        //  add some new type of properties, it should either define them in the content or in some custom
        //  configuration.json.
    }
}
