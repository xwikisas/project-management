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
package com.xwiki.projectmanagement.internal.rest;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.exception.ExceptionUtils;

import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rest.XWikiResource;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;

/**
 * Provides a base for the project management resources.
 *
 * @version $Id$
 * @since 1.2.0
 */
public abstract class AbstractProjectManagementResource extends XWikiResource
{
    @Inject
    protected LiveDataConfigurationResolver<String> stringLiveDataConfigResolver;

    @Inject
    protected ComponentManager componentManager;

    @Inject
    protected ProjectManagementClientExecutionContext clientExecutionContext;

    protected List<LiveDataQuery.Filter> getFilters(String filtersString) throws LiveDataException
    {
        String serializedCfg = filtersString == null ? "" : filtersString;
        LiveDataConfiguration configuration = this.stringLiveDataConfigResolver.resolve(serializedCfg);
        if (configuration == null) {
            return Collections.emptyList();
        }

        if (configuration.getQuery() == null || configuration.getQuery().getFilters() == null) {
            return Collections.emptyList();
        }
        return configuration.getQuery().getFilters();
    }

    /**
     * Log the given failure and build an error response holding only the given message: the exception can hold
     * details about the project management server (URL, error responses) that are only meant for the administrators.
     *
     * @param status the status of the response
     * @param message the message sent to the client
     * @param e the failure to log
     * @return the error response
     */
    protected Response errorResponse(Response.Status status, String message, Exception e)
    {
        this.slf4Jlogger.warn("{} Root cause: [{}]", message, ExceptionUtils.getRootCauseMessage(e));
        return Response.status(status).entity(message).build();
    }

    protected void prepareClientContext()
    {
        if (clientExecutionContext instanceof DefaultProjectManagementClientExecutionContext) {
            Map<String, Object> context = new HashMap<>();
            for (Map.Entry<String, List<String>> stringListEntry : uriInfo.getQueryParameters().entrySet()) {
                if (stringListEntry.getValue().size() == 1) {
                    context.put(stringListEntry.getKey(), stringListEntry.getValue().get(0));
                } else {
                    context.put(stringListEntry.getKey(), stringListEntry.getValue());
                }
            }
            ((DefaultProjectManagementClientExecutionContext) clientExecutionContext)
                .setContext(context);
        }
    }

    /**
     * Remove the client context set by {@link #prepareClientContext()}, to call once the request is handled: the
     * thread is then reused for other requests, which must not see the parameters of this one.
     */
    protected void clearClientContext()
    {
        if (clientExecutionContext instanceof DefaultProjectManagementClientExecutionContext) {
            ((DefaultProjectManagementClientExecutionContext) clientExecutionContext).clearContext();
        }
    }
}
