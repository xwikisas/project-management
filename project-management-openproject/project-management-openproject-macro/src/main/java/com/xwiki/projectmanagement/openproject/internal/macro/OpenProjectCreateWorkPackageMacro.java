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

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.openproject.macro.OpenProjectCreateWorkPackageMacroParameters;

/**
 * OpenProject Create Work Package Macro.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("openproject-create-work-package")
public class OpenProjectCreateWorkPackageMacro extends AbstractMacro<OpenProjectCreateWorkPackageMacroParameters>
{
    @Inject
    private ContextualLocalizationManager l10n;

    /**
     * Default constructor.
     */
    public OpenProjectCreateWorkPackageMacro()
    {
        super("Open Project Create Work Package",
            "Easily create a new work package in OpenProject directly from XWiki.",
            OpenProjectCreateWorkPackageMacroParameters.class);
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public List<Block> execute(OpenProjectCreateWorkPackageMacroParameters parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {
        Block infoMessage =
            l10n.getTranslation("openproject-create-work-package.display.viewMode.message").render();
        return List.of(new GroupBlock(List.of(infoMessage), Collections.singletonMap("class", "box "
            + "infomessage")));
    }
}
