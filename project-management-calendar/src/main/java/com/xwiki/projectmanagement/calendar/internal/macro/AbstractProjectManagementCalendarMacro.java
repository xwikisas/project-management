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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.calendar.macro.CalendarMacroParameters;
import com.xwiki.projectmanagement.calendar.rest.CalendarResource;
import com.xwiki.projectmanagement.internal.macro.AbstractWorkItemsMacro;
import com.xwiki.projectmanagement.internal.macro.PresetFiltersCombiner;
import com.xwiki.projectmanagement.internal.source.WorkItemNameResolver;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceReader;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceRequest;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceResult;

/**
 * Abstract calendar macro meant to be implemented by project management implementers. It constructs a REST URL pointing
 * to the {@link CalendarResource} endpoint and delegates rendering to the {@code {{calendar}}} wiki macro.
 *
 * @param <T> the macro parameter type, must extend {@link CalendarMacroParameters}.
 * @version $Id$
 * @since 1.2.0
 */
public abstract class AbstractProjectManagementCalendarMacro<T extends CalendarMacroParameters>
    extends AbstractWorkItemsMacro<T>
{
    private static final String KEY_CLIENT = "client";

    private static final String SLASH = "/";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    @Named("ssrx")
    private SkinExtension ssrx;

    @Inject
    private PresetFiltersCombiner presetFiltersCombiner;

    @Inject
    private WorkItemSourceReader sourceReader;

    /**
     * Constructor.
     *
     * @param name the name of the macro.
     * @param description the description of the macro.
     * @param clazz the class of the parameters bean.
     */
    public AbstractProjectManagementCalendarMacro(String name, String description, Class<?> clazz)
    {
        this(name, description, null, clazz);
    }

    /**
     * @param name the name of the macro
     * @param description the description of the macro
     * @param contentDescriptor the description of the content of the macro, or {@code null} if it has no content
     * @param clazz the class of the parameters bean
     * @since 1.5.0
     */
    public AbstractProjectManagementCalendarMacro(String name, String description, ContentDescriptor contentDescriptor,
        Class<?> clazz)
    {
        super(name, description, contentDescriptor, clazz);
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
        // An invalid content is reported as is: its message tells the user what to fix.
        WorkItemSourceResult source = readSource(parameters, content, filters -> resolveNames(parameters, filters));
        parameters.setFilters(source.getFilters());
        try {
            this.ssrx.use("projectmanagercalendar/css/parameters.css");
            // The preset filters have to be resolved before building the REST URL, which carries the filters.
            maybeSetPresetFilters(parameters);
            // We build the REST URL to the CalendarResource endpoint, then add it to the rendering of the calendar
            // macro.
            URIBuilder uriBuilder = getUriBuilder(parameters, source);
            updateUrl(uriBuilder, parameters);
            return Collections.singletonList(getMacroBlock(parameters, uriBuilder));
        } catch (Exception e) {
            throw new MacroExecutionException("Failed to execute the project management calendar macro.", e);
        }
    }

    /**
     * Read the content of the macro with the source chosen by its {@code source} parameter. This has no side effect:
     * the extending classes can call it before executing the macro, e.g. to know whether the work items are filtered
     * on the current user.
     *
     * @param parameters the parameters of the macro
     * @param content the content of the macro
     * @return the work items selected by the macro: its {@code filters} parameter when the content is empty,
     *     otherwise the filters and the identifier read from the content
     * @throws MacroExecutionException if the content can't be read, or if it is used together with the
     *     {@code filters} parameter, which it replaces
     * @since 1.5.0
     */
    protected WorkItemSourceResult readSource(T parameters, String content) throws MacroExecutionException
    {
        return readSource(parameters, content, null);
    }

    /**
     * Read the content of the macro with the source chosen by its {@code source} parameter, the names written in the
     * filters read from the content being resolved by the given resolver. The extending classes can override it, for
     * example to ignore the content.
     *
     * @param parameters the parameters of the macro
     * @param content the content of the macro
     * @param nameResolver replaces the names written in the filters read from the content by ids, or {@code null} to
     *     keep them as text
     * @return the work items selected by the macro, as returned by the two-argument {@code readSource}
     * @throws MacroExecutionException if the content can't be read, or if a name can't be resolved
     * @since 1.5.0
     */
    protected WorkItemSourceResult readSource(T parameters, String content, WorkItemNameResolver nameResolver)
        throws MacroExecutionException
    {
        return this.sourceReader.read(new WorkItemSourceRequest().client(parameters.getClient()).content(content)
            .source(parameters.getSource()).filters(parameters.getFilters(), true).nameResolver(nameResolver));
    }

    /**
     * Replace the names written in the filters read from the content (for example the project {@code xwiki}) by the
     * ids of the resources of the project management tool. It is called when the macro is executed, after reading its
     * content, but not by the two-argument {@code readSource} (which only checks the content, for example for a filter
     * on the current user). By default, the names are kept and passed to the client as text.
     *
     * @param parameters the parameters of the macro
     * @param filters the filters read from the content, whose values are names
     *     ({@link com.xwiki.projectmanagement.source.WorkItemName}) or text (for example ids or dates)
     * @return the filters with the names replaced by ids
     * @throws MacroExecutionException if a name matches no resource or several
     * @since 1.5.0
     */
    protected List<LiveDataQuery.Filter> resolveNames(T parameters, List<LiveDataQuery.Filter> filters)
        throws MacroExecutionException
    {
        return filters;
    }

    private void maybeSetPresetFilters(T parameters) throws MacroExecutionException
    {
        if (StringUtils.isNotEmpty(parameters.getPresetId())) {
            parameters.setFilters(
                this.presetFiltersCombiner.combine(parameters.getPresetId(), parameters.getFilters()));
        }
    }

    private String getContextPath(XWikiContext wikiContext)
    {
        if (wikiContext.getRequest() != null) {
            return wikiContext.getRequest().getContextPath();
        }
        // No request, e.g. when the macro is rendered by a background job: use the configured web application path.
        String webAppPath = StringUtils.strip(wikiContext.getWiki().getWebAppPath(wikiContext), SLASH);
        return StringUtils.isEmpty(webAppPath) ? "" : SLASH + webAppPath;
    }

    private URIBuilder getUriBuilder(T parameters, WorkItemSourceResult source)
    {
        XWikiContext wikiContext = this.xcontextProvider.get();
        String contextPath = getContextPath(wikiContext);
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
        // The identifier and the other values read from the content, for the client.
        source.getClientContext().forEach((key, value) -> uriBuilder.addParameter(key, String.valueOf(value)));
        if (parameters.getLimit() != null) {
            uriBuilder.addParameter("limit", parameters.getLimit().toString());
        }
        return uriBuilder;
    }

    /**
     * Add additional query parameters to the REST URL passed to the calendar macro.
     *
     * @param ub the URI builder for the calendar REST endpoint.
     * @param parameters the macro parameters.
     */
    protected void updateUrl(URIBuilder ub, T parameters)
    {
        // Subclasses may override this method to append extra query parameters.
    }

    /**
     * Method to add the {@code excludeWorkItems} query parameter to the REST URL. When this parameter is present, the
     * calendar resource will skip the retrieval of standard work item events.
     *
     * @param ub the URI builder for the calendar REST endpoint.
     */
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
