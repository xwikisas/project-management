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

/**
 * What {@link WorkItemSourceReader} needs to know about a macro reading its content.
 *
 * @version $Id$
 * @since 1.5.0
 */
public final class WorkItemSourceRequest
{
    private String client;

    private String source;

    private String content;

    private String filters;

    private String identifier;

    private boolean filtersReplaced;

    private WorkItemNameResolver nameResolver;

    /**
     * @param client the id of the client retrieving the work items, or {@code null}
     * @return this request
     */
    public WorkItemSourceRequest client(String client)
    {
        this.client = client;
        return this;
    }

    /**
     * @param source the {@code source} parameter of the macro, or {@code null} for the default source
     * @return this request
     */
    public WorkItemSourceRequest source(String source)
    {
        this.source = source;
        return this;
    }

    /**
     * @param content the content of the macro
     * @return this request
     */
    public WorkItemSourceRequest content(String content)
    {
        this.content = content;
        return this;
    }

    /**
     * @param filters the {@code filters} parameter of the macro, before its preset is combined with it
     * @param replaced {@code true} if the content replaces the filters parameter, which can then not be used together
     *     with the content, {@code false} if the filters read from the content are added to the filters parameter
     *     (e.g. the filters of the chart datasets)
     * @return this request
     */
    public WorkItemSourceRequest filters(String filters, boolean replaced)
    {
        this.filters = filters;
        this.filtersReplaced = replaced;
        return this;
    }

    /**
     * @param identifier the {@code identifier} parameter of the macro, which the content replaces, or {@code null}
     * @return this request
     */
    public WorkItemSourceRequest identifier(String identifier)
    {
        this.identifier = identifier;
        return this;
    }

    /**
     * @param nameResolver replaces the names written in the filters read from the content by ids, or {@code null} to
     *     keep the names as text (for example when the macro only checks the filters before resolving them)
     * @return this request
     */
    public WorkItemSourceRequest nameResolver(WorkItemNameResolver nameResolver)
    {
        this.nameResolver = nameResolver;
        return this;
    }

    String getClient()
    {
        return this.client;
    }

    String getSource()
    {
        return this.source;
    }

    String getContent()
    {
        return this.content;
    }

    String getFilters()
    {
        return this.filters;
    }

    String getIdentifier()
    {
        return this.identifier;
    }

    boolean isFiltersReplaced()
    {
        return this.filtersReplaced;
    }

    WorkItemNameResolver getNameResolver()
    {
        return this.nameResolver;
    }
}
