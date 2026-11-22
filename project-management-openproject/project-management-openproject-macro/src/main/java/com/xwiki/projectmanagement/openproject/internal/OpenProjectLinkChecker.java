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

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.rendering.macro.MacroExecutionException;

import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectLink;

/**
 * Checks that the OpenProject link given to a macro (in its content or in its deprecated {@code identifier} parameter)
 * belongs to the server of the connection of the macro. The work packages are always requested from the server of the
 * connection, so the link of another server would otherwise silently select the work packages of the project (or the
 * saved query, or the work package) with the same identifier on the server of the connection.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component(roles = OpenProjectLinkChecker.class)
@Singleton
public class OpenProjectLinkChecker
{
    private static final String OTHER_SERVER_KEY = "openproject.link.otherServer";

    @Inject
    private OpenProjectConfiguration configuration;

    @Inject
    private ContextualLocalizationManager l10n;

    /**
     * @param instance the name of the OpenProject connection of the macro
     * @param identifier the work packages selected by the macro: a list of work packages, or a link
     * @throws MacroExecutionException if the identifier is a link of another server than the one of the connection
     */
    public void check(String instance, String identifier) throws MacroExecutionException
    {
        // The lists of work packages are not checked, and don't need the connection.
        if (OpenProjectLink.parse(identifier) == null) {
            return;
        }
        // The macros report a missing connection on their own.
        OpenProjectConnection connection = this.configuration.getConnection(instance);
        OpenProjectLink link = OpenProjectLink.getLinkOfAnotherServer(identifier, connection);
        if (link == null) {
            return;
        }
        String message =
            this.l10n.getTranslationPlain(OTHER_SERVER_KEY, link.getServer(), instance, connection.getServerURL());
        throw new MacroExecutionException(message != null ? message
            : String.format("The link is from the OpenProject server [%s], not [%s] of the connection [%s].",
                link.getServer(), connection.getServerURL(), instance));
    }
}
