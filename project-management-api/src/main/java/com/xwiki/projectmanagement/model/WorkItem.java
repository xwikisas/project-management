package com.xwiki.projectmanagement.model;

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

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Describes an issue, a ticket, work package or some item coming from a project management tool.
 *
 * @version $Id$
 * @since 1.0
 */
public class WorkItem
{
    private String type;

    private Linkable<String> identifier;

    private Linkable<String> summary;

    private String description;

    private Date startDate;

    private Date dueDate;

    private int progress;

    private Date creationDate;

    private Date updateDate;

    private Linkable<String> creator;

    private List<Linkable<String>> assignees;

    private String priority;

    private Linkable<String> project;

    private String status;

    private Linkable<String> reporter;

    private String resolution;

    private boolean resolved;

    private List<String> labels;

    private Date closeDate;

    private Linkable<String> milestones;

    private Linkable<String> closedBy;

    private Map<String, Object> meta;

    /**
     * @return the type of the work item. i.e. task, bug, epic, etc.
     */
    public String getType()
    {
        return type;
    }

    /**
     * @param type see {@link #getType()}.
     */
    public void setType(String type)
    {
        this.type = type;
    }

    /**
     * @return the identifier for this work item together with a link to its location. For example, Open project and
     *     GitHub identify work packages numerically: '1001', Jira using a key: 'XWIKI-1001'.
     */
    public Linkable<String> getIdentifier()
    {
        return identifier;
    }

    /**
     * @param identifier see {@link #getIdentifier()}.
     */
    public void setIdentifier(Linkable<String> identifier)
    {
        this.identifier = identifier;
    }

    /**
     * @return a text summarizing this work item together with a link to its location. On some platform, this property
     *     translates to the issue title.
     */
    public Linkable<String> getSummary()
    {
        return summary;
    }

    /**
     * @param summary see {@link #getSummary()}.
     */
    public void setSummary(Linkable<String> summary)
    {
        this.summary = summary;
    }

    /**
     * @return a text that describes this work item.
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * @param description see {@link #getDescription()}.
     */
    public void setDescription(String description)
    {
        this.description = description;
    }

    /**
     * @return the date when this work item was marked, by some user/entity, as started.
     */
    public Date getStartDate()
    {
        return startDate;
    }

    /**
     * @param startDate see {@link #getStartDate()}.
     */
    public void setStartDate(Date startDate)
    {
        this.startDate = startDate;
    }

    /**
     * @return the date when this work item is expected to be finished, set as resolved.
     */
    public Date getDueDate()
    {
        return dueDate;
    }

    /**
     * @param dueDate see {@link #getDueDate()}.
     */
    public void setDueDate(Date dueDate)
    {
        this.dueDate = dueDate;
    }

    /**
     * @return the progress, as a percentage, that has been done for this work item.
     */
    public int getProgress()
    {
        return progress;
    }

    /**
     * @param progress see {@link #getProgress()}.
     */
    public void setProgress(int progress)
    {
        this.progress = progress;
    }

    /**
     * @return the date when this work item was created.
     */
    public Date getCreationDate()
    {
        return creationDate;
    }

    /**
     * @param creationDate see {@link #getCreationDate()}.
     */
    public void setCreationDate(Date creationDate)
    {
        this.creationDate = creationDate;
    }

    /**
     * @return the last date when this work item was updated or modified.
     */
    public Date getUpdateDate()
    {
        return updateDate;
    }

    /**
     * @param updateDate see {@link #getUpdateDate()}.
     */
    public void setUpdateDate(Date updateDate)
    {
        this.updateDate = updateDate;
    }

    /**
     * @return a tuple identifying the creator of this work item. The tuple contains the display name of the user and a
     *     link to their profile location.
     */
    public Linkable<String> getCreator()
    {
        return creator;
    }

    /**
     * @param creator see {@link #getCreator()}.
     */
    public void setCreator(Linkable<String> creator)
    {
        this.creator = creator;
    }

    /**
     * @return a list of users assigned to this work item. Each user is a tuple containing the display name and the
     *     location to their profile location.
     */
    public List<Linkable<String>> getAssignees()
    {
        return assignees;
    }

    /**
     * @param assignees see {@link #getAssignees()}.
     */
    public void setAssignees(List<Linkable<String>> assignees)
    {
        this.assignees = assignees;
    }

    /**
     * @return the priority that was assigned to this work item. i.e. "minor", "critical", "major", etc.
     */
    public String getPriority()
    {
        return priority;
    }

    /**
     * @param priority see {@link #getPriority()}.
     */
    public void setPriority(String priority)
    {
        this.priority = priority;
    }

    /**
     * @return a tuple identifying the project where this work item belongs. The tuple contains the display name of the
     *     project and a link to its location.
     */
    public Linkable<String> getProject()
    {
        return project;
    }

    /**
     * @param project see {@link #getProject()}.
     */
    public void setProject(Linkable<String> project)
    {
        this.project = project;
    }

    /**
     * @return the current status of this work item.
     */
    public String getStatus()
    {
        return status;
    }

    /**
     * @param status see {@link #getStatus()}.
     */
    public void setStatus(String status)
    {
        this.status = status;
    }

    /**
     * @return a tuple representing the user thanks to whom this work item was created. If this work item represents a
     *     bug, it could be that somebody reported it and someone else created the ticket.
     */
    public Linkable<String> getReporter()
    {
        return reporter;
    }

    /**
     * @param reporter see {@link #getReporter()}.
     */
    public void setReporter(Linkable<String> reporter)
    {
        this.reporter = reporter;
    }

    /**
     * @return the resolution for this work item. A work item can be marked as resolved but it might need additional
     *     information on how it was closed. i.e. "duplicate", "won't resolve", "canceled" etc.
     */
    public String getResolution()
    {
        return resolution;
    }

    /**
     * @param resolution see {@link #getResolution()}.
     */
    public void setResolution(String resolution)
    {
        this.resolution = resolution;
    }

    /**
     * @return denotes whether this work item is closed/resolved or not.
     */
    public boolean isResolved()
    {
        return resolved;
    }

    /**
     * @param resolved see {@link #isResolved()}.
     */
    public void setResolved(boolean resolved)
    {
        this.resolved = resolved;
    }

    /**
     * @return a list of labels that were assigned to this work item.
     */
    public List<String> getLabels()
    {
        return labels;
    }

    /**
     * @param labels see {@link #getLabels()}.
     */
    public void setLabels(List<String> labels)
    {
        this.labels = labels;
    }

    /**
     * @return the date when this work item was marked as closed.
     */
    public Date getCloseDate()
    {
        return closeDate;
    }

    /**
     * @param closeDate see {@link #getCloseDate()}.
     */
    public void setCloseDate(Date closeDate)
    {
        this.closeDate = closeDate;
    }

    /**
     * @return a tuple identifying the milestone that was associated to this work item. i.e. on Github one can associate
     *     a closed issue to a software milestone, in the case of Jira, an issue can have a 'fix version'.
     */
    public Linkable<String> getMilestones()
    {
        return milestones;
    }

    /**
     * @param milestones see {@link #getMilestones()}.
     */
    public void setMilestones(Linkable<String> milestones)
    {
        this.milestones = milestones;
    }

    /**
     * @return a tuple that identifies the user that closed this work item. The user is identified through their display
     *     name and a link to the location of their profile.
     */
    public Linkable<String> getClosedBy()
    {
        return closedBy;
    }

    /**
     * @param closedBy see {@link #getClosedBy()}.
     */
    public void setClosedBy(Linkable<String> closedBy)
    {
        this.closedBy = closedBy;
    }

    /**
     * @return a map of any additional data attached to this work item.
     */
    public Map<String, Object> getMeta()
    {
        return meta;
    }

    /**
     * @param meta see {@link #getMeta()}.
     */
    public void setMeta(Map<String, Object> meta)
    {
        this.meta = meta;
    }
}
