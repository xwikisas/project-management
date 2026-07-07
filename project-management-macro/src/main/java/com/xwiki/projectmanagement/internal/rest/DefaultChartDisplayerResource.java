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
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

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
    private static final String TRANSLATION_KEY_FORMAT = "projectmanagement.chart.%s.parameter.%s";

    @Inject
    private ComponentManager componentManager;

    @Inject
    private ContextualLocalizationManager l10n;

    @Override
    public List<ChartDisplayerParameterInfo> getChartDisplayerInfo(String type) throws XWikiRestException
    {
        try {
            List<ChartDisplayerParameterInfo> parameterInfos = new ArrayList<>();
            ChartTypeDisplayer chartDisplayer = componentManager.getInstance(ChartTypeDisplayer.class, type);

            for (Map.Entry<String, Object> parameter : chartDisplayer.getParameterTypeTemplate().entrySet()) {
                ChartDisplayerParameterInfo parameterInfo = new ChartDisplayerParameterInfo();
                parameterInfo.setId(parameter.getKey());
                parameterInfo.setValues(chartDisplayer.getParameterTypeValues().get(parameter.getKey()));
                parameterInfo.setMultiple(parameter.getValue() instanceof Collection);
                String labelTranslation =
                    l10n.getTranslationPlain(String.format(TRANSLATION_KEY_FORMAT, type, parameter.getKey()));
                parameterInfo.setLabel(labelTranslation);
                String descriptionTranslation = l10n.getTranslationPlain(
                    String.format(TRANSLATION_KEY_FORMAT, type, parameter.getKey()) + ".description");
                parameterInfo.setDescription(descriptionTranslation);

                parameterInfos.add(parameterInfo);
            }
            return parameterInfos;
        } catch (ComponentLookupException e) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
    }
}
