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
package com.xwiki.projectmanagement.openproject.model;

import java.util.Date;

import org.joda.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;
import com.xwiki.projectmanagement.model.Linkable;

/**
 * Represents a time entry from an OpenProject instance.
 *
 * @version $Id$
 * @since 1.2
 */
public class TimeEntry extends BaseOpenProjectObject
{
    private static final String KEY_SPENT_ON = "spentOn";

    private static final String KEY_HOURS = "hours";

    private static final String KEY_COMMENT = "comment";

    private static final String KEY_ACTIVITY = "activity";

    private static final String KEY_WORK_PACKAGE = "workPackage";

    private static final String KEY_PROJECT_LINK = "projectLink";

    private static final String KEY_LINKS = "_links";

    private static final String KEY_TITLE = "title";

    private static final String KEY_HREF = "href";

    /**
     * Default constructor.
     */
    public TimeEntry()
    {
    }

    /**
     * Creates a {@link TimeEntry} from a JsonNode element returned by the {@code /api/v3/time_entries} endpoint.
     *
     * @param jsonNode the JsonNode containing the time entry data.
     */
    public TimeEntry(JsonNode jsonNode)
    {
        super(jsonNode);

        String spentOnText = jsonNode.path(KEY_SPENT_ON).asText();
        if (!spentOnText.isBlank()) {
            setSpentOn(LocalDate.parse(spentOnText).toDate());
        }

        setHours(jsonNode.path(KEY_HOURS).asText());
        setComment(jsonNode.path(KEY_COMMENT).path("raw").asText());

        JsonNode linksNode = jsonNode.path(KEY_LINKS);

        JsonNode activityNode = linksNode.path(KEY_ACTIVITY);
        setActivity(new Linkable(activityNode.path(KEY_TITLE).asText(), activityNode.path(KEY_HREF).asText()));

        JsonNode workPackageNode = linksNode.path(KEY_WORK_PACKAGE);
        String workPackageRef = workPackageNode.path(KEY_HREF).asText();
        setWorkPackage(new Linkable(workPackageNode.path(KEY_TITLE).asText(), workPackageRef));

        JsonNode projectNode = linksNode.path("project");
        setProjectLink(new Linkable(projectNode.path(KEY_TITLE).asText(), projectNode.path(KEY_HREF).asText()));
    }

    /**
     * @return the date on which the time was spent.
     */
    public Date getSpentOn()
    {
        return (Date) get(KEY_SPENT_ON);
    }

    /**
     * @param spentOn see {@link #getSpentOn()}.
     */
    public void setSpentOn(Date spentOn)
    {
        put(KEY_SPENT_ON, spentOn);
    }

    /**
     * @return the hours logged, as an ISO 8601 duration string (e.g. {@code "PT6H"}).
     */
    public String getHours()
    {
        return (String) get(KEY_HOURS);
    }

    /**
     * @param hours see {@link #getHours()}.
     */
    public void setHours(String hours)
    {
        put(KEY_HOURS, hours);
    }

    /**
     * @return the comment associated with this time entry.
     */
    public String getComment()
    {
        return (String) get(KEY_COMMENT);
    }

    /**
     * @param comment see {@link #getComment()}.
     */
    public void setComment(String comment)
    {
        put(KEY_COMMENT, comment);
    }

    /**
     * @return the activity type as a {@link Linkable}.
     */
    public Linkable getActivity()
    {
        return (Linkable) get(KEY_ACTIVITY);
    }

    /**
     * @param activity see {@link #getActivity()}.
     */
    public void setActivity(Linkable activity)
    {
        put(KEY_ACTIVITY, activity);
    }

    /**
     * @return the work package this time entry was logged against as a {@link Linkable}.
     */
    public Linkable getWorkPackage()
    {
        return (Linkable) get(KEY_WORK_PACKAGE);
    }

    /**
     * @param workPackage see {@link #getWorkPackage()}.
     */
    public void setWorkPackage(Linkable workPackage)
    {
        put(KEY_WORK_PACKAGE, workPackage);
    }

    /**
     * @return the project this time entry belongs to as a {@link Linkable}.
     */
    public Linkable getProjectLink()
    {
        return (Linkable) get(KEY_PROJECT_LINK);
    }

    /**
     * @param projectLink see {@link #getProjectLink()}.
     */
    public void setProjectLink(Linkable projectLink)
    {
        put(KEY_PROJECT_LINK, projectLink);
    }
}
