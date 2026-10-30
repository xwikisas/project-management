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

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectIdentifierResolver;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectAssignedToMeMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectAssignedToMeMacro}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectAssignedToMeMacroTest
{
    @InjectMockComponents
    private OpenProjectAssignedToMeMacro macro;

    @MockComponent
    private OpenProjectIdentifierResolver identifierResolver;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @Mock
    private OpenProjectApiClient apiClient;

    @Test
    void processParametersFiltersTheWorkPackagesAssignedToTheCurrentUser()
    {
        OpenProjectAssignedToMeMacroParameters parameters = new OpenProjectAssignedToMeMacroParameters();
        parameters.setInstance("main");
        parameters.setFilters("ignored");
        parameters.setIdentifier("ignored");

        this.macro.processParameters(parameters);

        assertEquals("{\"query\":{\"filters\":[{\"property\":\"assignees\","
            + "\"constraints\":[{\"operator\":\"=\",\"value\":\"me\"}]}]}}", parameters.getFilters());
        // The identifier is not passed to the live data source.
        assertEquals("client=openproject&instance=main&translationPrefix=openproject.",
            parameters.getSourceParameters());
    }

    @Test
    void asyncProcessParametersDoesNotResolveSavedQueries() throws Exception
    {
        OpenProjectAssignedToMeMacroParameters parameters = new OpenProjectAssignedToMeMacroParameters();
        parameters.setInstance("main");
        parameters.setIdentifier("saved");
        // The parent macro would add the URL of the results of this saved query.
        when(this.identifierResolver.getStandaloneQueryId("saved")).thenReturn("5");
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5")).thenReturn("results");

        this.macro.asyncProcessParameters(parameters);

        assertNull(parameters.getSourceParameters());
    }
}
