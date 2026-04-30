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
package com.xwiki.projectmanagement.chart.rest;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.xwiki.rest.XWikiRestException;

import com.xwiki.projectmanagement.chart.model.ChartDisplayerParameterInfo;

/**
 * Resource for retrieving information about the chart displayers.
 *
 * @version $Id$
 * @since 1.2.0-rc-1
 */
@Path("/wikis/{wikiName}/projectmanagement/chart/displayers/{type}")
public interface ChartDisplayerResource
{
    /**
     * @param type the type of the chart displayer. It's id.
     * @return a structure containing the
     */
    @GET
    List<ChartDisplayerParameterInfo> getChartDisplayerInfo(@PathParam("type") String type) throws XWikiRestException;
}
