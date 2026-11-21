package com.xwiki.projectmanagement.internal.source;

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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;

import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.source.WorkItemSelection;
import com.xwiki.projectmanagement.source.WorkItemSource;
import com.xwiki.projectmanagement.source.WorkItemSourceContext;

/**
 * The {@code list} source: the content lists the ids or keys of the work items to display, separated by commas,
 * spaces or new lines (e.g. {@code 1911, PROJ-123}). The work items are displayed in this order.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component
@Named("list")
@Singleton
public class ListWorkItemSource implements WorkItemSource
{
    @Override
    public WorkItemSelection read(String content, WorkItemSourceContext context) throws WorkItemSourceException
    {
        List<String> ids = Arrays.stream(content.split("[\\s,]+")).filter(id -> !id.isEmpty())
            .collect(Collectors.toList());
        if (ids.isEmpty()) {
            throw new WorkItemSourceException("The macro content lists no work item.");
        }
        return WorkItemSelection.ofIds(ids);
    }
}
