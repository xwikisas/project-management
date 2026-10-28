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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;

import com.xpn.xwiki.XWikiException;

/**
 * Builds the error responses of the REST resources. The details of the failure are logged and not sent to the client.
 *
 * @version $Id$
 */
public final class RestErrorResponses
{
    private RestErrorResponses()
    {
    }

    /**
     * Logs the given failure and builds an error response holding only the given message. A failure caused by missing
     * rights is turned into a response with the {@code 401} status and no entity.
     *
     * @param logger the logger to use
     * @param message the message describing the failed operation, sent to the client
     * @param e the failure
     * @return the error response
     */
    public static Response errorResponse(Logger logger, String message, Throwable e)
    {
        if (isAccessDenied(e)) {
            logger.debug("{} Root cause: [{}]", message, ExceptionUtils.getRootCauseMessage(e));
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        logger.error(message, e);
        return Response.serverError().entity(message).build();
    }

    private static boolean isAccessDenied(Throwable e)
    {
        for (Throwable throwable : ExceptionUtils.getThrowableList(e)) {
            if (throwable instanceof XWikiException
                && ((XWikiException) throwable).getCode() == XWikiException.ERROR_XWIKI_ACCESS_DENIED)
            {
                return true;
            }
        }
        return false;
    }
}
