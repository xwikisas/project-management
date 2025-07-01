package com.xwiki.projectmanagement.internal;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rest.XWikiRestComponent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.exception.WorkItemCreationException;
import com.xwiki.projectmanagement.exception.WorkItemDeletionException;
import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.exception.WorkItemUpdatingException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Test client retrieving entries from local files and other sources.
 *
 * @version $Id$
 */
@Component
@Named("test")
@Singleton
public class TestClient implements ProjectManagementClient
{
    // For testing. REMOVE
    @Inject
    @Named("com.xwiki.projectmanagement.internal.DefaultWorkItemsResource")
    private XWikiRestComponent workItemsResource;

    @Override
    public WorkItem getWorkItem(String workItemId) throws WorkItemNotFoundException
    {
        throw new NotImplementedException();
    }

    @Override
    public PaginatedResult<WorkItem> getWorkItems(int page, int pageSize, List<LiveDataQuery.Filter> filters,
        List<LiveDataQuery.SortEntry> sortEntries)
        throws WorkItemRetrievalException
    {
        PaginatedResult<WorkItem> result = new PaginatedResult<>();
        // GET ENTRIES FROM RESOURCE ---------------
        try {

            PaginatedResult<WorkItem> paginatedResult =
                (PaginatedResult<WorkItem>) ((DefaultWorkItemsResource) workItemsResource).getWorkItems("wiki",
                    "smth",
                    0, 10).getEntity();
            result.getItems().addAll(paginatedResult.getItems());
        } catch (Exception e) {

        }

        // GET ENTRIES FROM TEST FILE --------------
        Map<String, WorkItem> newDb = maybeGetTestEntries();
        if (newDb != null) {
            List<WorkItem> workItems = new ArrayList<>(newDb.values());
            result.getItems().addAll(workItems);
        }

        return result;
    }

    @Override
    public WorkItem createWorkItem(WorkItem workItem) throws WorkItemCreationException
    {
        throw new NotImplementedException();
    }

    @Override
    public WorkItem updateWorkItem(WorkItem workItem) throws WorkItemUpdatingException
    {
        throw new NotImplementedException();
    }

    @Override
    public boolean deleteWorkItem(String workItemId) throws WorkItemDeletionException
    {
        return false;
    }

    private static Map<String, WorkItem> maybeGetTestEntries()
    {
        String testPath = "/home/teo/Desktop/customLiveDataStore.json";
        File testFile = new File(testPath);
        String foundEntriesJSON = "";
        Map<String, WorkItem> newDb = null;
        if (testFile.exists()) {
            try (InputStream testFileInputStream = new FileInputStream(testFile)) {
                foundEntriesJSON = IOUtils.toString(testFileInputStream, Charset.defaultCharset());

                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode root = objectMapper.readTree(foundEntriesJSON);
                newDb = objectMapper.readerFor(new TypeReference<Map<String, WorkItem>>()
                {
                }).readValue(root);
            } catch (Exception e) {
            }
        }
        return newDb;
    }
}
