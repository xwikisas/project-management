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

        workItem.setDescription(workPackage.getDescription());
        workItem.setProgress(workPackage.getPercentageDone());
        workItem.setStartDate(workPackage.getStartDate());
        workItem.setDueDate(workPackage.getDueDate());
        workItem.setCreationDate(workPackage.getCreatedAt());
        workItem.setUpdateDate(workPackage.getUpdatedAt());
        workItem.setType(workPackage.getTypeOfWorkPackage().getValue());
        workItem.setIdentifier(new Linkable(String.valueOf(workPackage.getId()), workPackage.getSelf().getLocation()));
        workItem.setSummary(new Linkable(workPackage.getSubject(), workPackage.getSelf().getLocation()));
        workItem.setStatus(workPackage.getStatus().getValue());
        workItem.setCreator(workPackage.getAuthor());
        workItem.setAssignees(List.of(workPackage.getAssignee()));
        workItem.setProject(workPackage.getProject());
        workItem.setPriority(workPackage.getPriority().getValue());

        return workItem;
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
