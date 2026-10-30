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
package com.xwiki.projectmanagement.test.openproject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.repository.test.SolrTestUtils;
import org.xwiki.rest.model.jaxb.SearchResult;
import org.xwiki.rest.model.jaxb.SearchResults;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test the REST resources searching the pages linked to an OpenProject project or work package, used by the
 * "Referenced in" section of OpenProject.
 *
 * @version $Id$
 */
@UITest(
    properties = {
        AbstractOpenProjectIT.PRCHECKER_EXCLUDE_PROPERTY,
        AbstractOpenProjectIT.PLUGINS_PROPERTY
    },
    extraJARs = {
        AbstractOpenProjectIT.JYTHON_JAR,
        AbstractOpenProjectIT.SCHEDULER_API_JAR
    }
)
public class OpenProjectLinkSearchIT extends AbstractOpenProjectIT
{
    private static final String RELATION_CLASS = "ProjectManagement.Code.RelationClass";

    @Test
    @Order(1)
    void searchLinkedPages(TestUtils setup, TestReference testReference) throws Exception
    {
        setup.rest().delete(testReference);
        setup.rest().savePage(testReference, "Page linked to OpenProject", "Linked page");
        // Ids that no other test links to.
        setup.addObject(testReference, RELATION_CLASS, "client", "openproject", "project", "9001",
            "workItem", "9002");
        new SolrTestUtils(setup).waitEmptyQueue();

        String pageFullName = setup.serializeReference(testReference.getLocalDocumentReference());
        assertEquals(List.of(pageFullName), searchLinkedPages(setup, "projects", "9001"));
        assertEquals(List.of(pageFullName), searchLinkedPages(setup, "workPackages", "9002"));
        assertEquals(List.of(), searchLinkedPages(setup, "workPackages", "9001"));
    }

    private List<String> searchLinkedPages(TestUtils setup, String entityType, String id) throws Exception
    {
        SearchResults results =
            setup.rest().getResource("/openproject/links/{entityType}/{id}", Map.of(), entityType, id);
        return results.getSearchResults().stream().map(SearchResult::getPageFullName).collect(Collectors.toList());
    }
}
