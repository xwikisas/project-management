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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.localization.Translation;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.openproject.internal.LicenseChecker;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectCreateWorkPackageMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectCreateWorkPackageMacro}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectCreateWorkPackageMacroTest
{
    @InjectMockComponents
    private OpenProjectCreateWorkPackageMacro macro;

    @MockComponent
    private LicenseChecker licenseChecker;

    @MockComponent
    private ContextualLocalizationManager l10n;

    @Mock
    private Translation translation;

    @Mock
    private MacroTransformationContext context;

    @BeforeEach
    void setUp()
    {
        when(this.licenseChecker.getMissingLicenseBlock(this.context)).thenReturn(List.of());
    }

    @Test
    void displaysAnInformationMessageInViewMode() throws Exception
    {
        Block message = new WordBlock("Use the edit mode");
        when(this.l10n.getTranslation("openproject-create-work-package.display.viewMode.message"))
            .thenReturn(this.translation);
        when(this.translation.render()).thenReturn(message);

        List<Block> result =
            this.macro.execute(new OpenProjectCreateWorkPackageMacroParameters(), "", this.context);

        assertEquals(List.of(new GroupBlock(List.of(message), Map.of("class", "box infomessage"))), result);
        assertFalse(this.macro.supportsInlineMode());
    }

    @Test
    void returnsTheMissingLicenseMessage() throws Exception
    {
        List<Block> licenseBlock = List.of(new WordBlock("license"));
        when(this.licenseChecker.getMissingLicenseBlock(this.context)).thenReturn(licenseBlock);

        assertSame(licenseBlock,
            this.macro.execute(new OpenProjectCreateWorkPackageMacroParameters(), "", this.context));

        verifyNoInteractions(this.l10n);
    }
}
