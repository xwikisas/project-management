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
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import javax.inject.Inject;
import javax.inject.Provider;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    private static <T extends CalendarMacroParameters> MacroBlock getMacroBlock(T parameters, StringBuilder urlBuilder)
    {
        String jsonURL = urlBuilder.toString();

        // Build the parameters to pass to the {{calendar}} wiki macro.
        Map<String, String> calendarParams = new HashMap<>();
        calendarParams.put("json", jsonURL);
        if (parameters.getDefaultView() != null) {
            calendarParams.put("defaultView", parameters.getDefaultView().name());
        }
        if (parameters.getMinTime() != null) {
            calendarParams.put("minTime", parameters.getMinTime());
        }
        if (parameters.getMaxTime() != null) {
            calendarParams.put("maxTime", parameters.getMaxTime());
        }
        calendarParams.put("editable", "true");
        calendarParams.put("firstDay", String.valueOf(parameters.getFirstDay()));
        return new MacroBlock("calendar", calendarParams, null, false);
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
            XWikiContext wikiContext = this.xcontextProvider.get();
            String contextPath = wikiContext.getRequest().getContextPath();
            String wikiId = wikiContext.getWikiId();
            String clientId = parameters.getClient();

            // Build the base REST URL.
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(contextPath).append("/rest/wikis/").append(wikiId).append("/projectmanagement/")
                .append(clientId).append("/calendar");

            // Append query parameters.
            boolean hasParams = false;

            String filters = parameters.getFilters();
            if (filters != null && !filters.isEmpty()) {
                urlBuilder.append("?filters=").append(URLEncoder.encode(filters, StandardCharsets.UTF_8));
                hasParams = true;
            }

            // Append client context parameters (e.g. "instance" for OpenProject).
            Map<String, Object> ctx = this.macroContext.getContext();
            if (ctx != null && !ctx.isEmpty()) {
                for (Map.Entry<String, Object> entry : ctx.entrySet()) {
                    String key = entry.getKey();
                    if (KEY_CLIENT.equals(key)) {
                        continue;
                    }
                    String value = entry.getValue() != null ? entry.getValue().toString() : "";
                    urlBuilder.append(hasParams ? '&' : '?').append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                        .append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                    hasParams = true;
                }
            }
            urlBuilder.append(hasParams ? '&' : '?').append("limit=").append(parameters.getLimit());

            return Collections.singletonList(getMacroBlock(parameters, urlBuilder));
        } catch (Exception e) {
            throw new MacroExecutionException("Failed to execute the project management calendar macro.", e);
        }
    }
}
