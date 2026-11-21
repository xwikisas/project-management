package com.xwiki.projectmanagement.openproject.internal.source;

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

import org.junit.jupiter.api.Test;

import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.source.WorkItemSelection;
import com.xwiki.projectmanagement.source.WorkItemSourceContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OpenProjectUrlWorkItemSource}.
 *
 * @version $Id$
 */
class OpenProjectUrlWorkItemSourceTest
{
    private final OpenProjectUrlWorkItemSource source = new OpenProjectUrlWorkItemSource();

    private final WorkItemSourceContext context = new WorkItemSourceContext("openproject");

    @Test
    void readsTheLinkAsTheIdentifier() throws Exception
    {
        WorkItemSelection selection =
            this.source.read("\n HTTPS://op.example.com/projects/demo/work_packages?query_id=5 \n", this.context);

        assertEquals(Map.of("identifier", "HTTPS://op.example.com/projects/demo/work_packages?query_id=5"),
            selection.getClientScope());
        assertTrue(selection.getFilters().isEmpty());
        assertTrue(selection.getIds().isEmpty());
    }

    @Test
    void failsOnContentThatIsntALink()
    {
        assertEquals("The macro content [1911, 1912] isn't the link of an OpenProject work package list or saved "
            + "query.", assertThrows(WorkItemSourceException.class, () -> this.source.read("1911, 1912", this.context))
            .getMessage());
        assertThrows(WorkItemSourceException.class,
            () -> this.source.read("http://op/wp/1\nhttp://op/wp/2", this.context));
    }
}
