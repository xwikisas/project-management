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
package com.xwiki.projectmanagement.internal.macro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xwiki.extension.CoreExtension;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.repository.CoreExtensionRepository;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AsyncResultEventChecker}.
 *
 * @version $Id$
 */
@ComponentTest
class AsyncResultEventCheckerTest
{
    private static final String PLATFORM = "org.xwiki.platform:xwiki-platform-oldcore";

    @InjectMockComponents
    private AsyncResultEventChecker checker;

    @MockComponent
    private CoreExtensionRepository coreExtensionRepository;

    @ParameterizedTest
    @CsvSource({
        "15.10, false",
        "17.10.15, false",
        "18.9.0, false",
        "18.9.10, false",
        "18.10.0-milestone-1, false",
        "18.10.0-rc-1, true",
        "18.10.0-SNAPSHOT, true",
        "18.10.0, true",
        "19.0.0, true"
    })
    void isEventSentForTheResult(String version, boolean expected)
    {
        // XWiki sends the event for the inserted result since 18.10.0-rc-1 (XWIKI-25675), which wasn't backported to
        // the maintenance branches (17.10.x, 18.9.x).
        CoreExtension platform = mock(CoreExtension.class);
        when(platform.getId()).thenReturn(new ExtensionId(PLATFORM, version));
        when(this.coreExtensionRepository.getCoreExtension(PLATFORM)).thenReturn(platform);
        // The component was initialized before the version was set.
        this.checker.initialize();

        assertEquals(expected, this.checker.isEventSentForTheResult());
    }

    @Test
    void isEventSentForTheResultWhenTheVersionIsUnknown()
    {
        // The macros send the event themselves, as on the versions that don't send it.
        assertFalse(this.checker.isEventSentForTheResult());
    }

    @Test
    void isEventSentForTheResultWhenTheExtensionRepositoryFails()
    {
        when(this.coreExtensionRepository.getCoreExtension(PLATFORM)).thenThrow(new IllegalStateException("Failed"));
        this.checker.initialize();

        assertFalse(this.checker.isEventSentForTheResult());
    }
}
