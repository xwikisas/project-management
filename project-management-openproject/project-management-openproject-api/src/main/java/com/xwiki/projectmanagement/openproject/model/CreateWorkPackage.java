package com.xwiki.projectmanagement.openproject.model;

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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Describes the payload for creating a work package in OpenProject.
 *
 * @version $Id$
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateWorkPackage
{
    private String project;

    private String subject;

    private String assignee;

    private String type;

    private String status;

    private String priority;

    private String description;

    private String startDate;

    private String dueDate;

    /**
     * Default constructor.
     */
    public CreateWorkPackage()
    {

    }

    /**
     * Constructor.
     *
     * @param project the project
     * @param subject the subject
     * @param assignee the assignee
     * @param type the type
     * @param status the status
     * @param priority the priority
     */
    public CreateWorkPackage(String project, String subject, String assignee, String type, String status,
        String priority)
    {
        this.project = project;
        this.subject = subject;
        this.assignee = assignee;
        this.type = type;
        this.status = status;
        this.priority = priority;
    }

    /**
     * Getter for the project.
     *
     * @return the project
     */
    public String getProject()
    {
        return project;
    }

    /**
     * Setter for the project.
     *
     * @param project the project to set
     */
    public void setProject(String project)
    {
        this.project = project;
    }

    /**
     * Getter for the subject.
     *
     * @return the subject
     */
    public String getSubject()
    {
        return subject;
    }

    /**
     * Setter for the subject.
     *
     * @param subject the subject to set
     */
    public void setSubject(String subject)
    {
        this.subject = subject;
    }

    /**
     * Getter for the assignee.
     *
     * @return the assignee
     */
    public String getAssignee()
    {
        return assignee;
    }

    /**
     * Setter for the assignee.
     *
     * @param assignee the assignee to set
     */
    public void setAssignee(String assignee)
    {
        this.assignee = assignee;
    }

    /**
     * Getter for the type.
     *
     * @return the type
     */
    public String getType()
    {
        return type;
    }

    /**
     * Setter for the type.
     *
     * @param type the type to set
     */
    public void setType(String type)
    {
        this.type = type;
    }

    /**
     * Getter for the status.
     * @return the status
     */
    public String getStatus()
    {
        return status;
    }

    /**
     * Setter for the status.
     *
     * @param status the status to set
     */
    public void setStatus(String status)
    {
        this.status = status;
    }

    /**
     * Getter for the priority.
     *
     * @return the priority
     */
    public String getPriority()
    {
        return priority;
    }

    /**
     * Setter for the priority.
     *
     * @param priority the priority to set
     */
    public void setPriority(String priority)
    {
        this.priority = priority;
    }

    /**
     * Getter for the description.
     *
     * @return the description
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * Setter for the description.
     *
     * @param description the description to set
     */
    public void setDescription(String description)
    {
        this.description = description;
    }

    /**
     * Getter for the start date.
     *
     * @return the start date
     */
    public String getStartDate()
    {
        return startDate;
    }

    /**
     * Setter for the start date.
     *
     * @param startDate the start date to set
     */
    public void setStartDate(String startDate)
    {
        this.startDate = startDate;
    }

    /**
     * Getter for the due date.
     *
     * @return the due date
     */
    public String getDueDate()
    {
        return dueDate;
    }

    /**
     * Setter for the due date.
     *
     * @param dueDate the due date to set
     */
    public void setDueDate(String dueDate)
    {
        this.dueDate = dueDate;
    }
}
