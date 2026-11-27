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
import java.util.List;
import java.util.regex.Matcher;
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
    private static final Pattern WORK_PACKAGE_URL_ID_PATTERN = Pattern.compile(".*/work_packages/(\\d+).*");

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

            // The filters can exclude the work package.
            if (!StringUtils.isEmpty(macroBlock.getParameter("filters"))) {
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
            return getWorkPackageIdFromURL(content.trim());
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
            if (ids.size() == 1 && isNumericId(ids.get(0))) {
                return ids.get(0);
            }
        } catch (WorkItemSourceException e) {
            // The content lists no work package.
        }
        return null;
    }

    private String getWorkPackageId(String identifierParam)
    {
        if (StringUtils.isEmpty(identifierParam)) {
            return null;
        }
        // The id parameter can be either an URL that might point to a single work package.
        String workPackageId = getWorkPackageIdFromURL(identifierParam);
        if (workPackageId != null) {
            return workPackageId;
        }
        // Or it can be a single id or a list of ids. We are only interested if it displays a single id.
        return isNumericId(identifierParam) ? identifierParam : null;
    }

    private String getWorkPackageIdFromURL(String url)
    {
        try {
            new URL(url);
            Matcher matcher = WORK_PACKAGE_URL_ID_PATTERN.matcher(url);
            return matcher.find() ? matcher.group(1) : null;
        } catch (MalformedURLException ignored) {
            return null;
        }
    }

    private static boolean isNumericId(String id)
    {
        try {
            Integer.parseInt(id);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
