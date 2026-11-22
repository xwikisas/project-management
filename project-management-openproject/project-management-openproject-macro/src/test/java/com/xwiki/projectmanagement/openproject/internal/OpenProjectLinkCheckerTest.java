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
package com.xwiki.projectmanagement.openproject.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectLinkChecker}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectLinkCheckerTest
{
    private static final String OTHER_SERVER_LINK = "https://other.example.com/projects/demo/work_packages";

    private static final String SERVER_URL = "https://op.example.com";

    private static final String KEY = "openproject.link.otherServer";

    @InjectMockComponents
    private OpenProjectLinkChecker checker;

    @MockComponent
    private OpenProjectConfiguration configuration;

    @MockComponent
    private ContextualLocalizationManager l10n;

    @BeforeEach
    void setUp()
    {
        when(this.configuration.getConnection("main"))
            .thenReturn(new OpenProjectConnection("main", SERVER_URL, "", "", ""));
    }

    @Test
    void reportsALinkOfAnotherServer()
    {
        when(this.l10n.getTranslationPlain(KEY, "other.example.com", "main", SERVER_URL)).thenReturn("translated");

        MacroExecutionException exception =
            assertThrows(MacroExecutionException.class, () -> this.checker.check("main", OTHER_SERVER_LINK));

        assertEquals("translated", exception.getMessage());
    }

    @Test
    void reportsALinkOfAnotherServerWithoutTranslation()
    {
        MacroExecutionException exception =
            assertThrows(MacroExecutionException.class, () -> this.checker.check("main", OTHER_SERVER_LINK));

        assertEquals("The link is from the OpenProject server [other.example.com], not [https://op.example.com] of "
            + "the connection [main].", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "12, XWIKI-3", "http://OP.example.com/work_packages?query_id=5",
        "https://op.example.com:443/work_packages/5/activity" })
    void acceptsTheListsOfWorkPackagesAndTheLinksOfTheServer(String identifier)
    {
        assertDoesNotThrow(() -> this.checker.check("main", identifier));
    }

    @Test
    void doesNotReadTheConnectionForAListOfWorkPackages() throws Exception
    {
        this.checker.check("main", "12, XWIKI-3");

        verifyNoInteractions(this.configuration);
    }

    @Test
    void acceptsAnyLinkWhenTheConnectionDoesNotExist()
    {
        assertDoesNotThrow(() -> this.checker.check("unknown", OTHER_SERVER_LINK));
    }
}
