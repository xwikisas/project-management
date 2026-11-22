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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.internal.source.QueryWorkItemSource;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceReader;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceRequest;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectConditionChecker;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectFilterTranslator;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectFilterValueResolver;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectWorkItemFields;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests what each condition of the query of the OpenProject macros becomes in the OpenProject API: the query is read
 * by the query source, its conditions are checked as by {@link OpenProjectNameResolver}, combined, and translated into
 * OpenProject filters. The fields and their operators are those of an OpenProject 17 instance (the filter instance
 * schemas of {@code /api/v3/queries/filter_instance_schemas}).
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList({ WorkItemSourceReader.class, QueryWorkItemSource.class })
class OpenProjectQueryTest
{
    private static final List<String> LIST = List.of("=", "!");

    private static final List<String> OPTIONAL_LIST = List.of("=", "!", "*", "!*");

    private static final List<String> NUMBER = List.of("=", "!", ">=", "<=", "!*", "*");

    private static final List<String> TEXT = List.of("~", "!~");

    private static final List<String> DATE =
        List.of("<t+", ">t+", "t+", "t", "w", ">t-", "<t-", "t-", "=d", "<>d", "!*");

    private static final List<String> DATE_TIME = List.of(">t-", "<t-", "t-", "t", "w", "=d", "<>d");

    private static final String LABELS = "customField5";

    private static final OpenProjectWorkItemFields FIELDS = new OpenProjectWorkItemFields(List.of(
        field("id", "ID", "[]WorkPackage", LIST),
        field("project", "Project", "[]Project", LIST),
        field("type", "Type", "[]Type", LIST),
        field("priority", "Priority", "[]Priority", LIST),
        field("author", "Author", "[]User", LIST),
        field("assignee", "Assignee", "[]User", OPTIONAL_LIST),
        field("status", "Status", "[]Status", List.of("o", "=", "c", "!", "*")),
        field("subject", "Subject", "[1]String", TEXT),
        field("startDate", "Start date", "[1]Date", DATE),
        field("dueDate", "Finish date", "[1]Date", DATE),
        field("createdAt", "Created on", "[1]DateTime", DATE_TIME),
        field("updatedAt", "Updated on", "[1]DateTime", DATE_TIME),
        field("percentageDone", "% Complete", "[1]Integer", NUMBER),
        field("estimatedTime", "Work", "[1]Integer", NUMBER),
        field("targetVersions", "Version", "[]Version", List.of("=", "!", "*", "!*", "o", "l", "c")),
        field("parent", "Parent", "[]WorkPackage", LIST),
        field(LABELS, "Labels", "[]CustomOption", List.of("=", "&=", "!", "*", "!*")),
        field("customField7", "Approved", "[1]Boolean", LIST),
        field("customField8", "Count", "[1]Integer", NUMBER),
        // A version custom field, whose filter doesn't match the versions having a status.
        field("customField10", "Found in", "[]Version", List.of("=", "&=", "!", "*", "!*"))),
        List.of("id", "project", "type", "priority", "author", "assignee", "status", "subject", "description",
            "startDate", "dueDate", "createdAt", "updatedAt", "percentageDone", "estimatedTime", "targetVersion",
            "parent", LABELS, "customField7", "customField8", "customField10", "datesInterval", "search", "blocks"));

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    private final OpenProjectApiClient client = mock(OpenProjectApiClient.class);

    @BeforeComponent
    void registerContextComponentManager() throws Exception
    {
        this.componentManager.registerComponent(ComponentManager.class, "context", this.componentManager);
        // No version is named "open".
        when(this.client.getVersions()).thenReturn(new PaginatedResult<>(List.of(), 0, 0, 0));
    }

    static Stream<Arguments> queries()
    {
        // The expected OpenProject filters are written as "filter operator [values]; ...".
        return Stream.of(
            // Lists of ids.
            Arguments.of("identifier = 42", "id = [42]"),
            Arguments.of("identifier IN (1, 2)", "id = [1, 2]"),
            Arguments.of("identifier != 3", "id ! [3]"),
            Arguments.of("identifier NOT IN (3, 4)", "id ! [3, 4]"),
            Arguments.of("type = 1 AND priority != 2 AND project IN (3, 4)", "type = [1]; priority ! [2]; project = [3, "
                + "4]"),
            Arguments.of("creator = me", "author = [me]"),
            Arguments.of("assignees IS EMPTY", "assigned_to !* []"),
            Arguments.of("assignees IS NOT EMPTY", "assigned_to * []"),
            Arguments.of("parent = 12", "parent = [12]"),
            Arguments.of("blocks = 12", "blocks = [12]"),
            // Statuses.
            Arguments.of("status = open", "status o []"),
            Arguments.of("status = closed", "status c []"),
            Arguments.of("status != open", "status c []"),
            Arguments.of("status IN (1, 7)", "status = [1, 7]"),
            Arguments.of("status NOT IN (1)", "status ! [1]"),
            Arguments.of("status IS NOT EMPTY", "status * []"),
            Arguments.of("status IN (1, 7) AND status != 7", "status = [1]"),
            // Texts.
            Arguments.of("summary ~ \"page saving\"", "subject ~ [page saving]"),
            Arguments.of("summary !~ draft", "subject !~ [draft]"),
            // Unlike the search of the Live Data, the subject only (no work package key).
            Arguments.of("summary ~ PROJ-42", "subject ~ [PROJ-42]"),
            Arguments.of("description ~ wiki", "description ~ [wiki]"),
            Arguments.of("search ~ wiki", "search ** [wiki]"),
            // Dates.
            Arguments.of("startDate = 2026-11-21", "startDate =d [2026-11-21]"),
            Arguments.of("startDate >= 2026-11-01 AND startDate <= 2026-11-30", "startDate <>d [2026-11-01, 2026-11-30]"),
            Arguments.of("startDate >= 2026-11-01", "startDate <>d [2026-11-01, 3000-01-01]"),
            Arguments.of("startDate < 2026-11-01", "startDate <>d [1970-01-01, 2026-10-31]"),
            Arguments.of("startDate = today()", "startDate t []"),
            Arguments.of("startDate = thisWeek()", "startDate w []"),
            Arguments.of("startDate IS EMPTY", "startDate !* []"),
            Arguments.of("dueDate = 3d", "dueDate t+ [3]"),
            Arguments.of("dueDate = -3d", "dueDate t- [3]"),
            Arguments.of("dueDate >= 1w", "dueDate >t+ [7]"),
            Arguments.of("dueDate <= -3d", "dueDate <t- [3]"),
            Arguments.of("dueDate < today()", "dueDate <t- [1]"),
            Arguments.of("dueDate >= today() AND dueDate <= 7d", "dueDate <t+ [7]"),
            Arguments.of("dueDate >= -7d AND dueDate <= today()", "dueDate >t- [7]"),
            Arguments.of("creationDate >= -7d AND creationDate <= 0d", "createdAt >t- [7]"),
            Arguments.of("creationDate <= -30d", "createdAt <t- [30]"),
            Arguments.of("updateDate = -1d", "updatedAt t- [1]"),
            Arguments.of("updateDate = 2026-11-21", "updatedAt =d [2026-11-21]"),
            Arguments.of("dateRange >= 2026-11-01 AND dateRange <= 2026-11-30",
                "datesInterval <>d [2026-11-01, 2026-11-30]"),
            // Numbers.
            Arguments.of("progress = 50", "percentageDone = [50]"),
            Arguments.of("progress IN (0, 100)", "percentageDone = [0, 100]"),
            Arguments.of("progress != 50", "percentageDone ! [50]"),
            Arguments.of("progress >= 20", "percentageDone >= [20]"),
            Arguments.of("progress < 20", "percentageDone <= [19]"),
            Arguments.of("progress >= 20 AND progress <= 25", "percentageDone = [20, 21, 22, 23, 24, 25]"),
            Arguments.of("progress > 97 AND progress < 1000", "percentageDone = [98, 99, 100]"),
            Arguments.of("progress IS EMPTY", "percentageDone !* []"),
            Arguments.of("estimatedTime >= 2", "estimatedTime >= [2]"),
            Arguments.of("estimatedTime IS NOT EMPTY", "estimatedTime * []"),
            // Fields of the instance.
            Arguments.of("targetVersion = 3", "targetVersion = [3]"),
            Arguments.of("targetVersion NOT IN (3, 4)", "targetVersion ! [3, 4]"),
            Arguments.of("targetVersion IS EMPTY", "targetVersion !* []"),
            Arguments.of("Labels ALL IN (1, 2)", "customField5 &= [1, 2]"),
            Arguments.of("Labels IN (1, 2)", "customField5 = [1, 2]"),
            Arguments.of("Approved = true", "customField7 = [t]"),
            Arguments.of("Approved != FALSE", "customField7 ! [f]"),
            // Strict comparisons of whole numbers.
            Arguments.of("Count > 2", "customField8 >= [3]"),
            Arguments.of("Count < 2", "customField8 <= [1]"),
            Arguments.of("progress > 20 AND progress < 25", "percentageDone = [21, 22, 23, 24]"),
            Arguments.of("progress > -5 AND progress <= 3000000000", "percentageDone = [" + IntStream.rangeClosed(-4,
                100).filter(percentage -> percentage >= 0).mapToObj(String::valueOf).collect(Collectors.joining(", "))
                + "]"),
            // The open, locked and closed versions.
            Arguments.of("targetVersion = open", "targetVersion o []"),
            Arguments.of("targetVersion = Locked", "targetVersion l []"),
            Arguments.of("targetVersion = CLOSED", "targetVersion c []"),
            Arguments.of("targetVersion = open AND status = open", "targetVersion o []; status o []")
        );
    }

    static Stream<Arguments> unsupportedQueries()
    {
        return Stream.of(
            Arguments.of("status IS EMPTY", "The condition [status IS EMPTY] isn't supported by OpenProject, which "
                + "supports on [status]: = or IN, != or NOT IN, = open, = closed, IS NOT EMPTY."),
            Arguments.of("summary = x", "The condition [summary =] isn't supported by OpenProject, which "
                + "supports on [summary]: ~, !~."),
            Arguments.of("type ~ x", "The condition [type ~] isn't supported by OpenProject, which supports on [type]: "
                + "= or IN, != or NOT IN."),
            Arguments.of("type ALL IN (1)", "The condition [type ALL IN] isn't supported by OpenProject, which supports "
                + "on [type]: = or IN, != or NOT IN."),
            Arguments.of("creationDate IS EMPTY", "The condition [creationDate IS EMPTY] isn't supported by "
                + "OpenProject, which supports on [creationDate]: = today(), = thisWeek(), = a date, >= or <= a date, "
                + "= -Nd, <= -Nd, >= -Nd AND <= 0d."),
            Arguments.of("creationDate >= 3d", "The condition [creationDate >= Nd] isn't supported by OpenProject, "
                + "which supports on [creationDate]: = today(), = thisWeek(), = a date, >= or <= a date, = -Nd, <= -Nd, "
                + ">= -Nd AND <= 0d."),
            Arguments.of("progress > abc", "The operator [>] at position 10 can only compare dates (for example "
                + "2026-11-21) and numbers: write >= instead."),
            Arguments.of("estimatedTime > 2", "The condition [estimatedTime >] isn't supported by OpenProject, which "
                + "only compares the values of [estimatedTime] inclusively, and they aren't whole numbers: write >= "
                + "instead."),
            Arguments.of("summary > 5", "The condition [summary >] isn't supported by OpenProject, which supports on "
                + "[summary]: ~, !~."),
            Arguments.of("project = 3d", "The condition [project = Nd] isn't supported by OpenProject, which supports "
                + "on [project]: = or IN, != or NOT IN. A value looking like a date or a number of days (for example "
                + "3d) is a day: write it between quotes to compare a name."),
            Arguments.of("targetVersion != open", "The versions [open] of [targetVersion] can only be compared with "
                + "=, alone (for example targetVersion = open): write \"open\" between quotes for a version named "
                + "open."),
            Arguments.of("targetVersion IN (3, locked)", "The versions [locked] of [targetVersion] can only be "
                + "compared with =, alone (for example targetVersion = locked): write \"locked\" between quotes for a "
                + "version named locked."),
            Arguments.of("targetVersion = \"open\"", "The OpenProject version [open] of the filter on "
                + "[targetVersion] doesn't exist or you are not allowed to view it."),
            // The version custom fields have no version status: the keyword is a version name there.
            Arguments.of("\"Found in\" = closed", "The OpenProject version [closed] of the filter on [Found in] "
                + "doesn't exist or you are not allowed to view it."),
            Arguments.of("progress >= 150 AND progress <= 200", "The conditions on [progress] can't be true together: "
                + "no work item matches them."),
            Arguments.of("estimatedTime >= 2 AND estimatedTime <= 5", "The conditions on [estimatedTime] can't be "
                + "combined: OpenProject can't match a range of numbers, keep only the lower (>=) or the upper (<=) "
                + "bound."),
            Arguments.of("Labels = 1 AND Labels != 2", "The conditions on [Labels] can't be combined: OpenProject keeps "
                + "a single condition on a field having several values, for example write Labels ALL IN (a, b) "
                + "instead of Labels = a AND Labels = b."),
            Arguments.of("status = 1 AND status = 2", "The conditions on [status] can't be true together: no work item "
                + "matches them."),
            Arguments.of("summary ~ a AND summary ~ b", "The conditions on [summary] can't be combined: only =, !=, IN "
                + "and NOT IN conditions, and a lower (>=, >) and an upper (<=, <) bound of a number, can be repeated on a "
                + "property.")
        );
    }

    @ParameterizedTest
    @MethodSource("queries")
    void translateQuery(String query, String expectedFilters) throws Exception
    {
        assertEquals(expectedFilters, translate(query));
    }

    @ParameterizedTest
    @MethodSource("unsupportedQueries")
    void rejectQuery(String query, String expectedMessage)
    {
        assertEquals(expectedMessage, assertThrows(MacroExecutionException.class, () -> translate(query))
            .getMessage());
    }

    private String translate(String query) throws Exception
    {
        WorkItemSourceReader reader = this.componentManager.getInstance(WorkItemSourceReader.class);
        String filters = reader.read(new WorkItemSourceRequest().content(query).nameResolver(queryFilters -> {
            // As OpenProjectNameResolver.
            try {
                new OpenProjectConditionChecker(FIELDS, Set.of(LABELS)).check(queryFilters);
                return new OpenProjectFilterValueResolver(this.client, FIELDS).resolve(queryFilters);
            } catch (ProjectManagementException e) {
                throw new MacroExecutionException(e.getMessage(), e);
            }
        })).getFilters();
        List<LiveDataQuery.Filter> liveDataFilters = new ArrayList<>();
        for (JsonNode filterNode : MAPPER.readTree(filters).path("query").path("filters")) {
            LiveDataQuery.Filter filter = new LiveDataQuery.Filter();
            filter.setProperty(filterNode.path("property").asText());
            filter.setMatchAll(filterNode.path("matchAll").asBoolean());
            for (JsonNode constraint : filterNode.path("constraints")) {
                filter.getConstraints().add(new LiveDataQuery.Constraint(constraint.path("value").asText(),
                    constraint.path("operator").asText()));
            }
            liveDataFilters.add(filter);
        }
        String json = new OpenProjectFilterTranslator(FIELDS, null).translate(liveDataFilters).toJson();
        List<String> openProjectFilters = new ArrayList<>();
        for (JsonNode filter : MAPPER.readTree(json)) {
            String name = filter.fieldNames().next();
            List<String> values = new ArrayList<>();
            filter.path(name).path("values").forEach(value -> values.add(value.asText()));
            openProjectFilters.add(String.format("%s %s %s", name, filter.path(name).path("operator").asText(),
                values));
        }
        return String.join("; ", openProjectFilters);
    }

    private static WorkPackageField field(String key, String name, String filterValueType, List<String> operators)
    {
        WorkPackageField field = new WorkPackageField();
        field.setKey(key);
        field.setName(name);
        field.setFilterKey(key.endsWith("Versions") ? key.substring(0, key.length() - 1) : key);
        field.setFilterValueType(filterValueType);
        field.setFilterOperators(operators);
        return field;
    }
}
