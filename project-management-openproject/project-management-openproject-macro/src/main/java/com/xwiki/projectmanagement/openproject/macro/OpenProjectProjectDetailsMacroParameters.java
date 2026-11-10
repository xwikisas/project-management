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
package com.xwiki.projectmanagement.openproject.macro;

import java.util.List;

import org.xwiki.properties.annotation.PropertyDisplayType;
import org.xwiki.properties.annotation.PropertyOrder;
import org.xwiki.stability.Unstable;

import com.xwiki.projectmanagement.openproject.OpenProjectProjectDetailsProperty;

/**
 * Parameters for the OpenProject Project Details macro.
 *
 * @version $Id$
 * @since 1.2
 */
public class OpenProjectProjectDetailsMacroParameters extends BaseDirectOpenProjectMacroParameters
{
    private List<OpenProjectProjectDetailsProperty> properties =
        List.of(OpenProjectProjectDetailsProperty.NAME, OpenProjectProjectDetailsProperty.DESCRIPTION,
            OpenProjectProjectDetailsProperty.STATUS);

    /**
     * @return the properties of the project to display, in the order of {@link OpenProjectProjectDetailsProperty}
     * @since 1.4.0
     */
    @Unstable
    public List<OpenProjectProjectDetailsProperty> getProperties()
    {
        return this.properties;
    }

    /**
     * @param properties see {@link #getProperties()}
     * @since 1.4.0
     */
    @Unstable
    @PropertyOrder(20)
    @PropertyDisplayType(OpenProjectProjectDetailsProperty.class)
    public void setProperties(List<OpenProjectProjectDetailsProperty> properties)
    {
        this.properties = properties;
    }
}
