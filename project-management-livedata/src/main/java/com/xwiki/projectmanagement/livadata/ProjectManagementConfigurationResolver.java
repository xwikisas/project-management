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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.livedata.LiveDataActionDescriptor;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataPropertyDescriptor;
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
        return translate(mergedConfig);
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
