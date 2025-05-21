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
package com.xwiki.projectmanagement.dto;

import java.util.Date;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xwiki.projectmanagement.model.Linkable;

/**
 * DTO object.
 *
 * @version $Id$
 */
public class OpenProjectWorkItem
{
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date startDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date dueDate;

    private String subject;

    private Linkable<String> project;

    @JsonProperty("_links")
    private void unpackNested(Map<String, Object> links)
    {
        Map<String, Object> projectMap = (Map<String, Object>) links.get("project");
        if (projectMap != null) {
            String href = (String) projectMap.get("href");
            String title = (String) projectMap.get("title");
            this.project = new Linkable<>(href, title);
        }
    }

    /**
     * Gets the start date of the work item.
     *
     * @return the start date
     */
    public Date getStartDate()
    {
        return startDate;
    }

    /**
     * Sets the start date of the work item.
     *
     * @param startDate the date when the work item starts
     */
    public void setStartDate(Date startDate)
    {
        this.startDate = startDate;
    }

    /**
     * Gets the due date of the work item.
     *
     * @return the due date
     */
    public Date getDueDate()
    {
        return dueDate;
    }

    /**
     * Sets the due date of the work item.
     *
     * @param dueDate the date by which the work item should be completed
     */
    public void setDueDate(Date dueDate)
    {
        this.dueDate = dueDate;
    }

    /**
     * Gets the subject or title of the work item.
     *
     * @return the subject
     */
    public String getSubject()
    {
        return subject;
    }

    /**
     * Sets the subject or title of the work item.
     *
     * @param subject the subject of the work item
     */
    public void setSubject(String subject)
    {
        this.subject = subject;
    }

    /**
     * Gets the reference to the associated project.
     *
     * @return a linkable reference to the project
     */
    public Linkable<String> getProject()
    {
        return project;
    }

    /**
     * Sets the reference to the associated project.
     *
     * @param project a linkable reference to the project
     */
    public void setProject(Linkable<String> project)
    {
        this.project = project;
    }
}
