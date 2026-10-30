package com.xwiki.projectmanagement.internal;

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

import com.xwiki.projectmanagement.internal.displayers.WorkItemLivedataDisplayer;

/**
 * An enum listing the different ways a list of work items can be displayed.
 *
 * @version $Id$
 */
public enum WorkItemsDisplayer
{
    /**
     * Display the work items using a livedata.
     */
    liveData,
    /**
     * Display the work items using a livedata in the card layout.
     */
    liveDataCards,
    /**
     * Display a single work item in a page structure.
     */
    workItemsSingle,
    /**
     * Display a single work item as an inline badge.
     */
    workItemInline,
    /**
     * Display the work items as a bulleted list, one work item per line with its linked identifier and its summary.
     *
     * @since 1.3.1
     */
    workItemsList,
    /**
     * Display the linked identifiers of the work items separated by commas, usable inline in a sentence.
     *
     * @since 1.3.1
     */
    workItemsEnum;

    /**
     * @return the hint of the macro component that displays the work items this way
     * @since 1.4.0
     */
    public String getMacroHint()
    {
        if (this == liveData || this == liveDataCards) {
            return WorkItemLivedataDisplayer.ROLE_HINT;
        }
        return name();
    }
}
