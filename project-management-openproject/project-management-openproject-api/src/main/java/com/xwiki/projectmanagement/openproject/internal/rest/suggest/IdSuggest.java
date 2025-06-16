package com.xwiki.projectmanagement.openproject.internal.rest.suggest;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.rest.XWikiResource;

import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Rest endpoint that suggests ids based on some query string.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.suggest.IdSuggest")
@Singleton
@Path("/wikis/{wikiName}/openproject/instance/{instance}/id")
public class IdSuggest extends XWikiResource implements Initializable
{
    private static final String XWIKI_URL = "http://xwiki.org/";

    private final List<WorkItem> database = new ArrayList<>();

    // We need to return the following JSON. Most important being the value and label.
    // value: propertyValue.value,
    // label: metaData.label,
    // icon: metaData.icon,
    // url: metaData.url,
    // hint: metaData.hint
    @Override
    public void initialize() throws InitializationException
    {
        for (int i = 0; i < 100; i++) {
            WorkItem workItem = new WorkItem();
            workItem.setIdentifier(new Linkable<String>(Integer.toString(i), XWIKI_URL + i));
            workItem.setSummary(new Linkable<String>("Nice title" + i, XWIKI_URL + i));
            workItem.setDescription("This is a nice description " + i);
            database.add(workItem);
        }
    }

    /**
     * @param wiki the wiki that contains the configured client.
     * @param client the open project client where to search for work item suggestions.
     * @param search the string that should match the work item summary (or maybe other props as well).
     * @param pageSize the number of elements that should be returned.
     * @return a list of objects with the following properties: value, label, icon, url, hint.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getWorkItems(
        @PathParam("wikiName") String wiki,
        @PathParam("client") String client,
        @QueryParam("search") @DefaultValue("") String search,
        @QueryParam("pageSize") @DefaultValue("10") int pageSize)
    {
        String lowerSearch = search.toLowerCase();
        List<Map<String, String>> response = database.stream()
            .filter(workItem -> workItem.getIdentifier().getValue().contains(lowerSearch) || workItem.getSummary()
                .getValue().contains(lowerSearch))
            .limit(pageSize)
            .map(workItem -> {
                Map<String, String> formattedWorkItem = new HashMap<>();
                formattedWorkItem.put("value", workItem.getIdentifier().getValue());
                formattedWorkItem.put("label", workItem.getSummary().getValue());
                formattedWorkItem.put("hint", workItem.getDescription());
                formattedWorkItem.put("url", workItem.getIdentifier().getLocation());
                return formattedWorkItem;
            }).collect(Collectors.toList());
        return Response.ok(response).build();
    }
}
