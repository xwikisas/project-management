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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;

import com.xwiki.projectmanagement.internal.macro.AbstractProjectManagementMacro;
import com.xwiki.projectmanagement.openproject.internal.LicenseChecker;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroParameterResolver;
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

    @Inject
    @Named("ssrx")
    private SkinExtension ssrx;

    @Inject
    @Named("jsx")
    private SkinExtension jsx;

    @Inject
    private LicenseChecker licenseChecker;

    @Inject
    private UserTokenChecker userTokenChecker;

    @Inject
    private StylingSetupManager stylingSetupManager;

    @Inject
    private OpenProjectMacroParameterResolver parameterResolver;

    /**
     * Default constructor.
     */
    public OpenProjectMacro()
    {
        super("Open Project", "Retrieve work items from open project.", null, OpenProjectMacroParameters.class);
    }

    /**
     * Constructor for subclasses that need to customise the macro name, description and parameters class.
     *
     * @param name the macro name
     * @param description the macro description
     * @param parametersClass the parameters class
     */
    protected OpenProjectMacro(String name, String description,
        Class<? extends OpenProjectMacroParameters> parametersClass)
    {
        super(name, description, null, parametersClass);
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
        List<Block> licenseBlock = licenseChecker.getMissingLicenseBlock(context);
        if (!licenseBlock.isEmpty()) {
            return licenseBlock;
        }

        String instanceToUse = parameterResolver.resolveInstance(parameters);

        ssrx.use("openproject/css/propertyStyles.css");
        stylingSetupManager.useInstanceStyle(instanceToUse);
        jsx.use("OpenProject.Code.ViewAction");

        List<Block> warningBlock = userTokenChecker.getWarningBlock(instanceToUse);

        if (!warningBlock.isEmpty()) {
            return warningBlock;
        }
        List<Block> result = super.execute(parameters, content, context);
        Map<String, String> params = Collections.singletonMap(CLASS, "open-project-macro");
        return context.isInline() ? Collections.singletonList(new FormatBlock(result, Format.NONE, params))
            : Collections.singletonList(new GroupBlock(result, params));
    }
}
