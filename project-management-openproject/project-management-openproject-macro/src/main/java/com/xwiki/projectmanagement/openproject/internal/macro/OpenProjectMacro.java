package com.xwiki.projectmanagement.openproject.internal.macro;

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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.licensing.Licensor;
import com.xwiki.projectmanagement.internal.macro.AbstractProjectManagementMacro;
import com.xwiki.projectmanagement.openproject.internal.UserTokenChecker;
import com.xwiki.projectmanagement.openproject.internal.displayer.StylingSetupManager;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;

/**
 * Open project macro.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("openproject")
public class OpenProjectMacro extends AbstractProjectManagementMacro<OpenProjectMacroParameters>
{
    private static final String CLASS = "class";

    private static final List<String> OPEN_PROJECT_CODE_SPACE = Arrays.asList("OpenProject", "Code");

    @Inject
    @Named("ssrx")
    private SkinExtension ssrx;

    @Inject
    @Named("jsx")
    private SkinExtension jsx;

    @Inject
    private Licensor licensor;

    @Inject
    private Provider<XWikiContext> xContextProvider;

    @Inject
    private UserTokenChecker userTokenChecker;

    @Inject
    private StylingSetupManager stylingSetupManager;

    /**
     * Default constructor.
     */
    public OpenProjectMacro()
    {
        super("Open Project", "Retrieve work items from open project.", null, OpenProjectMacroParameters.class);
    }

    @Override
    public void processParameters(OpenProjectMacroParameters parameters)
    {
        addToSourceParams(parameters, "client", "openproject");

        addToSourceParams(parameters, "instance", parameters.getInstance());

        addToSourceParams(parameters, "identifier", parameters.getIdentifier());

        addToSourceParams(parameters, "translationPrefix", "openproject.");
    }

    @Override
    public List<Block> execute(OpenProjectMacroParameters parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {
        XWikiContext xContext = this.xContextProvider.get();

        if (!licensor.hasLicensure(
            new DocumentReference(xContext.getWikiId(), OPEN_PROJECT_CODE_SPACE, "OpenProjectConnectionClass")))
        {
            return List.of(new MacroBlock(
                "missingLicenseMessage",
                Map.of("extensionName", "openproject.extension.name"),
                null,
                context.isInline())
            );
        }

        ssrx.use("openproject/css/propertyStyles.css");
        stylingSetupManager.useInstanceStyle(parameters.getInstance());
        jsx.use("OpenProject.Code.ViewAction");

        List<Block> warningBlock = userTokenChecker.getWarningBlock(parameters.getInstance());
        if (!warningBlock.isEmpty()) {
            return warningBlock;
        }
        return Collections.singletonList(new GroupBlock(super.execute(parameters, content, context),
            Collections.singletonMap(CLASS, "open-project-macro")));
    }
}
