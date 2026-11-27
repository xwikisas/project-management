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
package com.xwiki.projectmanagement.internal.macro;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.extension.CoreExtension;
import org.xwiki.extension.repository.CoreExtensionRepository;
import org.xwiki.extension.version.Version;
import org.xwiki.extension.version.internal.DefaultVersion;

/**
 * Tells whether XWiki sends the xwiki:dom:updated event for the content inserted by an asynchronous rendering, which
 * initializes it (e.g. the charts and the Live Data tables), since XWiki 18.10.0-rc-1 (XWIKI-25675). Before, XWiki
 * sends no event (before XWiki 18.6) or sends it for the removed placeholder, and the macros send it themselves (see
 * {@link ProjectManagementAsyncExecutor}).
 *
 * @version $Id$
 */
@Component(roles = AsyncResultEventChecker.class)
@Singleton
public class AsyncResultEventChecker implements Initializable
{
    // DefaultVersion is internal, but it's the implementation that compares the versions of the extensions like the
    // extension manager does (e.g. 18.10.0-milestone-1 < 18.10.0-rc-1 < 18.10.0-SNAPSHOT < 18.10.0), and it has been
    // stable for years.
    private static final Version FIXED_VERSION = new DefaultVersion("18.10.0-rc-1");

    // The version of the platform, whose resources include the script inserting the asynchronous results (async.js).
    private static final String PLATFORM_EXTENSION_ID = "org.xwiki.platform:xwiki-platform-oldcore";

    @Inject
    private Provider<CoreExtensionRepository> coreExtensionRepositoryProvider;

    @Inject
    private Logger logger;

    private boolean eventSentForTheResult;

    @Override
    public void initialize()
    {
        // The version of XWiki doesn't change while it runs.
        this.eventSentForTheResult = computeEventSentForTheResult();
    }

    /**
     * @return {@code true} if XWiki sends the xwiki:dom:updated event for the elements inserted by an asynchronous
     *     rendering, {@code false} if it doesn't or if the version of XWiki is unknown
     */
    public boolean isEventSentForTheResult()
    {
        return this.eventSentForTheResult;
    }

    private boolean computeEventSentForTheResult()
    {
        try {
            CoreExtension platform = this.coreExtensionRepositoryProvider.get().getCoreExtension(PLATFORM_EXTENSION_ID);
            return platform != null && platform.getId().getVersion().compareTo(FIXED_VERSION) >= 0;
        } catch (RuntimeException e) {
            // E.g. when the extension repository isn't available, in some tests.
            this.logger.debug("Failed to get the version of XWiki.", e);
            return false;
        }
    }
}
