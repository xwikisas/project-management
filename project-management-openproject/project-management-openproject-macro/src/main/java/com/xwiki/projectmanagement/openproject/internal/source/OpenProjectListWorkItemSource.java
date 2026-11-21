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
package com.xwiki.projectmanagement.openproject.internal.source;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;

import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectClient;
import com.xwiki.projectmanagement.source.WorkItemSelection;
import com.xwiki.projectmanagement.source.WorkItemSource;
import com.xwiki.projectmanagement.source.WorkItemSourceContext;

/**
 * The {@code list} source of the OpenProject macros: the generic {@code list} source, whose listed work packages are
 * checked when the content is read, so that a value that isn't a work package id or key is reported with the macro,
 * whatever displays the work packages (e.g. a calendar, which can't report the failures of its requests).
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component
@Named("openproject/list")
@Singleton
public class OpenProjectListWorkItemSource implements WorkItemSource
{
    private static final String EXAMPLES = "(e.g. 123) or key (e.g. PROJ-123)";

    @Inject
    @Named("list")
    private WorkItemSource listSource;

    @Override
    public WorkItemSelection read(String content, WorkItemSourceContext context) throws WorkItemSourceException
    {
        WorkItemSelection selection = this.listSource.read(content, context);
        List<String> invalidValues = selection.getIds().stream()
            .filter(id -> !OpenProjectClient.isWorkPackageIdOrKey(id)).collect(Collectors.toList());
        if (invalidValues.size() == 1) {
            throw new WorkItemSourceException(
                String.format("[%s] isn't the id %s of a work package.", invalidValues.get(0), EXAMPLES));
        } else if (!invalidValues.isEmpty()) {
            throw new WorkItemSourceException(String.format("[%s] aren't the id %s of work packages.",
                String.join("], [", invalidValues), EXAMPLES));
        }
        return selection;
    }
}
