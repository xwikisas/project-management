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
package com.xwiki.projectmanagement.openproject.macro;

import org.xwiki.properties.annotation.PropertyDisplayHidden;

/**
 * Parameters for the OpenProject Projects macro.
 *
 * @version $Id$
 */
public class OpenProjectProjectsMacroParameters extends OpenProjectMacroParameters
{

    @PropertyDisplayHidden
    @Override
    public void setProperties(String properties)
    {
        super.setProperties(properties);
    }

    @PropertyDisplayHidden
    @Override
    public void setFilters(String filters)
    {
        super.setFilters(filters);
    }

    @PropertyDisplayHidden
    @Override
    public void setSort(String sort)
    {
        super.setSort(sort);
    }

    @PropertyDisplayHidden
    @Override
    public void setWorkItemsDisplayer(com.xwiki.projectmanagement.internal.WorkItemsDisplayer workItemsDisplayer)
    {
        super.setWorkItemsDisplayer(workItemsDisplayer);
    }

    @PropertyDisplayHidden
    @Override
    public void setLimit(Integer limit)
    {
        super.setLimit(limit);
    }

    @PropertyDisplayHidden
    @Override
    public void setOffset(Long offset)
    {
        super.setOffset(offset);
    }

    @PropertyDisplayHidden
    @Override
    public void setPageSizes(String pageSizes)
    {
        super.setPageSizes(pageSizes);
    }
}
