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
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
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

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private final XWiki xwiki = mock(XWiki.class);

    private final BaseObject configurationObject = mock(BaseObject.class);

    private List<OpenProjectConnection> connections;

    private static final OpenProjectConnection opConnection = new OpenProjectConnection(
        "firstConnection",
        "firstConnectionURL",
        "firstConnectionClientId",
        "firstConnectionClientSecret",
        "firstInstanceId"
    );

    @BeforeEach
    void setUp() throws Exception
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

        XWikiContext xcontext = mock(XWikiContext.class);
        when(this.contextProvider.get()).thenReturn(xcontext);
        when(xcontext.getWiki()).thenReturn(this.xwiki);
        XWikiDocument configurationDocument = mock(XWikiDocument.class);
        when(this.xwiki.getDocument(any(EntityReference.class), same(xcontext))).thenReturn(configurationDocument);
        when(configurationDocument.getXObject(any(EntityReference.class))).thenReturn(this.configurationObject);
    }

    @Test
    void getDefaultConnectionChosenInTheAdministration()
    {
        when(this.configurationObject.getStringValue("defaultConnection")).thenReturn("secondConnection");

        assertSame(this.connections.get(1), this.configuration.getDefaultConnection());
    }

    @Test
    void getDefaultConnectionWhenThereIsASingleConnection()
    {
        when(openProjectConfiguration.getProperty("openprojectConnections")).thenReturn(List.of(opConnection));
        // A default connection that was deleted.
        when(this.configurationObject.getStringValue("defaultConnection")).thenReturn("secondConnection");

        assertSame(opConnection, this.configuration.getDefaultConnection());
    }

    @Test
    void getDefaultConnectionWithoutDefault()
    {
        // Several connections, and none chosen.
        when(this.configurationObject.getStringValue("defaultConnection")).thenReturn("");
        assertNull(this.configuration.getDefaultConnection());

        // The default connection was deleted.
        when(this.configurationObject.getStringValue("defaultConnection")).thenReturn("deletedConnection");
        assertNull(this.configuration.getDefaultConnection());

        when(openProjectConfiguration.getProperty("openprojectConnections")).thenReturn(List.of());
        assertNull(this.configuration.getDefaultConnection());
    }

    @Test
    void getDefaultConnectionWhenTheConfigurationCantBeRead() throws Exception
    {
        when(this.xwiki.getDocument(any(EntityReference.class), any(XWikiContext.class)))
            .thenThrow(new XWikiException(0, 0, "Database unavailable"));

        assertNull(this.configuration.getDefaultConnection());
        assertEquals("Failed to read the default OpenProject connection. Cause: [XWikiException: Error number 0 in 0: "
            + "Database unavailable].", this.logCapture.getMessage(0));
    }

    @Test
    void getConnectionByName()
    {
        OpenProjectConnection result = configuration.getConnection(opConnection.getConnectionName());

        assertEquals(opConnection, result);
    }

    @Test
    void getUnknownConnectionReturnsNull()
    {
        String connectionName = "invalidConnection";

        OpenProjectConnection result = configuration.getConnection(connectionName);

        assertNull(result);
    }
}
