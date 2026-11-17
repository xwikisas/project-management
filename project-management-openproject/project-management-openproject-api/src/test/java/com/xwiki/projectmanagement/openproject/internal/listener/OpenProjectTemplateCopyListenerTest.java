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
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.xwiki.bridge.event.DocumentCreatingEvent;
import org.xwiki.job.Job;
import org.xwiki.job.JobContext;
import org.xwiki.job.Request;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.observation.remote.RemoteObservationManagerContext;
import org.xwiki.refactoring.job.CreateRequest;
import org.xwiki.refactoring.job.MoveRequest;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectTemplateCopyListener}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectTemplateCopyListenerTest
{
    private static final DocumentReference TEMPLATE =
        new DocumentReference("wiki", Arrays.asList("OpenProject", "Code", "Template"), "WebHome");

    private static final DocumentReference TEMPLATE_CHILD = new DocumentReference("wiki",
        Arrays.asList("OpenProject", "Code", "Template", "Project Specifications"), "WebHome");

    private static final DocumentReference TARGET = new DocumentReference("wiki", "Space", "WebHome");

    private static final DocumentReference COPY =
        new DocumentReference("wiki", Arrays.asList("Space", "Project Specifications"), "WebHome");

    @MockComponent
    private JobContext jobContext;

    @MockComponent
    private RemoteObservationManagerContext remoteObservationManagerContext;

    @InjectMockComponents
    private OpenProjectTemplateCopyListener listener;

    @Test
    void displaysTheCopiesOfTheTemplatePages()
    {
        runningCreateJob(TEMPLATE);

        assertFalse(onCreating(COPY).isHidden());
    }

    @Test
    void displaysTheTranslationsOfTheCopiesOfTheTemplatePages()
    {
        runningCreateJob(TEMPLATE);

        assertFalse(onCreating(new DocumentReference(COPY, Locale.FRENCH)).isHidden());
    }

    @Test
    void keepsThePagesCreatedElsewhereDuringTheJobHidden()
    {
        runningCreateJob(TEMPLATE);

        assertTrue(onCreating(new DocumentReference("wiki", "Other", "Page")).isHidden());
        assertTrue(onCreating(TEMPLATE_CHILD).isHidden());
    }

    @Test
    void keepsThePagesCreatedFromAnotherTemplateHidden()
    {
        runningCreateJob(new DocumentReference("wiki", "Other", "Template"));

        assertTrue(onCreating(COPY).isHidden());
    }

    @Test
    void keepsThePagesCreatedFromTheTemplateOfAnotherWikiHidden()
    {
        runningCreateJob(TEMPLATE.replaceParent(TEMPLATE.getWikiReference(), new WikiReference("other")));

        assertTrue(onCreating(COPY).isHidden());
    }

    @Test
    void keepsThePagesCopiedOrRenamedLaterHidden()
    {
        running(new MoveRequest());

        assertTrue(onCreating(COPY).isHidden());
    }

    @Test
    void keepsThePagesCreatedOutsideOfAJobHidden()
    {
        assertTrue(onCreating(COPY).isHidden());
    }

    @Test
    void ignoresTheRemoteEvents()
    {
        runningCreateJob(TEMPLATE);
        when(this.remoteObservationManagerContext.isRemoteState()).thenReturn(true);

        assertTrue(onCreating(COPY).isHidden());
    }

    private void runningCreateJob(DocumentReference template)
    {
        CreateRequest request = new CreateRequest();
        request.setEntityReferences(List.of(TARGET));
        request.setTemplateReference(template);
        running(request);
    }

    private void running(Request request)
    {
        Job job = mock(Job.class);
        when(job.getRequest()).thenReturn(request);
        when(this.jobContext.getCurrentJob()).thenReturn(job);
    }

    private XWikiDocument onCreating(DocumentReference reference)
    {
        XWikiDocument document = new XWikiDocument(reference);
        document.setHidden(true);
        this.listener.onEvent(new DocumentCreatingEvent(reference), document, null);
        return document;
    }
}
