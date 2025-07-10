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
 * Defines the rendering blocks that should be used in order to display a Work Item property.
 *
 * @version $Id$
 */
@Role
public interface WorkItemPropertyDisplayer
{
    /**
     * Generates a list of blocks that can be used to render the property in different contexts.
     *
     * @param property the property value that will be displayed.
     * @param params any additional params that the displayer might need (i.e. translation prefix).
     * @return a list of blocks that define how the property should be displayed.
     */
    List<Block> display(Object property, Map<String, String> params);
}
