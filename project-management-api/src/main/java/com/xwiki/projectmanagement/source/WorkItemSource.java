package com.xwiki.projectmanagement.source;

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

import org.xwiki.component.annotation.Role;
import org.xwiki.stability.Unstable;

import com.xwiki.projectmanagement.exception.WorkItemSourceException;

/**
 * Reads the content of a work item macro, written in a given format, into the selection of the work items to display.
 * The {@code source} parameter of the macros chooses the format, i.e. the hint of the component, which is looked up as
 * {@code <client>/<source>} first (e.g. {@code openproject/url}), then as {@code <source>} (e.g. {@code list}): a
 * client can then add its own formats or replace the generic ones.
 * <p>
 * A source only reads the content: it doesn't request the project management tool, so that the content can be read
 * several times and without the account of the current user. The selected work items are retrieved later, by the
 * client.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Role
@Unstable
public interface WorkItemSource
{
    /**
     * @param content the content of the macro, e.g. {@code 1911, PROJ-123} for a list of work items
     * @param context the context of the macro reading the content
     * @return the work items selected by the content
     * @throws WorkItemSourceException if the content isn't valid for this source, e.g. a syntax error, the message
     *     telling the user what is wrong
     */
    WorkItemSelection read(String content, WorkItemSourceContext context) throws WorkItemSourceException;
}
