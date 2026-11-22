package com.xwiki.projectmanagement.internal.rest;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rest.XWikiResource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.source.WorkItemName;
import com.xwiki.projectmanagement.source.WorkItemSource;
import com.xwiki.projectmanagement.source.WorkItemSourceContext;

/**
 * Parses the query written in the content of a work item macro into the filters edited by the filter builder of the
 * macro editor, so that a single parser reads the queries, the builder writing them back as text.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component
@Named("com.xwiki.projectmanagement.internal.rest.WorkItemQueryResource")
@Singleton
@Path("/wikis/{wikiName}/projectmanagement/query/filters")
public class WorkItemQueryResource extends XWikiResource
{
    private static final String VALUE = "value";

    /**
     * The conditions that the filter builder doesn't offer, by operator: the days relative to today (for example
     * {@code dueDate <= -3d}) and the strict comparisons of numbers (for example {@code progress > 20}).
     */
    private static final Map<String, String> UNSHOWN_OPERATORS;

    static {
        String relativeDays = "the days relative to today";
        String strictComparisons = "the strict comparisons (> and <)";
        UNSHOWN_OPERATORS = Map.of("t+", relativeDays, "t-", relativeDays, "<t+", relativeDays, ">t+", relativeDays,
            "<t-", relativeDays, ">t-", relativeDays, ">", strictComparisons, "<", strictComparisons);
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    @Named("query")
    private WorkItemSource querySource;

    /**
     * @param query the query, for example {@code project = xwiki AND status = open}
     * @return {@code {"filters": [...]}}, the Live Data filters of the query, each name being written as text with
     *     {@code "name": true} in its constraint (so that a name made of digits is written back between quotes), or a
     *     400 response {@code {"message": "..."}} telling what is wrong with the query, or that the builder can't edit
     *     it (the days relative to today, the strict comparisons); the keyword names ({@code open}, {@code closed},
     *     {@code locked}) are written as text, without the name flag, so that they are written back without quotes
     * @throws JsonProcessingException if the response couldn't be serialized
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFilters(@QueryParam("query") String query) throws JsonProcessingException
    {
        List<LiveDataQuery.Filter> filters;
        try {
            filters = this.querySource.read(query == null ? "" : query, new WorkItemSourceContext(null)).getFilters();
        } catch (WorkItemSourceException e) {
            return badRequest(e.getMessage());
        }
        for (LiveDataQuery.Filter filter : filters) {
            for (LiveDataQuery.Constraint constraint : filter.getConstraints()) {
                String unshown = UNSHOWN_OPERATORS.get(constraint.getOperator());
                if (unshown != null) {
                    return badRequest(String.format("The filters can't show %s, used on [%s].", unshown,
                        filter.getProperty()));
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (LiveDataQuery.Filter filter : filters) {
            List<Map<String, Object>> constraints = new ArrayList<>();
            for (LiveDataQuery.Constraint constraint : filter.getConstraints()) {
                Map<String, Object> constraintMap = new LinkedHashMap<>();
                constraintMap.put("operator", constraint.getOperator());
                if (constraint.getValue() instanceof WorkItemName) {
                    WorkItemName name = (WorkItemName) constraint.getValue();
                    constraintMap.put(VALUE, name.getName());
                    if (!name.isKeyword()) {
                        constraintMap.put("name", true);
                    }
                } else {
                    constraintMap.put(VALUE, constraint.getValue());
                }
                constraints.add(constraintMap);
            }
            Map<String, Object> filterMap = new LinkedHashMap<>();
            filterMap.put("property", filter.getProperty());
            filterMap.put("constraints", constraints);
            result.add(filterMap);
        }
        return Response.ok(this.objectMapper.writeValueAsString(Map.of("filters", result)),
            MediaType.APPLICATION_JSON).build();
    }

    private Response badRequest(String message) throws JsonProcessingException
    {
        return Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
            .entity(this.objectMapper.writeValueAsString(Map.of("message", message))).build();
    }
}
