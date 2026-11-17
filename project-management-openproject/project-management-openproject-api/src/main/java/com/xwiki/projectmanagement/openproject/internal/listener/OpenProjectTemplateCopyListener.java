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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.bridge.event.DocumentCreatingEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.job.Job;
import org.xwiki.job.JobContext;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.observation.remote.RemoteObservationManagerContext;
import org.xwiki.refactoring.job.CreateRequest;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Makes the pages copied from the OpenProject template space visible, before they are saved. The template pages are
 * hidden, as technical pages, and the create job that creates a space from the template copies them, and their
 * translations, with their hidden flag.
 * <p>
 * Only the pages created under the target of a create job using the OpenProject template are concerned: a page that the
 * user hid keeps its hidden flag when it's copied, renamed or imported later.
 *
 * @version $Id$
 * @since 1.4.0
 */
@Component
@Named(OpenProjectTemplateCopyListener.NAME)
@Singleton
public class OpenProjectTemplateCopyListener extends AbstractEventListener
{
    /**
     * The name of the listener.
     */
    public static final String NAME = "com.xwiki.projectmanagement.openproject.internal.listener."
        + "OpenProjectTemplateCopyListener";

    private static final LocalDocumentReference TEMPLATE_HOME =
        new LocalDocumentReference(Arrays.asList("OpenProject", "Code", "Template"), "WebHome");

    @Inject
    private JobContext jobContext;

    @Inject
    private RemoteObservationManagerContext remoteObservationManagerContext;

    /**
     * Default constructor.
     */
    public OpenProjectTemplateCopyListener()
    {
        super(NAME, List.of(new DocumentCreatingEvent()));
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        XWikiDocument document = (XWikiDocument) source;
        if (document.isHidden() && !this.remoteObservationManagerContext.isRemoteState()
            && isCopyOfTemplate(document.getDocumentReference()))
        {
            document.setHidden(false);
        }
    }

    private boolean isCopyOfTemplate(DocumentReference reference)
    {
        Job job = this.jobContext.getCurrentJob();
        if (job == null || !(job.getRequest() instanceof CreateRequest)) {
            return false;
        }
        CreateRequest request = (CreateRequest) job.getRequest();
        DocumentReference templateHome = new DocumentReference(TEMPLATE_HOME, reference.getWikiReference());
        if (!templateHome.equals(request.getTemplateReference())) {
            return false;
        }
        // Only the pages created under the target of the job: other pages may be created while the job runs.
        return request.getEntityReferences().stream().anyMatch(target -> isUnder(reference, target));
    }

    private boolean isUnder(DocumentReference reference, EntityReference target)
    {
        // The pages of the space whose home page is the target.
        EntityReference targetSpace = target.getType() == EntityType.DOCUMENT ? target.getParent() : target;
        return targetSpace != null && reference.hasParent(targetSpace);
    }
}
