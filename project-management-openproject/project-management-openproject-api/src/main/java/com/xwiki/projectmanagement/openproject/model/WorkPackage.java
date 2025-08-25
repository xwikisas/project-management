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

import com.xwiki.projectmanagement.model.Linkable;

/**
 * Describes a Work package related to OpenProject.
 *
 * @version $Id$
 * @since 1.0
 */
public class WorkPackage extends BaseOpenProjectObject
{
    /**
     * The key identifying the derived start date of the work package.
     */
    private static final String DERIVED_START_DATE = "derivedStartDate";

    /**
     * The key identifying the derived due date of the work package.
     */
    private static final String DERIVED_DUE_DATE = "derivedDueDate";

    /**
     * The key identifying the internal type of the work package.
     */
    private static final String TYPE = "_type";

    /**
     * The key identifying the subject or title of the work package.
     */
    private static final String SUBJECT = "subject";

    /**
     * The key identifying the description of the work package.
     */
    private static final String DESCRIPTION = "description";

    /**
     * The key identifying the start date of the work package.
     */
    private static final String START_DATE = "startDate";

    /**
     * The key identifying the due date of the work package.
     */
    private static final String DUE_DATE = "dueDate";

    /**
     * The key identifying when the work package was created.
     */
    private static final String CREATED_AT = "createdAt";

    /**
     * The key identifying when the work package was last updated.
     */
    private static final String UPDATED_AT = "updatedAt";

    /**
     * The key identifying the type of the work package.
     */
    private static final String TYPE_OF_WORK_PACKAGE = "typeOfWorkPackage";

    /**
     * The key identifying the priority of the work package.
     */
    private static final String PRIORITY = "priority";

    /**
     * The key identifying the project to which the work package belongs.
     */
    private static final String PROJECT = "project";

    /**
     * The key identifying the percentage done for the work package.
     */
    private static final String PERCENTAGE_DONE = "percentageDone";

    /**
     * The key identifying the status of the work package.
     */
    private static final String STATUS = "status";

    /**
     * The key identifying the author (creator) of the work package.
     */
    private static final String AUTHOR = "author";

    /**
     * The key identifying the assignee of the work package.
     */
    private static final String ASSIGNEE = "assignee";

    /**
     * Gets the derived start date of the work package.
     *
     * @return the derived start date
     */
    public Date getDerivedStartDate()
    {
        return (Date) get(DERIVED_START_DATE);
    }

    /**
     * Sets the derived start date of the work package.
     *
     * @param derivedStartDate the derived start date to set
     */
    public void setDerivedStartDate(Date derivedStartDate)
    {
        put(DERIVED_START_DATE, derivedStartDate);
    }

    /**
     * Gets the derived due date of the work package.
     *
     * @return the derived due date
     */
    public Date getDerivedDueDate()
    {
        return (Date) get(DERIVED_DUE_DATE);
    }

    /**
     * Sets the derived due date of the work package.
     *
     * @param derivedDueDate the derived due date to set
     */
    public void setDerivedDueDate(Date derivedDueDate)
    {
        put(DERIVED_DUE_DATE, derivedDueDate);
    }

    /**
     * Gets the internal type of the work package.
     *
     * @return the type
     */
    public String getType()
    {
        return (String) get(TYPE);
    }

    /**
     * Sets the internal type of the work package.
     *
     * @param type the type to set
     */
    public void setType(String type)
    {
        put(TYPE, type);
    }

    /**
     * Gets the subject or title of the work package.
     *
     * @return the subject
     */
    public String getSubject()
    {
        return (String) get(SUBJECT);
    }

    /**
     * Sets the subject or title of the work package.
     *
     * @param subject the subject to set
     */
    public void setSubject(String subject)
    {
        put(SUBJECT, subject);
    }

    /**
     * Gets the description of the work package.
     *
     * @return the description
     */
    public String getDescription()
    {
        return (String) get(DESCRIPTION);
    }

    /**
     * Sets the description of the work package.
     *
     * @param description the description to set
     */
    public void setDescription(String description)
    {
        put(DESCRIPTION, description);
    }

    /**
     * Gets the start date of the work package.
     *
     * @return the start date
     */
    public Date getStartDate()
    {
        return (Date) get(START_DATE);
    }

    /**
     * Sets the start date of the work package.
     *
     * @param startDate the start date to set
     */
    public void setStartDate(Date startDate)
    {
        put(START_DATE, startDate);
    }

    /**
     * Gets the due date of the work package.
     *
     * @return the due date
     */
    public Date getDueDate()
    {
        return (Date) get(DUE_DATE);
    }

    /**
     * Sets the due date of the work package.
     *
     * @param dueDate the due date to set
     */
    public void setDueDate(Date dueDate)
    {
        put(DUE_DATE, dueDate);
    }

    /**
     * Gets the creation timestamp of the work package.
     *
     * @return the created at date
     */
    public Date getCreatedAt()
    {
        return (Date) get(CREATED_AT);
    }

    /**
     * Sets the creation timestamp of the work package.
     *
     * @param createdAt the created at date to set
     */
    public void setCreatedAt(Date createdAt)
    {
        put(CREATED_AT, createdAt);
    }

    /**
     * Gets the last updated timestamp of the work package.
     *
     * @return the updated at date
     */
    public Date getUpdatedAt()
    {
        return (Date) get(UPDATED_AT);
    }

    /**
     * Sets the last updated timestamp of the work package.
     *
     * @param updatedAt the updated at date to set
     */
    public void setUpdatedAt(Date updatedAt)
    {
        put(UPDATED_AT, updatedAt);
    }

    /**
     * Gets the type of work package, which includes its value and reference link.
     *
     * @return the type of work package as a {@link Linkable}
     */
    public Linkable getTypeOfWorkPackage()
    {
        return (Linkable) get(TYPE_OF_WORK_PACKAGE);
    }

    /**
     * Sets the type of work package, including its value and reference link.
     *
     * @param typeOfWorkPackage the type of work package to set as a {@link Linkable}
     */
    public void setTypeOfWorkPackage(Linkable typeOfWorkPackage)
    {
        put(TYPE_OF_WORK_PACKAGE, typeOfWorkPackage);
    }

    /**
     * Gets the priority of the work package, including its value and reference link.
     *
     * @return the priority as a {@link Linkable}
     */
    public Linkable getPriority()
    {
        return (Linkable) get(PRIORITY);
    }

    /**
     * Sets the priority of the work package, including its value and reference link.
     *
     * @param priority the priority to set as a {@link Linkable}
     */
    public void setPriority(Linkable priority)
    {
        put(PRIORITY, priority);
    }

    /**
     * Gets the percentage done for the work package.
     *
     * @return the percentage done as an integer
     */
    public Integer getPercentageDone()
    {
        return (Integer) get(PERCENTAGE_DONE);
    }

    /**
     * Sets the percentage done for the work package.
     *
     * @param percentageDone the percentage to set
     */
    public void setPercentageDone(Integer percentageDone)
    {
        put(PERCENTAGE_DONE, percentageDone);
    }

    /**
     * Gets the progress associated with the work package, including its reference.
     *
     * @return the project as a {@link Linkable}
     */
    public Linkable getProject()
    {
        return (Linkable) get(PROJECT);
    }

    /**
     * Sets the project associated with the work package.
     *
     * @param project the project to set as a {@link Linkable}
     */
    public void setProject(Linkable project)
    {
        put(PROJECT, project);
    }

    /**
     * Gets the status of the work package, including its value and reference link.
     *
     * @return the status as a {@link Linkable}
     */
    public Linkable getStatus()
    {
        return (Linkable) get(STATUS);
    }

    /**
     * Sets the status of the work package, including its value and reference link.
     *
     * @param status the status to set as a {@link Linkable}
     */
    public void setStatus(Linkable status)
    {
        put(STATUS, status);
    }

    /**
     * Gets the author of the work package, including their value and reference link.
     *
     * @return the author as a {@link Linkable}
     */
    public Linkable getAuthor()
    {
        return (Linkable) get(AUTHOR);
    }

    /**
     * Sets the author of the work package, including their value and reference link.
     *
     * @param author the author to set as a {@link Linkable}
     */
    public void setAuthor(Linkable author)
    {
        put(AUTHOR, author);
    }

    /**
     * Gets the assignee of the work package, including their value and reference link.
     *
     * @return the assignee as a {@link Linkable}
     */
    public Linkable getAssignee()
    {
        return (Linkable) get(ASSIGNEE);
    }

    /**
     * Sets the assignee of the work package, including their value and reference link.
     *
     * @param assignee the assignee to set as a {@link Linkable}
     */
    public void setAssignee(Linkable assignee)
    {
        put(ASSIGNEE, assignee);
    }

    @Override
    public String getName()
    {
        return this.getSubject();
    }

    @Override
    public void setName(String name)
    {
        this.setSubject(name);
    }
}
