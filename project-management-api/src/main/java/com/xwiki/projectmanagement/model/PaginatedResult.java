package com.xwiki.projectmanagement.model;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The representation of a paginated result that contains useful information for following requests.
 *
 * @param <T> the type of the data held by this class.
 * @version $Id$
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaginatedResult<T>
{
    @JsonProperty("items")
    private List<T> items;

    @JsonProperty
    private int page;

    @JsonProperty
    private int pageSize;

    @JsonProperty
    private int totalItems;

    /**
     * @param items the items contained by this paginated result. The size of the items should be smaller than the
     *     pageSize.
     * @param page which page does this object represent.
     * @param pageSize the number of items present in this paginated result.
     * @param totalItems the total number of items that can are present in the store where this paginated result is
     *     retrieved.
     */
    public PaginatedResult(List<T> items, int page, int pageSize, int totalItems)
    {
        this.items = items;
        this.page = page;
        this.pageSize = pageSize;
        this.totalItems = totalItems;
    }

    /**
     * @return the list of items that are present in this paginated result.
     */
    public List<T> getItems()
    {
        return items;
    }

    /**
     * @param items see {@link #getItems()}.
     */
    public void setItems(List<T> items)
    {
        this.items = items;
    }

    /**
     * @return the number of the page.
     */
    public int getPage()
    {
        return page;
    }

    /**
     * @param page see {@link #getPage()}.
     */
    public void setPage(int page)
    {
        this.page = page;
    }

    /**
     * @return the maximum number of elements the {@link #getItems()} can contain.
     */
    public int getPageSize()
    {
        return pageSize;
    }

    /**
     * @param pageSize see {@link #getPageSize()}.
     */
    public void setPageSize(int pageSize)
    {
        this.pageSize = pageSize;
    }

    /**
     * @return the total number of items that can be retrieved from the store where this paginated result was retrieved
     *     from.
     */
    public int getTotalItems()
    {
        return totalItems;
    }

    /**
     * @param totalItems see {@link #getTotalItems()}.
     */
    public void setTotalItems(int totalItems)
    {
        this.totalItems = totalItems;
    }
}
