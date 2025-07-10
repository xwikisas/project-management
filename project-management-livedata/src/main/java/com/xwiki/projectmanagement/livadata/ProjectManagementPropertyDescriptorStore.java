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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataPropertyDescriptor;
import org.xwiki.livedata.LiveDataPropertyDescriptorStore;

/**
 * Provide the generic descriptors of the project management properties merged with the descriptors coming from
 * eventual {@link com.xwiki.projectmanagement.ProjectManagementClient} implementations, if any.
 *
 * @version $Id$
 */
@Component
@Named("projectmanagement")
@Singleton
public class ProjectManagementPropertyDescriptorStore implements LiveDataPropertyDescriptorStore
{
    @Inject
    @Named("projectmanagement")
    private Provider<LiveDataConfiguration> liveDataConfigurationProvider;

    @Override
    public Optional<LiveDataPropertyDescriptor> get(String propertyId) throws LiveDataException
    {
        return LiveDataPropertyDescriptorStore.super.get(propertyId);
    }

    @Override
    public Collection<LiveDataPropertyDescriptor> get() throws LiveDataException
    {
        List<LiveDataPropertyDescriptor> properties = new ArrayList<>();

        properties.addAll(this.liveDataConfigurationProvider.get().getMeta().getPropertyDescriptors());

        return properties;
    }

    @Override
    public boolean save(LiveDataPropertyDescriptor propertyDescriptor) throws LiveDataException
    {
        return LiveDataPropertyDescriptorStore.super.save(propertyDescriptor);
    }

    @Override
    public Optional<LiveDataPropertyDescriptor> remove(String propertyId) throws LiveDataException
    {
        return LiveDataPropertyDescriptorStore.super.remove(propertyId);
    }
}
