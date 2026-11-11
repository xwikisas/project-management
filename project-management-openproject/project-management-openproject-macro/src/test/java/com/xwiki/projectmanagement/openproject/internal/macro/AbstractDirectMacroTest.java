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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncExecutor;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectAccessMode;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils;
import com.xwiki.projectmanagement.openproject.internal.UserTokenChecker;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessStatus;
import com.xwiki.projectmanagement.openproject.model.Project;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Common setup of the tests of the macros extending
 * {@link com.xwiki.projectmanagement.openproject.internal.AbstractOpenProjectDirectMacro}: the license and the user
 * token are valid, the {@code main} instance is configured with the server URL {@code https://op.example.com} and
 * the asynchronous rendering is done synchronously. The texts are translated with the English translations of the
 * module, so a text displayed without its translation key, or a missing key, is caught by the assertions.
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

    @MockComponent
    protected UserTokenChecker userTokenChecker;

    @MockComponent
    protected ContextualLocalizationManager l10n;

    protected final MacroTransformationContext context = new MacroTransformationContext();

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
        Properties translations = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/ApplicationResources.properties")) {
            translations.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        when(this.l10n.getTranslationPlain(anyString()))
            .thenAnswer(invocation -> translations.getProperty(invocation.getArgument(0)));
    }

    /**
     * Translate each text to its translation key between brackets, to check that the macro doesn't display English
     * texts that can't be translated.
     */
    protected void usePseudoTranslations()
    {
        when(this.l10n.getTranslationPlain(anyString()))
            .thenAnswer(invocation -> "[" + invocation.getArgument(0) + "]");
    }

    /**
     * Make OpenProject find a project by its identifier.
     *
     * @param identifier the identifier of the project
     * @param id the id of the project
     * @return the project
     */
    protected Project resolveProject(String identifier, int id) throws Exception
    {
        Project project = new Project();
        project.setId(id);
        when(this.apiClient.getProject(identifier)).thenReturn(project);
        return project;
    }

    /**
     * Make the current user call OpenProject anonymously through a connection with the given access mode.
     *
     * @param accessMode the access mode of the connection
     * @return the warning asking the user to connect their OpenProject account
     */
    protected List<Block> useAnonymousAccess(OpenProjectAccessMode accessMode)
    {
        OpenProjectConnection connection = new OpenProjectConnection("main", SERVER_URL, "id", "secret", "instanceId");
        connection.setAccessMode(accessMode);
        when(this.openProjectConfiguration.getConnection("main")).thenReturn(connection);
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.anonymous(this.apiClient));
        List<Block> warning = List.of(new WordBlock("connect"));
        when(this.userTokenChecker.getWarningBlock("main", OpenProjectAccessStatus.ANONYMOUS, true, this.context))
            .thenReturn(warning);
        return warning;
    }
}
