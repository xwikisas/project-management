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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.csrf.CSRFToken;
import org.xwiki.localization.LocalizationManager;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager;
import com.xwiki.projectmanagement.internal.macro.AbstractProjectManagementMacro;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
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
    @Inject
    @Named("ssrx")
    private SkinExtension ssrx;

    @Inject
    @Named("ssx")
    private SkinExtension ssx;

    @Inject
    @Named("jsx")
    private SkinExtension jsx;

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private Provider<XWikiContext> xContextProvider;

    @Inject
    private CSRFToken csrfToken;

    @Inject
    private WorkItemPropertyDisplayerManager displayerManager;

    @Inject
    private LocalizationManager l10n;

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
        ssrx.use("openproject/css/propertyStyles.css");
        ssx.use("OpenProject.Code.StyleSheets." + parameters.getInstance());
        jsx.use("OpenProject.Code.ViewAction");

        String viewAction = "view";
        XWikiContext xContext = this.xContextProvider.get();
        if (xContext.getAction().equals(viewAction)) {
            String connectionName = parameters.getInstance();

            String token = openProjectConfiguration.getAccessTokenForConfiguration(connectionName);
            if (token == null || token.isEmpty()) {
                String currentDocumentUrl = xContext.getDoc().getURL(viewAction, xContext);
                LocalDocumentReference connectionDocumentReference = new LocalDocumentReference(
                    Arrays.asList("OpenProject", "Code"), "RenewOAuthConnection");
                String redirectUrl =
                    xContext.getWiki().getURL(connectionDocumentReference, viewAction, xContext) + "?connectionName="
                        + connectionName
                        + "&redirectUrl="
                        + URLEncoder.encode(currentDocumentUrl, StandardCharsets.UTF_8)
                        + "&token="
                        + URLEncoder.encode(csrfToken.getToken(), StandardCharsets.UTF_8);

                List<Block> linkContentBlocks = displayerManager.displayProperty(
                    "",
                    l10n.getTranslationPlain("openproject.oauth.notauthorized.link",
                        xContext.getLocale()), Collections.emptyMap()
                );

                LinkBlock link = new LinkBlock(
                    linkContentBlocks,
                    new ResourceReference(redirectUrl, ResourceType.URL),
                    false
                );

                List<Block> message = displayerManager.displayProperty("", "openproject.oauth.notauthorized.hint",
                    Collections.emptyMap());
                message.add(link);
                return Collections.singletonList(
                    new GroupBlock(message, Collections.singletonMap("class", "box warningmessage")));
            }
        }
        return super.execute(parameters, content, context);
    }
}
