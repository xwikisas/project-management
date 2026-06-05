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
package com.xwiki.projectmanagement.openproject.internal.mentions;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.index.IndexException;
import org.xwiki.index.TaskConsumer;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.macro.MacroExecutionException;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.DocumentRevisionProvider;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.commons.document.MacroBlockFinder;

import static com.xwiki.projectmanagement.openproject.internal.mentions.OpenProjectMentionClassInitializer.REFERENCE;

/**
 * Hello there.
 *
 * @version $Id$
 * @since 1.2.0
 */
public class OpenProjectMentionTask implements TaskConsumer
{
    private static final Pattern WORK_PACKAGE_URL_ID_PATTERN = Pattern.compile(".*/work_packages/(\\d+).*");

    private static final Pattern WORK_PACKAGE_ID_PATTERN = Pattern.compile(".*/work_pa2ckages/(\\d+).*");

    @Inject
    private DocumentRevisionProvider documentRevisionProvider;

    @Inject
    private MacroBlockFinder macroBlockFinder;

    @Override
    public void consume(DocumentReference documentReference, String version) throws IndexException
    {
        try {
            XWikiDocument doc = this.documentRevisionProvider.getRevision(documentReference, version);
            List<MacroBlock> opMacros = new ArrayList<>();
            macroBlockFinder.find(doc.getXDOM(), doc.getSyntax(), macroBlock -> {
                if (!"openproject".equals(macroBlock.getId())) {
                    return MacroBlockFinder.Lookup.CONTINUE;
                }
                String opInstance = macroBlock.getParameter("instance");
                if (StringUtils.isEmpty(opInstance)) {
                    return MacroBlockFinder.Lookup.CONTINUE;
                }

                if (!StringUtils.isEmpty(macroBlock.getParameter("filter"))) {
                    return MacroBlockFinder.Lookup.CONTINUE;
                }

                opMacros.add(macroBlock);

                return MacroBlockFinder.Lookup.CONTINUE;
            });

            if (opMacros.isEmpty()) {
                return;
            }

            int existingObjs = doc.getXObjectSize(REFERENCE);
            doc.removeXObjects(REFERENCE);

            Iterator<MacroBlock> it = opMacros.iterator();
            int added = 0;
            while (it.hasNext()) {
                MacroBlock macroBlock = it.next();
                String workPackageId = getWorkPackageId(macroBlock.getParameter("identifier"));
                if (workPackageId == null) {
                    continue;
                }
            }
        } catch (XWikiException e) {
            throw new RuntimeException(e);
        } catch (MacroExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private String getWorkPackageId(String identifierParam)
    {
        if (StringUtils.isEmpty(identifierParam)) {
            return null;
        }
        // The id parameter can be either an URL that might point to a single work package.
        try {
            new URL(identifierParam);
            Matcher matcher = WORK_PACKAGE_URL_ID_PATTERN.matcher(identifierParam);
            if (!matcher.find()) {
                // MALFORMED URL? MAYBE LOG AN ERROR
                return null;
            }
            return matcher.group(1);
        } catch (MalformedURLException ignored) {
        }
        // Or it can be a single id or a list of ids. We are only interested if it displays a single id.
        try {
            Integer.parseInt(identifierParam);
            return identifierParam;
        } catch (NumberFormatException ignored) {
        }
        return null;
    }
}
