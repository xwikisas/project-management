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
package com.xwiki.projectmanagement.internal.rest;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;
import org.xwiki.wiki.manager.WikiManagerException;

import com.xpn.xwiki.XWikiContext;

/**
 * Handles the REST requests made on a given wiki ({@code /wikis/{wikiName}/...}) with that wiki as context wiki. The
 * REST requests are made with the main wiki as context wiki (or the wiki of the domain), while the project management
 * connections, as other configurations, are read from the context wiki.
 *
 * @version $Id$
 * @since 1.4.0
 */
@Component(roles = WikiRequestExecutor.class)
@Singleton
public class WikiRequestExecutor
{
    /**
     * Handles a request and builds its response.
     *
     * @param <E> the type of the failure of the request
     * @version $Id$
     */
    @FunctionalInterface
    public interface Request<E extends Exception>
    {
        /**
         * @return the response to the request
         * @throws E if the request fails
         */
        Response handle() throws E;
    }

    @Inject
    private ContextualAuthorizationManager authorizationManager;

    @Inject
    private WikiDescriptorManager wikiDescriptorManager;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private Logger logger;

    /**
     * Handles the given request with the given wiki as context wiki, and restores the context wiki after.
     *
     * @param <E> the type of the failure of the request
     * @param wiki the wiki on which the request is made
     * @param request the request to handle
     * @return the response of the request; 404 if the wiki doesn't exist; 401 if the current user doesn't have view
     *     right on the wiki; 500 if the existence of the wiki couldn't be checked
     * @throws E if the request fails
     */
    public <E extends Exception> Response execute(String wiki, Request<E> request) throws E
    {
        // The existence of the wiki is checked first: checking the rights on a missing wiki logs an error.
        try {
            if (!this.wikiDescriptorManager.exists(wiki)) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } catch (WikiManagerException e) {
            String message = "Failed to check if the wiki exists.";
            this.logger.warn("{} Root cause: [{}]", message, ExceptionUtils.getRootCauseMessage(e));
            return Response.serverError().type(MediaType.TEXT_PLAIN_TYPE).entity(message).build();
        }
        if (!this.authorizationManager.hasAccess(Right.VIEW, new WikiReference(wiki))) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        XWikiContext xcontext = this.xcontextProvider.get();
        String currentWiki = xcontext.getWikiId();
        try {
            xcontext.setWikiId(wiki);
            return request.handle();
        } finally {
            xcontext.setWikiId(currentWiki);
        }
    }
}
