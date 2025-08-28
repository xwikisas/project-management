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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

/**
 * Utility class for converting data related to OpenProject entities.
 *
 * @version $Id$
 * @since 1.0
 */
public final class OpenProjectConverters
{
    private OpenProjectConverters()
    {
    }

    /**
     * Converts a WorkPackage object from the OpenProject API to an internal WorkItem object.
     *
     * @param workPackage the {@link WorkPackage} to convert
     * @return a {@link WorkItem} containing the mapped data
     */
    public static WorkItem convertWorkPackageToWorkItem(WorkPackage workPackage)
    {
        WorkItem workItem = new WorkItem();

        setBasicInfo(workItem, workPackage);
        setDates(workItem, workPackage);
        setLinks(workItem, workPackage);
        setAssignments(workItem, workPackage);
        setProjectAndPriority(workItem, workPackage);

        return workItem;
    }

    private static void setBasicInfo(WorkItem workItem, WorkPackage workPackage)
    {
        if (workPackage.getDescription() != null) {
            workItem.setDescription(workPackage.getDescription());
        }
        if (workPackage.getPercentageDone() != null) {
            workItem.setProgress(workPackage.getPercentageDone());
        }
        if (workPackage.getTypeOfWorkPackage() != null) {
            workItem.setType(workPackage.getTypeOfWorkPackage().getValue());
        }
        if (workPackage.getStatus() != null) {
            workItem.setStatus(workPackage.getStatus().getValue());
        }
    }

    private static void setDates(WorkItem workItem, WorkPackage workPackage)
    {
        if (workPackage.getStartDate() != null) {
            workItem.setStartDate(workPackage.getStartDate());
        }
        if (workPackage.getDueDate() != null) {
            workItem.setDueDate(workPackage.getDueDate());
        }
        if (workPackage.getCreatedAt() != null) {
            workItem.setCreationDate(workPackage.getCreatedAt());
        }
        if (workPackage.getUpdatedAt() != null) {
            workItem.setUpdateDate(workPackage.getUpdatedAt());
        }
    }

    private static void setLinks(WorkItem workItem, WorkPackage workPackage)
    {
        if (workPackage.getSelf() != null) {
            workItem.setIdentifier(
                new Linkable(String.valueOf(workPackage.getId()), workPackage.getSelf().getLocation()));
            workItem.setSummary(new Linkable(workPackage.getSubject(), workPackage.getSelf().getLocation()));
        }
    }

    private static void setAssignments(WorkItem workItem, WorkPackage workPackage)
    {
        if (workPackage.getAuthor() != null) {
            workItem.setCreator(workPackage.getAuthor());
        }
        if (workPackage.getAssignee() != null) {
            workItem.setAssignees(List.of(workPackage.getAssignee()));
        }
    }

    private static void setProjectAndPriority(WorkItem workItem, WorkPackage workPackage)
    {
        if (workPackage.getProject() != null) {
            workItem.setProject(workPackage.getProject());
        }
        if (workPackage.getPriority() != null) {
            workItem.setPriority(workPackage.getPriority().getValue());
        }
    }

    /**
     * Converts a {@link PaginatedResult} of one type into a {@link PaginatedResult} of another type using the provided
     * mapping function.
     *
     * @param input the original {@link PaginatedResult} to convert
     * @param mapper a {@link Function} that maps each input item to an output item
     * @param <T> the type of input items
     * @param <R> the type of result items
     * @return a {@link PaginatedResult} containing the converted items with the same pagination metadata
     */
    public static <T, R> PaginatedResult<R> convertPaginatedResult(
        PaginatedResult<T> input,
        Function<T, R> mapper)
    {
        PaginatedResult<R> result = new PaginatedResult<>();
        List<R> mappedItems = new ArrayList<>();
        for (T item : input.getItems()) {
            mappedItems.add(mapper.apply(item));
        }
        result.setItems(mappedItems);
        result.setPage(input.getPage());
        result.setPageSize(input.getPageSize());
        result.setTotalItems(input.getTotalItems());
        return result;
    }
}
