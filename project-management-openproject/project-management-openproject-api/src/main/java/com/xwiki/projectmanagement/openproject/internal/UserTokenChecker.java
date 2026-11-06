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
import java.util.stream.Collectors;

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
import org.xwiki.rendering.block.SpaceBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessStatus;

/**
 * Builds the warnings displayed instead of the OpenProject data that the current user can't see: either they can't call
 * an OpenProject instance at all, or they can only call it anonymously while the data depends on their own account.
 *
 * @version $Id$
 * @since 1.2.0-rc-1
 */
@Component(roles = UserTokenChecker.class)
@Singleton
public class UserTokenChecker
{
    private static final String CLASS = "class";

    private static final String ACCOUNT_REQUIRED = "openproject.oauth.accountrequired";

    private static final List<String> OPEN_PROJECT_CODE_SPACE = Arrays.asList("OpenProject", "Code");

    @Inject
    private OpenProjectAccess openProjectAccess;

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
     * @param context the macro transformation context.
     * @return a warning block, to display instead of the OpenProject data, if the current user can't call the given
     *     OpenProject instance, neither with their own account nor anonymously (see
     *     {@link #getWarningBlock(String, OpenProjectAccessStatus, boolean, MacroTransformationContext)})
     */
    public List<Block> getWarningBlock(String instance, MacroTransformationContext context)
    {
        return getWarningBlock(instance, false, context);
    }

    /**
     * @param instance the identifier of a OpenProject instance, configured in the Admin Section.
     * @param requiresUserAccount {@code true} when the data to display depends on the OpenProject account of the
     *     current user
     * @param context the macro transformation context.
     * @return a warning block, to display instead of the OpenProject data (see
     *     {@link #getWarningBlock(String, OpenProjectAccessStatus, boolean, MacroTransformationContext)})
     * @since 1.4.0
     */
    public List<Block> getWarningBlock(String instance, boolean requiresUserAccount,
        MacroTransformationContext context)
    {
        return getWarningBlock(instance, openProjectAccess.resolve(instance).getStatus(), requiresUserAccount,
            context);
    }

    /**
     * @param instance the identifier of a OpenProject instance, configured in the Admin Section.
     * @param status how the current user reaches the given instance, when the caller has already resolved it.
     * @param requiresUserAccount {@code true} when the data to display depends on the OpenProject account of the
     *     current user (e.g. the work packages assigned to them), so that the public data of the instance can't
     *     replace it
     * @param context the macro transformation context.
     * @return a warning block, to display instead of the OpenProject data, if the current user can't call the given
     *     OpenProject instance, or can only call it anonymously while the data requires their own account; the
     *     warning invites the user to connect their OpenProject account, unless they are a guest or the access mode of
     *     the connection doesn't let users connect their account.
     * @since 1.4.0
     */
    public List<Block> getWarningBlock(String instance, OpenProjectAccessStatus status, boolean requiresUserAccount,
        MacroTransformationContext context)
    {
        if (status == OpenProjectAccessStatus.AUTHENTICATED
            || (status == OpenProjectAccessStatus.ANONYMOUS && !requiresUserAccount))
        {
            return Collections.emptyList();
        }
        OpenProjectConnection connection = this.openProjectConfiguration.getConnection(instance);
        if (connection == null) {
            return getMissingConnectionBlock(instance, context);
        }
        // A link to connect an OpenProject account only works for a connection that allows it.
        boolean userAccounts = connection.getAccessMode().allowsUserAccounts();
        if (status == OpenProjectAccessStatus.ANONYMOUS) {
            return userAccounts ? getMessageBlock(instance, ACCOUNT_REQUIRED, true, context)
                : getMessageBlock(instance, "openproject.oauth.accountunavailable", false, context);
        }
        if (!userAccounts) {
            // The connection only shows public data, but OpenProject didn't accept the anonymous requests.
            return getMessageBlock(instance, "openproject.oauth.publicdataunavailable", false, context);
        }
        return getMessageBlock(instance, "openproject.oauth.notauthorized", userAccounts, context);
    }

    /**
     * Display a warning about the OpenProject data that can't be displayed, e.g. because OpenProject didn't find it.
     * When the current user calls OpenProject anonymously through a connection that lets users connect their
     * OpenProject account, the warning is followed by a link to connect the account, since the data might be visible
     * to it.
     *
     * @param instance the name of the OpenProject connection
     * @param hint the warning to display
     * @param inline {@code true} if the warning is displayed inline
     * @return the warning
     * @since 1.4.0
     */
    public List<Block> getWarningBlock(String instance, Block hint, boolean inline)
    {
        OpenProjectConnection connection = this.openProjectConfiguration.getConnection(instance);
        boolean connectLink = connection != null && connection.getAccessMode().allowsUserAccounts()
            && openProjectAccess.resolve(instance).getStatus() == OpenProjectAccessStatus.ANONYMOUS;
        return getMessageBlock(instance, hint, connectLink ? ACCOUNT_REQUIRED : null, inline);
    }

    /**
     * @return a warning explaining which connections can be used, when the given one isn't configured or when none was
     *     given and there's no default connection
     */
    private List<Block> getMissingConnectionBlock(String instance, MacroTransformationContext context)
    {
        List<OpenProjectConnection> connections = this.openProjectConfiguration.getOpenProjectConnections();
        String connectionNames = connections == null ? ""
            : connections.stream().map(OpenProjectConnection::getConnectionName).collect(Collectors.joining(", "));
        Block hint;
        if (StringUtils.isEmpty(connectionNames)) {
            hint = this.l10n.getTranslation("openproject.connection.none.hint").render();
        } else if (StringUtils.isBlank(instance)) {
            hint = this.l10n.getTranslation("openproject.connection.missing.hint").render(connectionNames);
        } else {
            hint = this.l10n.getTranslation("openproject.connection.unknown.hint").render(instance, connectionNames);
        }
        return getMessageBlock(instance, hint, null, context.isInline());
    }

    private List<Block> getMessageBlock(String connectionName, String translationPrefix, boolean connectLink,
        MacroTransformationContext context)
    {
        return getMessageBlock(connectionName, l10n.getTranslation(translationPrefix + ".hint").render(),
            connectLink ? translationPrefix : null, context.isInline());
    }

    private List<Block> getMessageBlock(String connectionName, Block hint, String linkTranslationPrefix,
        boolean inline)
    {
        XWikiContext xContext = contextProvider.get();
        String viewAction = "view";

        List<Block> message = new ArrayList<>();
        message.add(hint);
        // Guests have no user to attach an OpenProject account to.
        if (linkTranslationPrefix != null && xContext.getUserReference() != null) {
            message.add(new SpaceBlock());
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
                Collections.singletonList(l10n.getTranslation(linkTranslationPrefix + ".link").render());

            LinkBlock link = new LinkBlock(
                linkContentBlocks,
                new ResourceReference(redirectUrl, ResourceType.URL),
                false
            );
            message.add(link);
        }
        Map<String, String> params = Collections.singletonMap(CLASS, "box warningmessage");
        return Collections.singletonList(
            inline ? new FormatBlock(message, Format.NONE, params) : new GroupBlock(message, params));
    }
}
