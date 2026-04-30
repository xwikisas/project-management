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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.csrf.CSRFToken;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;

/**
 * Responsible with checking if a connection to a given OpenProject instance exists and a token was retrieved.
 *
 * @version $Id$
 * @since 1.2.0-rc-1
 */
@Component(roles = UserTokenChecker.class)
@Singleton
public class UserTokenChecker
{
    private static final String CLASS = "class";

    private static final List<String> OPEN_PROJECT_CODE_SPACE = Arrays.asList("OpenProject", "Code");

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private CSRFToken csrfToken;

    @Inject
    private ContextualLocalizationManager l10n;

    @Inject
    private Provider<XWikiContext> contextProvider;

    /**
     * @param instance the identifier of a OpenProject instance, configured in the Admin Section.
     * @return a warning block if the connection of the current user to the given OpenProject instance does not exist or
     *     the token has expired.
     */
    public List<Block> getWarningBlock(String instance)
    {
        XWikiContext xContext = contextProvider.get();
        String viewAction = "view";
        if (xContext.getAction().equals(viewAction)) {
            String connectionName = instance;

            if (xContext.getUserReference() == null
                || openProjectConfiguration.getAccessTokenForConfiguration(connectionName) == null)
            {
                List<Block> warning = new ArrayList<>();
                warning.add(l10n.getTranslation("openproject.oauth.notauthorized.hint").render());
                if (xContext.getUserReference() != null) {
                    String currentDocumentUrl = xContext.getDoc().getURL(viewAction, xContext);
                    LocalDocumentReference connectionDocumentReference = new LocalDocumentReference(
                        OPEN_PROJECT_CODE_SPACE, "RenewOAuthConnection");
                    String redirectUrl =
                        xContext.getWiki().getURL(connectionDocumentReference, viewAction, xContext)
                            + "?connectionName="
                            + connectionName
                            + "&redirectUrl="
                            + URLEncoder.encode(currentDocumentUrl, StandardCharsets.UTF_8)
                            + "&token="
                            + URLEncoder.encode(csrfToken.getToken(), StandardCharsets.UTF_8);

                    List<Block> linkContentBlocks =
                        Collections.singletonList(l10n.getTranslation("openproject.oauth.notauthorized.link").render());

                    LinkBlock link = new LinkBlock(
                        linkContentBlocks,
                        new ResourceReference(redirectUrl, ResourceType.URL),
                        false
                    );
                    warning.add(link);
                }
                return Collections.singletonList(
                    new GroupBlock(warning, Collections.singletonMap(CLASS, "box warningmessage")));
            }
        }
        return Collections.emptyList();
    }
}
