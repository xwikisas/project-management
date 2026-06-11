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
 * Represents a news item from an OpenProject instance.
 *
 * @version $Id$
 * @since 1.2
 */
public class News extends BaseOpenProjectObject
{
    private static final String KEY_TITLE = "title";

    private static final String KEY_SUMMARY = "summary";

    private static final String KEY_DESCRIPTION = "description";

    private static final String KEY_CREATED_AT = "createdAt";

    private static final String KEY_AUTHOR = "author";

    private static final String KEY_PROJECT_LINK = "projectLink";

    private static final String KEY_LINKS = "_links";

    private static final String KEY_HREF = "href";

    /**
     * Default constructor.
     */
    public News()
    {
    }

    /**
     * Creates an {@link News} from a JsonNode element returned by the {@code /api/v3/news} endpoint.
     *
     * @param jsonNode the JsonNode containing the news item data.
     */
    public News(JsonNode jsonNode)
    {
        super(jsonNode);
        setTitle(jsonNode.path(KEY_TITLE).asText());
        setSummary(jsonNode.path(KEY_SUMMARY).asText());
        setDescription(jsonNode.path(KEY_DESCRIPTION).path("html").asText());

        String createdAtText = jsonNode.path(KEY_CREATED_AT).asText();
        if (!createdAtText.isBlank() && createdAtText.length() >= 10) {
            setCreatedAt(LocalDate.parse(createdAtText.substring(0, 10)).toDate());
        }

        JsonNode linksNode = jsonNode.path(KEY_LINKS);

        JsonNode authorNode = linksNode.path(KEY_AUTHOR);
        setAuthor(new Linkable(authorNode.path(KEY_TITLE).asText(), authorNode.path(KEY_HREF).asText()));

        JsonNode projectNode = linksNode.path("project");
        setProjectLink(new Linkable(projectNode.path(KEY_TITLE).asText(), projectNode.path(KEY_HREF).asText()));
    }

    /**
     * @return the title of the news item.
     */
    public String getTitle()
    {
        return (String) get(KEY_TITLE);
    }

    /**
     * @param title see {@link #getTitle()}.
     */
    public void setTitle(String title)
    {
        put(KEY_TITLE, title);
    }

    /**
     * @return the short summary of the news item.
     */
    public String getSummary()
    {
        return (String) get(KEY_SUMMARY);
    }

    /**
     * @param summary see {@link #getSummary()}.
     */
    public void setSummary(String summary)
    {
        put(KEY_SUMMARY, summary);
    }

    /**
     * @return the HTML description of the news item.
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
     * @return the date the news item was created.
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

    /**
     * @return the author of the news item as a {@link Linkable}.
     */
    public Linkable getAuthor()
    {
        return (Linkable) get(KEY_AUTHOR);
    }

    /**
     * @param author see {@link #getAuthor()}.
     */
    public void setAuthor(Linkable author)
    {
        put(KEY_AUTHOR, author);
    }

    /**
     * @return the project this news item belongs to as a {@link Linkable}.
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
