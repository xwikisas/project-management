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
package com.xwiki.projectmanagement.openproject.internal.rest.suggest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Provider;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.internal.rest.WikiRequestExecutor;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.FilterBuilder;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;
import com.xwiki.projectmanagement.openproject.model.Priority;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;
import com.xwiki.projectmanagement.openproject.model.User;
import com.xwiki.projectmanagement.openproject.model.Version;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
@ComponentList(WikiRequestExecutor.class)
class SuggestTest
{
    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @MockComponent
    private ContextualAuthorizationManager authorizationManager;

    @MockComponent
    private WikiDescriptorManager wikiDescriptorManager;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    private final XWikiContext xcontext = new XWikiContext();

    @InjectMockComponents
    private Suggest suggest;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @MockComponent
    private OpenProjectApiClient openProjectApiClient;

    private static final Integer OFFSET = 1;

    private static final Integer PAGE_SIZE = 10;

    private static final String INSTANCE = "instance";

    private static final String WIKI = "wiki";

    private static final String EMPTY_STRING = "";

    private static final String SEARCH_STRING = "searchString";

    @BeforeComponent
    void beforeComponent() throws Exception
    {
        // The requests are handled on any existing wiki the current user can view.
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.authorizationManager.hasAccess(eq(Right.VIEW), any(WikiReference.class))).thenReturn(true);
        when(this.wikiDescriptorManager.exists(any())).thenReturn(true);
    }

    @BeforeEach
    void setUp()
    {
        when(this.openProjectAccess.resolve(INSTANCE))
            .thenReturn(OpenProjectAccessResult.authenticated(this.openProjectApiClient));
    }

    @Test
    void getSuggestionsForIdentifierSearchesWorkPackageSubjects() throws ProjectManagementException
    {
        PaginatedResult<WorkPackage> workPackagePaginatedResult = new PaginatedResult<>();

        WorkPackage workPackage = new WorkPackage();
        workPackage.setId(1);
        workPackage.setName("workPackage");
        workPackage.setSelf(new Linkable("1", "location"));

        workPackagePaginatedResult.setItems(List.of(workPackage));

        when(this.openProjectApiClient.getWorkPackages(anyInt(), anyInt(), anyString(), anyString()))
            .thenReturn(workPackagePaginatedResult);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "id", SEARCH_STRING, 10, null);

        verify(openProjectApiClient).getWorkPackages(
            1,
            PAGE_SIZE,
            String.format(
                "[{\"%s\":{\"operator\":\"~\",\"values\":[\"%s\"]}}]",
                "subject",
                SEARCH_STRING.toLowerCase()
            ),
            EMPTY_STRING
        );

        assertInstanceOf(List.class, response.getEntity());
        List<Map<String, String>> responseEntity = (List<Map<String, String>>) response.getEntity();

        assertEquals(1, responseEntity.size());
        assertEquals(String.valueOf(workPackage.getId()), responseEntity.get(0).get("label"));
        assertEquals(String.valueOf(workPackage.getId()), responseEntity.get(0).get("value"));
        assertEquals(workPackage.getName(), responseEntity.get(0).get("hint"));
    }

    @Test
    void getSuggestionsForIdentifierWhenSearchingAnId() throws ProjectManagementException
    {
        WorkPackage byId = createWorkPackage(11, "Eleventh");
        WorkPackage bySubject = createWorkPackage(5, "Fix the 11 failing tests");
        PaginatedResult<WorkPackage> idResult = new PaginatedResult<>();
        idResult.setItems(List.of(byId));
        PaginatedResult<WorkPackage> subjectResult = new PaginatedResult<>();
        subjectResult.setItems(List.of(bySubject, byId));
        when(this.openProjectApiClient.getWorkPackages(1, 1, "[{\"id\":{\"operator\":\"=\",\"values\":[\"11\"]}}]",
            EMPTY_STRING)).thenReturn(idResult);
        when(this.openProjectApiClient.getWorkPackages(1, PAGE_SIZE,
            "[{\"subject\":{\"operator\":\"~\",\"values\":[\"11\"]}}]", EMPTY_STRING)).thenReturn(subjectResult);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "id", "11", PAGE_SIZE, null);

        List<Map<String, String>> responseEntity = (List<Map<String, String>>) response.getEntity();
        assertEquals(List.of("11", "5"),
            responseEntity.stream().map(suggestion -> suggestion.get("value")).collect(Collectors.toList()));
    }

    @Test
    void getSuggestionsForIdentifierWhenSearchedIdFails() throws ProjectManagementException
    {
        WorkPackage bySubject = createWorkPackage(5, "Fix the 11 failing tests");
        PaginatedResult<WorkPackage> subjectResult = new PaginatedResult<>();
        subjectResult.setItems(List.of(bySubject));
        when(this.openProjectApiClient.getWorkPackages(1, 1, "[{\"id\":{\"operator\":\"=\",\"values\":[\"11\"]}}]",
            EMPTY_STRING)).thenThrow(new ProjectManagementException("Invalid filter"));
        when(this.openProjectApiClient.getWorkPackages(1, PAGE_SIZE,
            "[{\"subject\":{\"operator\":\"~\",\"values\":[\"11\"]}}]", EMPTY_STRING)).thenReturn(subjectResult);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "id", "11", PAGE_SIZE, null);

        List<Map<String, String>> responseEntity = (List<Map<String, String>>) response.getEntity();
        assertEquals(List.of("5"),
            responseEntity.stream().map(suggestion -> suggestion.get("value")).collect(Collectors.toList()));
    }

    @Test
    void getSuggestionsForIdentifierWhenSearchingAnIdLimitsToPageSize() throws ProjectManagementException
    {
        PaginatedResult<WorkPackage> idResult = new PaginatedResult<>();
        idResult.setItems(List.of(createWorkPackage(11, "Eleventh")));
        PaginatedResult<WorkPackage> subjectResult = new PaginatedResult<>();
        subjectResult.setItems(List.of(createWorkPackage(5, "Fix the 11 failing tests")));
        when(this.openProjectApiClient.getWorkPackages(1, 1, "[{\"id\":{\"operator\":\"=\",\"values\":[\"11\"]}}]",
            EMPTY_STRING)).thenReturn(idResult);
        when(this.openProjectApiClient.getWorkPackages(1, 1, "[{\"subject\":{\"operator\":\"~\",\"values\":[\"11\"]}}]",
            EMPTY_STRING)).thenReturn(subjectResult);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "id", "11", 1, null);

        List<Map<String, String>> responseEntity = (List<Map<String, String>>) response.getEntity();
        assertEquals(List.of("11"),
            responseEntity.stream().map(suggestion -> suggestion.get("value")).collect(Collectors.toList()));
    }

    @Test
    void getSuggestionsForIdentifierWhenSearchingANumberTooLongForAnId() throws ProjectManagementException
    {
        PaginatedResult<WorkPackage> subjectResult = new PaginatedResult<>();
        subjectResult.setItems(List.of());
        when(this.openProjectApiClient.getWorkPackages(anyInt(), anyInt(), anyString(), anyString()))
            .thenReturn(subjectResult);

        suggest.getSuggestions(WIKI, INSTANCE, "id", "12345678901", PAGE_SIZE, null);

        verify(this.openProjectApiClient, never()).getWorkPackages(eq(1), eq(1), anyString(), anyString());
    }

    @Test
    void getSuggestionsForParentWhenSearchingAnId() throws ProjectManagementException
    {
        Type task = new Type();
        task.setId(1);
        Type milestone = new Type();
        milestone.setId(2);
        milestone.setMilestone(true);
        when(this.openProjectApiClient.getTypes()).thenReturn(new PaginatedResult<>(List.of(task, milestone), 0, 2, 2));
        PaginatedResult<WorkPackage> idResult = new PaginatedResult<>();
        idResult.setItems(List.of(createWorkPackage(11, "Eleventh")));
        PaginatedResult<WorkPackage> subjectResult = new PaginatedResult<>();
        subjectResult.setItems(List.of(createWorkPackage(5, "Fix the 11 failing tests")));
        String typeFilter = "{\"type\":{\"operator\":\"=\",\"values\":[\"1\"]}}";
        when(this.openProjectApiClient.getWorkPackages(1, 1,
            "[" + typeFilter + ",{\"id\":{\"operator\":\"=\",\"values\":[\"11\"]}}]", EMPTY_STRING))
            .thenReturn(idResult);
        when(this.openProjectApiClient.getWorkPackages(1, PAGE_SIZE,
            "[" + typeFilter + ",{\"subject\":{\"operator\":\"~\",\"values\":[\"11\"]}}]", EMPTY_STRING))
            .thenReturn(subjectResult);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "parent", "11", PAGE_SIZE, null);

        List<Map<String, String>> responseEntity = (List<Map<String, String>>) response.getEntity();
        assertEquals(List.of("#11: Eleventh", "#5: Fix the 11 failing tests"),
            responseEntity.stream().map(suggestion -> suggestion.get("label")).collect(Collectors.toList()));
    }

    @FunctionalInterface
    private interface EntitiesCall
    {
        PaginatedResult<? extends BaseOpenProjectObject> call(OpenProjectApiClient client)
            throws ProjectManagementException;
    }

    private static Stream<Arguments> entitySuggestions()
    {
        String nameFilter = String.format("[{\"%s\":{\"operator\":\"~\",\"values\":[\"%s\"]}}]", "name",
            SEARCH_STRING.toLowerCase());
        return Stream.of(
            Arguments.of("priorities", new Priority(), "priority",
                (EntitiesCall) OpenProjectApiClient::getPriorities, (EntitiesCall) OpenProjectApiClient::getPriorities),
            Arguments.of("statuses", new Status(), "status",
                (EntitiesCall) OpenProjectApiClient::getStatuses, (EntitiesCall) OpenProjectApiClient::getStatuses),
            Arguments.of("projects", new Project(), "project",
                (EntitiesCall) c -> c.getProjects(anyInt(), anyInt(), anyString()),
                (EntitiesCall) c -> c.getProjects(OFFSET, PAGE_SIZE, nameFilter)),
            Arguments.of("types", new Type(), "type",
                (EntitiesCall) OpenProjectApiClient::getTypes, (EntitiesCall) OpenProjectApiClient::getTypes),
            Arguments.of("users", new User(), "user",
                (EntitiesCall) c -> c.getUsers(anyInt(), anyInt(), anyString()),
                (EntitiesCall) c -> c.getUsers(OFFSET, PAGE_SIZE, nameFilter))
        );
    }

    @ParameterizedTest
    @MethodSource("entitySuggestions")
    void getSuggestionsForEntities(String property, BaseOpenProjectObject entity, String entityName,
        EntitiesCall stubbedCall, EntitiesCall expectedCall) throws ProjectManagementException
    {
        PaginatedResult<BaseOpenProjectObject> paginatedResult = new PaginatedResult<>();

        entity.setId(1);
        entity.setName(entityName);
        entity.setSelf(new Linkable("1", "location"));

        paginatedResult.setItems(List.of(entity));

        stubbedCall.call(doReturn(paginatedResult).when(this.openProjectApiClient));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, property, SEARCH_STRING, 10, null);

        expectedCall.call(verify(openProjectApiClient));

        verifySuccessResponse(response, paginatedResult.getItems());
    }

    @Test
    void getSuggestionsForUnknownPropertyReturnsNothing()
    {
        Response response = suggest.getSuggestions(WIKI, INSTANCE, "unknown", SEARCH_STRING, 10, null);

        verifySuccessResponse(response, List.of());
    }

    @Test
    void getSuggestionsServerError() throws ProjectManagementException
    {
        when(this.openProjectApiClient.getTypes()).thenThrow(new ProjectManagementException(
            "Failed to retrieve the OpenProject entities. [internal error details from http://openproject.local]."));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "types", SEARCH_STRING, 10, null);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        // The details of the failure are only logged, never sent to the client.
        assertEquals("Failed to retrieve the suggestions from OpenProject.", response.getEntity());
        assertEquals("Failed to retrieve the [types] suggestions from the OpenProject instance [instance]. Root cause: "
            + "[ProjectManagementException: Failed to retrieve the OpenProject entities. [internal error details from "
            + "http://openproject.local].]", this.logCapture.getMessage(0));
    }

    @Test
    void getSuggestionsWithoutConnectionReturnsNotFound()
    {
        when(this.openProjectAccess.resolve(INSTANCE)).thenReturn(OpenProjectAccessResult.notConnected());

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "", SEARCH_STRING, 10, null);

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    void getSuggestionsForVersions() throws ProjectManagementException
    {
        Version version1 = createVersion(1, "1.2.3");
        Version version2 = createVersion(2, "2.0");
        Version version3 = createVersion(3, "1.2.4");
        Version unnamed = new Version();
        unnamed.setId(4);
        when(this.openProjectApiClient.getVersions())
            .thenReturn(new PaginatedResult<>(List.of(version1, version2, version3, unnamed), 0, 4, 4));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "versions", "1.2", 1, null);

        verifySuccessResponse(response, List.of(version1));
    }

    @Test
    void getSuggestionsForVersionsSharingTheirName() throws ProjectManagementException
    {
        Version version1 = createVersion(1, "1.0");
        Version version2 = createVersion(2, "1.0");
        Version version3 = createVersion(3, "2.0");
        when(this.openProjectApiClient.getVersions())
            .thenReturn(new PaginatedResult<>(List.of(version1, version2, version3), 0, 3, 3));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "versions", "", PAGE_SIZE, null);

        // The versions whose name other versions have (e.g. in other projects) are written by id.
        assertEquals(List.of(suggestion(version1), suggestion(version2), namedSuggestion(version3)),
            response.getEntity());
    }

    @Test
    void getSuggestionsForUsersSharingTheirName() throws ProjectManagementException
    {
        User john1 = createUser(1, "John Doe");
        User john2 = createUser(2, "John Doe");
        User jane = createUser(3, "Jane Doe");
        when(this.openProjectApiClient.getUsers(OFFSET, PAGE_SIZE, nameFilter("doe")))
            .thenReturn(new PaginatedResult<>(List.of(john1, john2, jane), 0, 3, 3));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "users", "Doe", PAGE_SIZE, null);

        assertEquals(List.of(suggestion(john1), suggestion(john2), namedSuggestion(jane)), response.getEntity());
    }

    @Test
    void getSuggestionsForUsersFillingThePage() throws ProjectManagementException
    {
        User jane = createUser(3, "Jane Doe");
        when(this.openProjectApiClient.getUsers(1, 1, nameFilter("doe")))
            .thenReturn(new PaginatedResult<>(List.of(jane), 0, 1, 2));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "users", "Doe", 1, null);

        // Another user having the same name could be on the next page: the user is written by id.
        assertEquals(List.of(suggestion(jane)), response.getEntity());
    }

    @Test
    void getSuggestionsForSelectedUser() throws ProjectManagementException
    {
        User jane = createUser(3, "Jane Doe");
        when(this.openProjectApiClient.getUsers(eq(1), eq(PAGE_SIZE), anyString()))
            .thenReturn(new PaginatedResult<>(List.of(jane), 0, 1, 1));
        when(this.openProjectApiClient.getUsers(1, 100, nameFilter("jane doe")))
            .thenReturn(new PaginatedResult<>(List.of(jane, createUser(4, "Jane Doe")), 0, 2, 2));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "users", "j", PAGE_SIZE, "3");

        // The selected user is retrieved alone: the users having the same name are searched.
        assertEquals(List.of(suggestion(jane)), response.getEntity());
    }

    @Test
    void getSuggestionsForUsersWithTheirLogin() throws ProjectManagementException
    {
        User twin1 = createUser(5, "QA150 Twin", "qa150same1");
        User twin2 = createUser(6, "QA150 Twin", "qa150same2");
        // OpenProject gives the login of the users only to the users who can manage them.
        User unique = createUser(7, "QA150 Unique");
        when(this.openProjectApiClient.getUsers(OFFSET, PAGE_SIZE, nameFilter("qa150")))
            .thenReturn(new PaginatedResult<>(List.of(twin1, twin2, unique), 0, 3, 3));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "users", "QA150", PAGE_SIZE, null);

        // The users sharing a name are told apart by their login.
        assertEquals(List.of(Map.of("value", "5", "label", "QA150 Twin", "url", "location5", "hint", "qa150same1"),
            Map.of("value", "6", "label", "QA150 Twin", "url", "location6", "hint", "qa150same2"),
            namedSuggestion(unique)), response.getEntity());
    }

    @Test
    void getSuggestionsForSelectedUserWithTheirLogin() throws ProjectManagementException
    {
        User twin = createUser(6, "QA150 Twin", "qa150same2");
        when(this.openProjectApiClient.getUsers(eq(1), eq(PAGE_SIZE), anyString()))
            .thenReturn(new PaginatedResult<>(List.of(twin), 0, 1, 1));
        when(this.openProjectApiClient.getUsers(1, 100, nameFilter("qa150 twin")))
            .thenReturn(new PaginatedResult<>(List.of(createUser(5, "QA150 Twin", "qa150same1"), twin), 0, 2, 2));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "users", "q", PAGE_SIZE, "6");

        // The label of a selected user (e.g. of a value of a filter) is loaded with the login, which tells the user
        // apart from the users sharing its name.
        assertEquals(List.of(Map.of("value", "6", "label", "QA150 Twin", "url", "location6", "hint", "qa150same2")),
            response.getEntity());
    }

    @Test
    void getSuggestionsForProjectSelectedByIdentifier() throws ProjectManagementException
    {
        Project demo = createProject(3, "demo-project", "Demo");
        when(this.openProjectApiClient.getProject("demo-project")).thenReturn(demo);
        when(this.openProjectApiClient.getProjects(1, PAGE_SIZE, nameFilter("demo-project")))
            .thenReturn(new PaginatedResult<>(List.of(demo), 0, 1, 1));

        // The picker retrieves the label of its value by searching the value.
        Response response = suggest.getSuggestions(WIKI, INSTANCE, "projects", "demo-project", PAGE_SIZE,
            "demo-project");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(List.of(Map.of("value", "demo-project", "label", "Demo", "url", "location3", "queryValue",
            "demo-project")),
            response.getEntity());
    }

    @ParameterizedTest
    @MethodSource("selectedProjects")
    void getSuggestionsForProjectsWhenSearchingWithASelectedProject(String selectedItem)
        throws ProjectManagementException
    {
        when(this.openProjectApiClient.getProject(selectedItem)).thenReturn(createProject(3, "demo-project", "Demo"));
        when(this.openProjectApiClient.getProjects(1, PAGE_SIZE, nameFilter("s")))
            .thenReturn(new PaginatedResult<>(
                List.of(createProject(4, "scrum", "Scrum"), createProject(3, "demo-project", "Demo")), 0, 2, 2));

        // The pickers send their value along with the text typed by the user.
        Response response = suggest.getSuggestions(WIKI, INSTANCE, "projects", "s", PAGE_SIZE, selectedItem);

        // The selected project is suggested first, by the value of the picker, and only once. The projects are written
        // by their identifier in the query of a macro content.
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(List.of(
            Map.of("value", selectedItem, "label", "Demo", "url", "location3", "queryValue", "demo-project"),
            Map.of("value", "4", "label", "Scrum", "url", "location4", "queryValue", "scrum")), response.getEntity());
    }

    private static Stream<String> selectedProjects()
    {
        return Stream.of("3", "demo-project");
    }

    @Test
    void getSuggestionsForProjectsWhenSelectedProjectIsNotFound() throws ProjectManagementException
    {
        when(this.openProjectApiClient.getProject("unknown"))
            .thenThrow(new WorkItemRetrievalException("Not found", 404));
        when(this.openProjectApiClient.getProjects(1, PAGE_SIZE, nameFilter("unknown")))
            .thenReturn(new PaginatedResult<>(List.of(), 0, 0, 0));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "projects", "unknown", PAGE_SIZE, "unknown");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(List.of(), response.getEntity());
    }

    @Test
    void getSuggestionsForProjectsWhenSelectedProjectFails() throws ProjectManagementException
    {
        when(this.openProjectApiClient.getProject("demo-project"))
            .thenThrow(new WorkItemRetrievalException("Unauthorized", 401));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "projects", EMPTY_STRING, PAGE_SIZE,
            "demo-project");

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertEquals("Failed to retrieve the [projects] suggestions from the OpenProject instance [instance]. "
            + "Root cause: [WorkItemRetrievalException: Unauthorized]", this.logCapture.getMessage(0));
    }

    @Test
    void getSuggestionsForProjectsWhenTheDashboardProjectIsSelected() throws ProjectManagementException
    {
        when(this.openProjectApiClient.getProjects(1, PAGE_SIZE, nameFilter(EMPTY_STRING)))
            .thenReturn(new PaginatedResult<>(List.of(createProject(4, "scrum", "Scrum")), 0, 1, 1));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "projects", EMPTY_STRING, PAGE_SIZE,
            "use_selected_dashboard_project");

        assertEquals(List.of(Map.of("value", "4", "label", "Scrum", "url", "location4", "queryValue", "scrum")),
            response.getEntity());
        verify(this.openProjectApiClient, never()).getProject(anyString());
    }

    @ParameterizedTest
    @MethodSource("selectedItemsThatAreNotIds")
    void getSuggestionsForUsersWhenSelectedItemIsNotAnId(String selectedItem) throws ProjectManagementException
    {
        User user = new User();
        user.setId(1);
        user.setName("user");
        user.setSelf(new Linkable("1", "location"));
        when(this.openProjectApiClient.getUsers(OFFSET, PAGE_SIZE, nameFilter(SEARCH_STRING.toLowerCase())))
            .thenReturn(new PaginatedResult<>(List.of(user), 0, 1, 1));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "users", SEARCH_STRING, PAGE_SIZE, selectedItem);

        verifySuccessResponse(response, List.of(user));
    }

    @ParameterizedTest
    @MethodSource("selectedItemsThatAreNotIds")
    void getSuggestionsForWorkPackagesWhenSelectedItemIsNotAnId(String selectedItem) throws ProjectManagementException
    {
        String subjectFilter = "{\"subject\":{\"operator\":\"~\",\"values\":[\"searchstring\"]}}";
        when(this.openProjectApiClient.getWorkPackages(1, PAGE_SIZE, "[" + subjectFilter + "]", EMPTY_STRING))
            .thenReturn(new PaginatedResult<>(List.of(createWorkPackage(5, "Fifth")), 0, 1, 1));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "id", SEARCH_STRING, PAGE_SIZE, selectedItem);

        assertEquals(List.of(Map.of("value", "5", "label", "5", "url", "location5", "hint", "Fifth")),
            response.getEntity());
    }

    @ParameterizedTest
    @MethodSource("selectedItemsThatAreNotIds")
    void getSuggestionsForParentsWhenSelectedItemIsNotAnId(String selectedItem) throws ProjectManagementException
    {
        Type task = new Type();
        task.setId(1);
        when(this.openProjectApiClient.getTypes()).thenReturn(new PaginatedResult<>(List.of(task), 0, 1, 1));
        when(this.openProjectApiClient.getWorkPackages(1, PAGE_SIZE,
            "[{\"type\":{\"operator\":\"=\",\"values\":[\"1\"]}},"
                + "{\"subject\":{\"operator\":\"~\",\"values\":[\"searchstring\"]}}]", EMPTY_STRING))
            .thenReturn(new PaginatedResult<>(List.of(createWorkPackage(5, "Fifth")), 0, 1, 1));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "parent", SEARCH_STRING, PAGE_SIZE, selectedItem);

        assertEquals(List.of(Map.of("value", "/api/v3/work_packages/5", "label", "#5: Fifth", "url", "location5",
            "hint", "Fifth")), response.getEntity());
    }

    private static Stream<String> selectedItemsThatAreNotIds()
    {
        return Stream.of("admin", "1,2", "99999999999", "4294967296");
    }

    private static String nameFilter(String search)
    {
        return new FilterBuilder().addFilter("name", FilterBuilder.Operator.CONTAINS, search).build();
    }

    private static User createUser(int id, String name)
    {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setSelf(new Linkable(String.valueOf(id), "location" + id));
        return user;
    }

    private static User createUser(int id, String name, String login)
    {
        User user = createUser(id, name);
        user.setLogin(login);
        return user;
    }

    private static Project createProject(int id, String identifier, String name)
    {
        Project project = new Project();
        project.setId(id);
        project.setIdentifier(identifier);
        project.setName(name);
        project.setSelf(new Linkable(name, "location" + id));
        return project;
    }

    private static WorkPackage createWorkPackage(int id, String subject)
    {
        WorkPackage workPackage = new WorkPackage();
        workPackage.setId(id);
        workPackage.setName(subject);
        workPackage.setSelf(new Linkable(String.valueOf(id), "location" + id));
        return workPackage;
    }

    private static Version createVersion(int id, String name)
    {
        Version version = new Version();
        version.setId(id);
        version.setName(name);
        version.setSelf(new Linkable(name, "location" + id));
        return version;
    }

    /**
     * Checks the suggestions of the given resources, the ones other than projects (written by their identifier, which
     * the test projects don't have) being written by their name in a query.
     */
    private void verifySuccessResponse(Response response, List<? extends BaseOpenProjectObject> generatedObjects)
    {
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(
            generatedObjects
                .stream()
                .map(el -> el instanceof Project ? suggestion(el) : namedSuggestion(el))
                .collect(Collectors.toList()),
            response.getEntity()
        );
    }

    private static Map<String, String> suggestion(BaseOpenProjectObject object)
    {
        return Map.of("value", object.getId().toString(), "label", object.getName(), "url",
            object.getSelf().getLocation());
    }

    private static Map<String, String> namedSuggestion(BaseOpenProjectObject object)
    {
        return Map.of("value", object.getId().toString(), "label", object.getName(), "url",
            object.getSelf().getLocation(), "queryValue", object.getName());
    }

    @Test
    void getSuggestionsReadsTheConnectionFromTheWikiOfTheRequest()
    {
        this.xcontext.setWikiId("xwiki");
        List<String> connectionWikis = new ArrayList<>();
        when(this.openProjectAccess.resolve(INSTANCE)).thenAnswer(invocation -> {
            connectionWikis.add(this.xcontext.getWikiId());
            return OpenProjectAccessResult.notConfigured();
        });

        Response response = this.suggest.getSuggestions("subwiki", INSTANCE, "types", SEARCH_STRING, 10, null);

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertEquals(List.of("subwiki"), connectionWikis);
        assertEquals("xwiki", this.xcontext.getWikiId());
    }
}
