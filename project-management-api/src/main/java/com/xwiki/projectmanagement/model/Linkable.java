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

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Describes a property that be accessed through a URL.
 *
 * @param <T> the type of the value - typically a string.
 * @version $Id$
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Linkable<T>
{
    private T value;

    private String location;

    /**
     * Default constructor.
     */
    public Linkable()
    {

    }

    /**
     * @param value the value this object wraps.
     * @param location the location where the resource represented by this object can be accessed.
     */
    public Linkable(T value, String location)
    {
        this.value = value;
        this.location = location;
    }

    /**
     * @return the location where this object can be accessed. i.e. the url to a user profile.
     */
    public String getLocation()
    {
        return location;
    }

    /**
     * @param location see {@link #getLocation()}.
     */
    public void setLocation(String location)
    {
        this.location = location;
    }

    /**
     * @return the value of this property. i.e. a username.
     */
    public T getValue()
    {
        return value;
    }

    /**
     * @param value see {@link #getValue()}.
     */
    public void setValue(T value)
    {
        this.value = value;
    }
}
