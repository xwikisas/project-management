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
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.csrf.CSRFToken;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessStatus;

/**
 * Builds the messages telling the current user that they need to connect their OpenProject account, either to see any
 * data of an OpenProject instance, or to see more than its public data.
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
    private OpenProjectAccess openProjectAccess;

    @Inject
    private CSRFToken csrfToken;

    @Inject
    private ContextualLocalizationManager l10n;

    @Inject
    private Provider<XWikiContext> contextProvider;

    /**
     * @param instance the identifier of a OpenProject instance, configured in the Admin Section.
     * @param context the macro transformation context.
     * @return a warning block, to display instead of the OpenProject data, if the current user can't call the given
     *     OpenProject instance (see {@link #getWarningBlock(String, OpenProjectAccessStatus, MacroTransformationContext)})
     */
    public List<Block> getWarningBlock(String instance, MacroTransformationContext context)
    {
        return getWarningBlock(instance, openProjectAccess.resolve(instance).getStatus(), context);
    }

    /**
     * @param instance the identifier of a OpenProject instance, configured in the Admin Section.
     * @param status how the current user reaches the given instance, when the caller has already resolved it.
     * @param context the macro transformation context.
     * @return a warning block, to display instead of the OpenProject data, if the current user can't call the given
     *     OpenProject instance, neither with their own account nor anonymously.
     * @since 1.4.0
     */
    public List<Block> getWarningBlock(String instance, OpenProjectAccessStatus status,
        MacroTransformationContext context)
    {
        if (status == OpenProjectAccessStatus.AUTHENTICATED || status == OpenProjectAccessStatus.ANONYMOUS) {
            return Collections.emptyList();
        }
        return getMessageBlock(instance, "openproject.oauth.notauthorized", "box warningmessage", context);
    }

    /**
     * @param instance the identifier of a OpenProject instance, configured in the Admin Section.
     * @param context the macro transformation context.
     * @return a message block, to display along with the OpenProject data, inviting the current user to connect their
     *     OpenProject account when they only see the public data of the given instance (see
     *     {@link #getConnectHintBlock(String, OpenProjectAccessStatus, MacroTransformationContext)})
     * @since 1.4.0
     */
    public List<Block> getConnectHintBlock(String instance, MacroTransformationContext context)
    {
        return getConnectHintBlock(instance, openProjectAccess.resolve(instance).getStatus(), context);
    }

    /**
     * @param instance the identifier of a OpenProject instance, configured in the Admin Section.
     * @param status how the current user reaches the given instance.
     * @param context the macro transformation context.
     * @return a message block, to display along with the OpenProject data, inviting the current user to connect their
     *     OpenProject account when they only see the public data of the given instance; empty for guests, who can't
     *     connect an OpenProject account.
     * @since 1.4.0
     */
    public List<Block> getConnectHintBlock(String instance, OpenProjectAccessStatus status,
        MacroTransformationContext context)
    {
        if (status != OpenProjectAccessStatus.ANONYMOUS || contextProvider.get().getUserReference() == null) {
            return Collections.emptyList();
        }
        return getMessageBlock(instance, "openproject.oauth.publicdata", "box infomessage", context);
    }

    private List<Block> getMessageBlock(String connectionName, String translationPrefix, String cssClass,
        MacroTransformationContext context)
    {
        XWikiContext xContext = contextProvider.get();
        String viewAction = "view";

        List<Block> message = new ArrayList<>();
        message.add(l10n.getTranslation(translationPrefix + ".hint").render());
        if (xContext.getUserReference() != null) {
            String currentDocumentUrl = xContext.getDoc().getURL(viewAction, xContext);
            LocalDocumentReference connectionDocumentReference = new LocalDocumentReference(
                OPEN_PROJECT_CODE_SPACE, "RenewOAuthConnection");
            String redirectUrl =
                xContext.getWiki().getURL(connectionDocumentReference, viewAction, xContext)
                    + "?connectionName="
                    + URLEncoder.encode(StringUtils.defaultString(connectionName), StandardCharsets.UTF_8)
                    + "&redirectUrl="
                    + URLEncoder.encode(currentDocumentUrl, StandardCharsets.UTF_8)
                    + "&token="
                    + URLEncoder.encode(csrfToken.getToken(), StandardCharsets.UTF_8);

            List<Block> linkContentBlocks =
                Collections.singletonList(l10n.getTranslation(translationPrefix + ".link").render());

            LinkBlock link = new LinkBlock(
                linkContentBlocks,
                new ResourceReference(redirectUrl, ResourceType.URL),
                false
            );
            message.add(link);
        }
        Map<String, String> params = Collections.singletonMap(CLASS, cssClass);
        return Collections.singletonList(
            context.isInline() ? new FormatBlock(message, Format.NONE, params) : new GroupBlock(message, params));
    }
}
