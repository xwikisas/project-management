package com.xwiki.projectmanagement.openproject.internal.livedata;

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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataPropertyDescriptor;

import com.xpn.xwiki.XWikiContext;

/**
 * Retrieves the open project livedata configuration and resolves additional information based on the input
 * configuration.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("openproject")
@Singleton
public class OpenProjectLivedataConfigurationResolver implements LiveDataConfigurationResolver<LiveDataConfiguration>
{
    private static final String FILTER_KEY_SEARCHURL = "searchURL";

    private static final String SOURCE_PARAMS_INSTANCE = "instance";

    @Inject
    private LiveDataConfigurationResolver<String> stringLiveDataConfigResolver;

    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> xWikiContextProvider;

    private String defaultConfigJSON;

    @Override
    public LiveDataConfiguration resolve(LiveDataConfiguration input) throws LiveDataException
    {
        try {
            if (defaultConfigJSON == null || defaultConfigJSON.isEmpty()) {
                InputStream defaultConfigInputStream =
                    getClass().getResourceAsStream("/openProjectLiveDataConfiguration.json");
                if (defaultConfigInputStream == null) {
                    this.defaultConfigJSON = "";
                } else {
                    this.defaultConfigJSON = IOUtils.toString(defaultConfigInputStream, "UTF-8");
                }
            }
            LiveDataConfiguration configuration = stringLiveDataConfigResolver.resolve(defaultConfigJSON);

            if (input.getQuery() != null
                && input.getQuery().getSource() != null
                && input.getQuery().getSource().getParameters() != null
                && input.getQuery().getSource().getParameters().get(SOURCE_PARAMS_INSTANCE) != null)
            {
                maybeUpdateSearchURL(configuration, input);
            }
            return configuration;
        } catch (IOException | LiveDataException e) {
            logger.error("Could not read the livedata configuration of the Open Project client.", e);
            return null;
        }
    }

    private void maybeUpdateSearchURL(LiveDataConfiguration configuration, LiveDataConfiguration input)
    {
        if (configuration.getMeta() == null || configuration.getMeta().getPropertyDescriptors() == null) {
            return;
        }
        String instance = (String) input.getQuery().getSource().getParameters().get(SOURCE_PARAMS_INSTANCE);
        String wiki = getWiki();
        for (LiveDataPropertyDescriptor propertyDescriptor : configuration.getMeta().getPropertyDescriptors()) {
            if (propertyDescriptor.getFilter() == null || !"list".equals(propertyDescriptor.getFilter().getId())) {
                continue;
            }
            String searchURL = (String) propertyDescriptor.getFilter().getParameters().get(FILTER_KEY_SEARCHURL);
            if (searchURL == null || searchURL.isEmpty()) {
                continue;
            }
            searchURL = searchURL.replace("{wikiName}", wiki).replace("{instance}", instance);
            propertyDescriptor.getFilter().getParameters().put(FILTER_KEY_SEARCHURL, searchURL);
        }
    }

    private String getWiki()
    {
        String wiki = "xwiki";
        XWikiContext context = xWikiContextProvider.get();
        if (context == null) {
            return wiki;
        }
        return context.getWikiId() == null || context.getWikiId().isEmpty() ? wiki : context.getWikiId();
    }
}
