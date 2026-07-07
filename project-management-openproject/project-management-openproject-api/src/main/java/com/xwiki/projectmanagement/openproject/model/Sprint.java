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

/**
 * Describes a Sprint related to OpenProject.
 *
 * @version $Id$
 * @since 1.2
 */
public class Sprint extends BaseOpenProjectObject
{
    /**
     * The key identifying the start date of the sprint.
     */
    private static final String START_DATE = "startDate";

    /**
     * The key identifying the finish date of the sprint.
     */
    private static final String FINISH_DATE = "finishDate";

    /**
     * Default constructor.
     */
    public Sprint()
    {
    }

    /**
     * Create a Sprint object from a JsonNode.
     *
     * @param sprintNode the JsonNode containing the sprint information.
     */
    public Sprint(JsonNode sprintNode)
    {
        super(sprintNode);
        this.setStartDate(getTextOrNull(sprintNode, START_DATE));
        this.setFinishDate(getTextOrNull(sprintNode, FINISH_DATE));
    }

    /**
     * @return the start date of the sprint.
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
     * @return the finish date of the sprint.
     */
    public String getFinishDate()
    {
        return (String) get(FINISH_DATE);
    }

    /**
     * @param finishDate see {@link #getFinishDate()}.
     */
    public void setFinishDate(String finishDate)
    {
        put(FINISH_DATE, finishDate);
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
