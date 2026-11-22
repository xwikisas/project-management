package com.xwiki.projectmanagement.source;

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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WorkItemName}.
 *
 * @version $Id$
 */
class WorkItemNameTest
{
    @Test
    void isEqualToTheNamesWithTheSameText()
    {
        WorkItemName name = new WorkItemName("17");

        assertEquals(new WorkItemName("17"), name);
        assertEquals(new WorkItemName("17").hashCode(), name.hashCode());
        assertNotEquals(new WorkItemName("17.10"), name);
        // A name is never taken for the text of an id.
        assertNotEquals("17", name);
        assertEquals("17", name.getName());
        assertEquals("17", name.toString());
    }

    @Test
    void isAKeywordOnlyWhenWrittenWithoutQuotes()
    {
        WorkItemName keyword = new WorkItemName("open", true);

        assertTrue(keyword.isKeyword());
        assertFalse(new WorkItemName("open").isKeyword());
        // The quoted name "open" and the keyword open aren't the same value.
        assertNotEquals(new WorkItemName("open"), keyword);
        assertEquals(new WorkItemName("open", true), keyword);
        assertEquals(new WorkItemName("open", true).hashCode(), keyword.hashCode());
    }

    @Test
    void requiresAName()
    {
        assertThrows(NullPointerException.class, () -> new WorkItemName(null));
    }
}
