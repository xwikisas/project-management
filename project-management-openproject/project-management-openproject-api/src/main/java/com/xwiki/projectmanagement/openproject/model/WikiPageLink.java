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

import com.fasterxml.jackson.databind.JsonNode;
import com.xwiki.projectmanagement.model.Linkable;

/**
 * Model representing the xwiki pages mentioned inside work packages.
 *
 * @version $Id$
 * @since 1.2.0
 */
public class WikiPageLink
{
    private Integer id;

    private String identifier;

    private String type;

    private String createdAt;

    private String updatedAt;

    private Linkable self;

    private Linkable provider;

    private Linkable linkable;

    /**
     * Builds the model out of a REST response.
     *
     * @param element a json element taken from the REST response.
     */
    public WikiPageLink(JsonNode element)
    {
        setId(element.path("id").asInt());
        setIdentifier(element.path("identifier").asText());
        setType(element.path("_type").asText());
        setCreatedAt(element.path("createdAt").asText());
        setUpdatedAt(element.path("updatedAt").asText());
        JsonNode links = element.path("_links");
        setProvider(getLinkable(links.path("provider")));
        setLinkable(getLinkable(links.path("linkable")));
        setSelf(getLinkable(links.path("self")));
    }

    /**
     * @return the id of the link.
     */
    public Integer getId()
    {
        return id;
    }

    /**
     * @param id see {@link #getId()}.
     */
    public void setId(Integer id)
    {
        this.id = id;
    }

    /**
     * @return the identifier of the xwiki page.
     */
    public String getIdentifier()
    {
        return identifier;
    }

    /**
     * @param identifier see {@link #getIdentifier()}.
     */
    public void setIdentifier(String identifier)
    {
        this.identifier = identifier;
    }

    /**
     * @return the type of page link: either Relation or Inline.
     */
    public String getType()
    {
        return type;
    }

    /**
     * @param type see {@link #getType()}.
     */
    public void setType(String type)
    {
        this.type = type;
    }

    /**
     * @return the date when the relation was created.
     */
    public String getCreatedAt()
    {
        return createdAt;
    }

    /**
     * @param createdAt see {@link #getCreatedAt()}.
     */
    public void setCreatedAt(String createdAt)
    {
        this.createdAt = createdAt;
    }

    /**
     * @return the last date when the link was updated.
     */
    public String getUpdatedAt()
    {
        return updatedAt;
    }

    /**
     * @param updatedAt see {@link #getUpdatedAt()}.
     */
    public void setUpdatedAt(String updatedAt)
    {
        this.updatedAt = updatedAt;
    }

    /**
     * @return the name and link of the page relation.
     */
    public Linkable getSelf()
    {
        return self;
    }

    /**
     * @param self see {@link #getSelf()}.
     */
    public void setSelf(Linkable self)
    {
        this.self = self;
    }

    /**
     * @return the xwiki provider to which the page belongs to. It is the xwiki instance id.
     */
    public Linkable getProvider()
    {
        return provider;
    }

    /**
     * @param provider see {@link #getProvider()}.
     */
    public void setProvider(Linkable provider)
    {
        this.provider = provider;
    }

    /**
     * @return the information related to the work package that holds this relation.
     */
    public Linkable getLinkable()
    {
        return linkable;
    }

    /**
     * @param linkable see {@link #getLinkable()}.
     */
    public void setLinkable(Linkable linkable)
    {
        this.linkable = linkable;
    }

    private Linkable getLinkable(JsonNode node)
    {
        return new Linkable(node.path("title").asText(), node.path("href").asText());
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
