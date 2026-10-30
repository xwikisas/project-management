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
package com.xwiki.projectmanagement.internal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link Pagination}.
 *
 * @version $Id$
 */
class PaginationTest
{
    @ParameterizedTest
    @CsvSource({
        "1, 10, 0",
        "2, 10, 10",
        "4, 20, 60",
        // A page smaller than 1 is the first page.
        "0, 15, 0",
        "-2, 25, 0"
    })
    void getOffsetOfTheFirstItemOfThePage(int page, int pageSize, int expectedOffset)
    {
        assertEquals(expectedOffset, Pagination.getOffset(page, pageSize));
    }
}
