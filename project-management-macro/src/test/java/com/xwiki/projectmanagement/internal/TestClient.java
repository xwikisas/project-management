package com.xwiki.projectmanagement.internal;

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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.exception.WorkItemCreationException;
import com.xwiki.projectmanagement.exception.WorkItemDeletionException;
import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.exception.WorkItemUpdatingException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Test client retrieving entries from local files and other sources.
 *
 * @version $Id$
 */
@Component
@Named("test")
@Singleton
public class TestClient implements ProjectManagementClient, Initializable
{
    private static final Set<String> KNOWN_DATE_TYPES = Set.of(WorkItem.KEY_CLOSE_DATE,
        WorkItem.KEY_CREATION_DATE, WorkItem.KEY_DUE_DATE, WorkItem.KEY_START_DATE, WorkItem.KEY_UPDATE_DATE);

    private static final DateTimeFormatter EXPECTED_JSON_DATE_FORMAT = new DateTimeFormatterBuilder()
        .appendPattern("dd/MM/yyyy")
        .optionalStart()
        .appendPattern(" HH:mm:ss")
        .optionalEnd()
        .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
        .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
        .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
        .toFormatter();

    private final List<WorkItem> workItems = new ArrayList<>();

    @Override
    public void initialize() throws InitializationException
    {
        ObjectMapper objectMapper = new ObjectMapper();
        InputStream inputStream = getClass().getResourceAsStream("/testclient/workitems.json");
        if (inputStream == null) {
            return;
        }
        try {
            String itemsJson = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

            List<WorkItem> jsonNode = objectMapper.readValue(itemsJson, new TypeReference<List<WorkItem>>()
            {
            });

            for (WorkItem workItem : jsonNode) {
                for (Map.Entry<String, Object> prop : workItem.entrySet()) {
                    if (KNOWN_DATE_TYPES.contains(prop.getKey())) {
                        handleDateVal(prop);
                    } else if (prop.getValue() instanceof Map && (
                        ((Map<?, ?>) prop.getValue()).containsKey(Linkable.KEY_LOCATION)
                            || ((Map<?, ?>) prop.getValue()).containsKey(Linkable.KEY_VALUE)))
                    {
                        handleLinkableVal(prop);
                    } else if (prop.getValue() instanceof List && !((List<?>) prop.getValue()).isEmpty()) {
                        handleListVal(prop);
                    }
                }
            }
            workItems.addAll(jsonNode);
        } catch (IOException e) {
        }
    }

    @Override
    public WorkItem getWorkItem(String workItemId) throws WorkItemNotFoundException
    {
        WorkItem item = getWorkItemMatching(workItemId);
        if (item == null) {
            throw new WorkItemNotFoundException("Not found");
        }
        return item;
    }

    @Override
    public PaginatedResult<WorkItem> getWorkItems(int page, int pageSize, List<LiveDataQuery.Filter> filters,
        List<LiveDataQuery.SortEntry> sortEntries)
        throws WorkItemRetrievalException
    {
        PaginatedResult<WorkItem> result = new PaginatedResult<>();
        result.setPage(page);
        result.setPageSize(pageSize);

        List<WorkItem> filteredWorkItem = workItems.stream().filter(wi -> {
            boolean matchesFilters = true;
            for (LiveDataQuery.Filter filter : filters) {
                Object propVal = wi.get(filter.getProperty());
                Object valToMatch;
                if (propVal instanceof Linkable) {
                    valToMatch = ((Linkable) propVal).getValue();
                } else {
                    valToMatch = propVal;
                }
                // We currently assume that all operators are "equals".
                Boolean matchesConstraints =
                    filter.getConstraints().stream().map(constraint -> constraint.getValue().equals(valToMatch))
                        .reduce(false, (a, b) -> a || b);
                matchesFilters &= matchesConstraints;
            }
            return matchesFilters;
        }).collect(Collectors.toList());
        result.setTotalItems(workItems.size());
        int index = page * pageSize;
        if (index > filteredWorkItem.size()) {
            return result;
        }
        result.getItems().addAll(filteredWorkItem.subList(index, Math.min(filteredWorkItem.size(), index + pageSize)));
        return result;
    }

    @Override
    public WorkItem createWorkItem(WorkItem workItem) throws WorkItemCreationException
    {
        WorkItem dbWorkItem = getWorkItemMatching(workItem.getIdentifier().getValue());

        if (dbWorkItem != null) {
            throw new WorkItemCreationException("Item already exists.");
        }
        workItems.add(workItem);
        return workItem;
    }

    @Override
    public WorkItem updateWorkItem(WorkItem workItem) throws WorkItemUpdatingException
    {

        WorkItem existingWorkItem = getWorkItemMatching(workItem.getIdentifier().getValue());

        if (existingWorkItem == null) {
            throw new WorkItemUpdatingException("Not found");
        }

        int index = workItems.indexOf(existingWorkItem);
        workItems.remove(index);
        workItems.add(index, workItem);
        return workItem;
    }

    @Override
    public boolean deleteWorkItem(String workItemId) throws WorkItemDeletionException
    {
        WorkItem existingWorkItem = getWorkItemMatching(workItemId);
        if (existingWorkItem == null) {
            throw new WorkItemDeletionException("Not found");
        }
        workItems.remove(existingWorkItem);
        return true;
    }

    private static void handleLinkableVal(Map.Entry<String, Object> prop)
    {
        String val = (String) ((Map<?, ?>) prop.getValue()).get(Linkable.KEY_VALUE);
        String location = (String) ((Map<?, ?>) prop.getValue()).get(Linkable.KEY_LOCATION);
        prop.setValue(new Linkable(val, location));
    }

    private static void handleListVal(Map.Entry<String, Object> prop)
    {
        Object listVal = ((List<?>) prop.getValue()).get(0);
        if (listVal instanceof Map && (((Map<?, ?>) listVal).containsKey(Linkable.KEY_LOCATION)
            || ((Map<?, ?>) listVal).containsKey(Linkable.KEY_VALUE)))
        {
            List<Linkable> linkables = ((List<?>) prop.getValue())
                .stream()
                .map(i -> new Linkable(
                    (String) ((Map) i).get(Linkable.KEY_VALUE),
                    (String) ((Map) i).get(Linkable.KEY_VALUE)))
                .collect(
                    Collectors.toList());
            prop.setValue(linkables);
        }
    }

    private static void handleDateVal(Map.Entry<String, Object> prop)
    {
        Object dateVal = prop.getValue();
        if (dateVal instanceof Long) {
            prop.setValue(new Date((Long) dateVal));
        } else if (dateVal instanceof Integer) {
            prop.setValue(new Date((Integer) dateVal));
        } else if (dateVal instanceof String) {
            try {
                LocalDateTime localDateTime =
                    LocalDateTime.parse(dateVal.toString(), EXPECTED_JSON_DATE_FORMAT);
                Date date = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
                prop.setValue(date);
            } catch (DateTimeParseException e) {
                System.out.println(e);
            }
        }
    }

    private WorkItem getWorkItemMatching(String id)
    {
        return workItems.stream()
            .filter(w -> w.getIdentifier().getValue().equals(id))
            .findFirst().orElse(null);
    }
}