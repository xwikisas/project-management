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

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link DefaultProjectManagementClientExecutionContext}.
 *
 * @version $Id$
 */
class DefaultProjectManagementClientExecutionContextTest
{
    private final DefaultProjectManagementClientExecutionContext executionContext =
        new DefaultProjectManagementClientExecutionContext();

    @Test
    void emptyWhenNotSet()
    {
        assertTrue(this.executionContext.getContext().isEmpty());
        assertNull(this.executionContext.get("client"));
    }

    @Test
    void emptyWhenSetToNull()
    {
        this.executionContext.setContext(Map.of("client", "openproject"));
        this.executionContext.setContext(null);

        assertTrue(this.executionContext.getContext().isEmpty());
        assertNull(this.executionContext.get("client"));
    }

    @Test
    void getSetValues()
    {
        Map<String, Object> context = Map.of("client", "openproject", "translationPrefix", "openproject.");
        this.executionContext.setContext(context);

        assertEquals(context, this.executionContext.getContext());
        assertEquals("openproject", this.executionContext.get("client"));
        assertEquals("openproject.", this.executionContext.get("translationPrefix"));
        assertNull(this.executionContext.get("missing"));
    }

    @Test
    void closingThePushedContextRestoresThePreviousOne()
    {
        Map<String, Object> outer = Map.of("client", "openproject");
        this.executionContext.setContext(outer);

        try (DefaultProjectManagementClientExecutionContext.ContextScope scope =
            this.executionContext.pushContext(Map.of("client", "test")))
        {
            assertEquals("test", this.executionContext.get("client"));
        }

        assertEquals(outer, this.executionContext.getContext());
    }

    @Test
    void closingThePushedContextRemovesItWhenNoneWasSet()
    {
        try (DefaultProjectManagementClientExecutionContext.ContextScope scope =
            DefaultProjectManagementClientExecutionContext.pushContext(this.executionContext,
                Map.of("client", "test")))
        {
            assertEquals("test", this.executionContext.get("client"));
        }

        assertTrue(this.executionContext.getContext().isEmpty());
    }

    @Test
    void pushingAContextOnAnotherExecutionContextDoesNothing()
    {
        ProjectManagementClientExecutionContext otherContext = mock(ProjectManagementClientExecutionContext.class);

        try (DefaultProjectManagementClientExecutionContext.ContextScope scope =
            DefaultProjectManagementClientExecutionContext.pushContext(otherContext, Map.of("client", "test")))
        {
            assertTrue(this.executionContext.getContext().isEmpty());
        }

        verifyNoInteractions(otherContext);
    }

    @Test
    void contextIsBoundToTheCurrentThread() throws Exception
    {
        this.executionContext.setContext(Map.of("client", "openproject"));

        AtomicReference<Object> otherThreadValue = new AtomicReference<>("not called");
        Thread thread = new Thread(() -> otherThreadValue.set(this.executionContext.get("client")));
        thread.start();
        thread.join();

        assertNull(otherThreadValue.get());
        assertEquals("openproject", this.executionContext.get("client"));
    }
}
