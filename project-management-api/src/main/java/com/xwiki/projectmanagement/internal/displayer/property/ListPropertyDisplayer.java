package com.xwiki.projectmanagement.internal.displayer.property;

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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;

import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayer;
import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager;

/**
 * Iterates over the entries of the list property and calls their display method.
 *
 * @version $Id$
 */
public class ListPropertyDisplayer implements WorkItemPropertyDisplayer
{
    private final WorkItemPropertyDisplayerManager displayerManager;

    /**
     * @param displayerManager the displayer manager that should be used for the list values.
     */
    public ListPropertyDisplayer(WorkItemPropertyDisplayerManager displayerManager)
    {
        this.displayerManager = displayerManager;
    }

    @Override
    public List<Block> display(Object property, Map<String, Object> params)
    {
        if (property == null) {
            return Collections.emptyList();
        }
        List<?> listProperty = (List<?>) property;

        if (listProperty.isEmpty()) {
            return Collections.emptyList();
        }
        List<Block> blocks = new ArrayList<>();
        for (Object o : (List<?>) property) {
            blocks.add(new GroupBlock(displayerManager.displayProperty(o.getClass().getName(), o, params)));
        }
        return blocks;
    }
}
