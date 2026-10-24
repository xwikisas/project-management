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
package com.xwiki.projectmanagement.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for {@link WorkItemRetrievalException}.
 *
 * @version $Id$
 */
class WorkItemRetrievalExceptionTest
{
    @Test
    void statusCodeIsUnknownByDefault()
    {
        Exception cause = new Exception();

        WorkItemRetrievalException exception = new WorkItemRetrievalException("message");
        assertEquals(-1, exception.getStatusCode());
        assertEquals("message", exception.getMessage());
        assertNull(exception.getCause());

        exception = new WorkItemRetrievalException("message", cause);
        assertEquals(-1, exception.getStatusCode());
        assertSame(cause, exception.getCause());
    }

    @Test
    void statusCodeIsKept()
    {
        Exception cause = new Exception();

        assertEquals(401, new WorkItemRetrievalException("message", 401).getStatusCode());

        WorkItemRetrievalException exception = new WorkItemRetrievalException("message", cause, 503);
        assertEquals(503, exception.getStatusCode());
        assertSame(cause, exception.getCause());
    }
}
