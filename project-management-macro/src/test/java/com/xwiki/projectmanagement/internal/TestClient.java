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
package com.xwiki.projectmanagement.internal;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Read-only test client serving the work items of {@code testclient/workitems.json}. It supports only what the
 * rendering tests need: filters (any operator is treated as "equals"), sort entries (on the string value of the
 * property) and pagination.
 *
 * @version $Id$
 */
@Component
@Named("test")
@Singleton
public class TestClient implements ProjectManagementClient, Initializable
{
    private static final String FIXTURE = "/testclient/workitems.json";

    private static final Set<String> DATE_PROPERTIES = Set.of(WorkItem.KEY_CLOSE_DATE,
        WorkItem.KEY_CREATION_DATE, WorkItem.KEY_DUE_DATE, WorkItem.KEY_START_DATE, WorkItem.KEY_UPDATE_DATE);

    private static final DateTimeFormatter JSON_DATE_FORMAT = new DateTimeFormatterBuilder()
        .appendPattern("dd/MM/yyyy")
        .optionalStart()
        .appendPattern(" HH:mm:ss")
        .optionalEnd()
        .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
        .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
        .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
        .toFormatter();

    private List<WorkItem> workItems;

    @Override
    public void initialize() throws InitializationException
    {
        try (InputStream inputStream = getClass().getResourceAsStream(FIXTURE)) {
            if (inputStream == null) {
                throw new InitializationException(String.format("Missing test fixture [%s].", FIXTURE));
            }
            this.workItems = new ObjectMapper().readValue(inputStream, new TypeReference<List<WorkItem>>()
            {
            });
        } catch (IOException e) {
            throw new InitializationException(String.format("Failed to read the test fixture [%s].", FIXTURE), e);
        }
        // Jackson reads the nested objects as plain maps and the dates as strings.
        for (WorkItem workItem : this.workItems) {
            workItem.replaceAll(TestClient::toModelValue);
        }
    }

    @Override
    public WorkItem getWorkItem(String workItemId) throws WorkItemNotFoundException
    {
        return this.workItems.stream()
            .filter(workItem -> workItemId.equals(workItem.getLinkableValue(WorkItem.KEY_IDENTIFIER)))
            .findFirst()
            .orElseThrow(() -> new WorkItemNotFoundException(String.format("No work item [%s].", workItemId)));
    }

    @Override
    public PaginatedResult<WorkItem> getWorkItems(int offset, int pageSize, List<LiveDataQuery.Filter> filters,
        List<LiveDataQuery.SortEntry> sortEntries)
    {
        List<WorkItem> matching = this.workItems.stream()
            .filter(workItem -> filters.stream().allMatch(filter -> matches(workItem, filter)))
            .sorted(getComparator(sortEntries))
            .collect(Collectors.toList());

        PaginatedResult<WorkItem> result = new PaginatedResult<>();
        result.setPage(pageSize > 0 ? offset / pageSize + 1 : 1);
        result.setPageSize(pageSize);
        result.setTotalItems(matching.size());
        if (offset < matching.size()) {
            result.getItems().addAll(matching.subList(offset, Math.min(matching.size(), offset + pageSize)));
        }
        return result;
    }

    @Override
    public WorkItem createWorkItem(WorkItem workItem)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public WorkItem updateWorkItem(WorkItem workItem)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean deleteWorkItem(String workItemId)
    {
        throw new UnsupportedOperationException();
    }

    private static boolean matches(WorkItem workItem, LiveDataQuery.Filter filter)
    {
        String value = getStringValue(workItem, filter.getProperty());
        return filter.getConstraints().stream().anyMatch(constraint -> constraint.getValue().equals(value));
    }

    private static Comparator<WorkItem> getComparator(List<LiveDataQuery.SortEntry> sortEntries)
    {
        // Keep the order of the fixture when there's nothing to sort on.
        Comparator<WorkItem> comparator = (left, right) -> 0;
        for (LiveDataQuery.SortEntry sortEntry : sortEntries) {
            Comparator<String> valueComparator = sortEntry.isDescending()
                ? Comparator.<String>reverseOrder() : Comparator.<String>naturalOrder();
            comparator = comparator.thenComparing(workItem -> getStringValue(workItem, sortEntry.getProperty()),
                Comparator.nullsLast(valueComparator));
        }
        return comparator;
    }

    private static String getStringValue(WorkItem workItem, String property)
    {
        Object value = workItem.get(property);
        if (value instanceof Linkable) {
            return ((Linkable) value).getValue();
        }
        return value == null ? null : value.toString();
    }

    private static Object toModelValue(String property, Object value)
    {
        if (DATE_PROPERTIES.contains(property) && value instanceof String) {
            return Date.from(LocalDateTime.parse((String) value, JSON_DATE_FORMAT).atZone(ZoneId.systemDefault())
                .toInstant());
        } else if (value instanceof Map) {
            return toLinkable((Map<?, ?>) value);
        } else if (value instanceof List && !((List<?>) value).isEmpty() && ((List<?>) value).get(0) instanceof Map) {
            return ((List<?>) value).stream().map(item -> toLinkable((Map<?, ?>) item)).collect(Collectors.toList());
        }
        return value;
    }

    private static Linkable toLinkable(Map<?, ?> value)
    {
        return new Linkable((String) value.get(Linkable.KEY_VALUE), (String) value.get(Linkable.KEY_LOCATION));
    }
}
