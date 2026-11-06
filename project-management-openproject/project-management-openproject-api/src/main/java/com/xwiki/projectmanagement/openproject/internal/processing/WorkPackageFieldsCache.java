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
package com.xwiki.projectmanagement.openproject.internal.processing;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheException;
import org.xwiki.cache.CacheManager;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.cache.eviction.EntryEvictionConfiguration;
import org.xwiki.cache.eviction.LRUEvictionConfiguration;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Disposable;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessStatus;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

/**
 * Caches the work package fields of the OpenProject instances, which are needed for each request on work packages
 * (to display their fields and to map the filters and the sort) but rarely change.
 * <p>
 * The fields are cached per connection and user, since the custom fields a user sees depend on their rights in
 * OpenProject (the users who aren't connected share the fields of the public data). Whether a field holds several
 * values doesn't depend on the user, so it's cached per connection.
 *
 * @version $Id$
 * @since 1.4.0
 */
@Component(roles = WorkPackageFieldsCache.class)
@Singleton
public class WorkPackageFieldsCache implements Initializable, Disposable
{
    /**
     * How long the fields stay cached, in seconds: a field added to OpenProject shows up after at most this time.
     */
    static final int LIFESPAN = 5 * 60;

    private static final int MAX_ENTRIES = 1000;

    private static final String CACHE_ID_PREFIX = "projectmanagement.openproject.workPackageFields.";

    /**
     * The prefix of the type of the values of a filter taking several values. Both the single-value and the
     * multi-value list fields have such filters.
     */
    private static final String LIST_TYPE_PREFIX = "[]";

    @Inject
    private CacheManager cacheManager;

    @Inject
    private Provider<XWikiContext> contextProvider;

    private Cache<OpenProjectWorkItemFields> fields;

    /**
     * Whether each list field is multi-value, keyed by field, per connection.
     */
    private Cache<Map<String, Boolean>> multiValueFields;

    /**
     * The fields being retrieved, so that the macros of a page rendered at the same time share one request.
     */
    private final Map<String, CompletableFuture<OpenProjectWorkItemFields>> pendingFields = new ConcurrentHashMap<>();

    @Override
    public void initialize() throws InitializationException
    {
        try {
            this.fields = createCache(CACHE_ID_PREFIX + "fields");
            this.multiValueFields = createCache(CACHE_ID_PREFIX + "multiValue");
        } catch (CacheException e) {
            throw new InitializationException("Failed to create the cache of the OpenProject work package fields.",
                e);
        }
    }

    @Override
    public void dispose()
    {
        this.fields.dispose();
        this.multiValueFields.dispose();
    }

    /**
     * @param connectionName the name of the OpenProject connection
     * @param access how the current user reaches the connection
     * @return the work package fields the current user can see through the connection
     * @throws ProjectManagementException if the fields couldn't be retrieved
     */
    public OpenProjectWorkItemFields getFields(String connectionName, OpenProjectAccessResult access)
        throws ProjectManagementException
    {
        XWikiContext xcontext = this.contextProvider.get();
        String user = access.getStatus() == OpenProjectAccessStatus.AUTHENTICATED
            ? String.valueOf(xcontext.getUserReference()) : "";
        String key = getKey(xcontext.getWikiId(), connectionName, user);
        OpenProjectWorkItemFields cachedFields = this.fields.get(key);
        if (cachedFields != null) {
            return cachedFields;
        }

        CompletableFuture<OpenProjectWorkItemFields> future = new CompletableFuture<>();
        CompletableFuture<OpenProjectWorkItemFields> pending = this.pendingFields.putIfAbsent(key, future);
        if (pending != null) {
            return join(pending);
        }
        try {
            OpenProjectWorkItemFields retrievedFields = OpenProjectWorkItemFields.of(access.getClient());
            this.fields.set(key, retrievedFields);
            future.complete(retrievedFields);
            return retrievedFields;
        } catch (ProjectManagementException | RuntimeException e) {
            future.completeExceptionally(e);
            throw e;
        } finally {
            this.pendingFields.remove(key);
        }
    }

    /**
     * @param connectionName the name of the OpenProject connection
     * @param client the client to use to find out which fields are multi-value, when they aren't cached yet
     * @param workItemFields the fields of the instance, as returned by {@link #getFields}
     * @return the keys of the fields holding several values in a work package (e.g. the target versions or a
     *     multi-value list custom field)
     * @throws ProjectManagementException if the work package schemas couldn't be retrieved
     */
    public Set<String> getMultiValueFields(String connectionName, OpenProjectApiClient client,
        OpenProjectWorkItemFields workItemFields) throws ProjectManagementException
    {
        // A filter on a list field takes several values, whether the field holds one value or several.
        Set<String> listFields = workItemFields.getFields().values().stream()
            .filter(field -> StringUtils.startsWith(field.getFilterValueType(), LIST_TYPE_PREFIX))
            .map(WorkPackageField::getKey)
            .collect(Collectors.toSet());
        if (listFields.isEmpty()) {
            return Collections.emptySet();
        }

        String key = getKey(this.contextProvider.get().getWikiId(), connectionName);
        Map<String, Boolean> known = this.multiValueFields.get(key);
        if (known == null) {
            known = Collections.emptyMap();
        }
        Set<String> unknown = new HashSet<>(listFields);
        unknown.removeAll(known.keySet());
        if (!unknown.isEmpty()) {
            // E.g. a custom field the users who looked up the fields before couldn't see.
            Map<String, Boolean> found = client.getMultiValueWorkPackageFields(unknown);
            Map<String, Boolean> updated = new HashMap<>(known);
            for (String field : unknown) {
                // The fields not found in the schemas of the first projects are considered single-value.
                updated.put(field, Boolean.TRUE.equals(found.get(field)));
            }
            known = Collections.unmodifiableMap(updated);
            this.multiValueFields.set(key, known);
        }

        Map<String, Boolean> multiValue = known;
        return listFields.stream().filter(field -> Boolean.TRUE.equals(multiValue.get(field)))
            .collect(Collectors.toSet());
    }

    /**
     * Removes the cached fields, e.g. when a connection changes.
     */
    public void clear()
    {
        this.fields.removeAll();
        this.multiValueFields.removeAll();
    }

    private static OpenProjectWorkItemFields join(CompletableFuture<OpenProjectWorkItemFields> pending)
        throws ProjectManagementException
    {
        try {
            return pending.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof ProjectManagementException) {
                throw (ProjectManagementException) e.getCause();
            } else if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new ProjectManagementException("Failed to retrieve the OpenProject work package fields.",
                e.getCause());
        }
    }

    /**
     * @return a key made of the given parts, each prefixed by its length so that no two lists of parts give the same
     *     key
     */
    private static String getKey(String... parts)
    {
        StringBuilder key = new StringBuilder();
        for (String part : parts) {
            String value = StringUtils.defaultString(part);
            key.append(value.length()).append(':').append(value);
        }
        return key.toString();
    }

    private <T> Cache<T> createCache(String cacheId) throws CacheException
    {
        CacheConfiguration cacheConfiguration = new CacheConfiguration();
        cacheConfiguration.setConfigurationId(cacheId);
        LRUEvictionConfiguration lru = new LRUEvictionConfiguration();
        lru.setLifespan(LIFESPAN);
        lru.setMaxEntries(MAX_ENTRIES);
        cacheConfiguration.put(EntryEvictionConfiguration.CONFIGURATIONID, lru);
        return this.cacheManager.createNewCache(cacheConfiguration);
    }
}
