package com.xwiki.projectmanagement.openproject.rest;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.xwiki.rest.XWikiRestException;
import org.xwiki.rest.model.jaxb.SearchResults;

@Path("/wikis/{wikiName}/openproject/links")
public interface OpenProjectSearchResource
{

    @GET
    @Path("/projects/{id}")
    SearchResults getProjects(
        @PathParam("wikiName") String wikiName,
        @PathParam("projectId") String projectId,
        @QueryParam("number") @DefaultValue("-1") Integer number,
        @QueryParam("start") @DefaultValue("0") Integer start,
        @QueryParam("distinct") @DefaultValue("true") Boolean distinct,
        @QueryParam("orderField") @DefaultValue("") String orderField,
        @QueryParam("order") @DefaultValue("asc") String order,
        @QueryParam("prettyNames") @DefaultValue("false") Boolean withPrettyNames
    ) throws XWikiRestException;

    @GET
    @Path("/workPackages/{id}")
    SearchResults getWorkPackages(
        @PathParam("wikiName") String wikiName,
        @PathParam("workPackageId") String workPackageId,
        @QueryParam("number") @DefaultValue("-1") Integer number,
        @QueryParam("start") @DefaultValue("0") Integer start,
        @QueryParam("distinct") @DefaultValue("true") Boolean distinct,
        @QueryParam("orderField") @DefaultValue("") String orderField,
        @QueryParam("order") @DefaultValue("asc") String order,
        @QueryParam("prettyNames") @DefaultValue("false") Boolean withPrettyNames
    ) throws XWikiRestException;

}
