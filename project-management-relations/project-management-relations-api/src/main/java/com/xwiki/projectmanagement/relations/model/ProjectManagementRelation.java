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
package com.xwiki.projectmanagement.relations.model;

/**
 * The model representing the project management relation object. A relation between a page and project management
 * entity.
 *
 * @version $Id$
 */
public class ProjectManagementRelation
{
    private String client;

    private String project;

    private String workItem;

    private String clientParams;

    /**
     * @return the id of the project management client. i.e. openproject.
     */
    public String getClient()
    {
        return this.client;
    }

    /**
     * @param client see {@link #getClient()} .
     */
    public void setClient(String client)
    {
        this.client = client;
    }

    /**
     * @return the id of the OpenProject project that is linked to a xwiki page.
     */
    public String getProject()
    {
        return this.project;
    }

    /**
     * @param project see {@link #getProject()}.
     */
    public void setProject(String project)
    {
        this.project = project;
    }

    /**
     * @return the id of the OpenProject work package that is linked to a xwiki page.
     */
    public String getWorkItem()
    {
        return workItem;
    }

    /**
     * @param workItem see {@link #getWorkItem()} ()}.
     */
    public void setWorkItem(String workItem)
    {
        this.workItem = workItem;
    }

    /**
     * @return the stored client params in a string format. The storage format is defined by the client.
     */
    public String getClientParams()
    {
        return this.clientParams;
    }

    /**
     * @param clientParams see {@link #getClientParams()}.
     */
    public void setClientParams(String clientParams)
    {
        this.clientParams = clientParams;
    }
}
