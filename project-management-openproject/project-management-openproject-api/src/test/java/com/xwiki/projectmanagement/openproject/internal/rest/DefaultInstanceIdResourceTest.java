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
package com.xwiki.projectmanagement.openproject.internal.rest;

import javax.ws.rs.WebApplicationException;

import org.junit.jupiter.api.Test;
import org.xwiki.instance.InstanceId;
import org.xwiki.instance.InstanceIdManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultInstanceIdResource}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultInstanceIdResourceTest
{
    @InjectMockComponents
    private DefaultInstanceIdResource instanceIdResource;

    @MockComponent
    private InstanceIdManager instanceIdManager;

    @Test
    void getInstanceId()
    {
        InstanceId instanceId = new InstanceId("e3ee1b7e-4c4f-4b0a-9a4e-4a7e2c3b1d0f");
        when(this.instanceIdManager.getInstanceId()).thenReturn(instanceId);

        assertEquals(instanceId, this.instanceIdResource.getInstanceId());
    }

    @Test
    void getInstanceIdWhenNotInitialized()
    {
        WebApplicationException exception =
            assertThrows(WebApplicationException.class, () -> this.instanceIdResource.getInstanceId());

        assertEquals(500, exception.getResponse().getStatus());
    }
}
