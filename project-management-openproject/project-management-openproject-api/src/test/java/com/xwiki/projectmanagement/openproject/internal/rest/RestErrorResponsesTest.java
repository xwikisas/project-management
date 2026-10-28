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

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.xwiki.rest.XWikiRestException;

import com.xpn.xwiki.XWikiException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link RestErrorResponses}.
 *
 * @version $Id$
 */
class RestErrorResponsesTest
{
    private final Logger logger = mock(Logger.class);

    @Test
    void errorResponseHoldsOnlyTheMessage()
    {
        Exception exception = new XWikiException(XWikiException.MODULE_XWIKI_STORE,
            XWikiException.ERROR_XWIKI_STORE_HIBERNATE_SAVING_DOC, "Internal details");

        Response response = RestErrorResponses.errorResponse(this.logger, "Failed to save.", exception);

        assertEquals(500, response.getStatus());
        assertEquals("Failed to save.", response.getEntity());
        verify(this.logger).error("Failed to save.", exception);
    }

    @Test
    void errorResponseForMissingRights()
    {
        Exception exception = new XWikiRestException(new XWikiException(XWikiException.MODULE_XWIKI_ACCESS,
            XWikiException.ERROR_XWIKI_ACCESS_DENIED, "Access denied in edit mode on document [Space.Page]"));

        Response response = RestErrorResponses.errorResponse(this.logger, "Failed to save.", exception);

        assertEquals(401, response.getStatus());
        assertNull(response.getEntity());
    }
}
