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
package com.xwiki.projectmanagement.openproject.internal.macro;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncExecutor;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;

import static org.mockito.Mockito.when;

/**
 * Common setup of the tests of the macros extending
 * {@link com.xwiki.projectmanagement.openproject.internal.AbstractOpenProjectDirectMacro}: the license and the user
 * token are valid, the {@code main} instance is configured with the server URL {@code https://op.example.com} and
 * the asynchronous rendering is done synchronously.
 *
 * @version $Id$
 */
abstract class AbstractDirectMacroTest
{
    protected static final String SERVER_URL = "https://op.example.com";

    @MockComponent
    protected OpenProjectConfiguration openProjectConfiguration;

    @MockComponent
    protected OpenProjectAccess openProjectAccess;

    @MockComponent
    protected ProjectManagementAsyncExecutor asyncExecutor;

    @MockComponent
    @Named("wiki")
    protected ConfigurationSource wikiConfigSource;

    @Mock
    protected MacroTransformationContext context;

    @Mock
    protected OpenProjectApiClient apiClient;

    @BeforeEach
    void setUpDirectMacro() throws Exception
    {
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        when(this.openProjectConfiguration.getConnection("main"))
            .thenReturn(new OpenProjectConnection("main", SERVER_URL, "id", "secret", "instanceId"));
        when(this.wikiConfigSource.getProperty("dateformat", "dd/MM/yyyy")).thenReturn("yyyy-MM-dd");
        OpenProjectMacroTestUtils.executeSynchronously(this.asyncExecutor);
    }
}
