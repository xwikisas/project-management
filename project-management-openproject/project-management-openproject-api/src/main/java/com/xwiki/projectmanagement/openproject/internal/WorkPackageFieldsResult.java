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
package com.xwiki.projectmanagement.openproject.internal;

import java.util.List;

import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

/**
 * The work package fields of an OpenProject instance, along with the filters available to its work package queries,
 * which are read from the same response: some filters match no field (e.g. {@code typeahead}).
 *
 * @version $Id$
 * @since 1.4.1
 */
public class WorkPackageFieldsResult extends PaginatedResult<WorkPackageField>
{
    private final List<String> filterKeys;

    /**
     * @param fields the fields of the work packages
     * @param filterKeys the keys of the filters available to the work package queries
     */
    public WorkPackageFieldsResult(List<WorkPackageField> fields, List<String> filterKeys)
    {
        super(fields, 0, fields.size(), fields.size());
        this.filterKeys = filterKeys;
    }

    /**
     * @return the keys of the filters available to the work package queries (e.g. {@code status} or
     *     {@code customField3})
     */
    public List<String> getFilterKeys()
    {
        return this.filterKeys;
    }
}
