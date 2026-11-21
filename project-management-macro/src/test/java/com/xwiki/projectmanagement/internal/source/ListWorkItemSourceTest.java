package com.xwiki.projectmanagement.internal.source;

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

import org.junit.jupiter.api.Test;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.source.WorkItemSelection;
import com.xwiki.projectmanagement.source.WorkItemSourceContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ListWorkItemSource}.
 *
 * @version $Id$
 */
@ComponentTest
class ListWorkItemSourceTest
{
    @InjectMockComponents
    private ListWorkItemSource source;

    @Test
    void readsTheWorkItemsSeparatedByCommasSpacesOrNewLines() throws Exception
    {
        WorkItemSelection selection = this.source.read(" 1911, PROJ-123\n42 ,,7\n", new WorkItemSourceContext(null));

        assertEquals(List.of("1911", "PROJ-123", "42", "7"), selection.getIds());
        assertTrue(selection.getFilters().isEmpty());
        assertTrue(selection.getClientScope().isEmpty());
    }

    @Test
    void failsWithoutWorkItem()
    {
        WorkItemSourceContext context = new WorkItemSourceContext(null);

        assertEquals("The macro content lists no work item.",
            assertThrows(WorkItemSourceException.class, () -> this.source.read(" , ", context)).getMessage());
    }
}
