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
package com.xwiki.projectmanagement.internal.utility;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.extension.CoreExtension;
import org.xwiki.extension.repository.CoreExtensionRepository;
import org.xwiki.extension.version.Version;
import org.xwiki.extension.version.internal.DefaultVersion;

/**
 * Utility class for comparing the current xwiki version against a given one.
 *
 * @version $Id$
 * @since 1.2.1
 */
@Singleton
@Component(roles = XWikiVersionChecker.class)
public class XWikiVersionChecker
{
    @Inject
    private CoreExtensionRepository coreExtensionRepository;

    /**
     * @param version the version that we want to check against.
     * @return TRUE if the passed version is lower than the current xwiki version.
     */
    public boolean isLowerThan(String version)
    {
        Version xwikiVersion = getXWikiVersion();
        if (xwikiVersion == null) {
            return true;
        }
        return xwikiVersion.compareTo(new DefaultVersion(version)) < 0;
    }

    private Version getXWikiVersion()
    {
        CoreExtension coreExtension =
            coreExtensionRepository.getCoreExtension("org.xwiki.platform:xwiki-platform-model-api");
        if (coreExtension == null) {
            // Shouldn't happen.
            return null;
        }
        return coreExtension.getId().getVersion();
    }
}
