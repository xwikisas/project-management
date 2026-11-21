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

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;

import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.source.WorkItemSelection;
import com.xwiki.projectmanagement.source.WorkItemSource;
import com.xwiki.projectmanagement.source.WorkItemSourceContext;

/**
 * The {@code query} source, used by default: the content is a textual query on the work item properties, for example
 * {@code project = xwiki AND targetVersion = "17.10" AND status = closed} (see {@link WorkItemQueryParser} for the
 * syntax). The values written as names are left for the macro to resolve.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component
@Named(WorkItemSourceReader.DEFAULT_SOURCE)
@Singleton
public class QueryWorkItemSource implements WorkItemSource
{
    @Override
    public WorkItemSelection read(String content, WorkItemSourceContext context) throws WorkItemSourceException
    {
        return WorkItemSelection.ofFilters(WorkItemQueryParser.parse(content));
    }
}
