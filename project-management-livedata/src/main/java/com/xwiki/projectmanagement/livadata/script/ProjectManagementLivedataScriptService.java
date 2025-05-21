package com.xwiki.projectmanagement.livadata.script;

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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.script.service.ScriptService;

/**
 * Some description.
 *
 * @version $Id$
 */
@Component
@Named("projectmanagementlivedata")
@Singleton
public class ProjectManagementLivedataScriptService implements ScriptService
{
    @Inject
    @Named("projectmanagement")
    private LiveDataConfigurationResolver<LiveDataConfiguration> resolver;

    /**
     * @param hint the hint for the livedata source.
     * @return a collection of livedata property descriptors associated to the livedata source.
     */
    public LiveDataConfiguration getLivedataConfiguration(String hint)
    {
        LiveDataConfiguration liveDataConfiguration = new LiveDataConfiguration();
        LiveDataQuery liveDataQuery = new LiveDataQuery();
        LiveDataQuery.Source source = new LiveDataQuery.Source();
        source.setId("projectmanagement");
        source.setParameter("client", hint);
        liveDataQuery.setSource(source);
        liveDataConfiguration.setQuery(liveDataQuery);
        try {
            return resolver.resolve(liveDataConfiguration);
        } catch (LiveDataException e) {
            return null;
        }
    }
}
