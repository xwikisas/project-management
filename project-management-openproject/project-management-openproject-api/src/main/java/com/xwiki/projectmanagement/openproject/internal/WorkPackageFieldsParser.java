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
package com.xwiki.projectmanagement.openproject.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

/**
 * Reads the fields of the work packages from the responses of the OpenProject API: their description, from the query
 * form, and their values, from the work packages.
 *
 * @version $Id$
 * @since 1.4.0
 */
public final class WorkPackageFieldsParser
{
    private static final String EMBEDDED = "_embedded";

    private static final String LINKS = "_links";

    private static final String ELEMENTS = "elements";

    private static final String ALLOWED_VALUES = "allowedValues";

    private static final String HREF = "href";

    private static final String TITLE = "title";

    private static final String VALUES = "values";

    private static final String PROPERTY_COLUMN_TYPE = "QueryColumn::Property";

    /**
     * The fields whose value is the amount of work, in hours, as an ISO 8601 duration (e.g. {@code PT8H}).
     */
    private static final Set<String> WORK_FIELDS = Set.of("estimatedTime", "derivedEstimatedTime", "remainingTime",
        "derivedRemainingTime", "spentTime");

    /**
     * The field whose value is the number of days between the start and the finish dates, as an ISO 8601 duration
     * (e.g. {@code P3D}).
     */
    private static final String DURATION_FIELD = "duration";

    /**
     * The resources of the API that have a page in the OpenProject UI, at the same path without the API prefix.
     */
    private static final Pattern UI_RESOURCE_PATTERN =
        Pattern.compile("^/api/v3(/(?:users|versions|work_packages|projects)/[^/]+)$");

    private WorkPackageFieldsParser()
    {
    }

    /**
     * Reads the fields that can be displayed in a work package query from the response to a query form request.
     *
     * @param formNode the response of {@code POST /api/v3/queries/form}
     * @return the fields that are columns holding a work package property, with the filter matching them
     */
    public static List<WorkPackageField> parseQueryForm(JsonNode formNode)
    {
        JsonNode schemaNode = formNode.path(EMBEDDED).path("schema");
        Set<String> sortableKeys = getSortableKeys(schemaNode);
        Map<String, JsonNode> filterSchemas = getFilterSchemas(schemaNode);

        List<WorkPackageField> fields = new ArrayList<>();
        for (JsonNode columnNode : schemaNode.path("columns").path(EMBEDDED).path(ALLOWED_VALUES)) {
            if (!PROPERTY_COLUMN_TYPE.equals(columnNode.path("_type").asText())) {
                continue;
            }
            WorkPackageField field = new WorkPackageField();
            String key = columnNode.path("id").asText();
            field.setKey(key);
            field.setName(columnNode.path("name").asText(key));
            field.setSortable(sortableKeys.contains(key));
            setFilter(field, filterSchemas);
            fields.add(field);
        }
        return fields;
    }

    /**
     * Reads the values of the fields of a work package, apart from the ones that are links without a title (e.g. the
     * actions) or that have no value.
     *
     * @param workPackageNode the work package, as returned by the OpenProject API
     * @param connectionUrl the URL of the OpenProject instance, used to link the values to their page
     * @return the values, keyed by field: a {@link Linkable} for a link, a list of {@link Linkable} for a list of
     *     links and a {@link String} for the other values
     */
    public static Map<String, Object> parseFieldValues(JsonNode workPackageNode, String connectionUrl)
    {
        Map<String, Object> values = new HashMap<>();

        Iterator<Map.Entry<String, JsonNode>> attributes = workPackageNode.fields();
        while (attributes.hasNext()) {
            Map.Entry<String, JsonNode> attribute = attributes.next();
            if (!attribute.getKey().startsWith("_")) {
                String value = toText(attribute.getKey(), attribute.getValue());
                if (value != null) {
                    values.put(attribute.getKey(), value);
                }
            }
        }

        Iterator<Map.Entry<String, JsonNode>> links = workPackageNode.path(LINKS).fields();
        while (links.hasNext()) {
            Map.Entry<String, JsonNode> link = links.next();
            Object value = toLinkables(link.getValue(), connectionUrl);
            if (value != null) {
                values.put(link.getKey(), value);
            }
        }
        return values;
    }

    private static Set<String> getSortableKeys(JsonNode schemaNode)
    {
        Set<String> keys = new HashSet<>();
        for (JsonNode sortByNode : schemaNode.path("sortBy").path(EMBEDDED).path(ALLOWED_VALUES)) {
            keys.add(getLastPathSegment(sortByNode.path(LINKS).path("column").path(HREF).asText()));
        }
        return keys;
    }

    private static Map<String, JsonNode> getFilterSchemas(JsonNode schemaNode)
    {
        Map<String, JsonNode> filterSchemas = new LinkedHashMap<>();
        for (JsonNode filterSchema : schemaNode.path(EMBEDDED).path("filtersSchemas").path(EMBEDDED)
            .path(ELEMENTS))
        {
            filterSchemas.put(getLastPathSegment(filterSchema.path(LINKS).path("self").path(HREF).asText()),
                filterSchema);
        }
        return filterSchemas;
    }

    private static void setFilter(WorkPackageField field, Map<String, JsonNode> filterSchemas)
    {
        String key = field.getKey();
        // The filter of a multi-value field can be named in the singular, e.g. the targetVersion filter matches the
        // targetVersions field.
        String filterKey = filterSchemas.containsKey(key) ? key : null;
        if (filterKey == null && key.endsWith("s") && filterSchemas.containsKey(key.substring(0, key.length() - 1))) {
            filterKey = key.substring(0, key.length() - 1);
        }
        if (filterKey == null) {
            return;
        }
        field.setFilterKey(filterKey);

        List<String> operators = new ArrayList<>();
        for (JsonNode dependencies : filterSchemas.get(filterKey).path("_dependencies")) {
            Iterator<Map.Entry<String, JsonNode>> operatorDependencies = dependencies.path("dependencies").fields();
            while (operatorDependencies.hasNext()) {
                Map.Entry<String, JsonNode> operatorDependency = operatorDependencies.next();
                operators.add(
                    URLDecoder.decode(getLastPathSegment(operatorDependency.getKey()), StandardCharsets.UTF_8));
                JsonNode valueType = operatorDependency.getValue().path(VALUES).path("type");
                if (field.getFilterValueType() == null && valueType.isTextual()) {
                    field.setFilterValueType(valueType.asText());
                }
            }
        }
        field.setFilterOperators(operators);
    }

    private static String toText(String key, JsonNode value)
    {
        String text = null;
        if (value.isObject()) {
            // A formattable text, e.g. the value of a long text custom field.
            JsonNode raw = value.path("raw");
            text = raw.isTextual() ? raw.asText() : null;
        } else if (value.isValueNode() && !value.isNull()) {
            text = value.asText();
            if (WORK_FIELDS.contains(key)) {
                text = formatDuration(text, false);
            } else if (DURATION_FIELD.equals(key)) {
                text = formatDuration(text, true);
            }
        }
        return StringUtils.isEmpty(text) ? null : text;
    }

    private static String formatDuration(String isoDuration, boolean inDays)
    {
        try {
            Duration duration = Duration.parse(isoDuration);
            if (inDays) {
                return String.format("%sd", formatNumber(BigDecimal.valueOf(duration.toHours()).divide(
                    BigDecimal.valueOf(24), 2, RoundingMode.HALF_UP)));
            }
            return String.format("%sh", formatNumber(BigDecimal.valueOf(duration.toMinutes()).divide(
                BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)));
        } catch (DateTimeParseException e) {
            return isoDuration;
        }
    }

    private static String formatNumber(BigDecimal number)
    {
        BigDecimal stripped = number.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
    }

    private static Object toLinkables(JsonNode linkNode, String connectionUrl)
    {
        if (linkNode.isArray()) {
            List<Linkable> linkables = new ArrayList<>();
            for (JsonNode element : linkNode) {
                Linkable linkable = toLinkable(element, connectionUrl);
                if (linkable != null) {
                    linkables.add(linkable);
                }
            }
            return linkables.isEmpty() ? null : linkables;
        }
        return toLinkable(linkNode, connectionUrl);
    }

    private static Linkable toLinkable(JsonNode linkNode, String connectionUrl)
    {
        JsonNode title = linkNode.path(TITLE);
        if (!title.isTextual() || title.asText().isEmpty()) {
            return null;
        }
        Matcher matcher = UI_RESOURCE_PATTERN.matcher(linkNode.path(HREF).asText());
        return new Linkable(title.asText(), matcher.matches() ? connectionUrl + matcher.group(1) : null);
    }

    private static String getLastPathSegment(String path)
    {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
