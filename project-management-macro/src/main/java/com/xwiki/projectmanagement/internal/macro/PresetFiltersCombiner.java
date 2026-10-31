package com.xwiki.projectmanagement.internal.macro;

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.macro.MacroExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xwiki.projectmanagement.presets.Preset;
import com.xwiki.projectmanagement.presets.PresetsManager;

/**
 * Combines the filters of a preset with the filters of a work items macro, so that the macro can narrow down the
 * preset. Both are serialized Live Data configurations ({@code {"query":{"filters":[...]}}}), or arrays of them for
 * the charts having several datasets.
 * <p>
 * The filters of the two sides are combined with AND: a property filtered by only one side keeps its filter, and a
 * property filtered by both sides gets a single filter holding the constraints of the two sides, with
 * {@code matchAll} set so that all of them have to match. A single filter per property is kept since the Live Data
 * user interface handles only one filter per property.
 * <p>
 * When one side defines several datasets, the filters of the other side are applied to each of them. Both sides can't
 * define several datasets.
 *
 * @version $Id$
 * @since 1.4.0
 */
@Component(roles = PresetFiltersCombiner.class)
@Singleton
public class PresetFiltersCombiner
{
    private static final String QUERY = "query";

    private static final String FILTERS = "filters";

    private static final String PROPERTY = "property";

    private static final String CONSTRAINTS = "constraints";

    private static final String MATCH_ALL = "matchAll";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    private PresetsManager presetsManager;

    /**
     * @param presetId the id of the preset selected in the macro, if any
     * @param filters the filters of the macro, if any
     * @return the filters of the macro combined with the filters of the preset, or the filters of the macro when no
     *     preset is selected
     * @throws MacroExecutionException if the preset id is not valid, if the preset doesn't exist or if the filters
     *     can't be combined
     */
    public String combine(String presetId, String filters) throws MacroExecutionException
    {
        if (StringUtils.isEmpty(presetId)) {
            return filters;
        }
        Preset preset;
        try {
            preset = this.presetsManager.getPreset(Integer.parseInt(presetId));
        } catch (NumberFormatException e) {
            throw new MacroExecutionException("The preset id should be a number.");
        }
        if (preset == null) {
            throw new MacroExecutionException(String.format("There is no preset with id [%s].", presetId));
        }
        try {
            return combineFilters(preset.getFilter(), filters);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new MacroExecutionException(
                String.format("Failed to combine the filters of the preset [%s] with the filters of the macro.",
                    presetId), e);
        }
    }

    private String combineFilters(String presetFilters, String macroFilters)
        throws JsonProcessingException, MacroExecutionException
    {
        if (StringUtils.isBlank(presetFilters) || StringUtils.isBlank(macroFilters)) {
            return StringUtils.isBlank(presetFilters) ? macroFilters : presetFilters;
        }
        JsonNode presetNode = this.objectMapper.readTree(presetFilters);
        JsonNode macroNode = this.objectMapper.readTree(macroFilters);
        List<JsonNode> presetConfigs = getConfigurations(presetNode);
        List<JsonNode> macroConfigs = getConfigurations(macroNode);
        if (presetConfigs.isEmpty() || macroConfigs.isEmpty()) {
            return presetConfigs.isEmpty() ? macroFilters : presetFilters;
        }

        ArrayNode combined = combineConfigurations(presetConfigs, macroConfigs);
        // A single configuration is kept as is, unless one side defines datasets.
        JsonNode result = presetNode.isArray() || macroNode.isArray() ? combined : combined.get(0);
        return this.objectMapper.writeValueAsString(result);
    }

    private ArrayNode combineConfigurations(List<JsonNode> presetConfigs, List<JsonNode> macroConfigs)
        throws MacroExecutionException
    {
        if (presetConfigs.size() > 1 && macroConfigs.size() > 1) {
            throw new MacroExecutionException(
                "The filters of the macro and the preset can't both define several datasets.");
        }
        int count = Math.max(presetConfigs.size(), macroConfigs.size());
        ArrayNode combined = this.objectMapper.createArrayNode();
        for (int i = 0; i < count; i++) {
            combined.add(combineConfiguration(presetConfigs.get(Math.min(i, presetConfigs.size() - 1)),
                macroConfigs.get(Math.min(i, macroConfigs.size() - 1))));
        }
        return combined;
    }

    private List<JsonNode> getConfigurations(JsonNode node)
    {
        List<JsonNode> configurations = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(configurations::add);
        } else {
            configurations.add(node);
        }
        configurations.forEach(this::checkObject);
        return configurations;
    }

    private void checkObject(JsonNode node)
    {
        if (!node.isObject()) {
            throw new IllegalArgumentException(String.format("Expected a JSON object but got [%s].", node));
        }
    }

    private ObjectNode combineConfiguration(JsonNode presetConfig, JsonNode macroConfig)
    {
        // The other settings of the macro take precedence over the ones of the preset.
        ObjectNode result = (ObjectNode) presetConfig.deepCopy();
        ObjectNode query = getObject(result, QUERY);
        macroConfig.fields().forEachRemaining(field -> {
            if (!QUERY.equals(field.getKey())) {
                result.set(field.getKey(), field.getValue().deepCopy());
            }
        });
        macroConfig.path(QUERY).fields().forEachRemaining(field -> {
            if (!FILTERS.equals(field.getKey())) {
                query.set(field.getKey(), field.getValue().deepCopy());
            }
        });
        query.set(FILTERS, combineFilterList(presetConfig.path(QUERY).path(FILTERS),
            macroConfig.path(QUERY).path(FILTERS)));
        return result;
    }

    private ObjectNode getObject(ObjectNode parent, String key)
    {
        JsonNode child = parent.get(key);
        if (child instanceof ObjectNode) {
            return (ObjectNode) child;
        }
        return parent.putObject(key);
    }

    private ArrayNode combineFilterList(JsonNode presetFilters, JsonNode macroFilters)
    {
        ArrayNode result = this.objectMapper.createArrayNode();
        Map<String, ObjectNode> filtersByProperty = new LinkedHashMap<>();
        for (JsonNode filter : presetFilters) {
            checkObject(filter);
            ObjectNode copy = (ObjectNode) filter.deepCopy();
            result.add(copy);
            filtersByProperty.putIfAbsent(filter.path(PROPERTY).asText(), copy);
        }
        for (JsonNode filter : macroFilters) {
            checkObject(filter);
            ObjectNode presetFilter = filtersByProperty.get(filter.path(PROPERTY).asText());
            if (presetFilter == null) {
                result.add(filter.deepCopy());
            } else {
                mergeConstraints(presetFilter, filter);
            }
        }
        return result;
    }

    private void mergeConstraints(ObjectNode presetFilter, JsonNode macroFilter)
    {
        JsonNode macroConstraints = macroFilter.path(CONSTRAINTS);
        if (macroConstraints.isEmpty()) {
            return;
        }
        JsonNode presetConstraints = presetFilter.path(CONSTRAINTS);
        ArrayNode constraints = this.objectMapper.createArrayNode();
        presetConstraints.forEach(constraint -> constraints.add(constraint.deepCopy()));
        macroConstraints.forEach(constraint -> constraints.add(constraint.deepCopy()));
        if (presetConstraints.isEmpty()) {
            // Only the macro restricts this property, so its own semantics are kept.
            if (macroFilter.has(MATCH_ALL)) {
                presetFilter.set(MATCH_ALL, macroFilter.get(MATCH_ALL).deepCopy());
            }
        } else {
            presetFilter.put(MATCH_ALL, true);
        }
        presetFilter.set(CONSTRAINTS, constraints);
    }
}
