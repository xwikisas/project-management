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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rendering.macro.MacroExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceReader;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceResult;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectLink;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectLinkQuery;

/**
 * Moves the filters and the sorting of the link of an OpenProject work package list, given to an OpenProject macro
 * (by its content or by its deprecated {@code identifier} parameter), to the filters and the sorting of the macro, so
 * that its Live Data shows them and its viewers can change them. The link keeps only the filters that the Live Data
 * can't show (see {@link OpenProjectLinkQuery}), and the filters on the properties that the macro already filters,
 * the Live Data showing a single filter by property: each filter is then applied once, and removing a filter from the
 * Live Data removes it from the request. This is done once, when the macro is rendered, instead of for each request of
 * the Live Data.
 * <p>
 * The links of a single work package and of a saved query, and the lists of work packages, are left as they are: a
 * list (or a single work package) has no filter to show, and the filters of a saved query are stored on OpenProject,
 * which {@link OpenProjectSavedQueryResolver} reads with the account of the current user, to move them the same way.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component(roles = OpenProjectLinkQueryResolver.class)
@Singleton
public class OpenProjectLinkQueryResolver
{
    private static final String SORT_SEPARATOR = ":";

    private static final String DESCENDING = "desc";

    @Inject
    private OpenProjectLiveDataColumnsResolver columnsResolver;

    @Inject
    private WorkItemSourceReader sourceReader;

    @Inject
    private Logger logger;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param instance the OpenProject instance of the macro, e.g. {@code main}
     * @param sort the {@code sort} parameter of the macro, e.g. {@code status:asc}, or {@code null}
     * @param source the work packages selected by the macro
     * @return the work packages selected by the macro, where the filters of the link of a work package list are added
     *     to the other filters of the macro, its sorting comes after the {@code sort} parameter of the macro (which
     *     wins for the properties sorted by both), and the link only keeps the other filters; the given work packages
     *     when the identifier isn't the link of a work package list with filters or sorting, or when the filters of the
     *     link or the columns of the Live Data can't be read (the client then applies the filters of the link)
     * @throws MacroExecutionException if the filters of the macro can't be parsed
     */
    public WorkItemSourceResult resolve(String instance, String sort, WorkItemSourceResult source)
        throws MacroExecutionException
    {
        OpenProjectLink link = OpenProjectLink.parse(source.getIdentifier());
        if (link == null) {
            return source;
        }
        OpenProjectLinkQuery query;
        try {
            query = OpenProjectLinkQuery.read(link, () -> getColumns(instance, source.getFilters()));
        } catch (IllegalArgumentException e) {
            // The client reports the link when it requests the work packages.
            this.logger.debug("Failed to read the filters of the link [{}]: [{}].", link,
                ExceptionUtils.getRootCauseMessage(e));
            return source;
        } catch (RuntimeException e) {
            // The columns of the Live Data can't be retrieved: the client applies the filters of the link.
            this.logger.warn("Failed to show the filters of the link [{}] in the Live Data. Root cause: [{}].", link,
                ExceptionUtils.getRootCauseMessage(e));
            return source;
        }
        if (query == null) {
            return source;
        }
        String filters = this.sourceReader.mergeFilters(source.getFilters(), query.getFilters());
        String mergedSort = query.getSortEntries().isEmpty() ? null : mergeSort(sort, query.getSortEntries());
        return new WorkItemSourceResult(filters, query.getLink(), source.getClientScope(), mergedSort);
    }

    /**
     * @return the columns of the Live Data of the macro; a failure to retrieve them is reported as an
     *     {@link IllegalStateException}, so that it isn't mistaken for an invalid link
     */
    private OpenProjectLinkQuery.Columns getColumns(String instance, String filters)
    {
        try {
            // The Live Data shows a single filter by property: the link keeps its filters on the filtered properties.
            return this.columnsResolver.resolve(instance, getFilteredProperties(filters));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to retrieve the columns of the Live Data.", e);
        }
    }

    /**
     * @param filters the filters of the macro, as a serialized Live Data configuration, or {@code null}
     * @return the properties filtered by the filters
     */
    private Set<String> getFilteredProperties(String filters)
    {
        Set<String> properties = new HashSet<>();
        if (StringUtils.isBlank(filters)) {
            return properties;
        }
        try {
            JsonNode root = this.objectMapper.readTree(filters);
            for (JsonNode configuration : root.isArray() ? root : List.of(root)) {
                for (JsonNode filter : configuration.path("query").path("filters")) {
                    properties.add(filter.path("property").asText());
                }
            }
        } catch (JsonProcessingException e) {
            // The macro reports the invalid filters.
            this.logger.debug("Failed to read the filters [{}]: [{}].", filters, e.getMessage());
        }
        return properties;
    }

    /**
     * @return the sort parameter followed by the sort entries of the link on the properties that it doesn't sort,
     *     e.g. {@code status:asc,identifier:desc}
     */
    private static String mergeSort(String sort, List<LiveDataQuery.SortEntry> linkSortEntries)
    {
        Map<String, String> entries = new LinkedHashMap<>();
        for (String entry : StringUtils.split(StringUtils.defaultString(sort), ',')) {
            String property = StringUtils.trim(StringUtils.substringBefore(entry, SORT_SEPARATOR));
            if (!property.isEmpty()) {
                entries.putIfAbsent(property, StringUtils.trim(entry));
            }
        }
        for (LiveDataQuery.SortEntry sortEntry : linkSortEntries) {
            entries.putIfAbsent(sortEntry.getProperty(),
                sortEntry.getProperty() + SORT_SEPARATOR + (sortEntry.isDescending() ? DESCENDING : "asc"));
        }
        return String.join(",", entries.values());
    }
}
