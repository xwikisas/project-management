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

package com.xwiki.projectmanagement.calendar.internal.macro;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.calendar.macro.CalendarMacroParameters;
import com.xwiki.projectmanagement.internal.macro.AbstractWorkItemsMacro;
import org.apache.http.client.utils.URIBuilder;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract calendar macro meant to be implemented by project management implementers. It constructs a REST URL pointing
 * to the {@code CalendarResource} endpoint and delegates rendering to the {@code {{calendar}}} wiki macro.
 *
 * @param <T> the macro parameter type, must extend {@link CalendarMacroParameters}.
 * @version $Id$
 * @since 1.2.0-rc-7
 */
public abstract class AbstractProjectManagementCalendarMacro<T extends CalendarMacroParameters>
    extends AbstractWorkItemsMacro<T>
{
    private static final String KEY_CLIENT = "client";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    @Named("ssrx")
    private SkinExtension ssrx;

    /**
     * Constructor.
     *
     * @param name        the name of the macro.
     * @param description the description of the macro.
     * @param clazz       the class of the parameters bean.
     */
    public AbstractProjectManagementCalendarMacro(String name, String description, Class<?> clazz)
    {
        super(name, description, clazz);
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public List<Block> execute(T parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        try {
            this.ssrx.use("projectmanagercalendar/css/parameters.css");
            URIBuilder uriBuilder = getUriBuilder(parameters);
            updateUrl(uriBuilder, parameters);
            return Collections.singletonList(getMacroBlock(parameters, uriBuilder));
        } catch (Exception e) {
            throw new MacroExecutionException("Failed to execute the project management calendar macro.", e);
        }
    }

    private URIBuilder getUriBuilder(T parameters)
    {
        XWikiContext wikiContext = this.xcontextProvider.get();
        String contextPath = wikiContext.getRequest().getContextPath();
        String clientId = parameters.getClient();
        String wikiId = wikiContext.getWikiId();
        // Build the base REST URL.
        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder.setPath(contextPath + "/rest/wikis/" + wikiId + "/projectmanagement/" + clientId + "/calendar");
        String filters = parameters.getFilters();
        if (filters != null && !filters.isEmpty()) {
            uriBuilder.addParameter("filters", filters);
        }
        // Append client context parameters (e.g. "instance" for OpenProject).
        Map<String, Object> ctx = this.macroContext.getContext();
        ctx.forEach((key, value) -> uriBuilder.addParameter(key, value != null ? value.toString() : ""));
        uriBuilder.addParameter("limit", parameters.getLimit().toString());
        return uriBuilder;
    }

    protected void updateUrl(URIBuilder ub, T parameters)
    {
        // To be overwritten.
    }

    protected void excludeWorkItems(URIBuilder ub)
    {
        ub.addParameter("excludeWorkItems", "true");
    }

    private MacroBlock getMacroBlock(T parameters, URIBuilder uriBuilder)
    {
        String jsonURL = uriBuilder.toString();

        // Build the parameters to pass to the {{calendar}} wiki macro.
        Map<String, String> calendarParams = new HashMap<>();
        calendarParams.put("json", jsonURL);
        if (parameters.getDefaultView() != null) {
            calendarParams.put("defaultView", parameters.getDefaultView().name());
        }
        setTimeIntervals(parameters.getTimeInterval(), calendarParams);
        calendarParams.put("editable", "false");
        calendarParams.put("firstDay", String.valueOf(parameters.getFirstDay().getDayValue()));
        return new MacroBlock("calendar", calendarParams, null, false);
    }

    private void setTimeIntervals(String timeInterval, Map<String, String> calendarParams)
    {
        if (timeInterval != null && !timeInterval.isEmpty()) {
            String[] parts = timeInterval.split("-", 2);
            if (parts.length == 2) {
                String minTime = parts[0].trim();
                String maxTime = parts[1].trim();
                if (!minTime.isEmpty()) {
                    calendarParams.put("minTime", minTime);
                }
                if (!maxTime.isEmpty()) {
                    calendarParams.put("maxTime", maxTime);
                }
            }
        }
    }
}
