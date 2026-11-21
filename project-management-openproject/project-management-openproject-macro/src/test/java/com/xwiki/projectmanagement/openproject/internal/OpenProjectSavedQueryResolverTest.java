package com.xwiki.projectmanagement.openproject.internal;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.localization.Translation;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectIdentifierResolver;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectSavedQueryResolver}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectSavedQueryResolverTest
{
    private static final String SAVED_QUERY_LINK = "http://op/projects/demo/work_packages?query_id=5";

    @RegisterExtension
    private final LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private OpenProjectSavedQueryResolver resolver;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @MockComponent
    private OpenProjectIdentifierResolver identifierResolver;

    @MockComponent
    private UserTokenChecker userTokenChecker;

    @MockComponent
    private ContextualLocalizationManager l10n;

    @Mock
    private Translation translation;

    @Mock
    private OpenProjectApiClient apiClient;

    private final OpenProjectMacroParameters parameters = new OpenProjectMacroParameters();

    private final Map<String, String> sourceParameters = new LinkedHashMap<>();

    @BeforeEach
    void setUp()
    {
        this.parameters.setInstance("main");
        this.parameters.setIdentifier(SAVED_QUERY_LINK);
        when(this.identifierResolver.getStandaloneQueryId(SAVED_QUERY_LINK)).thenReturn("5");
    }

    @Test
    void addsTheUrlOfTheResultsOfTheSavedQuery() throws Exception
    {
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5")).thenReturn("results");

        assertEquals(List.of(), resolve(false));

        assertEquals(Map.of(OpenProjectIdentifierResolver.SAVED_QUERY_RESULTS_URL, "results"), this.sourceParameters);
    }

    @Test
    void ignoresIdentifiersThatAreNotSavedQueries()
    {
        this.parameters.setIdentifier("12");

        assertEquals(List.of(), resolve(false));

        assertEquals(Map.of(), this.sourceParameters);
        verifyNoInteractions(this.openProjectAccess);
    }

    @Test
    void displaysTheWorkPackagesWhenTheInstanceIsNotConnected() throws Exception
    {
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.notConnected());

        assertEquals(List.of(), resolve(false));

        assertEquals(Map.of(), this.sourceParameters);
        verify(this.identifierResolver, never()).getSavedQueryResultsUrl(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = { 403, 404 })
    void warnsWhenTheSavedQueryIsNotAvailable(int statusCode) throws Exception
    {
        // OpenProject answers 404 for the saved queries that the user isn't allowed to see too.
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.anonymous(this.apiClient));
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5"))
            .thenThrow(new WorkItemRetrievalException("Not found", statusCode));
        Block message = new WordBlock("message");
        when(this.l10n.getTranslation("openproject.savedQuery.unavailable")).thenReturn(this.translation);
        when(this.translation.render("5")).thenReturn(message);
        List<Block> warning = List.of(new WordBlock("warning"));
        when(this.userTokenChecker.getWarningBlock("main", message, true)).thenReturn(warning);

        assertSame(warning, resolve(true));

        assertEquals(Map.of(), this.sourceParameters);
    }

    @Test
    void displaysTheWorkPackagesWhenTheSavedQueryFailsWithAnotherStatus() throws Exception
    {
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5"))
            .thenThrow(new WorkItemRetrievalException("Server error", 500));

        assertEquals(List.of(), resolve(false));

        assertEquals(Map.of(), this.sourceParameters);
        assertEquals("Failed to retrieve the saved query [5] of the instance [main]. "
            + "Cause: [WorkItemRetrievalException: Server error].", this.logCapture.getMessage(0));
    }

    @Test
    void displaysTheWorkPackagesWhenTheSavedQueryFailsWithoutStatus() throws Exception
    {
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5"))
            .thenThrow(new ProjectManagementException("forbidden"));

        assertEquals(List.of(), resolve(false));

        assertEquals(Map.of(), this.sourceParameters);
        assertEquals("Failed to retrieve the saved query [5] of the instance [main]. "
            + "Cause: [ProjectManagementException: forbidden].", this.logCapture.getMessage(0));
    }

    private List<Block> resolve(boolean inline)
    {
        return this.resolver.resolve(this.parameters, inline, this.sourceParameters::put);
    }
}
