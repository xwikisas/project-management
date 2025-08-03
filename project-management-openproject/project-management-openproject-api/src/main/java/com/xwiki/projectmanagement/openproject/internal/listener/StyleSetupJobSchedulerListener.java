package com.xwiki.projectmanagement.openproject.internal.listener;

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

import java.util.Arrays;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.internal.event.XObjectUpdatedEvent;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.BaseObjectReference;
import com.xpn.xwiki.plugin.scheduler.JobState;
import com.xpn.xwiki.plugin.scheduler.SchedulerPlugin;
import com.xwiki.projectmanagement.openproject.internal.displayer.StylingSetupManager;

/**
 * Event listener that watches changes on OpenProject configuration documents and schedules the
 * {@link StylingSetupManager} with the context user equal to the one that created the Open Project configuration. This
 * assures us that the job will execute with a user that is active in the wiki and has logged in with OpenProject
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("com.xwiki.projectmanagement.openproject.internal.listener.StyleSetupJobSchedulerListener")
public class StyleSetupJobSchedulerListener extends AbstractEventListener
{
    private static final String OPEN_PROJECT = "OpenProject";

    private static final EntityReference CLASS_OPEN_PROJECT =
        BaseObjectReference.any("OpenProject.Code.OpenProjectConnectionClass");

    private static final EntityReference DOC_SCHEDULER_JOB =
        new LocalDocumentReference(Arrays.asList(OPEN_PROJECT, "Code"), "StylingSetupJob");

    private static final String OP_API_ID = "com.xwiki.projectmanagement:project-management-openproject-api";

    private static final String PLUGIN_SCHEDULER = "scheduler";

    private static final String JOB_CONTEXT_USER = "contextUser";

    @Inject
    private Logger logger;

    @Inject
    @Named("compact")
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private Provider<XWikiContext> contextProvider;

    /**
     * Default constructor.
     */
    public StyleSetupJobSchedulerListener()
    {
        super(StyleSetupJobSchedulerListener.class.getName(),
            Arrays.asList(new XObjectUpdatedEvent(CLASS_OPEN_PROJECT)));
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        maybeChangeJobUserAndReschedule((XWikiDocument) source, (XWikiContext) data);
    }

    private void maybeChangeJobUserAndReschedule(XWikiDocument source, XWikiContext data)
    {

        SchedulerPlugin scheduler = (SchedulerPlugin) data.getWiki().getPluginManager().getPlugin(PLUGIN_SCHEDULER);
        try {
            XWikiDocument jobDoc = data.getWiki().getDocument(DOC_SCHEDULER_JOB, data);
            BaseObject job = jobDoc.getXObject(SchedulerPlugin.XWIKI_JOB_CLASSREFERENCE);
            JobState jobState = scheduler.getJobStatus(job, data);

            boolean shouldSchedule = false;
            if (jobState.getQuartzState().equals(Trigger.TriggerState.NORMAL)) {
                shouldSchedule = true;
                scheduler.unscheduleJob(job, data);
            } else if (jobState.getQuartzState().equals(Trigger.TriggerState.NONE)) {
                shouldSchedule = true;
            }
            if (shouldSchedule) {
                String currentUser = serializer.serialize(data.getUserReference());
                // Skip if job user is already set to this one.
                if (currentUser.equals(job.getStringValue(JOB_CONTEXT_USER))) {
                    return;
                }
                job.setStringValue(JOB_CONTEXT_USER, currentUser);
                data.getWiki().saveDocument(jobDoc, String.format("Updated context user to [%s].",
                    data.getUserReference()), data);
                scheduler.scheduleJob(job, data);
            }
        } catch (XWikiException | SchedulerException e) {
            logger.warn("Failed to set the context user [{}] for the job [{}] and reschedule it. Cause: [{}].",
                data.getUserReference(), DOC_SCHEDULER_JOB, ExceptionUtils.getRootCauseMessage(e));
        }
    }
}
