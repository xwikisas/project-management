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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.observation.ObservationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;

import com.xwiki.projectmanagement.internal.macro.AbstractProjectManagementMacro;
import com.xwiki.projectmanagement.internal.source.WorkItemNameResolver;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceResult;
import com.xwiki.projectmanagement.openproject.event.BeforeOpenProjectMacroExecutionEvent;
import com.xwiki.projectmanagement.openproject.internal.CurrentUserFilterDetector;
import com.xwiki.projectmanagement.openproject.internal.LicenseChecker;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectClient;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectLinkChecker;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectLinkQueryResolver;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectNameResolver;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectSavedQueryResolver;
import com.xwiki.projectmanagement.openproject.internal.UserTokenChecker;
import com.xwiki.projectmanagement.openproject.internal.displayer.StylingSetupManager;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;

/**
 * OpenProject macro.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("openproject")
public class OpenProjectMacro extends AbstractProjectManagementMacro<OpenProjectMacroParameters>
{
    private static final String CLIENT = "openproject";

    private static final String CONTENT_DESCRIPTION =
        "The work packages to display, in the format chosen by the source parameter.";

    private static final String CLASS = "class";

    @Inject
    private OpenProjectNameResolver nameResolver;

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
    private ObservationManager observationManager;

    @Inject
    private CurrentUserFilterDetector currentUserFilterDetector;

    @Inject
    private OpenProjectLinkChecker linkChecker;

    @Inject
    private OpenProjectSavedQueryResolver savedQueryResolver;

    @Inject
    private OpenProjectLinkQueryResolver linkQueryResolver;

    /**
     * Default constructor.
     */
    public OpenProjectMacro()
    {
        super("OpenProject", "Retrieve work items from OpenProject.",
            createSourceContentDescriptor(CONTENT_DESCRIPTION), OpenProjectMacroParameters.class);
        setDefaultCategories(Set.of(OpenProjectClient.CLIENT_PRETTY_NAME, DEFAULT_CATEGORY_CONTENT));
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
        setDefaultCategories(Set.of(OpenProjectClient.CLIENT_PRETTY_NAME, DEFAULT_CATEGORY_CONTENT));
    }

    @Override
    protected String getClientId()
    {
        return CLIENT;
    }

    @Override
    public void processParameters(OpenProjectMacroParameters parameters)
    {
        addToSourceParams(parameters, "client", CLIENT);

        addToSourceParams(parameters, "instance", parameters.getInstance());

        addToSourceParams(parameters, "identifier", parameters.getIdentifier());

        addToSourceParams(parameters, "translationPrefix", "openproject.");
    }

    @Override
    public List<Block> asyncProcessParameters(OpenProjectMacroParameters parameters,
        MacroTransformationContext context)
    {
        return resolveSavedQuery(parameters, true, context);
    }

    @Override
    protected List<Block> asyncProcessParameters(OpenProjectMacroParameters parameters, String displayerContent,
        MacroTransformationContext context)
    {
        if (StringUtils.isEmpty(displayerContent)) {
            return asyncProcessParameters(parameters, context);
        }
        // The legacy Live Data configuration of the content replaces the filters and the sorting of the macro, so the
        // Live Data can't show the ones of a saved query: the client keeps applying them.
        return resolveSavedQuery(parameters, false, context);
    }

    private List<Block> resolveSavedQuery(OpenProjectMacroParameters parameters, boolean showInLiveData,
        MacroTransformationContext context)
    {
        return this.savedQueryResolver.resolve(parameters, context.isInline(), showInLiveData,
            (key, value) -> setSourceParam(parameters, key, value));
    }

    /**
     * @param parameters the parameters of the macro
     * @param source the work packages selected by the macro, with its content read
     * @return {@code true} if the displayed work packages depend on the OpenProject account of the current user, which
     *     anonymous requests can't use: the work packages are filtered on the current user
     */
    protected boolean requiresUserAccount(OpenProjectMacroParameters parameters, WorkItemSourceResult source)
    {
        return this.currentUserFilterDetector.hasCurrentUserFilter(parameters.getPresetId(), source.getFilters())
            || this.currentUserFilterDetector.hasCurrentUserFilterInIdentifier(source.getIdentifier());
    }

    @Override
    public List<Block> execute(OpenProjectMacroParameters parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {
        List<Block> licenseBlock = licenseChecker.getMissingLicenseBlock(context);
        if (!licenseBlock.isEmpty()) {
            return licenseBlock;
        }
        observationManager.notify(new BeforeOpenProjectMacroExecutionEvent(), this, parameters);

        String instanceToUse = parameters.getInstance();
        ssrx.use("openproject/css/propertyStyles.css");
        stylingSetupManager.useInstanceStyle(instanceToUse);
        jsx.use("OpenProject.Code.ViewAction");

        WorkItemSourceResult source = readSource(parameters, content);
        // The work packages are requested from the server of the connection, whatever the server of the link.
        this.linkChecker.check(instanceToUse, source.getIdentifier());
        List<Block> warningBlock = userTokenChecker.getWarningBlock(instanceToUse,
            requiresUserAccount(parameters, source), context);

        if (!warningBlock.isEmpty()) {
            return warningBlock;
        }
        List<Block> result = new ArrayList<>(super.execute(parameters, content, context));
        Map<String, String> params = Collections.singletonMap(CLASS, "open-project-macro");
        return context.isInline() ? Collections.singletonList(new FormatBlock(result, Format.NONE, params))
            : Collections.singletonList(new GroupBlock(result, params));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The filters and the sorting of the link of a work package list are moved to the filters and the sorting of the
     * macro, so that its Live Data shows them (see {@link OpenProjectLinkQueryResolver}).
     */
    @Override
    protected WorkItemSourceResult readSource(OpenProjectMacroParameters parameters, String content,
        WorkItemNameResolver nameResolver) throws MacroExecutionException
    {
        return this.linkQueryResolver.resolve(parameters.getInstance(), parameters.getSort(),
            super.readSource(parameters, content, nameResolver));
    }

    @Override
    protected List<LiveDataQuery.Filter> resolveNames(OpenProjectMacroParameters parameters,
        List<LiveDataQuery.Filter> filters) throws MacroExecutionException
    {
        return this.nameResolver.resolve(parameters.getInstance(), filters);
    }
}
