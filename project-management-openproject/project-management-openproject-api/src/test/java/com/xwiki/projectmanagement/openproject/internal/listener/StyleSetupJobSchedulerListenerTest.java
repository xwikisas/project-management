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

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.quartz.Trigger;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.internal.event.XObjectAddedEvent;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.plugin.XWikiPluginManager;
import com.xpn.xwiki.plugin.scheduler.JobState;
import com.xpn.xwiki.plugin.scheduler.SchedulerPlugin;
import com.xwiki.projectmanagement.openproject.internal.AdminAuthorChecker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
@ComponentTest
class StyleSetupJobSchedulerListenerTest
{
    private static final WikiReference WIKI = new WikiReference("xwiki");

    private static final DocumentReference CONNECTION_AUTHOR = new DocumentReference("xwiki", "XWiki", "Author");

    @InjectMockComponents
    private StyleSetupJobSchedulerListener listener;

    @MockComponent
    private AdminAuthorChecker adminAuthorChecker;

    @MockComponent
    @Named("compact")
    private EntityReferenceSerializer<String> serializer;

    @MockComponent
    @Named("document")
    private UserReferenceResolver<DocumentReference> userReferenceResolver;

    @Mock
    private XWikiContext context;

    @Mock
    private XWiki xwiki;

    @Mock
    private SchedulerPlugin scheduler;

    @Mock
    private XWikiDocument connectionDocument;

    @Mock
    private XWikiDocument jobDocument;

    @Mock
    private BaseObject job;

    @Mock
    private DocumentAuthors jobAuthors;

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.context.getWiki()).thenReturn(this.xwiki);
        when(this.context.getWikiReference()).thenReturn(WIKI);
        when(this.context.getUserReference()).thenReturn(CONNECTION_AUTHOR);
        XWikiPluginManager pluginManager = mock(XWikiPluginManager.class);
        when(this.xwiki.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("scheduler")).thenReturn(this.scheduler);
        when(this.xwiki.getDocument(any(EntityReference.class), any(XWikiContext.class)))
            .thenReturn(this.jobDocument);
        when(this.jobDocument.clone()).thenReturn(this.jobDocument);
        when(this.jobDocument.getXObject(SchedulerPlugin.XWIKI_JOB_CLASSREFERENCE)).thenReturn(this.job);
        when(this.jobDocument.getAuthors()).thenReturn(this.jobAuthors);
        when(this.job.getStringValue("contextUser")).thenReturn("XWiki.Admin");
        when(this.scheduler.getJobStatus(this.job, this.context))
            .thenReturn(new JobState(Trigger.TriggerState.NORMAL));

        when(this.connectionDocument.getDocumentReference())
            .thenReturn(new DocumentReference("xwiki", "Space", "Page"));
        when(this.adminAuthorChecker.getMetadataAuthor(this.connectionDocument)).thenReturn(CONNECTION_AUTHOR);
        when(this.serializer.serialize(CONNECTION_AUTHOR)).thenReturn("XWiki.Author");
    }

    @Test
    void onEventWhenConnectionAuthorIsAdmin() throws Exception
    {
        when(this.adminAuthorChecker.isAdmin(CONNECTION_AUTHOR, WIKI)).thenReturn(true);
        UserReference authorReference = mock(UserReference.class);
        when(this.userReferenceResolver.resolve(CONNECTION_AUTHOR)).thenReturn(authorReference);

        this.listener.onEvent(new XObjectAddedEvent(), this.connectionDocument, this.context);

        verify(this.scheduler).unscheduleJob(this.job, this.context);
        verify(this.job).setStringValue("contextUser", "XWiki.Author");
        verify(this.jobAuthors).setOriginalMetadataAuthor(authorReference);
        verify(this.jobAuthors, never()).setEffectiveMetadataAuthor(any());
        verify(this.xwiki).saveDocument(this.jobDocument, "Updated context user to [xwiki:XWiki.Author].",
            this.context);
        verify(this.scheduler).scheduleJob(this.job, this.context);
    }

    @Test
    void onEventWhenConnectionAuthorIsNotAdmin() throws Exception
    {
        when(this.adminAuthorChecker.isAdmin(CONNECTION_AUTHOR, WIKI)).thenReturn(false);

        this.listener.onEvent(new XObjectAddedEvent(), this.connectionDocument, this.context);

        verifyNoInteractions(this.scheduler);
        verify(this.job, never()).setStringValue(anyString(), anyString());
        verify(this.xwiki, never()).saveDocument(any(XWikiDocument.class), anyString(), any(XWikiContext.class));
    }
}
