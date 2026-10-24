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

import java.util.List;
import java.util.Map;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.licensing.Licensor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LicenseChecker}.
 *
 * @version $Id$
 */
@ComponentTest
class LicenseCheckerTest
{
    private static final DocumentReference LICENSED_CLASS =
        new DocumentReference("mywiki", List.of("OpenProject", "Code"), "OpenProjectConnectionClass");

    @InjectMockComponents
    private LicenseChecker licenseChecker;

    @MockComponent
    private Licensor licensor;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @Mock
    private XWikiContext xcontext;

    @Mock
    private MacroTransformationContext macroContext;

    @BeforeEach
    void setUp()
    {
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getWikiId()).thenReturn("mywiki");
    }

    @Test
    void hasLicenseChecksTheConnectionClassOfTheCurrentWiki()
    {
        when(this.licensor.hasLicensure(LICENSED_CLASS)).thenReturn(true);
        assertTrue(this.licenseChecker.hasLicense());

        when(this.licensor.hasLicensure(LICENSED_CLASS)).thenReturn(false);
        assertFalse(this.licenseChecker.hasLicense());
    }

    @Test
    void getMissingLicenseBlockWhenLicensed()
    {
        when(this.licensor.hasLicensure(LICENSED_CLASS)).thenReturn(true);

        assertTrue(this.licenseChecker.getMissingLicenseBlock(this.macroContext).isEmpty());
    }

    @Test
    void getMissingLicenseBlockWhenNotLicensed()
    {
        when(this.macroContext.isInline()).thenReturn(true);

        List<Block> blocks = this.licenseChecker.getMissingLicenseBlock(this.macroContext);

        assertEquals(1, blocks.size());
        MacroBlock macroBlock = (MacroBlock) blocks.get(0);
        assertEquals("missingLicenseMessage", macroBlock.getId());
        assertEquals(Map.of("extensionName", "openproject.extension.name"), macroBlock.getParameters());
        assertTrue(macroBlock.isInline());
    }
}
