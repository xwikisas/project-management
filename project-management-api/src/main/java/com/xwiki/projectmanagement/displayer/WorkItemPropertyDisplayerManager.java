package com.xwiki.projectmanagement.displayer;

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

import java.util.List;
import java.util.Map;

import org.xwiki.component.annotation.Role;
import org.xwiki.rendering.block.Block;

/**
 * Offers a centralized way of accessing a subset/all the existing {@link WorkItemPropertyDisplayer}.
 *
 * @version $Id$
 */
@Role
public interface WorkItemPropertyDisplayerManager
{
    /**
     * Finds the {@link WorkItemPropertyDisplayer} associated with the given property and passes the parameters to it.
     *
     * @param propertyName the name of the property that needs displaying.
     * @param propertyValue the value of the said property.
     * @param parameters any additional parameters that might be needed by the {@link WorkItemPropertyDisplayer}
     *     implementation.
     * @return a list of blocks that represent how the property should be displayed.
     */
    List<Block> displayProperty(String propertyName, Object propertyValue, Map<String, String> parameters);

    /**
     * Provides a way to check if the manager has any displayer registered for a given property.
     *
     * @param property the name of the property that might or might not have a displayer associated.
     * @return the {@link WorkItemPropertyDisplayer} associated with the property or null.
     */
    WorkItemPropertyDisplayer getDisplayerForProperty(String property);
}
