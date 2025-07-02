package com.xwiki.projectmanagement.livadata.internal.displayer;

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

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;

import com.xwiki.projectmanagement.livadata.displayer.ProjectManagementLiveDataDisplayer;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Default displayer implementation that handles the more complex work item properties (i.e. list of assignees) in a
 * generic way (i.e. transforms a list of linkables into a coherent html structure).
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class DefaultProjectManagementLiveDataDisplayer implements ProjectManagementLiveDataDisplayer
{
    private static final List<String> DATE_PROPERTIES = List.of(WorkItem.KEY_CLOSE_DATE, WorkItem.KEY_CREATION_DATE,
        WorkItem.KEY_DUE_DATE, WorkItem.KEY_START_DATE, WorkItem.KEY_UPDATE_DATE);

    @Override
    public void display(Collection<WorkItem> workItems)
    {
        for (WorkItem item : workItems) {
            Object assignees = item.get(WorkItem.KEY_ASSIGNEES);
            if (assignees instanceof Collection) {
                String displayVal = ((Collection<Map<String, Object>>) assignees).stream()
                    .map(assignee -> String.format("<a href=\"%s\">%s</a>",
                        assignee.getOrDefault(Linkable.KEY_LOCATION, ""), assignee.getOrDefault(Linkable.KEY_VALUE,
                            ""))).collect(Collectors.joining("<br/>"));
                item.put(WorkItem.KEY_ASSIGNEES, displayVal);
            }

            DATE_PROPERTIES.forEach(prop -> {
                Object dateProp = item.get(prop);
                if (dateProp instanceof Date) {
                    item.put(prop, ((Date) dateProp).getTime());
                }
            });
        }
    }
}
