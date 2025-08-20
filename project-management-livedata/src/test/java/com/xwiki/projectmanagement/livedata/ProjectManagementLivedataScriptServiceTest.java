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
package com.xwiki.projectmanagement.livedata;

import javax.inject.Named;

import org.junit.jupiter.api.Test;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.livadata.script.ProjectManagementLivedataScriptService;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test the flow of the {@link ProjectManagementLivedataScriptService}.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
@ComponentTest
class ProjectManagementLivedataScriptServiceTest
{
    @InjectMockComponents
    private ProjectManagementLivedataScriptService service;

    @MockComponent
    @Named("projectmanagement")
    private LiveDataConfigurationResolver<LiveDataConfiguration> resolver;

    @Test
    void testGetLivedataConfiguration_success() throws Exception
    {
        LiveDataConfiguration expectedConfig = new LiveDataConfiguration();
        when(resolver.resolve(any(LiveDataConfiguration.class)))
            .thenReturn(expectedConfig);

        LiveDataConfiguration result = service.getLivedataConfiguration("testClient");

        assertSame(expectedConfig, result);

        verify(resolver).resolve(argThat(config -> {
            LiveDataQuery query = config.getQuery();
            return query != null
                && query.getSource() != null
                && "projectmanagement".equals(query.getSource().getId())
                && "testClient".equals(query.getSource().getParameters().get("client"));
        }));
    }

    @Test
    void testGetLivedataConfiguration_resolverThrows() throws Exception
    {
        when(resolver.resolve(any(LiveDataConfiguration.class)))
            .thenThrow(new LiveDataException("failure"));

        LiveDataConfiguration result = service.getLivedataConfiguration("badClient");

        assertNull(result);
    }
}
