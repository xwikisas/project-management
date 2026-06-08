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
package com.xwiki.projectmanagement.script;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.descriptor.ComponentRole;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.script.service.ScriptService;

import com.xwiki.projectmanagement.ProjectManagementClient;

/**
 * Project management script service. Offers useful methods with regards to the project management implementers.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Component
@Named("projectmanagement")
@Singleton
public class ProjectManagementScriptService implements ScriptService
{
    @Inject
    private ComponentManager componentManager;

    /**
     * @return a list of existing {@link ProjectManagementClient} implementer hints.
     */
    public List<String> getClientHints()
    {
        return componentManager.getComponentDescriptorList(ProjectManagementClient.class).stream()
            .map(ComponentRole::getRoleHint).collect(Collectors.toList());
    }
}
