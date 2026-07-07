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
 * Describes the project object of a work package.
 *
 * @version $Id$
 * @since 1.0
 */
public class Project extends BaseOpenProjectObject
{
    private static final String KEY_IDENTIFIER = "identifier";

    private static final String KEY_DESCRIPTION = "description";

    private static final String KEY_STATUS = "status";

    private static final String KEY_CREATED_AT = "createdAt";

    private static final String KEY_LINKS = "_links";

    private static final String KEY_EMBEDDED = "_embedded";

    private static final String KEY_HREF = "href";

    private static final String KEY_NAME = "name";

    /**
     * Create a Project object from the full {@code /api/v3/projects/{id}} response.
     *
     * @param projectJson the JsonNode containing the project information.
     */
    public Project(JsonNode projectJson)
    {
        super(projectJson);

        JsonNode linksNode = projectJson.path(KEY_LINKS);

        setIdentifier(projectJson.path(KEY_IDENTIFIER).asText());
        setDescription(projectJson.path(KEY_DESCRIPTION).path("raw").asText());

        JsonNode statusEmbedded = projectJson.path(KEY_EMBEDDED).path(KEY_STATUS);
        String statusName = statusEmbedded.path(KEY_NAME).asText();
        String statusHref = linksNode.path(KEY_STATUS).path(KEY_HREF).asText();
        if (!statusName.isBlank()) {
            setStatus(new Linkable(statusName, statusHref));
        }

        String createdAtText = projectJson.path(KEY_CREATED_AT).asText();
        if (!createdAtText.isBlank() && createdAtText.length() >= 10) {
            setCreatedAt(LocalDate.parse(createdAtText.substring(0, 10)).toDate());
        }
    }

    /**
     * Default constructor.
     */
    public Project()
    {
    }

    /**
     * @return the short string identifier (slug) of this project, e.g. {@code "your-scrum-project"}.
     */
    public String getIdentifier()
    {
        return (String) get(KEY_IDENTIFIER);
    }

    /**
     * @param identifier see {@link #getIdentifier()}.
     */
    public void setIdentifier(String identifier)
    {
        put(KEY_IDENTIFIER, identifier);
    }

    /**
     * @return the raw text description of this project.
     */
    public String getDescription()
    {
        return (String) get(KEY_DESCRIPTION);
    }

    /**
     * @param description see {@link #getDescription()}.
     */
    public void setDescription(String description)
    {
        put(KEY_DESCRIPTION, description);
    }

    /**
     * @return the status of this project as a {@link Linkable} (value = status name, location = status API href).
     */
    public Linkable getStatus()
    {
        return (Linkable) get(KEY_STATUS);
    }

    /**
     * @param status see {@link #getStatus()}.
     */
    public void setStatus(Linkable status)
    {
        put(KEY_STATUS, status);
    }

    /**
     * @return the date this project was created.
     */
    public Date getCreatedAt()
    {
        return (Date) get(KEY_CREATED_AT);
    }

    /**
     * @param createdAt see {@link #getCreatedAt()}.
     */
    public void setCreatedAt(Date createdAt)
    {
        put(KEY_CREATED_AT, createdAt);
    }
}
