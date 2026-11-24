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

import org.xwiki.stability.Unstable;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Describes a Version related to OpenProject.
 *
 * @version $Id$
 * @since 1.2
 */
public class Version extends BaseOpenProjectObject
{
    /**
     * The key identifying the description of the version.
     */
    private static final String DESCRIPTION = "description";

    /**
     * The key identifying the start date of the version.
     */
    private static final String START_DATE = "startDate";

    /**
     * The key identifying the end date of the version.
     */
    private static final String END_DATE = "endDate";

    /**
     * The key identifying the status of the version.
     */
    private static final String STATUS = "status";

    /**
     * The key identifying the name of the project defining the version.
     */
    private static final String DEFINING_PROJECT_NAME = "definingProjectName";

    /**
     * Default constructor.
     */
    public Version()
    {
    }

    /**
     * Create a Version object from a JsonNode.
     *
     * @param versionNode the JsonNode containing the version information.
     */
    public Version(JsonNode versionNode)
    {
        super(versionNode);
        this.setDescription(versionNode.path(DESCRIPTION).path("raw").asText());
        this.setStartDate(getTextOrNull(versionNode, START_DATE));
        this.setEndDate(getTextOrNull(versionNode, END_DATE));
        this.setStatus(versionNode.path(STATUS).asText());
        this.setDefiningProjectName(getTextOrNull(versionNode.path("_links").path("definingProject"), "title"));
    }

    /**
     * @return the description of the version.
     */
    public String getDescription()
    {
        return (String) get(DESCRIPTION);
    }

    /**
     * @param description see {@link #getDescription()}.
     */
    public void setDescription(String description)
    {
        put(DESCRIPTION, description);
    }

    /**
     * @return the start date of the version.
     */
    public String getStartDate()
    {
        return (String) get(START_DATE);
    }

    /**
     * @param startDate see {@link #getStartDate()}.
     */
    public void setStartDate(String startDate)
    {
        put(START_DATE, startDate);
    }

    /**
     * @return the end date of the version.
     */
    public String getEndDate()
    {
        return (String) get(END_DATE);
    }

    /**
     * @param endDate see {@link #getEndDate()}.
     */
    public void setEndDate(String endDate)
    {
        put(END_DATE, endDate);
    }

    /**
     * @return the status of the version.
     */
    public String getStatus()
    {
        return (String) get(STATUS);
    }

    /**
     * @param status see {@link #getStatus()}.
     */
    public void setStatus(String status)
    {
        put(STATUS, status);
    }

    /**
     * @return the name of the project that defines the version (other projects can share it), or {@code null} when
     *     it's unknown, e.g. when the current user can't see the project
     * @since 1.5.0
     */
    @Unstable
    public String getDefiningProjectName()
    {
        return (String) get(DEFINING_PROJECT_NAME);
    }

    /**
     * @param definingProjectName see {@link #getDefiningProjectName()}
     * @since 1.5.0
     */
    @Unstable
    public void setDefiningProjectName(String definingProjectName)
    {
        put(DEFINING_PROJECT_NAME, definingProjectName);
    }

    private static String getTextOrNull(JsonNode node, String field)
    {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }
}
