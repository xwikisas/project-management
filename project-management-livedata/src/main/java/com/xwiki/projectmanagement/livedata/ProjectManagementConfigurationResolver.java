package com.xwiki.projectmanagement.livedata;

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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.livedata.LiveDataActionDescriptor;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataMeta;
import org.xwiki.livedata.LiveDataPropertyDescriptor;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.livedata.internal.JSONMerge;
import org.xwiki.localization.ContextualLocalizationManager;

/**
 * Merges an input livedata configuration with the generic project management configuration and with the configuration
 * coming from the {@link com.xwiki.projectmanagement.ProjectManagementClient} implementation.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("projectmanagement")
public class ProjectManagementConfigurationResolver implements LiveDataConfigurationResolver<LiveDataConfiguration>
{
    private static final String PREFIX_PROPERTY = "property.";

    private static final CharSequence PREFIX_ACTION = "action.";

    private static final String SUFFIX_HINT = ".hint";

    @Inject
    private Logger logger;

    @Inject
    private ContextualLocalizationManager l10n;

    @Inject
    private ComponentManager componentManager;

    @Inject
    private LiveDataConfigurationResolver<String> stringLiveDataConfigResolver;

    private String defaultSerializedConfig;

    private final Map<String, String> clientSerializedConfigs = new HashMap<>();

    private final JSONMerge jsonMerge = new JSONMerge();

    @Override
    public LiveDataConfiguration resolve(LiveDataConfiguration input) throws LiveDataException
    {
        LiveDataConfiguration defaultConfig = getDefaultConfiguration();

        defaultConfig = translate(defaultConfig);

        defaultConfig.setId(input.getId());

        LiveDataConfiguration clientConfig = maybeGetClientConfiguration(input);

        LiveDataConfiguration mergedConfig = null;
        // We want the returned configuration to have the properties coming from the input configuration (if any is
        // specified). Otherwise, the properties should have the values defined in the client configuration.
        // Otherwise, the values coming from the default configuration.
        if (clientConfig != null) {
            mergedConfig = this.jsonMerge.merge(defaultConfig, clientConfig);
        }
        if (mergedConfig != null) {
            mergedConfig = this.jsonMerge.merge(mergedConfig, input);
        } else {
            mergedConfig = this.jsonMerge.merge(defaultConfig, input);
        }
        removeUnknownProperties(mergedConfig);
        addMissingSortAndFilterDescriptors(mergedConfig);
        return translate(mergedConfig);
    }

    /**
     * Removes from the query the properties without descriptor (e.g. a typo in the properties parameter of a macro),
     * since Live Data fails to display when a property of the query has no descriptor. When every property is unknown,
     * the table has no columns. The removed properties are logged at debug level only, since valid properties are
     * also unknown when the client can't provide their descriptors (e.g. the custom fields of an OpenProject instance
     * that the current user can't access).
     */
    private void removeUnknownProperties(LiveDataConfiguration config)
    {
        LiveDataQuery query = config.getQuery();
        if (query == null || query.getProperties() == null || query.getProperties().isEmpty()) {
            return;
        }
        Set<String> knownProperties = getKnownProperties(config);
        List<String> properties = new ArrayList<>();
        List<String> unknownProperties = new ArrayList<>();
        for (String property : query.getProperties()) {
            if (knownProperties.contains(property)) {
                properties.add(property);
            } else {
                unknownProperties.add(property);
            }
        }
        if (!unknownProperties.isEmpty()) {
            this.logger.debug("Ignoring the unknown work item properties {} of the Live Data [{}].", unknownProperties,
                config.getId());
            query.setProperties(properties);
        }
    }

    /**
     * Adds a descriptor to the properties that are sorted or filtered without having one, since the Live Data sort and
     * filter panels fail to display a sort or filter entry on a property without descriptor. Such a property isn't
     * necessarily unknown to the client: it can be the name the client gives to the property (e.g. an OpenProject
     * column, such as {@code updatedAt}), or a property whose descriptor the client couldn't provide (e.g. a custom
     * field of an OpenProject instance that couldn't be reached). So the sort and filter entries are kept, and left to
     * the client, and the added descriptor only names the property after its id. It isn't displayed as a column, since
     * the properties without descriptor are removed from the displayed properties.
     */
    private void addMissingSortAndFilterDescriptors(LiveDataConfiguration config)
    {
        LiveDataQuery query = config.getQuery();
        if (query == null) {
            return;
        }
        Map<String, LiveDataPropertyDescriptor> missingDescriptors = new LinkedHashMap<>();
        Set<String> knownProperties = getKnownProperties(config);
        if (query.getSort() != null) {
            for (LiveDataQuery.SortEntry sortEntry : query.getSort()) {
                addMissingDescriptor(sortEntry.getProperty(), knownProperties, missingDescriptors)
                    .ifPresent(descriptor -> descriptor.setSortable(true));
            }
        }
        if (query.getFilters() != null) {
            for (LiveDataQuery.Filter filter : query.getFilters()) {
                addMissingDescriptor(filter.getProperty(), knownProperties, missingDescriptors)
                    .ifPresent(descriptor -> descriptor.setFilterable(true));
            }
        }
        if (missingDescriptors.isEmpty()) {
            return;
        }
        if (config.getMeta() == null) {
            config.setMeta(new LiveDataMeta());
        }
        List<LiveDataPropertyDescriptor> descriptors = config.getMeta().getPropertyDescriptors() == null
            ? new ArrayList<>() : new ArrayList<>(config.getMeta().getPropertyDescriptors());
        descriptors.addAll(missingDescriptors.values());
        config.getMeta().setPropertyDescriptors(descriptors);
        this.logger.debug("Adding a descriptor to the work item properties {} sorted or filtered in the Live Data "
            + "[{}], which have none.", missingDescriptors.keySet(), config.getId());
    }

    private static Optional<LiveDataPropertyDescriptor> addMissingDescriptor(String property,
        Set<String> knownProperties, Map<String, LiveDataPropertyDescriptor> missingDescriptors)
    {
        if (property == null || knownProperties.contains(property)) {
            return Optional.empty();
        }
        return Optional.of(missingDescriptors.computeIfAbsent(property, id -> {
            LiveDataPropertyDescriptor descriptor = new LiveDataPropertyDescriptor();
            descriptor.setId(id);
            descriptor.setName(id);
            descriptor.setVisible(false);
            return descriptor;
        }));
    }

    private static Set<String> getKnownProperties(LiveDataConfiguration config)
    {
        return config.getMeta() == null || config.getMeta().getPropertyDescriptors() == null
            ? Set.of() : config.getMeta().getPropertyDescriptors().stream().map(LiveDataPropertyDescriptor::getId)
            .collect(Collectors.toSet());
    }

    private LiveDataConfiguration getDefaultConfiguration()
    {
        try {
            if (defaultSerializedConfig != null && !defaultSerializedConfig.isEmpty()) {
                return stringLiveDataConfigResolver.resolve(defaultSerializedConfig);
            }
            InputStream defaultConfigInputStream =
                getClass().getResourceAsStream("/projectManagementLiveDataConfiguration.json");
            if (defaultConfigInputStream == null) {
                this.defaultSerializedConfig = "";
            } else {
                this.defaultSerializedConfig = IOUtils.toString(defaultConfigInputStream, "UTF-8");
            }
            return stringLiveDataConfigResolver.resolve(defaultSerializedConfig);
        } catch (LiveDataException | IOException e) {
            return null;
        }
    }

    private LiveDataConfiguration maybeGetClientConfiguration(LiveDataConfiguration inputConfig)
    {
        String clientId = (String) inputConfig.getQuery().getSource().getParameters().get("client");
        if (clientId == null || clientId.isEmpty()) {
            return null;
        }
        try {
            Type clientCfgProviderType = new DefaultParameterizedType(null, LiveDataConfigurationResolver.class,
                LiveDataConfiguration.class);
            if (componentManager.hasComponent(clientCfgProviderType, clientId)) {
                // Either look for a provider implemented by the client.
                LiveDataConfigurationResolver<LiveDataConfiguration> clientCfgProvider =
                    componentManager.getInstance(clientCfgProviderType, clientId);
                return clientCfgProvider.resolve(inputConfig);
            } else {
                String serializedCfg = clientSerializedConfigs.get(clientId);
                // Or try to see if some configuration matching the pattern exists.
                if (serializedCfg == null) {
                    InputStream inputStream = getClass().getResourceAsStream(
                        String.format("/%sProjectManagementLiveDataConfiguration.json", clientId));
                    if (inputStream == null) {
                        return null;
                    }
                    serializedCfg = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                }
                // Store the serialized configurations in memory so we don't read from the file system everytime.
                clientSerializedConfigs.put(clientId, serializedCfg);
                return this.stringLiveDataConfigResolver.resolve(serializedCfg);
            }
        } catch (IOException | LiveDataException | ComponentLookupException e) {
            return null;
        }
    }

    private LiveDataConfiguration translate(LiveDataConfiguration mergedConfig)
    {
        String translationPrefix =
            (String) mergedConfig.getQuery().getSource().getParameters().get("translationPrefix");
        if (translationPrefix == null || translationPrefix.isEmpty()) {
            return mergedConfig;
        }
        for (LiveDataPropertyDescriptor property : mergedConfig.getMeta().getPropertyDescriptors()) {
            translateProperty(translationPrefix, property);
        }

        if (mergedConfig.getMeta().getActions() == null) {
            return mergedConfig;
        }
        for (LiveDataActionDescriptor action : mergedConfig.getMeta().getActions()) {
            translateAction(translationPrefix, action);
        }
        return mergedConfig;
    }

    private void translateAction(String translationPrefix, LiveDataActionDescriptor action)
    {
        String translationPlain =
            this.l10n.getTranslationPlain(String.join("", translationPrefix, PREFIX_ACTION, action.getId()));
        if (translationPlain != null) {
            action.setName(translationPlain);
        }
        if (action.getName() == null) {
            action.setName(action.getId());
        }
        if (action.getDescription() == null) {
            action.setDescription(
                this.l10n.getTranslationPlain(
                    String.join("", translationPrefix, PREFIX_ACTION, action.getId(), SUFFIX_HINT)));
        }
    }

    private void translateProperty(String translationPrefix, LiveDataPropertyDescriptor property)
    {
        String translationPlain =
            this.l10n.getTranslationPlain(String.join("", translationPrefix, PREFIX_PROPERTY, property.getId()));
        if (translationPlain != null) {
            property.setName(translationPlain);
        }
        if (property.getName() == null) {
            property.setName(property.getId());
        }
        if (property.getDescription() == null) {
            property.setDescription(
                this.l10n.getTranslationPlain(
                    String.join("", translationPrefix, PREFIX_PROPERTY, property.getId(), SUFFIX_HINT)));
        }
    }
}
