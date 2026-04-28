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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Describes the payload for creating a work package in OpenProject.
 *
 * @version $Id$
 * @since 1.1
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
     * @param project project the project identifier, e.g. the API path of the project, such as
     *     "/api/v3/projects/1"
     * @param subject the subject of the work package
     * @param assignee the assignee of the work package
     * @param type the type of the work package
     * @param status the status of the work package
     * @param priority the priority of the work package
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
     * Getter for the project identifier.
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
     * @param project see {@link #getProject()}.
     */
    public void setProject(String project)
    {
        this.project = project;
    }

    /**
     * Getter for the subject of the work package.
     *
     * @return the subject of the work package
     */
    public String getSubject()
    {
        return subject;
    }

    /**
     * Setter for the subject.
     *
     * @param subject see {@link #getSubject()}.
     */
    public void setSubject(String subject)
    {
        this.subject = subject;
    }

    /**
     * Getter for the assignee of the work package.
     *
     * @return the assignee of the work package
     */
    public String getAssignee()
    {
        return assignee;
    }

    /**
     * Setter for the assignee of the work package.
     *
     * @param assignee see {@link #getAssignee()}.
     */
    public void setAssignee(String assignee)
    {
        this.assignee = assignee;
    }

    /**
     * Getter for the type of the work package.
     *
     * @return the type of the work package
     */
    public String getType()
    {
        return type;
    }

    /**
     * Setter for the type of the work package.
     *
     * @param type see {@link #getType()}.
     */
    public void setType(String type)
    {
        this.type = type;
    }

    /**
     * Getter for the status of the work package.
     *
     * @return the status of the work package
     */
    public String getStatus()
    {
        return status;
    }

    /**
     * Setter for the status of the work package.
     *
     * @param status see {@link #getStatus()}.
     */
    public void setStatus(String status)
    {
        this.status = status;
    }

    /**
     * Getter for the priority of the work package.
     *
     * @return the priority of the work package
     */
    public String getPriority()
    {
        return priority;
    }

    /**
     * Setter for the priority.
     *
     * @param priority see {@link #getPriority()}.
     */
    public void setPriority(String priority)
    {
        this.priority = priority;
    }

    /**
     * Getter for the description of the work package.
     *
     * @return the description of the work package
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * Setter for the description of the work package.
     *
     * @param description see {@link #getDescription()}.
     */
    public void setDescription(String description)
    {
        this.description = description;
    }

    /**
     * Getter for the start date of the work package.
     *
     * @return the start date of the work package
     */
    public String getStartDate()
    {
        return startDate;
    }

    /**
     * Setter for the start date of the work package.
     *
     * @param startDate see {@link #getStartDate()}.
     */
    public void setStartDate(String startDate)
    {
        this.startDate = startDate;
    }

    /**
     * Getter for the due date of the work package.
     *
     * @return the due date of the work package
     */
    public String getDueDate()
    {
        return dueDate;
    }

    /**
     * Setter for the due date of the work package.
     *
     * @param dueDate see {@link #getDueDate()}.
     */
    public void setDueDate(String dueDate)
    {
        this.dueDate = dueDate;
    }
}
