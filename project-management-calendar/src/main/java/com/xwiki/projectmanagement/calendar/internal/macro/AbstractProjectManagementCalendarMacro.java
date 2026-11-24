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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.calendar.macro.CalendarMacroParameters;
import com.xwiki.projectmanagement.calendar.rest.CalendarResource;
import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.internal.macro.AbstractWorkItemsMacro;
import com.xwiki.projectmanagement.internal.macro.PresetFiltersCombiner;
import com.xwiki.projectmanagement.internal.source.WorkItemNameResolver;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceReader;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceRequest;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceResult;

/**
 * Abstract calendar macro meant to be implemented by project management implementers. It constructs a REST URL pointing
 * to the {@link CalendarResource} endpoint and delegates rendering to the {@code {{calendar}}} wiki macro. The calendar
 * is preceded by hidden messages explaining why its events can't be loaded, one per {@link CalendarLoadError}: the
 * script of the calendar shows the one matching the status of the failed request of the events.
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

    private static final String CLASS = "class";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    @Named("ssrx")
    private SkinExtension ssrx;

    @Inject
    @Named("jsrx")
    private SkinExtension jsSkinExtension;

    @Inject
    private CalendarLoadErrorMessages loadErrorMessages;

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

    /**
     * {@inheritDoc}
     * <p>
     * The result is a single block wrapping the calendar and the messages of its load errors, to which the extending
     * classes can add parameters (e.g. a class or data attributes read by their scripts).
     */
    @Override
    public List<Block> execute(T parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        // The client context is passed to the calendar resource through its URL, and isn't needed once the URL is
        // built.
        try (DefaultProjectManagementClientExecutionContext.ContextScope scope =
            DefaultProjectManagementClientExecutionContext.pushContext(this.macroContext, getClientContext(parameters)))
        {
            return executeCalendar(parameters, content, context);
        }
    }

    /**
     * @param parameters the parameters of the macro
     * @return the client context of the calendar (e.g. the OpenProject connection), passed to the calendar REST
     *     resource through its URL; by default, the current client context
     * @since 1.5.0
     */
    protected Map<String, Object> getClientContext(T parameters)
    {
        return this.macroContext.getContext();
    }

    private List<Block> executeCalendar(T parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        VisibleHours visibleHours = VisibleHours.parse(parameters.getTimeInterval());
        // An invalid content is reported as is: its message tells the user what to fix.
        WorkItemSourceResult source = readSource(parameters, content, filters -> resolveNames(parameters, filters));
        parameters.setFilters(source.getFilters());
        try {
            this.ssrx.use("projectmanagercalendar/css/parameters.css");
            this.jsSkinExtension.use("projectmanagercalendar/js/calendarLoadError.js");
            // The preset filters have to be resolved before building the REST URL, which carries the filters.
            maybeSetPresetFilters(parameters);
            // We build the REST URL to the CalendarResource endpoint, then add it to the rendering of the calendar
            // macro.
            URIBuilder uriBuilder = getUriBuilder(parameters, source);
            updateUrl(uriBuilder, parameters);
            return Collections.singletonList(getCalendarBlock(parameters, visibleHours, uriBuilder, context));
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

    /**
     * The message displayed above the calendar when its events can't be loaded for the given reason. By default, the
     * {@link #getLoadErrorHint(CalendarLoadError) hint} of the error in a warning or error box. The extending classes
     * can override it, e.g. to invite the user to authenticate to the project management tool.
     *
     * @param parameters the parameters of the macro
     * @param error the reason why the events can't be loaded
     * @param context the macro transformation context
     * @return the message explaining the error
     * @since 1.5.0
     */
    protected List<Block> getLoadErrorMessage(T parameters, CalendarLoadError error,
        MacroTransformationContext context)
    {
        return this.loadErrorMessages.getMessage(error);
    }

    /**
     * @param error the reason why the events of the calendar can't be loaded
     * @return the default text explaining the error, translated
     * @since 1.5.0
     */
    protected Block getLoadErrorHint(CalendarLoadError error)
    {
        return this.loadErrorMessages.getHint(error);
    }

    private Block getCalendarBlock(T parameters, VisibleHours visibleHours, URIBuilder uriBuilder,
        MacroTransformationContext context)
    {
        String jsonURL = uriBuilder.toString();
        List<Block> children = new ArrayList<>();
        // The messages are hidden until the script of the calendar finds that the events can't be loaded: their text
        // is rendered here, escaped and translated, rather than built from the response of the failed request.
        for (CalendarLoadError error : CalendarLoadError.values()) {
            // The attributes are ordered, for a stable output.
            Map<String, String> messageParameters = new LinkedHashMap<>();
            messageParameters.put(CLASS, "projectmanagement-calendar-load-error");
            messageParameters.put("data-status", error.getStatus());
            // Boolean attribute, whose value is its name.
            String hidden = "hidden";
            messageParameters.put(hidden, hidden);
            children.add(new GroupBlock(getLoadErrorMessage(parameters, error, context), messageParameters));
        }
        children.add(getMacroBlock(parameters, visibleHours, jsonURL));
        Map<String, String> calendarParameters = new LinkedHashMap<>();
        calendarParameters.put(CLASS, "projectmanagement-calendar");
        // The script of the calendar recognizes the requests of its events by their URL.
        calendarParameters.put("data-events-url", jsonURL);
        return new GroupBlock(children, calendarParameters);
    }

    private MacroBlock getMacroBlock(T parameters, VisibleHours visibleHours, String jsonURL)
    {

        // Build the parameters to pass to the {{calendar}} wiki macro.
        Map<String, String> calendarParams = new HashMap<>();
        calendarParams.put("json", jsonURL);
        if (parameters.getDefaultView() != null) {
            calendarParams.put("defaultView", parameters.getDefaultView().name());
        }
        if (visibleHours.getStart() != null) {
            calendarParams.put("minTime", visibleHours.getStart());
        }
        if (visibleHours.getEnd() != null) {
            calendarParams.put("maxTime", visibleHours.getEnd());
        }
        calendarParams.put("editable", "false");
        calendarParams.put("firstDay", String.valueOf(parameters.getFirstDay().getDayValue()));
        return new MacroBlock("calendar", calendarParams, null, false);
    }
}
