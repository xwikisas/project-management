package com.xwiki.projectmanagement.internal.rest;

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

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xwiki.projectmanagement.internal.source.QueryWorkItemSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link WorkItemQueryResource}.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(QueryWorkItemSource.class)
class WorkItemQueryResourceTest
{
    @InjectMockComponents
    private WorkItemQueryResource resource;

    @Test
    void getFiltersFlagsTheNames() throws Exception
    {
        Response response = this.resource.getFilters("project = \"17\" AND status = open AND type = 1");

        assertEquals(200, response.getStatus());
        assertEquals("{\"filters\":[{\"property\":\"project\",\"constraints\":[{\"operator\":\"=\","
            + "\"value\":\"17\",\"name\":true}]},{\"property\":\"status\",\"constraints\":[{\"operator\":\"o\","
            + "\"value\":\"\"}]},{\"property\":\"type\",\"constraints\":[{\"operator\":\"=\",\"value\":\"1\"}]}]}",
            response.getEntity());
    }

    @Test
    void getFiltersReportsAnInvalidQuery() throws Exception
    {
        Response response = this.resource.getFilters("project = xwiki OR status = open");

        assertEquals(400, response.getStatus());
        assertEquals("{\"message\":\"OR (at position 17) is not supported yet: the query can only combine conditions "
            + "with AND.\"}", response.getEntity());
    }

    @Test
    void getFiltersWithoutQuery() throws Exception
    {
        assertEquals(400, this.resource.getFilters(null).getStatus());
    }
}
