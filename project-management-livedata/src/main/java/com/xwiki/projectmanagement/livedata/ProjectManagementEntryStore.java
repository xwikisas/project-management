package com.xwiki.projectmanagement.livedata;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.exception.ExceptionUtils;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveData;
import org.xwiki.livedata.LiveDataEntryStore;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.livedata.displayer.ProjectManagementLiveDataDisplayer;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Provides the storing and retrieval operations for the entries coming from an
 * {@link com.xwiki.projectmanagement.ProjectManagementClient} identified by a hint present in the livedata
 * configuration.
 *
 * @version $Id$
 */
@Component
@Named("projectmanagement")
@Singleton
public class ProjectManagementEntryStore implements LiveDataEntryStore
{
    private static final List<String> LINK_PROPERTIES = List.of(WorkItem.KEY_IDENTIFIER,
        WorkItem.KEY_SUMMARY, WorkItem.KEY_CREATOR, WorkItem.KEY_PROJECT, WorkItem.KEY_REPORTER);

    private static final String FLATTEN_FORMAT = "%s.%s";

    private static final int BAD_GATEWAY = 502;

    /**
     * The statuses of the failures that are temporary (a request timeout, too many requests), which are failures of
     * the tool rather than rejections of the request.
     */
    private static final Set<Integer> TEMPORARY_FAILURES = Set.of(408, 429);

    @Inject
    private ProjectManagementManager projectManagementManager;

    @Inject
    private ProjectManagementLiveDataDisplayer defaultDisplayer;

    @Inject
    private ComponentManager componentManager;

    @Inject
    private Logger logger;

    @Inject
    private ProjectManagementClientExecutionContext clientContext;

    @Override
    public Optional<Map<String, Object>> get(Object entryId) throws LiveDataException
    {
        // The entry id alone doesn't identify the client holding the work item (it's a source parameter, which only
        // the query provides), so single entries can't be retrieved.
        return Optional.empty();
    }

    @Override
    public LiveData get(LiveDataQuery query) throws LiveDataException
    {
        String clientId = (String) query.getSource().getParameters().getOrDefault("client", "");
        if (clientId.isEmpty()) {
            throw new LiveDataException("The client property was not specified in the source parameters.");
        }

        // Set the context for the client implementation and the displayers, until the entries are ready.
        Map<String, Object> context = new HashMap<>(query.getSource().getParameters());
        context.put(ProjectManagementClientExecutionContext.LIVE_DATA, Boolean.TRUE);
        try (DefaultProjectManagementClientExecutionContext.ContextScope scope =
            DefaultProjectManagementClientExecutionContext.pushContext(clientContext, context))
        {
            return getLiveData(query, clientId);
        }
    }

    private LiveData getLiveData(LiveDataQuery query, String clientId) throws LiveDataException
    {
        LiveData ld = new LiveData();
        PaginatedResult<WorkItem> workItems = null;
        try {
            workItems =
                projectManagementManager.getWorkItems(clientId, Math.toIntExact(query.getOffset()), query.getLimit(),
                    query.getFilters(), query.getSort());
        } catch (WorkItemException e) {
            if (isAuthenticationOrRightsFailure(e)) {
                throw new LiveDataException("Failed to retrieve the work items.", e);
            }
            throw toErrorResponse(clientId, e);
        }
        ld.setCount(workItems.getTotalItems());
        // The displayers replace the values of the entries by their HTML display, so they work on copies of the work
        // items of the client, which stay unchanged.
        List<WorkItem> entries = new ArrayList<>();
        for (WorkItem workItem : workItems.getItems()) {
            WorkItem entry = new WorkItem();
            entry.putAll(workItem);
            entries.add(entry);
        }
        applyDisplayers(entries, clientId);
        for (int i = 0; i < entries.size(); i++) {
            flatten(entries.get(i), workItems.getItems().get(i));
        }
        ld.getEntries().addAll(entries);

        return ld;
    }

    private static boolean isAuthenticationOrRightsFailure(WorkItemException e)
    {
        Integer statusCode = getStatusCode(e);
        return statusCode != null && (statusCode == Response.Status.UNAUTHORIZED.getStatusCode()
            || statusCode == Response.Status.FORBIDDEN.getStatusCode());
    }

    private static Integer getStatusCode(WorkItemException e)
    {
        return e instanceof WorkItemRetrievalException ? ((WorkItemRetrievalException) e).getStatusCode() : null;
    }

    /**
     * Live Data only notifies its viewers that the entries couldn't be retrieved, whatever the response. A
     * {@link LiveDataException} would be answered by the REST layer with its stack trace, which holds the messages of
     * the tool and the requests sent to it, and logged as an error at each request of the viewers (e.g. at each change
     * of a filter). The request rejected by the tool (e.g. a filter value it refuses) and the failures of the tool are
     * answered instead with a short message and a status telling them apart, the failure being logged on the server
     * only. The callers that don't go through the REST layer (e.g. {@code $services.liveData.query}, which only
     * catches {@link LiveDataException}) get the {@link WebApplicationException} for these failures.
     *
     * @return the exception answering the request with the error response
     */
    private WebApplicationException toErrorResponse(String clientId, WorkItemException e)
    {
        logger.warn("Failed to retrieve the work items of the client [{}]: [{}].", clientId,
            ExceptionUtils.getRootCauseMessage(e));
        logger.debug("Full failure:", e);
        Integer statusCode = getStatusCode(e);
        boolean rejected = statusCode != null && statusCode >= Response.Status.BAD_REQUEST.getStatusCode()
            && statusCode < Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()
            && !TEMPORARY_FAILURES.contains(statusCode);
        Response response = rejected
            ? Response.status(Response.Status.BAD_REQUEST)
                .entity("The project management tool rejected the request for the work items.")
                .type(MediaType.TEXT_PLAIN).build()
            : Response.status(BAD_GATEWAY).entity("Failed to retrieve the work items from the project management tool.")
                .type(MediaType.TEXT_PLAIN).build();
        return new WebApplicationException(e, response);
    }

    // TODO: Use the WorkItemPropertyDisplayerManager in order to display certain properties. (linkables, props that
    //  generate html structures).
    private void applyDisplayers(List<WorkItem> workItems, String clientId)
    {
        if (!componentManager.hasComponent(ProjectManagementLiveDataDisplayer.class, clientId)) {
            defaultDisplayer.display(workItems);
            return;
        }
        try {
            ProjectManagementLiveDataDisplayer displayer =
                componentManager.getInstance(ProjectManagementLiveDataDisplayer.class, clientId);
            displayer.display(workItems);
        } catch (ComponentLookupException e) {
            logger.warn("Failed to find the project management livedata displayer with hint [{}].",
                clientId);
        }
    }

    /**
     * Expose the linkables of the work item as {@code <property>.location} (a raw URL, e.g. for the actions that open
     * the work item) and, when the displayer kept them as is, {@code <property>.value}.
     *
     * @param entry the work item as displayed
     * @param workItem the work item of the client
     */
    // TODO: Have a more generic method of flattening a work item.
    private static void flatten(WorkItem entry, WorkItem workItem)
    {
        for (String linkWorkItemProperty : LINK_PROPERTIES) {
            Object linkable = workItem.get(linkWorkItemProperty);
            if (linkable instanceof Map && ((Map<?, ?>) linkable).get(Linkable.KEY_LOCATION) != null) {
                entry.put(String.format(FLATTEN_FORMAT, linkWorkItemProperty, Linkable.KEY_LOCATION),
                    ((Map<?, ?>) linkable).get(Linkable.KEY_LOCATION));
            }
            Object displayed = entry.get(linkWorkItemProperty);
            if (displayed instanceof Map) {
                entry.put(String.format(FLATTEN_FORMAT, linkWorkItemProperty, Linkable.KEY_LOCATION),
                    ((Map<?, ?>) displayed).get(Linkable.KEY_LOCATION));
                entry.put(String.format(FLATTEN_FORMAT, linkWorkItemProperty, Linkable.KEY_VALUE),
                    ((Map<?, ?>) displayed).get(Linkable.KEY_VALUE));
                entry.remove(linkWorkItemProperty);
            }
        }
    }
}
