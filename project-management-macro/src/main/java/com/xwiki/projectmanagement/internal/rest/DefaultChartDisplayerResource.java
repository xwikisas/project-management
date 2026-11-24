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
package com.xwiki.projectmanagement.internal.rest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.rest.XWikiResource;
import org.xwiki.rest.XWikiRestException;

import com.xwiki.projectmanagement.chart.displayer.ChartTypeDisplayer;
import com.xwiki.projectmanagement.chart.model.ChartDisplayerParameterInfo;
import com.xwiki.projectmanagement.chart.rest.ChartDisplayerResource;

/**
 * Resource for retrieving information about the existing {@link ChartTypeDisplayer}.
 *
 * @version $Id$
 * @since 1.2.0-rc-1
 */
@Singleton
@Component
@Named("com.xwiki.projectmanagement.internal.rest.DefaultChartDisplayerResource")
public class DefaultChartDisplayerResource extends XWikiResource implements ChartDisplayerResource
{
    /**
     * The translation keys are prefixed with the client if it has its own texts (e.g. "work packages" for
     * OpenProject), otherwise with this generic prefix.
     */
    private static final String GENERIC_PREFIX = "projectmanagement";

    private static final String TRANSLATION_KEY_FORMAT = "chart.%s.parameter.%s";

    /**
     * The labels of the values of a parameter are shared by the chart types, like the values.
     */
    private static final String VALUE_TRANSLATION_KEY_FORMAT = "chart.parameter.%s.value.%s";

    @Inject
    private ComponentManager componentManager;

    @Inject
    private ContextualLocalizationManager l10n;

    @Override
    public List<ChartDisplayerParameterInfo> getChartDisplayerInfo(String type, String client)
        throws XWikiRestException
    {
        try {
            List<ChartDisplayerParameterInfo> parameterInfos = new ArrayList<>();
            ChartTypeDisplayer chartDisplayer = componentManager.getInstance(ChartTypeDisplayer.class, type);

            for (Map.Entry<String, Object> parameter : chartDisplayer.getParameterTypeTemplate().entrySet()) {
                ChartDisplayerParameterInfo parameterInfo = new ChartDisplayerParameterInfo();
                parameterInfo.setId(parameter.getKey());
                List<String> values = chartDisplayer.getParameterTypeValues().get(parameter.getKey());
                parameterInfo.setValues(values);
                parameterInfo.setValueLabels(getValueLabels(parameter.getKey(), values, client));
                parameterInfo.setMultiple(parameter.getValue() instanceof Collection);
                String labelKey = String.format(TRANSLATION_KEY_FORMAT, type, parameter.getKey());
                parameterInfo.setLabel(translate(labelKey, client));
                parameterInfo.setDescription(translate(labelKey + ".description", client));

                parameterInfos.add(parameterInfo);
            }
            return parameterInfos;
        } catch (ComponentLookupException e) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
    }

    private Map<String, String> getValueLabels(String parameter, List<String> values, String client)
    {
        if (values == null) {
            return null;
        }
        Map<String, String> valueLabels = new LinkedHashMap<>();
        for (String value : values) {
            String label = translate(String.format(VALUE_TRANSLATION_KEY_FORMAT, parameter, value), client);
            valueLabels.put(value, label != null ? label : value);
        }
        return valueLabels;
    }

    private String translate(String key, String client)
    {
        if (StringUtils.isNotEmpty(client)) {
            String translation = l10n.getTranslationPlain(client + '.' + key);
            if (translation != null) {
                return translation;
            }
        }
        return l10n.getTranslationPlain(GENERIC_PREFIX + '.' + key);
    }
}
