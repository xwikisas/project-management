package com.xwiki.projectmanagement.livadata.displayer;

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

import org.xwiki.component.annotation.Role;

import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Iterates over a work item collection and prepares them for display.
 *
 * @version $Id$
 * @since 1.0
 */
@Role
public interface ProjectManagementLiveDataDisplayer
{
    /**
     * Prepares the work items for displaying. It can either add new properties to each work item or change the values
     * of the properties completely (i.e. iterate the assignees and generate a html structure ready for display).
     *
     * @param workItems a collection of work items.
     */
    void display(Collection<WorkItem> workItems);
}
