package com.xwiki.projectmanagement.livadata;

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
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.internal.JSONMerge;

/**
 * Some Name.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("projectmanagement")
public class ProjectManagementConfigurationResolver implements LiveDataConfigurationResolver<LiveDataConfiguration>
{
    @Inject
    @Named("projectmanagement")
    private Provider<LiveDataConfiguration> configurationProvider;

    private JSONMerge jsonMerge = new JSONMerge();

    @Override
    public LiveDataConfiguration resolve(LiveDataConfiguration input) throws LiveDataException
    {
        LiveDataConfiguration defaultConfig = configurationProvider.get();

        defaultConfig.setId(input.getId());

        LiveDataConfiguration mergedConfig = this.jsonMerge.merge(defaultConfig, input);

        return mergedConfig;
    }
}
