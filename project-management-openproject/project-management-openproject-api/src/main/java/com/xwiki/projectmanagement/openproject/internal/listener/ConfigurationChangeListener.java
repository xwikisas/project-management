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
import com.xpn.xwiki.internal.event.XObjectAddedEvent;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.BaseObjectReference;
import com.xpn.xwiki.plugin.scheduler.JobState;
import com.xpn.xwiki.plugin.scheduler.SchedulerPlugin;

/**
 * Event listener that watches changes on OpenProject configuration documents and performs various.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("com.xwiki.projectmanagement.openproject.internal.listener.ConfigurationChangeListener")
public class ConfigurationChangeListener extends AbstractEventListener
{
    private static final String SPACE_PROJECT_MANAGEMENT = "ProjectManagement";

    private static final EntityReference CLASS_OPEN_PROJECT =
//        new LocalDocumentReference(SPACE_PROJECT_MANAGEMENT, "OpenProjectConnectionClass");
        BaseObjectReference.any("ProjectManagement.OpenProjectConnectionClass");

    private static final EntityReference DOC_SCHEDULER_JOB =
        new LocalDocumentReference(Arrays.asList(SPACE_PROJECT_MANAGEMENT, "Code"), "StylingSetupJob");

    @Inject
    private Logger logger;

    @Inject
    @Named("compact")
    private EntityReferenceSerializer<String> serializer;

    /**
     * Default constructor.
     */
    public ConfigurationChangeListener()
    {
        super(ConfigurationChangeListener.class.getName(),
            Arrays.asList(new XObjectAddedEvent(CLASS_OPEN_PROJECT)));
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        XWikiContext xcontext = (XWikiContext) data;
        XWikiDocument document = (XWikiDocument) source;

        SchedulerPlugin scheduler = (SchedulerPlugin) xcontext.getWiki().getPluginManager().getPlugin("scheduler");
        try {
            XWikiDocument jobDoc = xcontext.getWiki().getDocument(DOC_SCHEDULER_JOB, xcontext);
            BaseObject job = jobDoc.getXObject(SchedulerPlugin.XWIKI_JOB_CLASSREFERENCE);
            JobState jobState = scheduler.getJobStatus(job, xcontext);

            if (jobState.getQuartzState().equals(Trigger.TriggerState.NORMAL)) {
                scheduler.unscheduleJob(job, xcontext);
                job.setStringValue("contextUser", serializer.serialize(xcontext.getUserReference()));
                xcontext.getWiki().saveDocument(jobDoc, String.format("Updated context user to [%s].",
                    xcontext.getUserReference()), xcontext);
                scheduler.scheduleJob(job, xcontext);
            } else if (jobState.getQuartzState().equals(Trigger.TriggerState.NONE)) {
                scheduler.scheduleJob(job, xcontext);
            }
        } catch (XWikiException | SchedulerException e) {
            logger.warn("Failed to set the context user [{}] for the job [{}] and reschedule it. Cause: [{}].",
                xcontext.getUserReference(), DOC_SCHEDULER_JOB, ExceptionUtils.getRootCauseMessage(e));
        }
    }
}
