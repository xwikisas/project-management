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

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.quartz.Trigger;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.mockito.MockitoComponentManager;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.internal.event.XObjectAddedEvent;
import com.xpn.xwiki.internal.event.XObjectUpdatedEvent;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.plugin.XWikiPluginManager;
import com.xpn.xwiki.plugin.scheduler.JobState;
import com.xpn.xwiki.plugin.scheduler.SchedulerPlugin;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xwiki.projectmanagement.openproject.internal.AdminAuthorChecker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StyleSetupJobSchedulerListener}.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
@ComponentList(AdminAuthorChecker.class)
class StyleSetupJobSchedulerListenerTest
{
    private static final DocumentReference JOB_DOCUMENT =
        new DocumentReference("xwiki", Arrays.asList("OpenProject", "Code"), "StylingSetupJob");

    private static final DocumentReference JOB_AUTHOR = new DocumentReference("xwiki", "XWiki", "JobAuthor");

    private static final DocumentReference CONNECTION_AUTHOR = new DocumentReference("xwiki", "XWiki", "Author");

    private static final DocumentReference CURRENT_USER = new DocumentReference("xwiki", "XWiki", "CurrentUser");

    private static final WikiReference MAIN_WIKI = new WikiReference("xwiki");

    private static final WikiReference SUBWIKI = new WikiReference("subwiki");

    private static final String CONTEXT_USER = "contextUser";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @InjectMockComponents
    private StyleSetupJobSchedulerListener listener;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private SchedulerPlugin scheduler;

    private UserReferenceResolver<DocumentReference> userResolver;

    @BeforeEach
    void setUp() throws Exception
    {
        this.userResolver = this.componentManager.getInstance(UserReferenceResolver.TYPE_DOCUMENT_REFERENCE,
            "document");

        this.scheduler = mock(SchedulerPlugin.class);
        XWikiPluginManager pluginManager = mock(XWikiPluginManager.class);
        when(pluginManager.getPlugin("scheduler")).thenReturn(this.scheduler);
        doReturn(pluginManager).when(this.oldcore.getSpyXWiki()).getPluginManager();
        when(this.scheduler.getJobStatus(any(), any())).thenReturn(new JobState(Trigger.TriggerState.NORMAL));

        this.oldcore.getXWikiContext().setUserReference(CURRENT_USER);
        createJob(JOB_DOCUMENT, "XWiki.JobAuthor");
    }

    private void createJob(DocumentReference reference, String contextUser) throws XWikiException
    {
        XWikiContext context = this.oldcore.getXWikiContext();
        XWikiDocument jobDocument = new XWikiDocument(reference);
        jobDocument.getAuthors().setEffectiveMetadataAuthor(this.userResolver.resolve(JOB_AUTHOR));
        jobDocument.getAuthors().setOriginalMetadataAuthor(this.userResolver.resolve(JOB_AUTHOR));
        BaseObject job = jobDocument.newXObject(SchedulerPlugin.XWIKI_JOB_CLASSREFERENCE, context);
        job.setStringValue(CONTEXT_USER, contextUser);
        this.oldcore.getSpyXWiki().saveDocument(jobDocument, context);
    }

    private void setJobState(Trigger.TriggerState state) throws Exception
    {
        when(this.scheduler.getJobStatus(any(), any())).thenReturn(new JobState(state));
    }

    private XWikiDocument connectionSavedBy(DocumentReference author, String wiki)
    {
        XWikiDocument connection = new XWikiDocument(new DocumentReference(wiki, "OpenProject", "Connection"));
        connection.getAuthors().setEffectiveMetadataAuthor(this.userResolver.resolve(author));
        return connection;
    }

    private void setAdmin(DocumentReference user, WikiReference wiki)
    {
        when(this.oldcore.getMockAuthorizationManager().hasAccess(Right.ADMIN, user, wiki)).thenReturn(true);
    }

    private XWikiDocument getJobDocument() throws XWikiException
    {
        return this.oldcore.getSpyXWiki().getDocument(JOB_DOCUMENT, this.oldcore.getXWikiContext());
    }

    @Test
    void runsTheJobAsTheAdminWhoSavedTheConnection() throws Exception
    {
        setAdmin(CONNECTION_AUTHOR, MAIN_WIKI);

        this.listener.onEvent(new XObjectUpdatedEvent(), connectionSavedBy(CONNECTION_AUTHOR, "xwiki"),
            this.oldcore.getXWikiContext());

        XWikiDocument jobDocument = getJobDocument();
        BaseObject job = jobDocument.getXObject(SchedulerPlugin.XWIKI_JOB_CLASSREFERENCE);
        assertEquals("XWiki.Author", job.getStringValue(CONTEXT_USER));
        assertEquals(this.userResolver.resolve(CONNECTION_AUTHOR),
            jobDocument.getAuthors().getOriginalMetadataAuthor());
        // The job script keeps running with the rights of its own author.
        assertEquals(this.userResolver.resolve(JOB_AUTHOR), jobDocument.getAuthors().getEffectiveMetadataAuthor());
        // Rescheduling is an outgoing command to the scheduler, not observable on the stored document.
        verify(this.scheduler).unscheduleJob(any(), any());
        verify(this.scheduler).scheduleJob(any(), any());
        // The styles of the saved connection are generated right away, asynchronously.
        verify(this.scheduler).triggerJob(any(), any());
    }

    @Test
    void triggersTheJobWhenItAlreadyRunsAsTheAdminWhoSavedTheConnection() throws Exception
    {
        setAdmin(CONNECTION_AUTHOR, MAIN_WIKI);
        createJob(JOB_DOCUMENT, "XWiki.Author");

        this.listener.onEvent(new XObjectAddedEvent(), connectionSavedBy(CONNECTION_AUTHOR, "xwiki"),
            this.oldcore.getXWikiContext());

        assertEquals(this.userResolver.resolve(JOB_AUTHOR), getJobDocument().getAuthors().getOriginalMetadataAuthor());
        verify(this.scheduler).triggerJob(any(), any());
        verify(this.scheduler, never()).unscheduleJob(any(), any());
        verify(this.scheduler, never()).scheduleJob(any(), any());
    }

    @Test
    void schedulesAndTriggersAnUnscheduledJob() throws Exception
    {
        setAdmin(CONNECTION_AUTHOR, MAIN_WIKI);
        createJob(JOB_DOCUMENT, "XWiki.Author");
        setJobState(Trigger.TriggerState.NONE);

        this.listener.onEvent(new XObjectAddedEvent(), connectionSavedBy(CONNECTION_AUTHOR, "xwiki"),
            this.oldcore.getXWikiContext());

        assertEquals(this.userResolver.resolve(CONNECTION_AUTHOR),
            getJobDocument().getAuthors().getOriginalMetadataAuthor());
        verify(this.scheduler, never()).unscheduleJob(any(), any());
        verify(this.scheduler).scheduleJob(any(), any());
        verify(this.scheduler).triggerJob(any(), any());
    }

    @Test
    void leavesAPausedJobAlone() throws Exception
    {
        setAdmin(CONNECTION_AUTHOR, MAIN_WIKI);
        setJobState(Trigger.TriggerState.PAUSED);

        this.listener.onEvent(new XObjectUpdatedEvent(), connectionSavedBy(CONNECTION_AUTHOR, "xwiki"),
            this.oldcore.getXWikiContext());

        assertJobFields("XWiki.JobAuthor", JOB_AUTHOR);
        verify(this.scheduler, never()).scheduleJob(any(), any());
        verify(this.scheduler, never()).triggerJob(any(), any());
    }

    @Test
    void usesTheJobOfTheConnectionWiki() throws Exception
    {
        setAdmin(CONNECTION_AUTHOR, SUBWIKI);
        DocumentReference subwikiJob = JOB_DOCUMENT.setWikiReference(SUBWIKI);
        createJob(subwikiJob, "XWiki.JobAuthor");
        XWikiContext context = this.oldcore.getXWikiContext();

        this.listener.onEvent(new XObjectUpdatedEvent(), connectionSavedBy(CONNECTION_AUTHOR, SUBWIKI.getName()),
            context);

        XWikiDocument jobDocument = this.oldcore.getSpyXWiki().getDocument(subwikiJob, context);
        assertEquals("xwiki:XWiki.Author",
            jobDocument.getXObject(SchedulerPlugin.XWIKI_JOB_CLASSREFERENCE).getStringValue(CONTEXT_USER));
        assertEquals(this.userResolver.resolve(CONNECTION_AUTHOR),
            jobDocument.getAuthors().getOriginalMetadataAuthor());
        // The job of the current wiki is left alone, and the current wiki is restored.
        assertJobFields("XWiki.JobAuthor", JOB_AUTHOR);
        assertEquals(MAIN_WIKI, context.getWikiReference());
        verify(this.scheduler).triggerJob(any(), any());
    }

    @Test
    void ignoresAWikiWithoutTheJob() throws Exception
    {
        setAdmin(CONNECTION_AUTHOR, SUBWIKI);

        this.listener.onEvent(new XObjectUpdatedEvent(), connectionSavedBy(CONNECTION_AUTHOR, SUBWIKI.getName()),
            this.oldcore.getXWikiContext());

        assertJobUnchanged();
    }

    @Test
    void ignoresConnectionSavedByNonAdmin() throws Exception
    {
        this.listener.onEvent(new XObjectUpdatedEvent(), connectionSavedBy(CONNECTION_AUTHOR, "xwiki"),
            this.oldcore.getXWikiContext());

        assertJobUnchanged();
    }

    @Test
    void ignoresConnectionSavedByNonAdminOfTheConnectionWiki() throws Exception
    {
        setAdmin(CONNECTION_AUTHOR, MAIN_WIKI);

        this.listener.onEvent(new XObjectUpdatedEvent(), connectionSavedBy(CONNECTION_AUTHOR, SUBWIKI.getName()),
            this.oldcore.getXWikiContext());

        assertJobUnchanged();
    }

    @Test
    void leavesTheCachedJobDocumentUnchangedWhenSaveFails() throws Exception
    {
        setAdmin(CONNECTION_AUTHOR, MAIN_WIKI);
        doThrow(new XWikiException()).when(this.oldcore.getSpyXWiki()).saveDocument(any(XWikiDocument.class),
            anyString(), any(XWikiContext.class));

        this.listener.onEvent(new XObjectUpdatedEvent(), connectionSavedBy(CONNECTION_AUTHOR, "xwiki"),
            this.oldcore.getXWikiContext());

        assertJobFields("XWiki.JobAuthor", JOB_AUTHOR);
        assertEquals("Failed to set the context user [xwiki:XWiki.Author] for the job "
            + "[OpenProject.Code.StylingSetupJob] and trigger it. Cause: [XWikiException: Error number 0 in 0].",
            this.logCapture.getMessage(0));
        verify(this.scheduler, never()).triggerJob(any(), any());
    }

    private void assertJobFields(String contextUser, DocumentReference originalMetadataAuthor) throws XWikiException
    {
        XWikiDocument jobDocument = getJobDocument();
        assertEquals(contextUser,
            jobDocument.getXObject(SchedulerPlugin.XWIKI_JOB_CLASSREFERENCE).getStringValue(CONTEXT_USER));
        assertEquals(this.userResolver.resolve(originalMetadataAuthor),
            jobDocument.getAuthors().getOriginalMetadataAuthor());
    }

    private void assertJobUnchanged() throws XWikiException
    {
        assertJobFields("XWiki.JobAuthor", JOB_AUTHOR);
        // A refused connection must neither reschedule nor trigger the job.
        verifyNoInteractions(this.scheduler);
    }
}
