package com.xwiki.projectmanagement.internal.source;

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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The work items selected by a macro once its content is read by its source, expressed with the existing macro
 * parameters, so that the rest of the macro execution is unchanged: the Live Data filters, the sorting and the
 * identifier (the listed work items or a link understood by the client), plus the other values for the client.
 *
 * @version $Id$
 * @since 1.5.0
 */
public final class WorkItemSourceResult
{
    private final String filters;

    private final String identifier;

    private final Map<String, String> clientScope;

    private final String sort;

    /**
     * @param filters the filters, as a serialized Live Data configuration (or a list of configurations, one per chart
     *     dataset), or {@code null}
     * @param identifier the identifier of the work items, i.e. ids or keys separated by commas, or a link understood
     *     by the client, or {@code null}
     * @param clientScope the other values passed to the client in its execution context
     */
    public WorkItemSourceResult(String filters, String identifier, Map<String, String> clientScope)
    {
        this(filters, identifier, clientScope, null);
    }

    /**
     * @param filters the filters, as a serialized Live Data configuration (or a list of configurations, one per chart
     *     dataset), or {@code null}
     * @param identifier the identifier of the work items, i.e. ids or keys separated by commas, or a link understood
     *     by the client, or {@code null}
     * @param clientScope the other values passed to the client in its execution context
     * @param sort the sorting of the work items, in the format of the {@code sort} parameter (e.g.
     *     {@code status:asc,identifier:desc}), or {@code null} to keep the {@code sort} parameter of the macro
     */
    public WorkItemSourceResult(String filters, String identifier, Map<String, String> clientScope, String sort)
    {
        this.filters = filters;
        this.identifier = identifier;
        this.clientScope = Collections.unmodifiableMap(new LinkedHashMap<>(clientScope));
        this.sort = sort;
    }

    /**
     * @return the filters, as a serialized Live Data configuration (or a list of configurations, one per chart
     *     dataset), or {@code null}
     */
    public String getFilters()
    {
        return this.filters;
    }

    /**
     * @return the identifier of the work items, i.e. ids or keys separated by commas, or a link understood by the
     *     client, or {@code null}
     */
    public String getIdentifier()
    {
        return this.identifier;
    }

    /**
     * @return the sorting of the work items, in the format of the {@code sort} parameter (e.g.
     *     {@code status:asc,identifier:desc}), or {@code null} to keep the {@code sort} parameter of the macro
     */
    public String getSort()
    {
        return this.sort;
    }

    /**
     * @return the other values passed to the client in its execution context
     */
    public Map<String, String> getClientScope()
    {
        return this.clientScope;
    }

    /**
     * @return the values to add to the execution context of the client: the identifier, if any, and the client scope
     */
    public Map<String, Object> getClientContext()
    {
        Map<String, Object> context = new LinkedHashMap<>(this.clientScope);
        if (this.identifier != null) {
            context.put(WorkItemSourceReader.IDENTIFIER, this.identifier);
        }
        return context;
    }
}
