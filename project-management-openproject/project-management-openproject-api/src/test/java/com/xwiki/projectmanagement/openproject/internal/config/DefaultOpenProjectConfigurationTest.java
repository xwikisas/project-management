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
package com.xwiki.projectmanagement.openproject.internal.config;

import java.util.List;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultOpenProjectConfiguration}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultOpenProjectConfigurationTest
{
    @InjectMockComponents
    private DefaultOpenProjectConfiguration configuration;

    @MockComponent
    @Named("openproject")
    private ConfigurationSource openProjectConfiguration;

    private List<OpenProjectConnection> connections;

    private static final OpenProjectConnection opConnection = new OpenProjectConnection(
        "firstConnection",
        "firstConnectionURL",
        "firstConnectionClientId",
        "firstConnectionClientSecret",
        "firstInstanceId"
    );

    @BeforeEach
    void setUp()
    {
        this.connections = List.of(
            opConnection,
            new OpenProjectConnection(
                "secondConnection",
                "secondConnectionURL",
                "secondConnectionClientId",
                "secondConnectionClientSecret",
                "secondInstanceId"
            ),
            new OpenProjectConnection(
                "thirdConnection",
                "thirdConnectionURL",
                "thirdConnectionClientId",
                "thirdConnectionClientSecret",
                "thirdInstanceId"
            )
        );

        when(openProjectConfiguration.getProperty("openprojectConnections")).thenReturn(this.connections);
    }

    @Test
    public void getConnectionsTest()
    {
        List<OpenProjectConnection> result = configuration.getOpenProjectConnections();
        assertEquals(this.connections, result);
    }

    @Test
    public void getConnectionTest()
    {
        OpenProjectConnection result = configuration.getConnection(opConnection.getConnectionName());

        assertEquals(opConnection, result);
    }

    @Test
    public void getConnectionFailsTest()
    {
        String connectionName = "invalidConnection";

        OpenProjectConnection result = configuration.getConnection(connectionName);

        assertNull(result);
    }
}
