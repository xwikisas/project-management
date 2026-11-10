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
package com.xwiki.projectmanagement.openproject.internal.listener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.observation.remote.RemoteObservationManagerContext;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.internal.event.XObjectAddedEvent;
import com.xpn.xwiki.internal.event.XObjectUpdatedEvent;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.BaseObjectReference;
import com.xwiki.projectmanagement.openproject.internal.AdminAuthorChecker;

/**
 * Keeps the default OpenProject connection, used by the macros that don't set their instance parameter, in sync with
 * the connections:
 * <ul>
 * <li>when a second connection is added while no default connection is set, the first connection becomes the default
 * one, so that the macros written while it was the only connection keep using it;</li>
 * <li>when the default connection is renamed, the default connection follows the new name.</li>
 * </ul>
 * A default connection that doesn't match any connection anymore, e.g. once deleted, is the same as no default
 * connection for the macros, so it isn't cleared on deletion: this way, moving the page of the default connection
 * (deleted, then created again under the same name) keeps it the default one.
 * Like in the OpenProject configuration, only the connections saved by a user with administration right on the wiki
 * are taken into account, and the default connection is only changed when the current user has administration right
 * on the wiki holding the connection.
 *
 * @version $Id$
 * @since 1.4.0
 */
@Component
@Singleton
@Named(DefaultConnectionListener.NAME)
public class DefaultConnectionListener extends AbstractEventListener
{
    /**
     * The name of the listener.
     */
    public static final String NAME = "com.xwiki.projectmanagement.openproject.internal.listener."
        + "DefaultConnectionListener";

    private static final List<String> CODE_SPACE = Arrays.asList("OpenProject", "Code");

    private static final String CONNECTION_CLASS_NAME = "OpenProject.Code.OpenProjectConnectionClass";

    private static final LocalDocumentReference CONNECTION_CLASS =
        new LocalDocumentReference(CODE_SPACE, "OpenProjectConnectionClass");

    private static final EntityReference ANY_CONNECTION_OBJECT = BaseObjectReference.any(CONNECTION_CLASS_NAME);

    private static final LocalDocumentReference CONFIGURATION_DOCUMENT =
        new LocalDocumentReference(CODE_SPACE, "OpenProjectConfiguration");

    private static final LocalDocumentReference CONFIGURATION_CLASS =
        new LocalDocumentReference(CODE_SPACE, "OpenProjectConfigurationClass");

    private static final String CONNECTION_NAME = "connectionName";

    private static final String DEFAULT_CONNECTION = "defaultConnection";

    @Inject
    private Logger logger;

    @Inject
    private QueryManager queryManager;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    @Inject
    private AdminAuthorChecker adminAuthorChecker;

    @Inject
    private RemoteObservationManagerContext remoteObservationManagerContext;

    /**
     * Default constructor.
     */
    public DefaultConnectionListener()
    {
        super(NAME, Arrays.asList(new XObjectAddedEvent(ANY_CONNECTION_OBJECT),
            new XObjectUpdatedEvent(ANY_CONNECTION_OBJECT)));
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        // In a cluster, the member where the connection was saved updates the default connection.
        if (this.remoteObservationManagerContext.isRemoteState()) {
            return;
        }
        XWikiDocument connection = (XWikiDocument) source;
        XWikiContext context = (XWikiContext) data;
        WikiReference wiki = connection.getDocumentReference().getWikiReference();
        if (context.getUserReference() == null || !this.adminAuthorChecker.isAdmin(context.getUserReference(), wiki)) {
            this.logger.debug("Not updating the default OpenProject connection of the wiki [{}] since the current "
                + "user doesn't have administration right on it.", wiki);
            return;
        }
        try {
            if (event instanceof XObjectAddedEvent) {
                onConnectionAdded(connection, context);
            } else if (isSavedByAdmin(connection)) {
                onConnectionRenamed(getConnectionName(connection.getOriginalDocument()),
                    getConnectionName(connection), wiki, context);
            }
        } catch (XWikiException | QueryException e) {
            this.logger.warn("Failed to update the default OpenProject connection after the change of the connection "
                + "stored on [{}]. Cause: [{}].", connection.getDocumentReference(),
                ExceptionUtils.getRootCauseMessage(e));
        }
    }

    private void onConnectionAdded(XWikiDocument connection, XWikiContext context)
        throws XWikiException, QueryException
    {
        WikiReference wiki = connection.getDocumentReference().getWikiReference();
        String defaultConnection = getDefaultConnection(wiki, context);
        if (StringUtils.isNotBlank(defaultConnection)
            && getConnectionNames(wiki, null, context).contains(defaultConnection))
        {
            return;
        }
        // The macros that don't set their instance parameter use the only connection: make it the default one before
        // it stops being the only one. With several other connections and no default one, these macros don't know
        // which one to use already, and the administrator has to choose.
        List<String> otherConnections = getConnectionNames(wiki, connection.getDocumentReference(), context);
        if (otherConnections.size() == 1) {
            setDefaultConnection(otherConnections.get(0), wiki, context);
        }
    }

    private void onConnectionRenamed(String previousName, String newName, WikiReference wiki, XWikiContext context)
        throws XWikiException
    {
        if (StringUtils.isNotBlank(previousName) && !Objects.equals(previousName, newName)
            && previousName.equals(getDefaultConnection(wiki, context)))
        {
            setDefaultConnection(newName, wiki, context);
        }
    }

    private boolean isSavedByAdmin(XWikiDocument document)
    {
        return this.adminAuthorChecker.isAdmin(this.adminAuthorChecker.getMetadataAuthor(document),
            document.getDocumentReference().getWikiReference());
    }

    private String getConnectionName(XWikiDocument document)
    {
        BaseObject object = document != null ? document.getXObject(CONNECTION_CLASS) : null;
        return object != null ? object.getStringValue(CONNECTION_NAME) : null;
    }

    /**
     * @return the names of the connections of the given wiki that are taken into account (saved by an administrator),
     *     from the oldest to the newest, apart from the one stored on the excluded document
     */
    private List<String> getConnectionNames(WikiReference wiki, DocumentReference excludedDocument,
        XWikiContext context) throws XWikiException, QueryException
    {
        List<String> documentNames = this.queryManager.createQuery(String.format(
            "select doc.fullName from Document doc, doc.object(%s) as connection order by doc.creationDate, "
                + "doc.fullName", CONNECTION_CLASS_NAME), Query.XWQL).setWiki(wiki.getName()).execute();
        List<String> names = new ArrayList<>();
        for (String documentName : documentNames) {
            XWikiDocument document =
                context.getWiki().getDocument(this.resolver.resolve(documentName, wiki), context);
            String name = getConnectionName(document);
            if (!document.getDocumentReference().equals(excludedDocument) && StringUtils.isNotBlank(name)
                && isSavedByAdmin(document))
            {
                names.add(name);
            }
        }
        return names;
    }

    private String getDefaultConnection(WikiReference wiki, XWikiContext context) throws XWikiException
    {
        BaseObject configuration = context.getWiki()
            .getDocument(new DocumentReference(CONFIGURATION_DOCUMENT, wiki), context).getXObject(CONFIGURATION_CLASS);
        return configuration != null ? configuration.getStringValue(DEFAULT_CONNECTION) : null;
    }

    private void setDefaultConnection(String name, WikiReference wiki, XWikiContext context) throws XWikiException
    {
        // Work on a copy of the cached document.
        XWikiDocument configuration =
            context.getWiki().getDocument(new DocumentReference(CONFIGURATION_DOCUMENT, wiki), context).clone();
        if (configuration.isNew()) {
            // The pages of the extension aren't installed on this wiki: don't create a partial configuration page that
            // would conflict with the one of the extension when it's installed.
            this.logger.debug("Not setting the default OpenProject connection since [{}] doesn't exist.",
                configuration.getDocumentReference());
            return;
        }
        configuration.getXObject(CONFIGURATION_CLASS, true, context).setStringValue(DEFAULT_CONNECTION, name);
        configuration.setAuthorReference(context.getUserReference());
        context.getWiki().saveDocument(configuration,
            String.format("Set the default OpenProject connection to [%s].", name), true, context);
    }
}
