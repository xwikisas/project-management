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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.licensing.Licensor;

/**
 * Responsible for checking if there is an active OpenProject license.
 *
 * @version $Id$
 * @since 1.2
 */
@Component(roles = LicenseChecker.class)
@Singleton
public class LicenseChecker
{
    private static final List<String> OPEN_PROJECT_CODE_SPACE = Arrays.asList("OpenProject", "Code");

    private static final String EXTENSION_NAME_PARAM = "extensionName";

    private static final String EXTENSION_NAME_VALUE = "openproject.extension.name";

    @Inject
    private Licensor licensor;

    @Inject
    private Provider<XWikiContext> xContextProvider;

    /**
     * Checks whether the OpenProject license is valid.
     *
     * @return {@code true} if a valid license exists, {@code false} otherwise.
     */
    public boolean hasLicense()
    {
        XWikiContext xContext = xContextProvider.get();
        return licensor.hasLicensure(
            new DocumentReference(xContext.getWikiId(), OPEN_PROJECT_CODE_SPACE, "OpenProjectConnectionClass"));
    }

    /**
     * Returns a missing license macro block if the license is not valid, or an empty list if it is.
     *
     * @param context the macro transformation context used to determine if the block is inline.
     * @return a list containing the {@code missingLicenseMessage} macro block, or an empty list if licensed.
     */
    public List<Block> getMissingLicenseBlock(MacroTransformationContext context)
    {
        if (!hasLicense()) {
            return List.of(new MacroBlock(
                "missingLicenseMessage",
                Map.of(EXTENSION_NAME_PARAM, EXTENSION_NAME_VALUE),
                null,
                context.isInline())
            );
        }
        return Collections.emptyList();
    }
}
