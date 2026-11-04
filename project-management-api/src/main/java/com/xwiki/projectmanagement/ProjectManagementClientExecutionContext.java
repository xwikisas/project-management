package com.xwiki.projectmanagement;

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

import java.util.Map;

import org.xwiki.component.annotation.Role;

/**
 * The execution context of the project management client. Can be used to pass additional information to the client
 * implementation.
 *
 * @version $Id$
 * @since 1.0
 */
@Role
public interface ProjectManagementClientExecutionContext
{
    /**
     * The key of the context value that is {@link Boolean#TRUE} when the work items are requested by Live Data, whose
     * viewers can filter and sort them, and which can't display why a request failed. A client can then return no work
     * item for a request it can't answer (e.g. an unsupported combination of filters), while it reports the failure to
     * the other callers (e.g. a chart, which displays it).
     *
     * @since 1.4.0
     */
    String LIVE_DATA = "liveData";

    /**
     * @return a map containing various information that can be used by the client. Returns an empty map if the context
     *     was not set.
     */
    Map<String, Object> getContext();

    /**
     * @param key identifies tha value that might or might not be set in the context.
     * @return a value from the current execution context or null if the key was not found.
     */
    Object get(String key);
}
