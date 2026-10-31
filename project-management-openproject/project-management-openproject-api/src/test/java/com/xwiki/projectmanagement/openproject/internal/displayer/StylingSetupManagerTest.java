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
package com.xwiki.projectmanagement.openproject.internal.displayer;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.AdminAuthorChecker;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StylingSetupManager}.
 *
 * @version $Id$
 */
@ComponentTest
class StylingSetupManagerTest
{
    private static final String CONNECTION_NAME = "main";

    private static final String SERVER_URL = "https://openproject.example.org";

    private static final DocumentReference USER = new DocumentReference("wiki", "XWiki", "Admin");

    @InjectMockComponents
    private StylingSetupManager stylingSetupManager;

    @MockComponent
    private OpenProjectConfiguration openProjectConfiguration;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @MockComponent
    private DocumentReferenceResolver<EntityReference> documentReferenceResolver;

    @MockComponent
    @Named("document")
    private UserReferenceResolver<DocumentReference> userReferenceResolver;

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    @MockComponent
    private AdminAuthorChecker adminAuthorChecker;

    @Mock
    private XWikiContext xcontext;

    @Mock
    private XWiki xwiki;

    @Mock
    private XWikiDocument stylesDocument;

    @Mock
    private BaseObject styleObject;

    @Mock
    private OpenProjectApiClient client;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.contextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getWiki()).thenReturn(this.xwiki);
        when(this.xcontext.getUserReference()).thenReturn(USER);
        when(this.xcontext.getWikiReference()).thenReturn(USER.getWikiReference());
        when(this.xcontext.getWikiId()).thenReturn(USER.getWikiReference().getName());
        when(this.adminAuthorChecker.isAdmin(USER, USER.getWikiReference())).thenReturn(true);
        when(this.adminAuthorChecker.isLastSavedByAdmin(this.stylesDocument)).thenReturn(true);
        when(this.stylesDocument.clone()).thenReturn(this.stylesDocument);
        OpenProjectConnection connection =
            new OpenProjectConnection(CONNECTION_NAME, SERVER_URL, "id", "secret", "inst");
        when(this.openProjectConfiguration.getOpenProjectConnections())
            .thenReturn(Collections.singletonList(connection));
        DocumentReference stylesReference = new DocumentReference("wiki", "OpenProject", CONNECTION_NAME);
        when(this.documentReferenceResolver.resolve(any(EntityReference.class))).thenReturn(stylesReference);
        when(this.xwiki.getDocument(stylesReference, this.xcontext)).thenReturn(this.stylesDocument);
        when(this.stylesDocument.getXObject(any(EntityReference.class), anyString(), anyString(), anyBoolean()))
            .thenReturn(this.styleObject);
        when(this.stylesDocument.getAuthors()).thenReturn(mock(DocumentAuthors.class));

        Status status = new Status();
        status.setName("New");
        status.setColor("#ff0000");
        when(this.client.getStatuses()).thenReturn(new PaginatedResult<>(Collections.singletonList(status), 1, 1, 1));
        Type type = new Type();
        type.setName("Bug");
        type.setColor("#00ff00");
        when(this.client.getTypes()).thenReturn(new PaginatedResult<>(Collections.singletonList(type), 1, 1, 1));
    }

    @Test
    void setupInstanceStylesWhenAuthenticated() throws Exception
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.authenticated(this.client));

        this.stylingSetupManager.setupInstanceStyles();

        verify(this.styleObject).set(eq("code"), anyString(), same(this.xcontext));
        verify(this.styleObject).setIntValue("parse", 0);
        verify(this.xwiki).saveDocument(this.stylesDocument, "Updated styles.", this.xcontext);
        verify(this.xwiki, never()).deleteDocument(any(XWikiDocument.class), any(XWikiContext.class));
    }

    @Test
    void setupInstanceStylesWhenNotAdmin() throws Exception
    {
        when(this.adminAuthorChecker.isAdmin(USER, USER.getWikiReference())).thenReturn(false);
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.authenticated(this.client));

        this.stylingSetupManager.setupInstanceStyles();

        verifyNoInteractions(this.client);
        verify(this.xwiki, never()).saveDocument(any(XWikiDocument.class), anyString(), any(XWikiContext.class));
    }

    @Test
    void setupInstanceStylesWhenStylesPageWasNotSavedByAdmin() throws Exception
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.authenticated(this.client));
        when(this.adminAuthorChecker.isLastSavedByAdmin(this.stylesDocument)).thenReturn(false);
        XWikiDocument newStylesDocument = mock(XWikiDocument.class);
        when(newStylesDocument.clone()).thenReturn(newStylesDocument);
        when(newStylesDocument.isNew()).thenReturn(true);
        when(newStylesDocument.getAuthors()).thenReturn(mock(DocumentAuthors.class));
        BaseObject newStyleObject = mock(BaseObject.class);
        when(newStylesDocument.getXObject(any(EntityReference.class), eq(true), same(this.xcontext)))
            .thenReturn(newStyleObject);
        when(this.xwiki.getDocument(any(DocumentReference.class), same(this.xcontext)))
            .thenReturn(this.stylesDocument, newStylesDocument);

        this.stylingSetupManager.setupInstanceStyles();

        verify(this.xwiki).deleteDocument(this.stylesDocument, this.xcontext);
        verify(this.xwiki, never()).saveDocument(same(this.stylesDocument), anyString(), any(XWikiContext.class));
        verify(newStyleObject).set(eq("code"), anyString(), same(this.xcontext));
        verify(newStyleObject).setIntValue("parse", 0);
        verify(this.xwiki).saveDocument(newStylesDocument, "Updated styles.", this.xcontext);
        assertEquals("Replacing the styles page [wiki:OpenProject.main] since it wasn't last saved by a user with "
            + "administration right on the wiki.", this.logCapture.getMessage(0));
    }

    @Test
    void setupInstanceStylesWhenShowingPublicData() throws Exception
    {
        // The current user, an administrator, isn't connected to OpenProject but the connection shows its public
        // data: the statuses and types are read with the client of the public data.
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.anonymous(this.client));

        this.stylingSetupManager.setupInstanceStyles();

        verify(this.styleObject).set(eq("code"), anyString(), same(this.xcontext));
        verify(this.xwiki).saveDocument(this.stylesDocument, "Updated styles.", this.xcontext);
        verify(this.openProjectAccess, never()).getAnonymousClient(any());
    }

    @Test
    void setupInstanceStylesWhenNotConnected() throws Exception
    {
        // The current user isn't connected to OpenProject, and the connection doesn't show its public data: the
        // statuses and types, which are the same for every user, are read without credentials by the styling job.
        when(this.openProjectAccess.resolve(CONNECTION_NAME)).thenReturn(OpenProjectAccessResult.notConnected());
        when(this.openProjectAccess.getAnonymousClient(CONNECTION_NAME)).thenReturn(this.client);

        this.stylingSetupManager.setupInstanceStyles(true);

        verify(this.styleObject).set(eq("code"), anyString(), same(this.xcontext));
        verify(this.xwiki).saveDocument(this.stylesDocument, "Updated styles.", this.xcontext);
    }

    @Test
    void setupInstanceStylesWhenNotConnectedWithoutAnonymousFallback() throws Exception
    {
        // When rendering, the statuses and types aren't requested without credentials, since it isn't cached.
        when(this.openProjectAccess.resolve(CONNECTION_NAME)).thenReturn(OpenProjectAccessResult.notConnected());

        this.stylingSetupManager.setupInstanceStyles();

        verify(this.openProjectAccess, never()).getAnonymousClient(any());
        verify(this.xwiki, never()).saveDocument(any(XWikiDocument.class), anyString(), any(XWikiContext.class));
    }

    @Test
    void setupInstanceStylesWhenAnonymousRequestsAreRefused() throws Exception
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME)).thenReturn(OpenProjectAccessResult.notConnected());
        when(this.openProjectAccess.getAnonymousClient(CONNECTION_NAME)).thenReturn(this.client);
        // OpenProject gives the statuses to anonymous users, but not the types when no project is public.
        when(this.client.getTypes()).thenThrow(new WorkItemRetrievalException("Forbidden", 403));

        this.stylingSetupManager.setupInstanceStyles(true);

        verify(this.xwiki, never()).saveDocument(any(XWikiDocument.class), anyString(), any(XWikiContext.class));
        assertEquals("Skipping styling update for [main] since OpenProject refused to give its statuses or types to "
            + "anonymous users. Either connect the user [wiki:XWiki.Admin] to OpenProject, or allow anonymous users "
            + "to see them in the OpenProject administration (e.g. by disabling \"Login required\" and making a "
            + "project public).", this.logCapture.getMessage(0));
    }

    @Test
    void setupInstanceStylesWhenTheAccountOfTheUserIsRefused() throws Exception
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.authenticated(this.client));
        when(this.client.getStatuses()).thenThrow(new WorkItemRetrievalException("Unauthorized", 401));

        this.stylingSetupManager.setupInstanceStyles(true);

        verify(this.openProjectAccess, never()).getAnonymousClient(any());
        verify(this.xwiki, never()).saveDocument(any(XWikiDocument.class), anyString(), any(XWikiContext.class));
        assertEquals("Skipping styling update for [main] since OpenProject refused to give its statuses or types to "
            + "the user [wiki:XWiki.Admin]: their OpenProject account may have been disconnected or lack permissions. "
            + "Cause: [WorkItemRetrievalException: Unauthorized].", this.logCapture.getMessage(0));
    }

    @Test
    void setupInstanceStylesWhenTheStatusesCantBeRetrieved() throws Exception
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.authenticated(this.client));
        when(this.client.getStatuses()).thenThrow(new WorkItemRetrievalException("Server error", 500));

        this.stylingSetupManager.setupInstanceStyles(true);

        verify(this.xwiki, never()).saveDocument(any(XWikiDocument.class), anyString(), any(XWikiContext.class));
        assertEquals("Failed to update the styling for the configured OpenProject instance [main]. "
            + "Cause: [WorkItemRetrievalException: Server error].", this.logCapture.getMessage(0));
    }

    @Test
    void setupInstanceStylesWhenTheFailureHasNoStatusCode() throws Exception
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.authenticated(this.client));
        when(this.client.getStatuses())
            .thenThrow(new WorkItemRetrievalException("No status", new IllegalStateException("Network"), null));

        this.stylingSetupManager.setupInstanceStyles(true);

        verify(this.xwiki, never()).saveDocument(any(XWikiDocument.class), anyString(), any(XWikiContext.class));
        assertEquals("Failed to update the styling for the configured OpenProject instance [main]. "
            + "Cause: [IllegalStateException: Network].", this.logCapture.getMessage(0));
    }

    @Test
    void setupInstanceStylesWhenNotConfigured() throws Exception
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME)).thenReturn(OpenProjectAccessResult.notConfigured());

        this.stylingSetupManager.setupInstanceStyles(true);

        verify(this.openProjectAccess, never()).getAnonymousClient(any());
        verify(this.xwiki, never()).saveDocument(any(XWikiDocument.class), anyString(), any(XWikiContext.class));
    }

    @Test
    void setupInstanceStylesWhileAnotherUpdateIsRunning() throws Exception
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.authenticated(this.client));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PaginatedResult<Status> statuses = this.client.getStatuses();
        when(this.client.getStatuses()).thenAnswer(invocation -> {
            started.countDown();
            release.await(10, TimeUnit.SECONDS);
            return statuses;
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // The styling job.
            Future<?> job = executor.submit(() -> this.stylingSetupManager.setupInstanceStyles(true));
            assertTrue(started.await(10, TimeUnit.SECONDS));

            // A calendar rendered meanwhile doesn't wait for the job, and doesn't update the styles itself.
            this.stylingSetupManager.setupInstanceStyles();

            verify(this.openProjectAccess).resolve(CONNECTION_NAME);
            release.countDown();
            job.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        verify(this.xwiki).saveDocument(this.stylesDocument, "Updated styles.", this.xcontext);

        // Once the job is done, the styles can be updated again.
        this.stylingSetupManager.setupInstanceStyles();

        verify(this.openProjectAccess, times(2)).resolve(CONNECTION_NAME);
    }
}
