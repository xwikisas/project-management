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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.macro.MacroExecutionException;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.commons.document.MacroBlockFinder;
import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectLink;
import com.xwiki.projectmanagement.source.WorkItemSource;
import com.xwiki.projectmanagement.source.WorkItemSourceContext;

import static com.xwiki.projectmanagement.openproject.internal.mentions.OpenProjectMentionClassInitializer.PROP_INSTANCE;

/**
 * Finds the work packages mentioned in a page: the {@code openproject} macros displaying a single work package,
 * selected by its numeric id or its URL, either in the macro content (with the {@code list} or {@code url} source) or
 * in the deprecated {@code identifier} parameter.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component(roles = WorkPackageMentionFinder.class)
@Singleton
public class WorkPackageMentionFinder
{
    // ASCII digits only: Character.isDigit accepts other digits.
    private static final Pattern NUMERIC_ID_PATTERN = Pattern.compile("^[0-9]+$");

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");

    private static final String IDENTIFIER = "identifier";

    private static final String SOURCE = "source";

    private static final String OPENPROJECT = "openproject";

    @Inject
    private MacroBlockFinder macroBlockFinder;

    @Inject
    @Named("list")
    private WorkItemSource listSource;

    /**
     * @param doc the page
     * @return the work packages mentioned in the content of the page, in the order of their macros
     * @throws MacroExecutionException if the content of the page can't be parsed
     */
    public List<WorkPackageMention> find(XWikiDocument doc) throws MacroExecutionException
    {
        List<WorkPackageMention> mentions = new ArrayList<>();
        macroBlockFinder.find(doc.getXDOM(), doc.getSyntax(), macroBlock -> {
            if (!OPENPROJECT.equals(macroBlock.getId())) {
                return MacroBlockFinder.Lookup.CONTINUE;
            }
            String opInstance = macroBlock.getParameter(PROP_INSTANCE);
            if (StringUtils.isEmpty(opInstance)) {
                return MacroBlockFinder.Lookup.CONTINUE;
            }

            // The filters, including the ones of a preset, can exclude the work package.
            if (!StringUtils.isEmpty(macroBlock.getParameter("filters"))
                || !StringUtils.isEmpty(macroBlock.getParameter("presetId")))
            {
                return MacroBlockFinder.Lookup.CONTINUE;
            }

            String workPackageId = getWorkPackageId(macroBlock);
            if (workPackageId != null) {
                mentions.add(new WorkPackageMention(workPackageId, opInstance));
            }

            return MacroBlockFinder.Lookup.CONTINUE;
        });
        return mentions;
    }

    /**
     * @return the id of the work package displayed by the macro, if it displays a single work package selected by its
     *     numeric id or by its URL, {@code null} otherwise
     */
    private String getWorkPackageId(MacroBlock macroBlock)
    {
        String content = macroBlock.getContent();
        if (StringUtils.isBlank(content)) {
            // The work packages are selected by the deprecated identifier parameter.
            return getWorkPackageId(macroBlock.getParameter(IDENTIFIER));
        }
        if (StringUtils.isNotBlank(macroBlock.getParameter(IDENTIFIER))) {
            // The macro fails when the identifier parameter is used together with the content.
            return null;
        }
        // The content of a macro without source parameter is a query.
        String source = StringUtils.defaultString(macroBlock.getParameter(SOURCE)).trim();
        if ("list".equalsIgnoreCase(source)) {
            return getListedWorkPackageId(content);
        } else if ("url".equalsIgnoreCase(source)) {
            // The url source accepts a single link, without spaces.
            String url = content.trim();
            return WHITESPACE_PATTERN.matcher(url).find() ? null : getWorkPackageIdFromLink(url);
        }
        return null;
    }

    /**
     * @return the id listed in the content of a macro with the {@code list} source, if it lists a single numeric id,
     *     {@code null} otherwise (a work package listed by its key, e.g. {@code PROJ-123}, would have to be resolved
     *     by OpenProject)
     */
    private String getListedWorkPackageId(String content)
    {
        try {
            List<String> ids = this.listSource.read(content, new WorkItemSourceContext(OPENPROJECT)).getIds();
            if (ids.size() == 1) {
                return getNumericId(ids.get(0));
            }
        } catch (WorkItemSourceException e) {
            // The content lists no work package.
        }
        return null;
    }

    private String getWorkPackageId(String identifierParam)
    {
        String identifier = StringUtils.trim(identifierParam);
        if (StringUtils.isEmpty(identifier)) {
            return null;
        }
        // The identifier can be a link, e.g. of a work package.
        OpenProjectLink link = OpenProjectLink.parse(identifier);
        if (link != null) {
            return link.getWorkPackageId();
        }
        // Or a list of work packages: only a single numeric id is a mention.
        return getNumericId(identifier);
    }

    /**
     * @return the id of the work package of the link, read as the macros read it, if it's the link of a work package,
     *     {@code null} otherwise
     */
    private static String getWorkPackageIdFromLink(String url)
    {
        OpenProjectLink link = OpenProjectLink.parse(url);
        return link == null ? null : link.getWorkPackageId();
    }

    /**
     * @return the id without its leading zeros, as stored by OpenProject and searched by the mentions resource (e.g.
     *     {@code 12} for {@code 012}), if it's a numeric id, {@code null} otherwise
     */
    private static String getNumericId(String id)
    {
        if (!NUMERIC_ID_PATTERN.matcher(id).matches()) {
            return null;
        }
        String normalizedId = StringUtils.stripStart(id, "0");
        return normalizedId.isEmpty() ? "0" : normalizedId;
    }
}
