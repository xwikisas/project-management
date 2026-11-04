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
package com.xwiki.projectmanagement.internal.displayer.property;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.rendering.block.Block;

import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayer;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Displays the identifier of a work item as a link labelled with the key of the work item (e.g. {@code XWIKI-123} for
 * an OpenProject work package on an instance using project-based semantic identifiers) when it has one, and with the
 * identifier otherwise.
 *
 * @version $Id$
 * @since 1.4.0
 */
public class IdentifierPropertyDisplayer implements WorkItemPropertyDisplayer
{
    /**
     * The display parameter holding the displayed work item.
     */
    public static final String PARAMETER_WORK_ITEM = "workItem";

    private final WorkItemPropertyDisplayer linkableDisplayer;

    /**
     * @param linkableDisplayer the displayer of the linkable values, used to display the identifier
     */
    public IdentifierPropertyDisplayer(WorkItemPropertyDisplayer linkableDisplayer)
    {
        this.linkableDisplayer = linkableDisplayer;
    }

    @Override
    public List<Block> display(Object property, Map<String, Object> params)
    {
        Object workItem = params != null ? params.get(PARAMETER_WORK_ITEM) : null;
        if (workItem instanceof WorkItem && property instanceof Map) {
            String key = ((WorkItem) workItem).getKey();
            if (StringUtils.isNotEmpty(key)) {
                Object location = ((Map<?, ?>) property).get(Linkable.KEY_LOCATION);
                return this.linkableDisplayer.display(
                    new Linkable(key, location != null ? location.toString() : null), params);
            }
        }
        return this.linkableDisplayer.display(property, params);
    }
}
