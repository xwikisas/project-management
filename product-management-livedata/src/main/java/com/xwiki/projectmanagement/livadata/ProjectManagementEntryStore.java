package com.xwiki.projectmanagement.livadata;

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
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.descriptor.ComponentDescriptor;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveData;
import org.xwiki.livedata.LiveDataEntryStore;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.livadata.displayer.ProjectManagementPropertyDisplayer;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Something.
 *
 * @version $Id$
 */
@Component
@Named("projectmanagement")
@Singleton
public class ProjectManagementEntryStore implements LiveDataEntryStore
{
    private Map<String, Map<String, Object>> db = new HashMap<>();

    @Inject
    private ProjectManagementManager projectManagementManager;

    @Inject
    private ComponentManager componentManager;

    @Inject
    private Logger logger;

    /**
     * @param entryId identifies the entry to return
     * @return sadas
     * @throws LiveDataException sadasd
     */
    @Override
    public Optional<Map<String, Object>> get(Object entryId) throws LiveDataException
    {
        return Optional.of(db.get(entryId));
    }

    @Override
    public Optional<Object> get(Object entryId, String property) throws LiveDataException
    {
        return LiveDataEntryStore.super.get(entryId, property);
    }

    @Override
    public LiveData get(LiveDataQuery query) throws LiveDataException
    {
        LiveData ld = new LiveData();
        String clientId = (String) query.getSource().getParameters().getOrDefault("client", "");
        if (clientId.isEmpty()) {
            throw new LiveDataException("The client property was not specified in the source parameters.");
        }
        // GET ENTRIES FROM TEST FILE
        Map<String, Map<String, Object>> newDb = maybeGetTestEntries();
        if (newDb != null) {
            ld.setCount(newDb.size());
            ld.getEntries().addAll(new ArrayList<>(newDb.values()));
            return ld;
        }
        Set<Map.Entry<String, Map<String, Object>>> entrySet = db.entrySet();

        PaginatedResult<WorkItem> workItems = null;
        try {
            workItems = projectManagementManager.getWorkItems(clientId, Math.toIntExact(query.getOffset()), query.getLimit(),
                query.getFilters());
        } catch (WorkItemRetrievalException e) {
            throw new LiveDataException("Failed to retrieve the work items.", e);
        }
        ld.getEntries().addAll(workItems.getItems());
        // TODO: Add a method that modifies how the returned objects/properties are displayed. Maybe a
        //  ProjectManagementLivedataDisplayer with the hint = property that generates some html. For example, for
        //  the status property we might want a custom displayer in the case of JIRA that displays a nice icon. Hint
        //  might be jira.status.
        List<ComponentDescriptor<ProjectManagementPropertyDisplayer>> displayerDescriptors =
            componentManager.getComponentDescriptorList((Type) ProjectManagementPropertyDisplayer.class);
        for (ComponentDescriptor<ProjectManagementPropertyDisplayer> displayerDescriptor : displayerDescriptors) {
            if (displayerDescriptor.getRoleHint().startsWith(clientId)) {
                try {
                    componentManager.getInstance(ProjectManagementPropertyDisplayer.class,
                        displayerDescriptor.getRoleHint());
                } catch (ComponentLookupException e) {
                    logger.warn("Failed to find the project management livedata displayer with hint [{}].",
                        displayerDescriptor.getRoleHint());
                }
            }
        }

        return ld;
    }

    private static Map<String, Map<String, Object>> maybeGetTestEntries()
    {
        String testPath = "/home/teo/Desktop/customLiveDataStore.json";
        File testFile = new File(testPath);
        String foundEntriesJSON = "";
        Map<String, Map<String, Object>> newDb = null;
        if (testFile.exists()) {
            try (InputStream testFileInputStream = new FileInputStream(testFile)) {
                foundEntriesJSON = IOUtils.toString(testFileInputStream, Charset.defaultCharset());

                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode root = objectMapper.readTree(foundEntriesJSON);
                newDb = objectMapper.readerFor(new TypeReference<Map<String, Map<String, Object>>>()
                {
                }).readValue(root);
            } catch (Exception e) {
            }
        }
        return newDb;
    }

    @Override
    public Optional<Object> save(Map<String, Object> entry) throws LiveDataException
    {
        return LiveDataEntryStore.super.save(entry);
    }

    @Override
    public Optional<Object> update(Object entryId, String property, Object value) throws LiveDataException
    {
        return LiveDataEntryStore.super.update(entryId, property, value);
    }

    @Override
    public Optional<Map<String, Object>> remove(Object entryId) throws LiveDataException
    {
        return LiveDataEntryStore.super.remove(entryId);
    }
}
