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

import java.util.Collections;
import java.util.Map;

import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;

/**
 * Default implementation for the {@link ProjectManagementClientExecutionContext}. Uses thread local map that should be
 * set before the client gets executed.
 *
 * @version $Id$
 */
@Component
@Singleton
public class DefaultProjectManagementClientExecutionContext implements ProjectManagementClientExecutionContext
{
    private final ThreadLocal<Map<String, Object>> context = new ThreadLocal<>();

    @Override
    public Map<String, Object> getContext()
    {
        Map<String, Object> setContext = context.get();
        if (setContext == null) {
            return Collections.emptyMap();
        }
        return setContext;
    }

    @Override
    public Object get(String key)
    {
        return getContext().get(key);
    }

    /**
     * @param context a map of entries that serve the client implementation.
     */
    public void setContext(Map<String, Object> context)
    {
        this.context.set(context);
    }
}

