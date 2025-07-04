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
import org.xwiki.xml.XMLUtils;

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
    @Override
    public void display(Collection<WorkItem> workItems)
    {
        for (WorkItem item : workItems) {
            for (Map.Entry<String, Object> itemProperty : item.entrySet()) {
                if (itemProperty.getValue() instanceof List) {
                    displayListProperty(itemProperty);
                } else if (itemProperty.getValue() instanceof Date) {
                    displayDateProperty(itemProperty);
                }
            }
        }
    }

    private void displayDateProperty(Map.Entry<String, Object> itemProperty)
    {
        itemProperty.setValue(((Date) itemProperty.getValue()).getTime());
    }

    private void displayListProperty(Map.Entry<String, Object> itemProperty)
    {
        if (((List<?>) itemProperty.getValue()).isEmpty()) {
            return;
        }
        if (((List<?>) itemProperty.getValue()).get(0) instanceof Linkable) {
            String listPropValue = ((List<Linkable>) itemProperty.getValue())
                .stream()
                .map(linkable -> String.format(
                        "<a href=\"%s\">%s</a>",
                        XMLUtils.escape((String) linkable.getOrDefault(Linkable.KEY_LOCATION, "")),
                        XMLUtils.escape((String) linkable.getOrDefault(Linkable.KEY_VALUE, ""))
                    )
                )
                .collect(Collectors.joining("<br/>"));
            itemProperty.setValue(listPropValue);
        } else {
            String listPropValue = ((List<?>) itemProperty.getValue())
                .stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
            itemProperty.setValue(listPropValue);
        }
    }
}
