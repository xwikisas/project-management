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

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Describes an issue, a ticket, work package or some item coming from a project management tool.
 *
 * @version $Id$
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkItem extends HashMap<String, Object>
{
    /**
     * The key identifying the type property of the work item.
     */
    public static final String KEY_TYPE = "type";

    /**
     * The key identifying the identifier property of the work item.
     */
    public static final String KEY_IDENTIFIER = "identifier";

    /**
     * The key identifying the summary property of the work item.
     */
    public static final String KEY_SUMMARY = "summary";

    /**
     * The key identifying the description property of the work item.
     */
    public static final String KEY_DESCRIPTION = "description";

    /**
     * The key identifying the startDate property of the work item.
     */
    public static final String KEY_START_DATE = "startDate";

    /**
     * The key identifying the dueDate property of the work item.
     */
    public static final String KEY_DUE_DATE = "dueDate";

    /**
     * The key identifying the progress property of the work item.
     */
    public static final String KEY_PROGRESS = "progress";

    /**
     * The key identifying the creationDate property of the work item.
     */
    public static final String KEY_CREATION_DATE = "creationDate";

    /**
     * The key identifying the updateDate property of the work item.
     */
    public static final String KEY_UPDATE_DATE = "updateDate";

    /**
     * The key identifying the creator property of the work item.
     */
    public static final String KEY_CREATOR = "creator";

    /**
     * The key identifying the assignees property of the work item.
     */
    public static final String KEY_ASSIGNEES = "assignees";

    /**
     * The key identifying the priority property of the work item.
     */
    public static final String KEY_PRIORITY = "priority";

    /**
     * The key identifying the project property of the work item.
     */
    public static final String KEY_PROJECT = "project";

    /**
     * The key identifying the status property of the work item.
     */
    public static final String KEY_STATUS = "status";

    /**
     * The key identifying the reporter property of the work item.
     */
    public static final String KEY_REPORTER = "reporter";

    /**
     * The key identifying the resolution property of the work item.
     */
    public static final String KEY_RESOLUTION = "resolution";

    /**
     * The key identifying the resolved property of the work item.
     */
    public static final String KEY_RESOLVED = "resolved";

    /**
     * The key identifying the labels property of the work item.
     */
    public static final String KEY_LABELS = "labels";

    /**
     * The key identifying the closeDate property of the work item.
     */
    public static final String KEY_CLOSE_DATE = "closeDate";

    /**
     * The key identifying the milestones property of the work item.
     */
    public static final String KEY_MILESTONES = "milestones";

    /**
     * The key identifying the closedBy property of the work item.
     */
    public static final String KEY_CLOSED_BY = "closedBy";

    /**
     * @return the type of the work item. i.e. task, bug, epic, etc.
     */
    @JsonProperty(KEY_TYPE)
    public String getType()
    {
        return (String) get(KEY_TYPE);
    }

    /**
     * @param type see {@link #getType()}.
     */
    @JsonProperty(KEY_TYPE)
    public void setType(String type)
    {
        put(KEY_TYPE, type);
    }

    /**
     * @return the identifier for this work item together with a link to its location. For example, Open project and
     *     GitHub identify work packages numerically: '1001', Jira using a key: 'XWIKI-1001'.
     */
    @JsonProperty(KEY_IDENTIFIER)
    public Linkable getIdentifier()
    {
        return (Linkable) get(KEY_IDENTIFIER);
    }

    /**
     * @param identifier see {@link #getIdentifier()}.
     */
    @JsonProperty(KEY_IDENTIFIER)
    public void setIdentifier(Linkable identifier)
    {
        put(KEY_IDENTIFIER, identifier);
    }

    /**
     * @return a text summarizing this work item together with a link to its location. On some platform, this property
     *     translates to the issue title.
     */
    @JsonProperty(KEY_SUMMARY)
    public Linkable getSummary()
    {
        return (Linkable) get(KEY_SUMMARY);
    }

    /**
     * @param summary see {@link #getSummary()}.
     */
    @JsonProperty(KEY_SUMMARY)
    public void setSummary(Linkable summary)
    {
        put(KEY_SUMMARY, summary);
    }

    /**
     * @return a text that describes this work item.
     */
    @JsonProperty(KEY_DESCRIPTION)
    public String getDescription()
    {
        return (String) get(KEY_DESCRIPTION);
    }

    /**
     * @param description see {@link #getDescription()}.
     */
    @JsonProperty(KEY_DESCRIPTION)
    public void setDescription(String description)
    {
        put(KEY_DESCRIPTION, description);
    }

    /**
     * @return the date when this work item was marked, by some user/entity, as started.
     */
    @JsonProperty(KEY_START_DATE)
    public Date getStartDate()
    {
        return (Date) get(KEY_START_DATE);
    }

    /**
     * @param startDate see {@link #getStartDate()}.
     */
    @JsonProperty(KEY_START_DATE)
    public void setStartDate(Date startDate)
    {
        put(KEY_START_DATE, startDate);
    }

    /**
     * @return the date when this work item is expected to be finished, set as resolved.
     */
    @JsonProperty(KEY_DUE_DATE)
    public Date getDueDate()
    {
        return (Date) get(KEY_DUE_DATE);
    }

    /**
     * @param dueDate see {@link #getDueDate()}.
     */
    @JsonProperty(KEY_DUE_DATE)
    public void setDueDate(Date dueDate)
    {
        put(KEY_DUE_DATE, dueDate);
    }

    /**
     * @return the progress, as a percentage, that has been done for this work item.
     */
    @JsonProperty(KEY_PROGRESS)
    public int getProgress()
    {
        return (int) get(KEY_PROGRESS);
    }

    /**
     * @param progress see {@link #getProgress()}.
     */
    @JsonProperty(KEY_PROGRESS)
    public void setProgress(int progress)
    {
        put(KEY_PROGRESS, progress);
    }

    /**
     * @return the date when this work item was created.
     */
    @JsonProperty(KEY_CREATION_DATE)
    public Date getCreationDate()
    {
        return (Date) get(KEY_CREATION_DATE);
    }

    /**
     * @param creationDate see {@link #getCreationDate()}.
     */
    @JsonProperty(KEY_CREATION_DATE)
    public void setCreationDate(Date creationDate)
    {
        put(KEY_CREATION_DATE, creationDate);
    }

    /**
     * @return the last date when this work item was updated or modified.
     */
    @JsonProperty(KEY_UPDATE_DATE)
    public Date getUpdateDate()
    {
        return (Date) get(KEY_UPDATE_DATE);
    }

    /**
     * @param updateDate see {@link #getUpdateDate()}.
     */
    @JsonProperty(KEY_UPDATE_DATE)
    public void setUpdateDate(Date updateDate)
    {
        put(KEY_UPDATE_DATE, updateDate);
    }

    /**
     * @return a tuple identifying the creator of this work item. The tuple contains the display name of the user and a
     *     link to their profile location.
     */
    @JsonProperty(KEY_CREATOR)
    public Linkable getCreator()
    {
        return (Linkable) get(KEY_CREATOR);
    }

    /**
     * @param creator see {@link #getCreator()}.
     */
    @JsonProperty(KEY_CREATOR)
    public void setCreator(Linkable creator)
    {
        put(KEY_CREATOR, creator);
    }

    /**
     * @return a list of users assigned to this work item. Each user is a tuple containing the display name and the
     *     location to their profile location.
     */
    @JsonProperty(KEY_ASSIGNEES)
    public List<Linkable> getAssignees()
    {
        return (List<Linkable>) get(KEY_ASSIGNEES);
    }

    /**
     * @param assignees see {@link #getAssignees()}.
     */
    @JsonProperty(KEY_ASSIGNEES)
    public void setAssignees(List<Linkable> assignees)
    {
        put(KEY_ASSIGNEES, assignees);
    }

    /**
     * @return the priority that was assigned to this work item. i.e. "minor", "critical", "major", etc.
     */
    @JsonProperty(KEY_PRIORITY)
    public String getPriority()
    {
        return (String) get(KEY_PRIORITY);
    }

    /**
     * @param priority see {@link #getPriority()}.
     */
    @JsonProperty(KEY_PRIORITY)
    public void setPriority(String priority)
    {
        put(KEY_PRIORITY, priority);
    }

    /**
     * @return a tuple identifying the project where this work item belongs. The tuple contains the display name of the
     *     project and a link to its location.
     */
    @JsonProperty(KEY_PROJECT)
    public Linkable getProject()
    {
        return (Linkable) get(KEY_PROJECT);
    }

    /**
     * @param project see {@link #getProject()}.
     */
    @JsonProperty(KEY_PROJECT)
    public void setProject(Linkable project)
    {
        put(KEY_PROJECT, project);
    }

    /**
     * @return the current status of this work item.
     */
    @JsonProperty(KEY_STATUS)
    public String getStatus()
    {
        return (String) get(KEY_STATUS);
    }

    /**
     * @param status see {@link #getStatus()}.
     */
    @JsonProperty(KEY_STATUS)
    public void setStatus(String status)
    {
        put(KEY_STATUS, status);
    }

    /**
     * @return a tuple representing the user thanks to whom this work item was created. If this work item represents a
     *     bug, it could be that somebody reported it and someone else created the ticket.
     */
    @JsonProperty(KEY_REPORTER)
    public Linkable getReporter()
    {
        return (Linkable) get(KEY_REPORTER);
    }

    /**
     * @param reporter see {@link #getReporter()}.
     */
    @JsonProperty(KEY_REPORTER)
    public void setReporter(Linkable reporter)
    {
        put(KEY_REPORTER, reporter);
    }

    /**
     * @return the resolution for this work item. A work item can be marked as resolved but it might need additional
     *     information on how it was closed. i.e. "duplicate", "won't resolve", "canceled" etc.
     */
    @JsonProperty(KEY_RESOLUTION)
    public String getResolution()
    {
        return (String) get(KEY_RESOLUTION);
    }

    /**
     * @param resolution see {@link #getResolution()}.
     */
    @JsonProperty(KEY_RESOLUTION)
    public void setResolution(String resolution)
    {
        put(KEY_RESOLUTION, resolution);
    }

    /**
     * @return denotes whether this work item is closed/resolved or not.
     */
    @JsonProperty(KEY_RESOLVED)
    public boolean isResolved()
    {
        return (boolean) get(KEY_RESOLVED);
    }

    /**
     * @param resolved see {@link #isResolved()}.
     */
    @JsonProperty(KEY_RESOLVED)
    public void setResolved(boolean resolved)
    {
        put(KEY_RESOLVED, resolved);
    }

    /**
     * @return a list of labels that were assigned to this work item.
     */
    @JsonProperty(KEY_LABELS)
    public List<String> getLabels()
    {
        return (List<String>) get(KEY_LABELS);
    }

    /**
     * @param labels see {@link #getLabels()}.
     */
    @JsonProperty(KEY_LABELS)
    public void setLabels(List<String> labels)
    {
        put(KEY_LABELS, labels);
    }

    /**
     * @return the date when this work item was marked as closed.
     */
    @JsonProperty(KEY_CLOSE_DATE)
    public Date getCloseDate()
    {
        return (Date) get(KEY_CLOSE_DATE);
    }

    /**
     * @param closeDate see {@link #getCloseDate()}.
     */
    @JsonProperty(KEY_CLOSE_DATE)
    public void setCloseDate(Date closeDate)
    {
        put(KEY_CLOSE_DATE, closeDate);
    }

    /**
     * @return a tuple identifying the milestone that was associated to this work item. i.e. on Github one can associate
     *     a closed issue to a software milestone, in the case of Jira, an issue can have a 'fix version'.
     */
    @JsonProperty(KEY_MILESTONES)
    public Linkable getMilestones()
    {
        return (Linkable) get(KEY_MILESTONES);
    }

    /**
     * @param milestones see {@link #getMilestones()}.
     */
    @JsonProperty(KEY_MILESTONES)
    public void setMilestones(Linkable milestones)
    {
        put(KEY_MILESTONES, milestones);
    }

    /**
     * @return a tuple that identifies the user that closed this work item. The user is identified through their display
     *     name and a link to the location of their profile.
     */
    @JsonProperty(KEY_CLOSED_BY)
    public Linkable getClosedBy()
    {
        return (Linkable) get(KEY_CLOSED_BY);
    }

    /**
     * @param closedBy see {@link #getClosedBy()}.
     */
    @JsonProperty(KEY_CLOSED_BY)
    public void setClosedBy(Linkable closedBy)
    {
        put(KEY_CLOSED_BY, closedBy);
    }

    /**
     * @return the map.
     */
    @JsonAnyGetter
    public Map<String, Object> getMapEntries()
    {
        return this;
    }

    /**
     * @param key the key.
     * @param value the value.
     */
    @JsonAnySetter
    public void putEntry(String key, Object value)
    {
        this.put(key, value);
    }

    /**
     * Utility method that retrieves a linkable property.
     *
     * @param key the key of the linkable property. i.e. identifier, summary.
     * @return the linkable property as a map or an empty map if nothing was found.
     */
    public Map<String, Object> getLinkable(String key)
    {
        Object property = get(key);
        if (!(property instanceof Map<?, ?>)) {
            return Collections.emptyMap();
        }
        return (Map<String, Object>) property;
    }

    /**
     * Utility method that retrieves the value of a linkable property.
     *
     * @param key the key of the linkable property. i.e. identifier, summary.
     * @return the value of the linkable property or null if the linkable property does not exist.
     */
    public String getLinkableValue(String key)
    {

        return (String) getLinkable(key).get(Linkable.KEY_VALUE);
    }

    /**
     * Utility method that retrieves the location of a linkable property.
     *
     * @param key the key of the linkable property. i.e. identifier, summary.
     * @return the location of the linkable property or null if the linkable property does not exist.
     */
    public String getLinkableLocation(String key)
    {

        return (String) getLinkable(key).get(Linkable.KEY_VALUE);
    }
}
