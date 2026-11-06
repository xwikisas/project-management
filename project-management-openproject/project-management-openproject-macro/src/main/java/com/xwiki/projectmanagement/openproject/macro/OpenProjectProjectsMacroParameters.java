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
import org.xwiki.properties.annotation.PropertyDisplayType;

import com.xwiki.projectmanagement.openproject.OpenProjectInstance;
import com.xwiki.projectmanagement.openproject.OpenProjectInstanceHolder;

/**
 * Parameters for the OpenProject Projects macro.
 *
 * @version $Id$
 */
public class OpenProjectProjectsMacroParameters implements OpenProjectInstanceHolder
{
    private static final int DEFAULT_LIMIT = 5;

    private String instance;

    private Integer limit;

    private Integer count;

    @Override
    public String getInstance()
    {
        return instance;
    }

    @Override
    @PropertyDisplayType(OpenProjectInstance.class)
    public void setInstance(String instance)
    {
        this.instance = instance;
    }

    /**
     * @return the maximum number of projects to display: the {@code limit} parameter if set, otherwise the
     *     deprecated {@code count} parameter if set, otherwise 5
     * @since 1.4.0
     */
    public int getLimit()
    {
        if (this.limit != null) {
            return this.limit;
        }
        return this.count != null ? this.count : DEFAULT_LIMIT;
    }

    /**
     * @param limit see {@link #getLimit()}
     * @since 1.4.0
     */
    public void setLimit(int limit)
    {
        this.limit = limit;
    }

    /**
     * @return see {@link #getLimit()}
     * @deprecated since 1.4.0, use {@link #getLimit()} instead
     */
    @Deprecated(since = "1.4.0")
    public int getCount()
    {
        return getLimit();
    }

    /**
     * @param count the maximum number of projects to display; the {@code limit} parameter takes precedence over
     *     it when both are set
     * @deprecated since 1.4.0, use {@link #setLimit(int)} instead
     */
    @Deprecated(since = "1.4.0")
    @PropertyDisplayHidden
    public void setCount(int count)
    {
        this.count = count;
    }
}
