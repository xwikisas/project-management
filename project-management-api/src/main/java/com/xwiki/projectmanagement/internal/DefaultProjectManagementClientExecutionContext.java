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

    /**
     * Remove the context of the current thread, so that it doesn't outlive the request that set it (the threads are
     * reused for other requests).
     */
    public void clearContext()
    {
        this.context.remove();
    }

    /**
     * Set the context of the current thread until the returned scope is closed, which restores the context that was
     * set before (or removes the context when none was set), so that the context doesn't leak to the code executed
     * later by the same thread (e.g. the next macro of the page, or the next request handled by the thread).
     *
     * @param context a map of entries that serve the client implementation
     * @return the scope to close once the client calls are done, typically with a try-with-resources statement
     */
    public ContextScope pushContext(Map<String, Object> context)
    {
        Map<String, Object> previousContext = this.context.get();
        this.context.set(context);
        return () -> {
            if (previousContext == null) {
                this.context.remove();
            } else {
                this.context.set(previousContext);
            }
        };
    }

    /**
     * Set the context of the current thread until the returned scope is closed, when the given execution context is
     * the default one, which is the only one whose context can be set.
     *
     * @param executionContext the execution context of the project management clients
     * @param context a map of entries that serve the client implementation
     * @return the scope to close once the client calls are done, which restores the previous context
     * @see #pushContext(Map)
     */
    public static ContextScope pushContext(ProjectManagementClientExecutionContext executionContext,
        Map<String, Object> context)
    {
        if (executionContext instanceof DefaultProjectManagementClientExecutionContext) {
            return ((DefaultProjectManagementClientExecutionContext) executionContext).pushContext(context);
        }
        return () -> { };
    }

    /**
     * The scope of a context set with {@link #pushContext(Map)}: closing it restores the previous context.
     */
    @FunctionalInterface
    public interface ContextScope extends AutoCloseable
    {
        @Override
        void close();
    }
}

