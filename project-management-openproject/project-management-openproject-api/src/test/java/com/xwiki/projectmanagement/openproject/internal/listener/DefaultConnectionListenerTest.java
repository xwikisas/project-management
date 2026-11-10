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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.observation.event.Event;
import org.xwiki.observation.remote.RemoteObservationManagerContext;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.internal.event.XObjectAddedEvent;
import com.xpn.xwiki.internal.event.XObjectUpdatedEvent;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xwiki.projectmanagement.openproject.internal.AdminAuthorChecker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultConnectionListener}.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
@ComponentList(AdminAuthorChecker.class)
class DefaultConnectionListenerTest
{
    private static final List<String> CODE_SPACE = Arrays.asList("OpenProject", "Code");

    private static final LocalDocumentReference CONNECTION_CLASS =
        new LocalDocumentReference(CODE_SPACE, "OpenProjectConnectionClass");

    private static final LocalDocumentReference CONFIGURATION_CLASS =
        new LocalDocumentReference(CODE_SPACE, "OpenProjectConfigurationClass");

    private static final WikiReference MAIN_WIKI = new WikiReference("xwiki");

    private static final DocumentReference CONFIGURATION_DOCUMENT =
        new DocumentReference(MAIN_WIKI.getName(), CODE_SPACE, "OpenProjectConfiguration");

    private static final DocumentReference ADMIN = new DocumentReference("xwiki", "XWiki", "Admin");

    private static final DocumentReference USER = new DocumentReference("xwiki", "XWiki", "User");

    private static final String DEFAULT_CONNECTION = "defaultConnection";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @MockComponent
    private RemoteObservationManagerContext remoteObservationManagerContext;

    @MockComponent
    private QueryManager queryManager;

    @InjectMockComponents
    private DefaultConnectionListener listener;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private final List<String> connectionPages = new ArrayList<>();

    private UserReferenceResolver<DocumentReference> userResolver;

    private Query query;

    @BeforeEach
    void setUp() throws Exception
    {
        this.userResolver = this.componentManager.getInstance(UserReferenceResolver.TYPE_DOCUMENT_REFERENCE,
            "document");
        when(this.oldcore.getMockAuthorizationManager().hasAccess(Right.ADMIN, ADMIN, MAIN_WIKI)).thenReturn(true);
        this.oldcore.getXWikiContext().setUserReference(ADMIN);

        this.query = mock(Query.class);
        when(this.queryManager.createQuery(anyString(), eq(Query.XWQL))).thenReturn(this.query);
        when(this.query.setWiki(MAIN_WIKI.getName())).thenReturn(this.query);
        when(this.query.<String>execute()).thenReturn(this.connectionPages);

        // The configuration page installed with the extension.
        setDefaultConnection("");
    }

    private XWikiDocument saveConnection(String page, String name, DocumentReference author) throws XWikiException
    {
        XWikiContext context = this.oldcore.getXWikiContext();
        XWikiDocument connection = new XWikiDocument(new DocumentReference(MAIN_WIKI.getName(),
            Arrays.asList("OpenProject", "Code", "OpenProjectConfigurations"), page));
        connection.newXObject(CONNECTION_CLASS, context).setStringValue("connectionName", name);
        connection.getAuthors().setEffectiveMetadataAuthor(this.userResolver.resolve(author));
        this.oldcore.getSpyXWiki().saveDocument(connection, context);
        this.connectionPages.add("OpenProject.Code.OpenProjectConfigurations." + page);
        return this.oldcore.getSpyXWiki().getDocument(connection.getDocumentReference(), context);
    }

    private XWikiDocument saveConnection(String page, String name) throws XWikiException
    {
        return saveConnection(page, name, ADMIN);
    }

    private void setDefaultConnection(String name) throws XWikiException
    {
        XWikiContext context = this.oldcore.getXWikiContext();
        XWikiDocument configuration = new XWikiDocument(CONFIGURATION_DOCUMENT);
        configuration.newXObject(CONFIGURATION_CLASS, context).setStringValue(DEFAULT_CONNECTION, name);
        this.oldcore.getSpyXWiki().saveDocument(configuration, context);
    }

    private String getDefaultConnection() throws XWikiException
    {
        XWikiDocument configuration =
            this.oldcore.getSpyXWiki().getDocument(CONFIGURATION_DOCUMENT, this.oldcore.getXWikiContext());
        BaseObject object = configuration.getXObject(CONFIGURATION_CLASS);
        return object != null ? object.getStringValue(DEFAULT_CONNECTION) : null;
    }

    private XWikiDocument rename(XWikiDocument connection, String newName) throws XWikiException
    {
        XWikiDocument renamed = connection.clone();
        renamed.setOriginalDocument(connection);
        renamed.getXObject(CONNECTION_CLASS).setStringValue("connectionName", newName);
        return renamed;
    }

    private void delete(XWikiDocument connection) throws XWikiException
    {
        this.oldcore.getSpyXWiki().deleteDocument(connection, this.oldcore.getXWikiContext());
        this.connectionPages.removeIf(page -> page.endsWith("." + connection.getDocumentReference().getName()));
    }

    private void onEvent(Event event, XWikiDocument connection)
    {
        this.listener.onEvent(event, connection, this.oldcore.getXWikiContext());
    }

    @Test
    void addingTheSecondConnectionMakesTheFirstOneTheDefault() throws Exception
    {
        saveConnection("Connection1", "first");
        XWikiDocument second = saveConnection("Connection2", "second");

        onEvent(new XObjectAddedEvent(), second);

        assertEquals("first", getDefaultConnection());
        XWikiDocument configuration =
            this.oldcore.getSpyXWiki().getDocument(CONFIGURATION_DOCUMENT, this.oldcore.getXWikiContext());
        assertEquals(ADMIN, configuration.getAuthorReference());
        assertEquals("Set the default OpenProject connection to [first].", configuration.getComment());
    }

    @Test
    void addingTheFirstConnectionDoesntSetADefault() throws Exception
    {
        onEvent(new XObjectAddedEvent(), saveConnection("Connection1", "first"));

        assertEquals("", getDefaultConnection());
    }

    @Test
    void addingAConnectionKeepsAnExistingDefault() throws Exception
    {
        saveConnection("Connection1", "first");
        saveConnection("Connection2", "second");
        setDefaultConnection("second");

        onEvent(new XObjectAddedEvent(), saveConnection("Connection3", "third"));

        assertEquals("second", getDefaultConnection());
    }

    @Test
    void addingAConnectionDoesntChooseAmongSeveralConnections() throws Exception
    {
        saveConnection("Connection1", "first");
        saveConnection("Connection2", "second");

        onEvent(new XObjectAddedEvent(), saveConnection("Connection3", "third"));

        assertEquals("", getDefaultConnection());
    }

    @Test
    void addingAConnectionReplacesADefaultThatDoesntExistAnymore() throws Exception
    {
        saveConnection("Connection1", "first");
        setDefaultConnection("deleted");

        onEvent(new XObjectAddedEvent(), saveConnection("Connection2", "second"));

        assertEquals("first", getDefaultConnection());
    }

    @Test
    void addingAConnectionIgnoresTheConnectionsNotSavedByAnAdministrator() throws Exception
    {
        saveConnection("Connection1", "first", USER);

        onEvent(new XObjectAddedEvent(), saveConnection("Connection2", "second"));
        assertEquals("", getDefaultConnection());

        saveConnection("Connection3", "third");
        onEvent(new XObjectAddedEvent(), saveConnection("Connection4", "fourth"));
        assertEquals("", getDefaultConnection());
    }

    @Test
    void renamingTheDefaultConnectionUpdatesTheDefault() throws Exception
    {
        XWikiDocument first = saveConnection("Connection1", "first");
        XWikiDocument second = saveConnection("Connection2", "second");
        setDefaultConnection("first");

        onEvent(new XObjectUpdatedEvent(), rename(second, "other"));
        assertEquals("first", getDefaultConnection());

        onEvent(new XObjectUpdatedEvent(), rename(first, "renamed"));
        assertEquals("renamed", getDefaultConnection());
    }

    @Test
    void updatingTheDefaultConnectionWithoutRenamingItKeepsTheDefault() throws Exception
    {
        XWikiDocument first = saveConnection("Connection1", "first");
        setDefaultConnection("first");
        XWikiDocument configuration =
            this.oldcore.getSpyXWiki().getDocument(CONFIGURATION_DOCUMENT, this.oldcore.getXWikiContext());

        onEvent(new XObjectUpdatedEvent(), rename(first, "first"));

        assertEquals(configuration.getVersion(), this.oldcore.getSpyXWiki()
            .getDocument(CONFIGURATION_DOCUMENT, this.oldcore.getXWikiContext()).getVersion());
    }

    @Test
    void movingTheDefaultConnectionKeepsTheDefault() throws Exception
    {
        XWikiDocument first = saveConnection("Connection1", "first");
        saveConnection("Connection2", "second");
        setDefaultConnection("first");

        // A move deletes the page, then creates it under its new name.
        delete(first);
        onEvent(new XObjectAddedEvent(), saveConnection("Moved", "first"));

        assertEquals("first", getDefaultConnection());
    }

    @Test
    void addingAConnectionAfterDeletingTheDefaultOneMakesTheOnlyRemainingOneTheDefault() throws Exception
    {
        XWikiDocument first = saveConnection("Connection1", "first");
        saveConnection("Connection2", "second");
        setDefaultConnection("first");

        // The macros that don't set their instance parameter use the only remaining connection.
        delete(first);
        onEvent(new XObjectAddedEvent(), saveConnection("Connection3", "third"));

        assertEquals("second", getDefaultConnection());
    }

    @Test
    void renamingAConnectionNotSavedByAnAdministratorKeepsTheDefault() throws Exception
    {
        XWikiDocument ignored = saveConnection("Connection1", "first", USER);
        saveConnection("Connection2", "second");
        setDefaultConnection("first");

        onEvent(new XObjectUpdatedEvent(), rename(ignored, "renamed"));

        assertEquals("first", getDefaultConnection());
    }

    @Test
    void doesntCreateTheConfigurationPage() throws Exception
    {
        XWikiContext context = this.oldcore.getXWikiContext();
        this.oldcore.getSpyXWiki().deleteDocument(
            this.oldcore.getSpyXWiki().getDocument(CONFIGURATION_DOCUMENT, context), context);
        saveConnection("Connection1", "first");

        onEvent(new XObjectAddedEvent(), saveConnection("Connection2", "second"));

        assertTrue(this.oldcore.getSpyXWiki().getDocument(CONFIGURATION_DOCUMENT, context).isNew());
    }

    @Test
    void doesNothingForANonAdministrator() throws Exception
    {
        saveConnection("Connection1", "first");
        XWikiDocument second = saveConnection("Connection2", "second");
        this.oldcore.getXWikiContext().setUserReference(USER);

        onEvent(new XObjectAddedEvent(), second);
        assertEquals("", getDefaultConnection());

        this.oldcore.getXWikiContext().setUserReference(null);
        onEvent(new XObjectAddedEvent(), second);
        assertEquals("", getDefaultConnection());
    }

    @Test
    void doesNothingForARemoteEvent() throws Exception
    {
        saveConnection("Connection1", "first");
        XWikiDocument second = saveConnection("Connection2", "second");
        when(this.remoteObservationManagerContext.isRemoteState()).thenReturn(true);

        onEvent(new XObjectAddedEvent(), second);

        assertEquals("", getDefaultConnection());
    }

    @Test
    void logsTheFailures() throws Exception
    {
        saveConnection("Connection1", "first");
        XWikiDocument second = saveConnection("Connection2", "second");
        when(this.query.execute()).thenThrow(new QueryException("Database unavailable", null, null));

        onEvent(new XObjectAddedEvent(), second);

        assertEquals("", getDefaultConnection());
        assertEquals("Failed to update the default OpenProject connection after the change of the connection stored "
            + "on [xwiki:OpenProject.Code.OpenProjectConfigurations.Connection2]. Cause: [QueryException: Database "
            + "unavailable].", this.logCapture.getMessage(0));
    }
}
