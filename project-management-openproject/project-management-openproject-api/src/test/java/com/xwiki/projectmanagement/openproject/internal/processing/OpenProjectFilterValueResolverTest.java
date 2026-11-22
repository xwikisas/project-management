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

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;
import com.xwiki.projectmanagement.openproject.model.Priority;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;
import com.xwiki.projectmanagement.openproject.model.User;
import com.xwiki.projectmanagement.openproject.model.Version;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;
import com.xwiki.projectmanagement.source.WorkItemName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectFilterValueResolver}, against the resources of an OpenProject instance held in
 * memory.
 *
 * @version $Id$
 */
class OpenProjectFilterValueResolverTest
{
    private static final String NOT_FOUND = "%s doesn't exist or you are not allowed to view it.";

    private final OpenProjectApiClient client = mock(OpenProjectApiClient.class);

    private final OpenProjectWorkItemFields fields = new OpenProjectWorkItemFields(List.of(
        field("version", "Version", "version", OpenProjectWorkItemFields.VERSION_FILTER_VALUE_TYPE,
            List.of("=", "!", "*", "!*", "o", "l", "c")),
        field("customField4", "Reviewer", "customField4", "[]User", List.of()),
        // A version custom field, whose filter doesn't match the versions having a status.
        field("customField9", "Found in", "customField9", OpenProjectWorkItemFields.VERSION_FILTER_VALUE_TYPE,
            List.of("=", "&=", "!", "*", "!*"))));

    private final OpenProjectFilterValueResolver resolver =
        new OpenProjectFilterValueResolver(this.client, this.fields);

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.client.getStatuses()).thenReturn(result(resource(new Status(), 1, "New"),
            resource(new Status(), 12, "Closed"), resource(new Status(), 17, "Rejected")));
        when(this.client.getTypes()).thenReturn(result(resource(new Type(), 1, "Task"), resource(new Type(), 2,
            "Bug")));
        when(this.client.getPriorities()).thenReturn(result(resource(new Priority(), 8, "Normal"),
            resource(new Priority(), 9, "High")));
        when(this.client.getVersions()).thenReturn(result(resource(new Version(), 44, "1.19"),
            resource(new Version(), 45, "2.0"), resource(new Version(), 60, "1.19"), resource(new Version(), 70,
                "3.0"), resource(new Version(), 80, "Closed")));
        when(this.client.getProjectVersions(14)).thenReturn(result(resource(new Version(), 44, "1.19"),
            resource(new Version(), 45, "2.0")));
        when(this.client.getProjectVersions(15)).thenReturn(result(resource(new Version(), 60, "1.19")));
        when(this.client.getProject("documentation-application")).thenReturn(project(14, "documentation-application",
            "Documentation Application"));
        when(this.client.getProject("14")).thenReturn(project(14, "documentation-application",
            "Documentation Application"));
        when(this.client.getProject("demo")).thenThrow(new WorkItemRetrievalException("Not found", 404));
        when(this.client.getProjects(eq(1), anyInt(), anyString())).thenReturn(result(
            project(15, "meeting-application", "Meeting Application"),
            project(16, "meeting-application-old", "Meeting Application (old)"),
            project(20, "demo-1", "Demo"), project(21, "demo-2", "Demo")));
        when(this.client.getUsers(eq(1), anyInt(), anyString())).thenReturn(result(
            resource(new User(), 5, "John Doe"), resource(new User(), 6, "John Doe Junior")));
    }

    @Test
    void resolveKeepsTheIdsWithoutRequestingOpenProject() throws Exception
    {
        LiveDataQuery.Filter project = new LiveDataQuery.Filter("project", "equals", "14");
        LiveDataQuery.Filter version = new LiveDataQuery.Filter("targetVersion", "=", "44");
        LiveDataQuery.Filter status = new LiveDataQuery.Filter("status", "=", "12");

        List<LiveDataQuery.Filter> resolved = this.resolver.resolve(List.of(project, version, status));

        assertSame(project, resolved.get(0));
        assertSame(version, resolved.get(1));
        assertSame(status, resolved.get(2));
        verifyNoInteractions(this.client);
    }

    @Test
    void resolveNamesIgnoringTheCase() throws Exception
    {
        LiveDataQuery.Filter status = new LiveDataQuery.Filter("status", "equals", name("closed"));
        status.getConstraints().add(new LiveDataQuery.Constraint(name(" REJECTED "), "equals"));
        status.getConstraints().add(new LiveDataQuery.Constraint("1", "equals"));
        // Without operator, the default operator of the filter is used.
        status.getConstraints().add(new LiveDataQuery.Constraint(name("New"), null));

        List<LiveDataQuery.Filter> resolved = this.resolver.resolve(List.of(status,
            new LiveDataQuery.Filter("type", "!", name("bug")),
            new LiveDataQuery.Filter("priority", "contains", name("High")),
            new LiveDataQuery.Filter("assignees", "=", name("john doe")),
            new LiveDataQuery.Filter("Reviewer", "=", name("John Doe"))));

        assertEquals(List.of("status equals 12", "status equals 17", "status equals 1", "status null 1",
            "type ! 2",
            "priority contains 9", "assignees = 5", "Reviewer = 5"), toStrings(resolved));
        // Each kind of resource is retrieved once.
        verify(this.client, times(1)).getUsers(eq(1), anyInt(), anyString());
    }

    @Test
    void resolveKeepsTheValuesThatArentNamesTheCurrentUserAndTheOtherProperties() throws Exception
    {
        LiveDataQuery.Filter text = new LiveDataQuery.Filter("status", "~", "Clo");
        LiveDataQuery.Filter open = new LiveDataQuery.Filter("status", "o", "");
        LiveDataQuery.Filter number = new LiveDataQuery.Filter("status", "=", 12);

        // The current user is written me, without quotes: it isn't a name.
        LiveDataQuery.Filter currentUser = new LiveDataQuery.Filter("assignees", "=", "me");

        List<LiveDataQuery.Filter> resolved = this.resolver.resolve(List.of(text, open, number, currentUser,
            new LiveDataQuery.Filter("summary", "=", name("Closed")), new LiveDataQuery.Filter(null, "=",
                name("Closed"))));

        assertSame(text, resolved.get(0));
        assertSame(open, resolved.get(1));
        assertSame(number, resolved.get(2));
        assertSame(currentUser, resolved.get(3));
        // The names that aren't resolved are kept as text.
        assertEquals(List.of("summary = Closed", "null = Closed"), toStrings(resolved.subList(4, 6)));
        verifyNoInteractions(this.client);
    }

    @Test
    void resolveANameMadeOfDigitsAsAName() throws Exception
    {
        assertFails(String.format(NOT_FOUND, "The OpenProject type [2] of the filter on [type]"),
            new LiveDataQuery.Filter("type", "=", name("2")));
        // The project 14 exists, but the project named 14 doesn't: it isn't looked up by id.
        assertFails(String.format(NOT_FOUND, "The OpenProject project [14] of the filter on [project]"),
            new LiveDataQuery.Filter("project", "=", name("14")));
        when(this.client.getProjects(eq(1), anyInt(), anyString())).thenReturn(result(project(30, "year", "2024")));
        assertEquals(List.of("project = 30"),
            toStrings(this.resolver.resolve(List.of(new LiveDataQuery.Filter("project", "=", name("2024"))))));
    }

    @Test
    void resolveProjectsByIdentifierThenByName() throws Exception
    {
        LiveDataQuery.Filter projects =
            new LiveDataQuery.Filter("project", "equals", name("Documentation-Application"));
        projects.getConstraints().add(new LiveDataQuery.Constraint(name("meeting application"), "equals"));

        assertEquals(List.of("project equals 14", "project equals 15"),
            toStrings(this.resolver.resolve(List.of(projects))));
    }

    @Test
    void resolveVersionsWithinTheFilteredProjects() throws Exception
    {
        // The project filter comes after the version filter, but restricts it.
        List<LiveDataQuery.Filter> resolved = this.resolver.resolve(List.of(
            new LiveDataQuery.Filter("targetVersion", "=", name("1.19")),
            new LiveDataQuery.Filter("project", "equals", name("documentation-application"))));

        assertEquals(List.of("targetVersion = 44", "project equals 14"), toStrings(resolved));
    }

    @Test
    void resolveVersionsWithinAProjectFilteredByANumber() throws Exception
    {
        // The filters written by hand can hold the project id as a JSON number.
        List<LiveDataQuery.Filter> resolved = this.resolver.resolve(List.of(
            new LiveDataQuery.Filter("project", "equals", 15),
            new LiveDataQuery.Filter("targetVersion", "=", name("1.19"))));

        assertEquals(List.of("project equals 15", "targetVersion = 60"), toStrings(resolved));
    }

    @Test
    void resolveVersionsOfSeveralFilteredProjects() throws Exception
    {
        LiveDataQuery.Filter projects = new LiveDataQuery.Filter("project", "equals", "14");
        projects.getConstraints().add(new LiveDataQuery.Constraint("15", null));

        List<LiveDataQuery.Filter> resolved = this.resolver.resolve(List.of(projects,
            new LiveDataQuery.Filter("targetVersion", "=", name("1.19"))));

        // Each filtered project has its own version 1.19.
        assertEquals(List.of("project equals 14", "project null 15", "targetVersion = 44", "targetVersion = 60"),
            toStrings(resolved));
    }

    @Test
    void resolveVersionsWithoutFilteredProject() throws Exception
    {
        // The excluded projects don't restrict the versions.
        assertEquals(List.of("project ! 14", "targetVersion = 45"), toStrings(this.resolver.resolve(List.of(
            new LiveDataQuery.Filter("project", "!", "14"),
            new LiveDataQuery.Filter("targetVersion", "=", name("2.0"))))));

        assertFails("The name [1.19] of the filter on [targetVersion] matches several OpenProject versions, whose ids "
            + "are [44, 60]: use one of these ids instead.",
            new LiveDataQuery.Filter("targetVersion", "=", name("1.19")));
    }

    @Test
    void resolveFailsOnAVersionOutsideTheFilteredProjects()
    {
        assertFails("The OpenProject version [3.0] of the filter on [targetVersion] isn't a version of the filtered "
            + "projects [14].", new LiveDataQuery.Filter("project", "=", "14"),
            new LiveDataQuery.Filter("targetVersion", "=", name("3.0")));
        assertFails(String.format(NOT_FOUND, "The OpenProject version [4.0] of the filter on [targetVersion]"),
            new LiveDataQuery.Filter("project", "=", "14"),
            new LiveDataQuery.Filter("targetVersion", "=", name("4.0")));
    }

    @Test
    void resolveTheVersionStatuses() throws Exception
    {
        assertEquals(List.of("targetVersion o ", "targetVersion l ", "targetVersion c "),
            toStrings(this.resolver.resolve(List.of(
                new LiveDataQuery.Filter("targetVersion", "=", new WorkItemName("open", true)),
                new LiveDataQuery.Filter("targetVersion", "=", new WorkItemName("LOCKED", true)),
                new LiveDataQuery.Filter("targetVersion", "=", new WorkItemName("Closed", true))))));
        // Elsewhere, the keyword names are names: on the other properties, and on the version custom fields, whose
        // filter doesn't match the versions having a status.
        assertEquals(List.of("status = 12", "Found in = 80"), toStrings(this.resolver.resolve(List.of(
            new LiveDataQuery.Filter("status", "=", new WorkItemName("closed", true)),
            new LiveDataQuery.Filter("Found in", "=", new WorkItemName("closed", true))))));
        // When the fields of the instance can't be retrieved, the built-in target version still has the statuses.
        assertEquals(List.of("targetVersion l ", "Found in = open"), toStrings(new OpenProjectFilterValueResolver(
            this.client, OpenProjectWorkItemFields.empty()).resolve(List.of(
                new LiveDataQuery.Filter("targetVersion", "=", new WorkItemName("locked", true)),
                new LiveDataQuery.Filter("Found in", "=", new WorkItemName("open", true))))));
        // A quoted name is a version name.
        assertFails(String.format(NOT_FOUND, "The OpenProject version [open] of the filter on [targetVersion]"),
            new LiveDataQuery.Filter("targetVersion", "=", name("open")));
        LiveDataQuery.Filter list = new LiveDataQuery.Filter("targetVersion", "=", "44");
        list.getConstraints().add(new LiveDataQuery.Constraint(new WorkItemName("open", true), "="));
        assertFails("The versions [open] of [targetVersion] can only be compared with =, alone (for example "
            + "targetVersion = open): write \"open\" between quotes for a version named open.", list);
        assertFails("The versions [locked] of [targetVersion] can only be compared with =, alone (for example "
            + "targetVersion = locked): write \"locked\" between quotes for a version named locked.",
            new LiveDataQuery.Filter("targetVersion", "!", new WorkItemName("locked", true)));
    }

    @Test
    void resolveFailsOnUnknownOrAmbiguousNames()
    {
        assertFails(String.format(NOT_FOUND, "The OpenProject status [Done] of the filter on [status]"),
            new LiveDataQuery.Filter("status", "equals", name("Done")));
        assertFails(String.format(NOT_FOUND, "The OpenProject user [Jane Doe] of the filter on [creator]"),
            new LiveDataQuery.Filter("creator", "equals", name("Jane Doe")));
        assertFails("The name [demo] of the filter on [project] matches several OpenProject projects, whose ids are "
            + "[20, 21]: use one of these ids instead.", new LiveDataQuery.Filter("project", "equals", name("demo")));
    }

    @Test
    void resolveTheNameMeAsAUserName()
    {
        // A quoted "me" is the user named me, not the current user.
        assertFails(String.format(NOT_FOUND, "The OpenProject user [me] of the filter on [assignees]"),
            new LiveDataQuery.Filter("assignees", "=", name("me")));
    }

    @Test
    void resolveFailsOnAnUnknownProjectWithoutCause() throws Exception
    {
        when(this.client.getProject("nope-project")).thenThrow(new WorkItemRetrievalException("Not found", 404));

        OpenProjectFilterValueException exception = assertThrows(OpenProjectFilterValueException.class,
            () -> this.resolver.resolve(List.of(new LiveDataQuery.Filter("project", "=", name("nope-project")))));

        assertEquals(String.format(NOT_FOUND, "The OpenProject project [nope-project] of the filter on [project]"),
            exception.getMessage());
        // The macro error box displays the message of the root cause, which would be the failed request.
        assertNull(exception.getCause());
    }

    @Test
    void resolveProjectNamesThatCantBeIdentifiersByNameOnly() throws Exception
    {
        // OpenProject fails when it's requested a project whose identifier has accented letters.
        when(this.client.getProject(anyString())).thenThrow(new WorkItemRetrievalException("Internal error", 500));
        when(this.client.getProjects(eq(1), anyInt(), anyString())).thenReturn(result(
            project(22, "qa-ete", "QA \u00c9t\u00e9"), project(23, "qa-ete-bis", "QA \u00c9t\u00e9 bis")));

        assertEquals(List.of("project = 22"), toStrings(this.resolver.resolve(
            List.of(new LiveDataQuery.Filter("project", "=", name("qa \u00e9t\u00e9"))))));
        assertFails(String.format(NOT_FOUND, "The OpenProject project [Proj\u00e9t] of the filter on [project]"),
            new LiveDataQuery.Filter("project", "=", name("Proj\u00e9t")));
        verify(this.client, never()).getProject(anyString());
    }

    @Test
    void checkPropertiesFailsOnUnknownProperties() throws Exception
    {
        OpenProjectFilterValueResolver checker = new OpenProjectFilterValueResolver(this.client,
            new OpenProjectWorkItemFields(List.of(field("customField4", "Reviewer", "customField4", "[]User",
                List.of())),
                List.of("status", "project", "customField4")));
        // The generic properties, the fields of the instance and its filters are known.
        checker.checkProperties(List.of(new LiveDataQuery.Filter("status", "=", "1"),
            new LiveDataQuery.Filter("summary", "~", "a"), new LiveDataQuery.Filter("Reviewer", "=", "5"),
            new LiveDataQuery.Filter("customField4", "=", "5")));

        assertEquals("Unknown property [colour]: use the id of a work package property, for example status, "
            + "assignees or targetVersion, or the name of a custom field.",
            assertThrows(OpenProjectFilterValueException.class, () -> checker.checkProperties(
                List.of(new LiveDataQuery.Filter("colour", "=", "red")))).getMessage());
        assertEquals("Unknown property [STATUS]: the property names are case-sensitive, did you mean [status]?",
            assertThrows(OpenProjectFilterValueException.class, () -> checker.checkProperties(
                List.of(new LiveDataQuery.Filter("STATUS", "o", "")))).getMessage());
        assertEquals("Unknown property [reviewer]: the property names are case-sensitive, did you mean [Reviewer]?",
            assertThrows(OpenProjectFilterValueException.class, () -> checker.checkProperties(
                List.of(new LiveDataQuery.Filter("reviewer", "=", "5")))).getMessage());
    }

    @Test
    void checkPropertiesAcceptsAllPropertiesWhenTheFiltersAreUnknown() throws Exception
    {
        this.resolver.checkProperties(List.of(new LiveDataQuery.Filter("colour", "=", "red")));
        verifyNoInteractions(this.client);
    }

    @Test
    void resolveFailsWhenTheResourcesCantBeRetrieved() throws Exception
    {
        when(this.client.getProject("xwiki")).thenThrow(new WorkItemRetrievalException("Unauthorized", 401));

        WorkItemRetrievalException exception = assertThrows(WorkItemRetrievalException.class,
            () -> this.resolver.resolve(List.of(new LiveDataQuery.Filter("project", "=", name("xwiki")))));

        assertEquals(401, exception.getStatusCode());
    }

    private void assertFails(String message, LiveDataQuery.Filter... filters)
    {
        OpenProjectFilterValueException exception = assertThrows(OpenProjectFilterValueException.class,
            () -> this.resolver.resolve(List.of(filters)));
        assertEquals(message, exception.getMessage());
    }

    private static WorkItemName name(String name)
    {
        return new WorkItemName(name);
    }

    private static List<String> toStrings(List<LiveDataQuery.Filter> filters)
    {
        return filters.stream().flatMap(filter -> filter.getConstraints().stream()
                .map(constraint -> String.format("%s %s %s", filter.getProperty(), constraint.getOperator(),
                    constraint.getValue())))
            .collect(Collectors.toList());
    }

    @SafeVarargs
    private static <T> PaginatedResult<T> result(T... items)
    {
        return new PaginatedResult<>(List.of(items), 0, items.length, items.length);
    }

    private static <T extends BaseOpenProjectObject> T resource(T resource, int id, String name)
    {
        resource.setId(id);
        resource.setName(name);
        return resource;
    }

    private static Project project(int id, String identifier, String name)
    {
        Project project = resource(new Project(), id, name);
        project.setIdentifier(identifier);
        return project;
    }

    private static WorkPackageField field(String key, String name, String filterKey, String filterValueType,
        List<String> filterOperators)
    {
        WorkPackageField field = new WorkPackageField();
        field.setKey(key);
        field.setName(name);
        field.setFilterKey(filterKey);
        field.setFilterValueType(filterValueType);
        field.setFilterOperators(filterOperators);
        return field;
    }
}
