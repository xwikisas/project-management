package com.xwiki.projectmanagement.openproject.internal;

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

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rendering.macro.MacroExecutionException;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectFilterValueException;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectFilterValueResolver;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectWorkItemFields;
import com.xwiki.projectmanagement.openproject.internal.processing.WorkPackageFieldsCache;

/**
 * Resolves the names written in the query of an OpenProject macro (for example the project {@code xwiki} or the
 * status {@code "In progress"}) into the ids of the OpenProject resources, with the account of the current user (see
 * {@link OpenProjectFilterValueResolver}), so that the macro reports a name matching no resource, or several, instead
 * of displaying no work package. It also checks the properties of the filters, which are ignored when unknown.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component(roles = OpenProjectNameResolver.class)
@Singleton
public class OpenProjectNameResolver
{
    @Inject
    private OpenProjectAccess openProjectAccess;

    @Inject
    private WorkPackageFieldsCache workPackageFieldsCache;

    @Inject
    private Logger logger;

    /**
     * @param instance the OpenProject instance of the macro, for example {@code main}
     * @param filters the filters read from the macro content, whose names are
     *     {@link com.xwiki.projectmanagement.source.WorkItemName} values
     * @return the filters whose names are replaced by ids; the filters are returned as they are when the instance
     *     can't be used (for example when it isn't configured) or can't be reached, the macro reporting it when it
     *     retrieves the work packages
     * @throws MacroExecutionException if a filter is on an unknown property (when the filters of the instance are
     *     known), or if a name matches no OpenProject resource, or several, the message naming the property (and the
     *     value)
     */
    public List<LiveDataQuery.Filter> resolve(String instance, List<LiveDataQuery.Filter> filters)
        throws MacroExecutionException
    {
        OpenProjectAccessResult access = this.openProjectAccess.resolve(instance);
        if (access.getClient() == null) {
            return filters;
        }
        OpenProjectWorkItemFields fields;
        try {
            fields = this.workPackageFieldsCache.getFields(instance, access);
        } catch (ProjectManagementException | UnsupportedOperationException e) {
            // Only the names in the filters on the generic properties can be resolved.
            fields = OpenProjectWorkItemFields.empty();
        }
        try {
            OpenProjectFilterValueResolver resolver = new OpenProjectFilterValueResolver(access.getClient(), fields);
            resolver.checkProperties(filters);
            return resolver.resolve(filters);
        } catch (OpenProjectFilterValueException e) {
            throw new MacroExecutionException(e.getMessage(), e);
        } catch (ProjectManagementException e) {
            this.logger.debug("Failed to resolve the names in the filters of the instance [{}]: [{}].", instance,
                ExceptionUtils.getRootCauseMessage(e));
            return filters;
        }
    }
}
