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

import javax.inject.Named;

import org.junit.jupiter.api.Test;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.source.WorkItemSelection;
import com.xwiki.projectmanagement.source.WorkItemSource;
import com.xwiki.projectmanagement.source.WorkItemSourceContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectListWorkItemSource}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectListWorkItemSourceTest
{
    private final WorkItemSourceContext context = new WorkItemSourceContext("openproject");

    @InjectMockComponents
    private OpenProjectListWorkItemSource source;

    @MockComponent
    @Named("list")
    private WorkItemSource listSource;

    @Test
    void readsTheWorkPackageIdsAndKeys() throws Exception
    {
        WorkItemSelection selection = WorkItemSelection.ofIds(List.of("237", "PROJ-12", "qa150_t3-1"));
        when(this.listSource.read("content", this.context)).thenReturn(selection);

        assertSame(selection, this.source.read("content", this.context));
    }

    @Test
    void failsOnAValueThatIsNotAWorkPackage() throws Exception
    {
        when(this.listSource.read("237, #238", this.context))
            .thenReturn(WorkItemSelection.ofIds(List.of("237", "#238")));

        assertEquals("[#238] isn't the id (e.g. 123) or key (e.g. PROJ-123) of a work package.",
            assertThrows(WorkItemSourceException.class, () -> this.source.read("237, #238", this.context))
                .getMessage());
    }

    @Test
    void namesAllTheValuesThatAreNotWorkPackages() throws Exception
    {
        when(this.listSource.read("abc 237;238 239", this.context))
            .thenReturn(WorkItemSelection.ofIds(List.of("abc", "237;238", "239")));

        assertEquals("[abc], [237;238] aren't the id (e.g. 123) or key (e.g. PROJ-123) of work packages.",
            assertThrows(WorkItemSourceException.class, () -> this.source.read("abc 237;238 239", this.context))
                .getMessage());
    }
}
