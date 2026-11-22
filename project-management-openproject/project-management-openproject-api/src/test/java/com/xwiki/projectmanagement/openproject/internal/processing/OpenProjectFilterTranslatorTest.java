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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.WorkPackageFieldsResult;
import com.xwiki.projectmanagement.openproject.model.Version;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectFilterTranslator} and {@link OpenProjectFilters}.
 *
 * @version $Id$
 */
class OpenProjectFilterTranslatorTest
{
    private static final String TARGET_VERSIONS = "targetVersions";

    private static final String LIST_OF_VERSIONS = "[]Version";

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    final ObjectMapper mapper = new ObjectMapper();

    private final OpenProjectApiClient client = mock(OpenProjectApiClient.class);

    private final OpenProjectWorkItemFields fields = new OpenProjectWorkItemFields(List.of(
        field("id", "ID", "id", "[]WorkPackage"),
        field("status", "Status", "status", "[]Status"),
        field(TARGET_VERSIONS, "Target versions", "targetVersion", LIST_OF_VERSIONS),
        field("category", "Category", null, null),
        field("customField3", "Observed in", "customField3", LIST_OF_VERSIONS),
        field("customField6", "category", "customField6", "[1]String")));

    @Test
    void convertFiltersToOpenProjectJson() throws ProjectManagementException, JsonProcessingException
    {
        List<LiveDataQuery.Filter> filters = new ArrayList<>();

        LiveDataQuery.Filter filter1 = new LiveDataQuery.Filter("property1", "operator1", "value1");
        LiveDataQuery.Filter filter2 = new LiveDataQuery.Filter("property2", "operator2", "value2");

        filters.add(filter1);
        filters.add(filter2);

        String expected = "["
            + "{"
            + "\"property1\":{\"operator\":\"operator1\",\"values\":[\"value1\"]}},"
            + "{\"property2\":{\"operator\":\"operator2\",\"values\":[\"value2\"]}"
            + "}"
            + "]";
        String convertedFilters = translate(filters).toJson();

        assertEqualsStringFilters(expected, convertedFilters);
    }

    @Test
    void mergeFiltersGroupsConstraintsWithSameOperator() throws ProjectManagementException, JsonProcessingException
    {
        List<LiveDataQuery.Filter> filters = new ArrayList<>();

        LiveDataQuery.Filter filter1 = new LiveDataQuery.Filter();
        LiveDataQuery.Filter filter2 = new LiveDataQuery.Filter();

        LiveDataQuery.Constraint firstConstraint = new LiveDataQuery.Constraint("value1", "equals");
        LiveDataQuery.Constraint secondConstraint = new LiveDataQuery.Constraint("value2", "equals");
        // The Live Data offers the between operator only on the date properties, with a start/end value.
        LiveDataQuery.Constraint thirdConstraint = new LiveDataQuery.Constraint("2026-01-01/2026-02-01", "between");

        filter1.setProperty("identifier");
        filter1.getConstraints().add(firstConstraint);
        filter1.getConstraints().add(secondConstraint);

        filter2.setProperty("startDate");
        filter2.getConstraints().add(thirdConstraint);

        filters.add(filter1);
        filters.add(filter2);

        String expected = "["
            + "{"
            + "\"startDate\":{\"operator\":\"<>d\",\"values\":[\"2026-01-01\",\"2026-02-01\"]}"
            + "},"
            + "{"
            + "\"id\":{\"operator\":\"=\",\"values\":[\"value1\",\"value2\"]}}"
            + "]";
        String convertedFilters = translate(filters).mergeWithUrlFilters(null);

        assertEqualsStringFilters(expected, convertedFilters);
    }

    @Test
    void sameFilterDifferentOperatorsInLivedataFilters()
        throws ProjectManagementException, JsonProcessingException
    {
        List<LiveDataQuery.Filter> filters = new ArrayList<>();

        LiveDataQuery.Filter filter1 = new LiveDataQuery.Filter();
        LiveDataQuery.Filter filter2 = new LiveDataQuery.Filter();

        LiveDataQuery.Constraint firstConstraint = new LiveDataQuery.Constraint("value1", "operator1");
        LiveDataQuery.Constraint secondConstraint = new LiveDataQuery.Constraint("value2", "operator2");

        filter1.setProperty("property1");
        filter1.getConstraints().add(firstConstraint);

        filter2.setProperty("property1");
        filter2.getConstraints().add(secondConstraint);

        filters.add(filter1);
        filters.add(filter2);

        //we expect to have only the second filter converted
        String expected = "["
            + "{\"property1\":{\"operator\":\"operator2\",\"values\":[\"value2\"]}}"
            + "]";
        String convertedFilters = translate(filters).mergeWithUrlFilters(null);

        assertEqualsStringFilters(expected, convertedFilters);
    }

    @Test
    void mergeFiltersConvertsOpenProjectFilters() throws JsonProcessingException, ProjectManagementException
    {
        String opFiltersString = "["
            + "{\"n\":\"identifier\",\"o\":\"contains\",\"v\":[\"value1\"]},"
            + "{\"n\":\"property2\",\"o\":\"operator2\",\"v\":[\"value2\"]}"
            + "]";

        JsonNode opFiltersJson = mapper.readTree(opFiltersString);

        String expected = "["
            + "{"
            + "\"identifier\":{\"operator\":\"contains\",\"values\":[\"value1\"]}},"
            + "{\"property2\":{\"operator\":\"operator2\",\"values\":[\"value2\"]}"
            + "}"
            + "]";
        String convertedValues = translate(new ArrayList<>()).mergeWithUrlFilters(opFiltersJson);

        assertEqualsStringFilters(expected, convertedValues);
    }

    @Test
    void liveDataAndOpenProjectFilters() throws JsonProcessingException, ProjectManagementException
    {
        List<LiveDataQuery.Filter> filters = new ArrayList<>();

        LiveDataQuery.Filter filter1 = new LiveDataQuery.Filter();

        LiveDataQuery.Constraint firstConstraint = new LiveDataQuery.Constraint("value1", "operator1");

        filter1.setProperty("property1");
        filter1.getConstraints().add(firstConstraint);

        filters.add(filter1);

        String opFiltersString = "["
            + "{\"n\":\"property2\",\"o\":\"operator2\",\"v\":[\"value2\"]}"
            + "]";

        JsonNode opFiltersJson = mapper.readTree(opFiltersString);

        String expected = "["
            + "{"
            + "\"property1\":{\"operator\":\"operator1\",\"values\":[\"value1\"]}},"
            + "{\"property2\":{\"operator\":\"operator2\",\"values\":[\"value2\"]}"
            + "}"
            + "]";
        String convertedValues = translate(filters).mergeWithUrlFilters(opFiltersJson);

        assertEqualsStringFilters(expected, convertedValues);
    }

    @Test
    void liveDataAndOpenProjectSameFilterInBothSources()
        throws JsonProcessingException, ProjectManagementException
    {
        List<LiveDataQuery.Filter> filters = new ArrayList<>();

        LiveDataQuery.Filter filter1 = new LiveDataQuery.Filter();

        LiveDataQuery.Constraint firstConstraint = new LiveDataQuery.Constraint("value1", "operator1");

        filter1.setProperty("property1");
        filter1.getConstraints().add(firstConstraint);

        filters.add(filter1);

        String opFiltersString = "["
            + "{\"n\":\"property1\",\"o\":\"operator1\",\"v\":[\"value2\"]}"
            + "]";

        JsonNode opFiltersJson = mapper.readTree(opFiltersString);

        String expected = "["
            + "{\"property1\":{\"operator\":\"operator1\",\"values\":[\"value1\",\"value2\"]}}"
            + "]";
        String convertedValues = translate(filters).mergeWithUrlFilters(opFiltersJson);

        assertEqualsStringFilters(expected, convertedValues);
    }

    @Test
    void mergeApiFiltersOnDifferentProperties() throws JsonProcessingException, ProjectManagementException
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("summary", "contains", "bug"));
        JsonNode apiFilters = mapper.readTree("[{\"status\":{\"operator\":\"o\",\"values\":[]}}]");

        String expected = "["
            + "{\"subject\":{\"operator\":\"~\",\"values\":[\"bug\"]}},"
            + "{\"status\":{\"operator\":\"o\",\"values\":[]}}"
            + "]";

        assertEqualsStringFilters(expected, translate(filters).mergeWithApiFilters(apiFilters));
    }

    @Test
    void mergeApiFiltersOnSamePropertyAndOperator() throws JsonProcessingException, ProjectManagementException
    {
        // The livedata property is mapped to the OpenProject one (identifier -> id) before merging.
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("identifier", "equals", "5"),
            new LiveDataQuery.Filter("identifier", "equals", "6"));
        JsonNode apiFilters = mapper.readTree("[{\"id\":{\"operator\":\"=\",\"values\":[\"4\",\"5\"]}}]");

        // The values of the api filters come first, and the duplicated value is kept once.
        String expected = "[{\"id\":{\"operator\":\"=\",\"values\":[\"4\",\"5\",\"6\"]}}]";

        assertEqualsStringFilters(expected, translate(filters).mergeWithApiFilters(apiFilters));
    }

    @Test
    void mergeApiFiltersOnSamePropertyWithDifferentOperators()
        throws JsonProcessingException, ProjectManagementException
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("status", "equals", "1"));
        JsonNode apiFilters = mapper.readTree("[{\"status\":{\"operator\":\"o\",\"values\":[]}}]");

        // Both operators are kept, as separate filters on the same property.
        String expected = "["
            + "{\"status\":{\"operator\":\"=\",\"values\":[\"1\"]}},"
            + "{\"status\":{\"operator\":\"o\",\"values\":[]}}"
            + "]";

        assertEqualsStringFilters(expected, translate(filters).mergeWithApiFilters(apiFilters));
    }

    @Test
    void mergeApiFiltersWithoutApiFilters() throws JsonProcessingException, ProjectManagementException
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("summary", "contains", "bug"));
        String expected = "[{\"subject\":{\"operator\":\"~\",\"values\":[\"bug\"]}}]";

        assertEqualsStringFilters(expected, translate(filters).mergeWithApiFilters(null));
        assertEqualsStringFilters(expected, translate(filters).mergeWithApiFilters(mapper.readTree("[]")));
    }

    @Test
    void convertDateRangeFilter() throws JsonProcessingException, ProjectManagementException
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("summary", "contains", "bug"),
            new LiveDataQuery.Filter("dateRange", "between", "2026-10-01T00:00:00Z/2026-11-01T00:00:00Z"));

        // The date range is mapped to the OpenProject datesInterval filter, which also matches the work packages
        // having only a start date or only a due date inside the interval.
        String expected = "["
            + "{\"subject\":{\"operator\":\"~\",\"values\":[\"bug\"]}},"
            + "{\"datesInterval\":{\"operator\":\"<>d\","
            + "\"values\":[\"2026-10-01T00:00:00Z\",\"2026-11-01T00:00:00Z\"]}}"
            + "]";

        assertEqualsStringFilters(expected, translate(filters).toJson());
    }

    @Test
    void convertBeforeAndAfterDateFilters() throws JsonProcessingException, ProjectManagementException
    {
        // OpenProject has no before or after operators: they become intervals bounded by a distant date.
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("startDate", "before", "2026-10-01"),
            new LiveDataQuery.Filter("dueDate", "after", "2026-11-01"));

        String expected = "["
            + "{\"startDate\":{\"operator\":\"<>d\",\"values\":[\"1970-01-01\",\"2026-10-01\"]}},"
            + "{\"dueDate\":{\"operator\":\"<>d\",\"values\":[\"2026-11-01\",\"3000-01-01\"]}}"
            + "]";

        assertEqualsStringFilters(expected, translate(filters).toJson());
    }

    @Test
    void translateNumberAndBooleanValuesAsText() throws Exception
    {
        // The values of the filters written by hand can be JSON numbers or booleans: OpenProject reads them as text.
        LiveDataQuery.Filter project = new LiveDataQuery.Filter("project", "equals", 14);
        project.getConstraints().add(new LiveDataQuery.Constraint(15L, "equals"));

        OpenProjectFilters openProjectFilters = new OpenProjectFilterTranslator(this.fields, this.client).translate(
            List.of(project, new LiveDataQuery.Filter("targetVersion", "=", 12),
                new LiveDataQuery.Filter("summary", "contains", 42), new LiveDataQuery.Filter("progress", "less", 50),
                new LiveDataQuery.Filter("customField6", "=", true)));

        assertJsonEquals("["
            + "{\"project\":{\"operator\":\"=\",\"values\":[\"14\",\"15\"]}},"
            + "{\"targetVersion\":{\"operator\":\"=\",\"values\":[\"12\"]}},"
            + "{\"subject\":{\"operator\":\"~\",\"values\":[\"42\"]}},"
            + "{\"percentageDone\":{\"operator\":\"<=\",\"values\":[\"49\"]}},"
            + "{\"customField6\":{\"operator\":\"=\",\"values\":[\"true\"]}}"
            + "]", openProjectFilters.toJson());
        // A number is an id, never a version name.
        verify(this.client, never()).getVersions();
    }

    @Test
    void convertSingleDateFilters() throws JsonProcessingException, ProjectManagementException
    {
        // Only a range of dates is split: a single date is passed as is, even when it contains a slash.
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("creationDate", "=d", "2026/11/10 10:30"),
            new LiveDataQuery.Filter("dueDate", "=d", "2026-11-10T10:30:00+01:00"));

        String expected = "["
            + "{\"createdAt\":{\"operator\":\"=d\",\"values\":[\"2026/11/10 10:30\"]}},"
            + "{\"dueDate\":{\"operator\":\"=d\",\"values\":[\"2026-11-10T10:30:00+01:00\"]}}"
            + "]";

        assertEqualsStringFilters(expected, translate(filters).toJson());
    }

    @Test
    void convertFiltersDropsTheConstraintsWithoutValue() throws JsonProcessingException, ProjectManagementException
    {
        LiveDataQuery.Filter summary = new LiveDataQuery.Filter("summary", "contains", "");
        summary.getConstraints().add(new LiveDataQuery.Constraint(null, "contains"));
        LiveDataQuery.Filter description = new LiveDataQuery.Filter("description", "contains", "");
        description.getConstraints().add(new LiveDataQuery.Constraint("bug", "contains"));

        // The filters left without any constraint are dropped too.
        String expected = "[{\"description\":{\"operator\":\"~\",\"values\":[\"bug\"]}}]";

        assertEqualsStringFilters(expected, translate(List.of(summary, description)).toJson());
    }

    @Test
    void convertFiltersKeepsTheOperatorsThatTakeNoValue() throws JsonProcessingException, ProjectManagementException
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("status", "o", ""),
            new LiveDataQuery.Filter("assignees", "!*", ""), new LiveDataQuery.Filter("startDate", "t", ""));

        // The empty value is sent as is: checked against OpenProject 17.9.4, values [""] returns the same work
        // packages as values [] for the o, c, *, !*, t and w operators.
        String expected = "["
            + "{\"status\":{\"operator\":\"o\",\"values\":[\"\"]}},"
            + "{\"assigned_to\":{\"operator\":\"!*\",\"values\":[\"\"]}},"
            + "{\"startDate\":{\"operator\":\"t\",\"values\":[\"\"]}}"
            + "]";

        assertEqualsStringFilters(expected, translate(filters).toJson());
    }

    @Test
    void convertContainsOnListFilters() throws JsonProcessingException, ProjectManagementException
    {
        // The list properties are matched on their exact values, the text ones on a substring.
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("assignees", "contains", "5"),
            new LiveDataQuery.Filter("status", "contains", "1"), new LiveDataQuery.Filter("summary", "contains", "a"));

        String expected = "["
            + "{\"assigned_to\":{\"operator\":\"=\",\"values\":[\"5\"]}},"
            + "{\"status\":{\"operator\":\"=\",\"values\":[\"1\"]}},"
            + "{\"subject\":{\"operator\":\"~\",\"values\":[\"a\"]}}"
            + "]";

        assertEqualsStringFilters(expected, translate(filters).toJson());
    }

    @Test
    void convertGenericLiveDataOperators() throws JsonProcessingException, ProjectManagementException
    {
        // The Live Data offers its generic operators on every filter, whatever the operators of the property.
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("identifier", "startsWith", "12"),
            new LiveDataQuery.Filter("assignees", "empty", ""), new LiveDataQuery.Filter("summary", "equals", "a"),
            new LiveDataQuery.Filter("description", "startsWith", "b"),
            new LiveDataQuery.Filter("progress", "less", "50"),
            new LiveDataQuery.Filter("startDate", "contains", "2026-01-01/2026-02-01"));

        // OpenProject can't match the subject or the description exactly, nor their beginning: they contain the text.
        String expected = "["
            + "{\"id\":{\"operator\":\"=\",\"values\":[\"12\"]}},"
            + "{\"assigned_to\":{\"operator\":\"!*\",\"values\":[\"\"]}},"
            + "{\"subject\":{\"operator\":\"~\",\"values\":[\"a\"]}},"
            + "{\"description\":{\"operator\":\"~\",\"values\":[\"b\"]}},"
            + "{\"percentageDone\":{\"operator\":\"<=\",\"values\":[\"49\"]}},"
            + "{\"startDate\":{\"operator\":\"<>d\",\"values\":[\"2026-01-01\",\"2026-02-01\"]}}"
            + "]";

        assertEqualsStringFilters(expected, translate(filters).toJson());
    }

    @Test
    void convertFiltersDropsTheUnsupportedGenericOperators() throws JsonProcessingException, ProjectManagementException
    {
        LiveDataQuery.Filter summary = new LiveDataQuery.Filter("summary", "empty", "");
        summary.getConstraints().add(new LiveDataQuery.Constraint("bug", "contains"));

        assertEqualsStringFilters("[{\"subject\":{\"operator\":\"~\",\"values\":[\"bug\"]}}]",
            translate(List.of(summary)).toJson());
        assertEquals("Ignoring the [empty] constraint of the filter on [summary], since OpenProject supports no "
            + "equivalent operator for this property.", this.logCapture.getMessage(0));
    }

    @Test
    void translateDropsTheUnknownPropertiesWhenTheFiltersOfTheInstanceAreKnown() throws Exception
    {
        when(this.client.getWorkPackageFields()).thenReturn(
            new PaginatedResult<>(List.of(field("category", "Category", null, null)), 0, 1, 1));
        List<LiveDataQuery.Filter> filters =
            List.of(new LiveDataQuery.Filter("foo", "=", "x"), new LiveDataQuery.Filter("typeahead", "**", "1"));
        // The filters of the instance are unknown: an unknown property may be a filter of the instance.
        assertEquals(List.of("foo", "typeahead"), getFilterNames(new OpenProjectFilterTranslator(
            OpenProjectWorkItemFields.of(this.client), this.client).translate(filters)));

        // The filters of the instance are known when they are read along with the fields.
        when(this.client.getWorkPackageFields()).thenReturn(new WorkPackageFieldsResult(
            List.of(field("category", "Category", null, null)), List.of("typeahead")));
        assertEquals(List.of("typeahead"), getFilterNames(new OpenProjectFilterTranslator(
            OpenProjectWorkItemFields.of(this.client), this.client).translate(filters)));

        OpenProjectWorkItemFields fieldsAndFilters = new OpenProjectWorkItemFields(
            List.of(field("status", "Status", "status", "[]Status"),
                field(TARGET_VERSIONS, "Target versions", "targetVersion", LIST_OF_VERSIONS)),
            // As listed by the query form of the global queries.
            List.of("status", "subject", "id", "targetVersion", "createdAt", "typeahead"));
        OpenProjectFilters openProjectFilters = new OpenProjectFilterTranslator(fieldsAndFilters, this.client)
            .translate(Stream.of("foo", "identifier.value", "summary", "labels", "createdAt", "typeahead",
                "targetVersion", "status", "dueDate", "customFieldX")
                .map(property -> new LiveDataQuery.Filter(property, "equals", "x")).collect(Collectors.toList()));

        // OpenProject rejects a filter that doesn't exist, failing the whole request. The generic properties that
        // OpenProject filters on, the fields and the OpenProject filters of the instance are kept.
        assertEquals(List.of("subject", "createdAt", "typeahead", "targetVersion", "status", "dueDate"),
            getFilterNames(openProjectFilters));
    }

    @Test
    void translateKeepsTheFiltersOfTheProjectQueries() throws Exception
    {
        OpenProjectWorkItemFields fieldsAndFilters =
            new OpenProjectWorkItemFields(List.of(field("status", "Status", "status", "[]Status")),
                List.of("status", "subject", "customField5"));

        OpenProjectFilters openProjectFilters = new OpenProjectFilterTranslator(fieldsAndFilters, this.client)
            .translate(Stream.of("subprojectId", "customField12", "customField5", "customField", "foo")
                .map(property -> new LiveDataQuery.Filter(property, "=", "1")).collect(Collectors.toList()));

        // The global queries don't have the subprojects filter, nor the filters on the custom fields referencing
        // users or versions, which the queries of a project can have.
        assertEquals(List.of("subprojectId", "customField12", "customField5"), getFilterNames(openProjectFilters));
    }

    @Test
    void translateFiltersOnFields() throws Exception
    {
        Version version1 = version(10, "1.2.3");
        Version version2 = version(11, "1.2.3");
        when(this.client.getVersions()).thenReturn(
            new PaginatedResult<>(List.of(version1, version2, version(12, "2.0"), version1), 0, 4, 4));

        LiveDataQuery.Filter versionFilter = new LiveDataQuery.Filter("targetVersion", "contains", "1.2.3");
        versionFilter.getConstraints().add(new LiveDataQuery.Constraint("12", "contains"));
        versionFilter.getConstraints().add(new LiveDataQuery.Constraint("unknown", "contains"));

        OpenProjectFilters openProjectFilters = new OpenProjectFilterTranslator(this.fields, this.client).translate(
            List.of(versionFilter, new LiveDataQuery.Filter("status", "o", ""),
                new LiveDataQuery.Filter("customField6", "contains", "x"),
                new LiveDataQuery.Filter("category", "equals", "24"),
                new LiveDataQuery.Filter("foo", "equals", "x"), new LiveDataQuery.Filter("customField3", "=", "1")));

        assertJsonEquals("["
            // The name of a version is replaced by the ids of all the versions with this name, an unknown name is kept
            // so that OpenProject reports it. A list filter matches the values picked in the list.
            + "{\"targetVersion\":{\"operator\":\"=\",\"values\":[\"10\",\"11\",\"12\",\"unknown\"]}},"
            // The OpenProject operators are kept.
            + "{\"status\":{\"operator\":\"o\",\"values\":[\"\"]}},"
            + "{\"customField6\":{\"operator\":\"~\",\"values\":[\"x\"]}},"
            // A field without filter is sent as it is, so that OpenProject reports it.
            + "{\"category\":{\"operator\":\"=\",\"values\":[\"24\"]}},"
            // The filters of the instance are unknown: an unknown property may be a filter of the instance.
            + "{\"foo\":{\"operator\":\"=\",\"values\":[\"x\"]}},"
            // An OpenProject filter on a field, rather than the property exposing the field, is kept too.
            + "{\"customField3\":{\"operator\":\"=\",\"values\":[\"1\"]}}"
            + "]", openProjectFilters.toJson());
    }

    @Test
    void translateVersionIds() throws Exception
    {
        assertJsonEquals("[{\"customField3\":{\"operator\":\"=\",\"values\":[\"10\"]}}]",
            new OpenProjectFilterTranslator(this.fields, this.client)
                .translate(List.of(new LiveDataQuery.Filter("Observed in", "=", "10"))).toJson());
        verify(this.client, never()).getVersions();
    }

    @Test
    void translateGenericLiveDataOperatorsOnFields() throws Exception
    {
        // The Live Data offers its generic operators on every filter, whatever the operators of the field.
        WorkPackageField notes = field("customField1", "Notes", "customField1", "[1]String");
        notes.setFilterOperators(List.of("~", "!~"));
        WorkPackageField labels = field("customField2", "Labels", "customField2", "[]CustomOption");
        labels.setFilterOperators(List.of("=", "!"));
        WorkPackageField effort = field("customField3", "Effort", "customField3", "[1]Integer");
        effort.setFilterOperators(List.of("=", "!", ">=", "<="));
        OpenProjectWorkItemFields customFields = new OpenProjectWorkItemFields(List.of(notes, labels, effort));

        LiveDataQuery.Filter notesFilter = new LiveDataQuery.Filter("Notes", "equals", "a");
        notesFilter.getConstraints().add(new LiveDataQuery.Constraint("b", "startsWith"));
        LiveDataQuery.Filter labelsFilter = new LiveDataQuery.Filter("Labels", "startsWith", "1");
        labelsFilter.getConstraints().add(new LiveDataQuery.Constraint("", "empty"));
        LiveDataQuery.Filter effortFilter = new LiveDataQuery.Filter("Effort", "less", "5");
        effortFilter.getConstraints().add(new LiveDataQuery.Constraint("1.5", "greater"));
        effortFilter.getConstraints().add(new LiveDataQuery.Constraint("2", "equals"));

        OpenProjectFilters openProjectFilters = new OpenProjectFilterTranslator(customFields, this.client)
            .translate(List.of(notesFilter, labelsFilter, effortFilter));

        assertJsonEquals("["
            // A text field that can't match a text exactly, nor its beginning, matches the texts containing it.
            + "{\"customField1\":{\"operator\":\"~\",\"values\":[\"a\",\"b\"]}},"
            // The constraints using an operator that the field doesn't support are dropped.
            + "{\"customField2\":{\"operator\":\"=\",\"values\":[\"1\"]}},"
            // OpenProject compares the numbers inclusively.
            + "{\"customField3\":{\"operator\":\"<=\",\"values\":[\"4\"]}},"
            + "{\"customField3\":{\"operator\":\">=\",\"values\":[\"1.5\"]}},"
            + "{\"customField3\":{\"operator\":\"=\",\"values\":[\"2\"]}}"
            + "]", openProjectFilters.toJson());
        assertEquals("Ignoring the [empty] constraint of the filter on [Labels], since OpenProject supports no "
            + "equivalent operator for this property.", this.logCapture.getMessage(0));
    }

    @Test
    void translateGenericLiveDataOperatorsOnGenericProperties() throws Exception
    {
        WorkPackageField subject = field("subject", "Subject", "subject", "[1]String");
        subject.setFilterOperators(List.of("~", "!~"));
        WorkPackageField status = field("status", "Status", "status", "[]Status");
        status.setFilterOperators(List.of("o", "=", "c", "!", "*"));
        WorkPackageField createdAt = field("createdAt", "Created on", "createdAt", "[1]DateTime");
        OpenProjectWorkItemFields genericFields = new OpenProjectWorkItemFields(List.of(subject, status, createdAt));

        LiveDataQuery.Filter statusFilter = new LiveDataQuery.Filter("status", "startsWith", "1");
        statusFilter.getConstraints().add(new LiveDataQuery.Constraint("", "empty"));

        OpenProjectFilters openProjectFilters = new OpenProjectFilterTranslator(genericFields, this.client)
            .translate(List.of(new LiveDataQuery.Filter("summary", "equals", "a"), statusFilter,
                new LiveDataQuery.Filter("creationDate", "before", "2026-01-01")));

        // The operators of the generic properties are checked against the OpenProject filters, the dates being
        // converted with their values, which depend on the operator.
        assertJsonEquals("["
            + "{\"subject\":{\"operator\":\"~\",\"values\":[\"a\"]}},"
            // The status can't be empty in OpenProject.
            + "{\"status\":{\"operator\":\"=\",\"values\":[\"1\"]}},"
            + "{\"createdAt\":{\"operator\":\"<>d\",\"values\":[\"1970-01-01\",\"2026-01-01\"]}}"
            + "]", openProjectFilters.toJson());
        assertEquals("Ignoring the [empty] constraint of the filter on [status], since OpenProject supports no "
            + "equivalent operator for this property.", this.logCapture.getMessage(0));
    }

    @Test
    void translateSearchesOfWorkPackageKeys() throws Exception
    {
        LiveDataQuery.Filter severalKeys = new LiveDataQuery.Filter("summary", "contains", "PROJ-42");
        severalKeys.getConstraints().add(new LiveDataQuery.Constraint("PROJ-43", "contains"));

        OpenProjectFilters openProjectFilters = new OpenProjectFilterTranslator(this.fields, this.client).translate(
            List.of(new LiveDataQuery.Filter("summary", "contains", " PROJ-42 "),
                new LiveDataQuery.Filter("summary", "contains", "proj-"),
                new LiveDataQuery.Filter("summary", "contains", "PROJ 42"),
                new LiveDataQuery.Filter("summary", "contains", "e-mail"),
                new LiveDataQuery.Filter("summary", "contains", "PROJ-4a"),
                new LiveDataQuery.Filter("summary", "equals", "PROJ-42"), severalKeys));

        assertJsonEquals("["
            // A key, or the beginning of one, is searched with the filter of the OpenProject work package pickers,
            // which matches the keys as well as the subjects.
            + "{\"typeahead\":{\"operator\":\"**\",\"values\":[\"PROJ-42\"]}},"
            + "{\"typeahead\":{\"operator\":\"**\",\"values\":[\"proj-\"]}},"
            // The other searches stay searches of the subject.
            + "{\"subject\":{\"operator\":\"~\",\"values\":[\"PROJ 42\"]}},"
            + "{\"subject\":{\"operator\":\"~\",\"values\":[\"e-mail\"]}},"
            + "{\"subject\":{\"operator\":\"~\",\"values\":[\"PROJ-4a\"]}},"
            + "{\"subject\":{\"operator\":\"~\",\"values\":[\"PROJ-42\"]}},"
            + "{\"subject\":{\"operator\":\"~\",\"values\":[\"PROJ-42\",\"PROJ-43\"]}}"
            + "]", openProjectFilters.toJson());
    }

    @Test
    void translateOtherFilters() throws Exception
    {
        // The filters that aren't on work packages are only converted, a text shaped like a work package key included.
        assertJsonEquals("[{\"subject\":{\"operator\":\"~\",\"values\":[\"ABC-1\"]}},"
            + "{\"name\":{\"operator\":\"~\",\"values\":[\"xwiki\"]}}]",
            OpenProjectFilterTranslator.translateOtherFilters(List.of(
                new LiveDataQuery.Filter("summary", "contains", "ABC-1"),
                new LiveDataQuery.Filter("name", "contains", "xwiki"))).toJson());
    }

    @Test
    void isEmpty() throws Exception
    {
        assertTrue(translate(List.of()).isEmpty());
        // The constraints without value are dropped, as the constraints whose operator isn't supported.
        assertTrue(translate(List.of(new LiveDataQuery.Filter("summary", "contains", ""),
            new LiveDataQuery.Filter("summary", "empty", ""))).isEmpty());
        assertEquals("Ignoring the [empty] constraint of the filter on [summary], since OpenProject supports no "
            + "equivalent operator for this property.", this.logCapture.getMessage(0));
        assertEquals(false, translate(List.of(new LiveDataQuery.Filter("status", "o", ""))).isEmpty());
    }

    /**
     * Translates filters on the generic work item properties, without the fields of an instance.
     */
    private static OpenProjectFilters translate(List<LiveDataQuery.Filter> filters) throws ProjectManagementException
    {
        return new OpenProjectFilterTranslator(OpenProjectWorkItemFields.empty(), null).translate(filters);
    }

    /**
     * Compares the filters in any order (the merged filters are built from a map), but each one as many times as
     * expected.
     */
    private void assertEqualsStringFilters(String expected, String actual) throws JsonProcessingException
    {
        assertEquals(canonicalFilters(expected), canonicalFilters(actual));
    }

    private List<String> canonicalFilters(String filters) throws JsonProcessingException
    {
        ObjectMapper sortingMapper = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        List<String> canonicalFilters = new ArrayList<>();
        for (JsonNode filter : this.mapper.readTree(filters)) {
            canonicalFilters.add(sortingMapper.writeValueAsString(this.mapper.convertValue(filter, Object.class)));
        }
        Collections.sort(canonicalFilters);
        return canonicalFilters;
    }

    private void assertJsonEquals(String expected, String actual) throws JsonProcessingException
    {
        // Compared as JSON trees, since the order of the fields of a JSON object isn't fixed.
        assertEquals(this.mapper.readTree(expected), this.mapper.readTree(actual));
    }

    private List<String> getFilterNames(OpenProjectFilters openProjectFilters) throws Exception
    {
        List<String> names = new ArrayList<>();
        this.mapper.readTree(openProjectFilters.toJson()).forEach(filter -> names.add(filter.fieldNames().next()));
        return names;
    }

    private static Version version(int id, String name)
    {
        Version version = new Version();
        version.setId(id);
        version.setName(name);
        return version;
    }

    private static WorkPackageField field(String key, String name, String filterKey, String filterValueType)
    {
        WorkPackageField field = new WorkPackageField();
        field.setKey(key);
        field.setName(name);
        field.setFilterKey(filterKey);
        field.setFilterValueType(filterValueType);
        field.setFilterOperators(filterKey == null ? List.of() : List.of("=", "!", "~", "*", "!*"));
        field.setSortable(true);
        return field;
    }
}
