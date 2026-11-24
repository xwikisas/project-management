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
package com.xwiki.projectmanagement.openproject.internal.rest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.inject.Provider;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.localization.ContextualLocalizationManager;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.internal.rest.WikiRequestExecutor;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectIdentifierResolver;
import com.xwiki.projectmanagement.openproject.model.SavedQuery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectLinkResource}.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList({ WikiRequestExecutor.class, OpenProjectIdentifierResolver.class })
class OpenProjectLinkResourceTest
{
    private static final String WIKI = "wiki";

    private static final String INSTANCE = "main";

    private static final String SERVER_URL = "https://op.example.com";

    private static final String SAVED_QUERY_LINK = SERVER_URL + "/projects/demo/work_packages?query_id=5";

    private static final String KIND = "kind";

    private static final String LEVEL = "level";

    private static final String MESSAGE = "message";

    private static final String INFO = "info";

    private static final String WARNING = "warning";

    private static final String SAVED_QUERY = "savedQuery";

    private static final String QUERY_NAME = "Open tasks";

    private static final String DESCRIBE = "openproject.link.describe.";

    private static final String UNAVAILABLE_KEY = "openproject.savedQuery.unavailable";

    private static final String NOT_CONNECTED_KEY = "openproject.link.notConnected";

    private static final String PROJECT_UNAVAILABLE_KEY = "openproject.link.projectUnavailable";

    private static final String PROJECT_NOT_CONNECTED_KEY = "openproject.link.projectNotConnected";

    private static final String NOT_CONNECTED = "notConnected";

    private static final String AUTHENTICATION_LINK = "authenticationLink";

    private static final String CONNECT_URL = "/xwiki/bin/view/OpenProject/Code/RenewOAuthConnection?token=x";

    private static final String CONNECT_LABEL = "Click here to authenticate.";

    private static final String PROJECT_LIST_LINK = SERVER_URL + "/projects/demo/work_packages";

    private static final String TRANSLATED = "translated";

    private static final String DOCUMENT = "Space.Page";

    private static final String WORK_PACKAGE_LINK = SERVER_URL + "/wp/12";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @MockComponent
    private OpenProjectConfiguration configuration;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @MockComponent
    private ContextualLocalizationManager l10n;

    @MockComponent
    private ContextualAuthorizationManager authorizationManager;

    @MockComponent
    private WikiDescriptorManager wikiDescriptorManager;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    private OpenProjectApiClient client;

    @MockComponent
    private PageEditRightChecker pageEditRightChecker;

    @InjectMockComponents
    private OpenProjectLinkResource resource;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @BeforeComponent
    void beforeComponent() throws Exception
    {
        // The requests are handled on any existing wiki the current user can view.
        when(this.xcontextProvider.get()).thenReturn(new XWikiContext());
        when(this.authorizationManager.hasAccess(eq(Right.VIEW), any(WikiReference.class))).thenReturn(true);
        when(this.wikiDescriptorManager.exists(any())).thenReturn(true);
    }

    @BeforeEach
    void setUp()
    {
        when(this.configuration.getConnection(INSTANCE))
            .thenReturn(new OpenProjectConnection(INSTANCE, SERVER_URL, null, null, null));
        when(this.openProjectAccess.resolve(INSTANCE)).thenReturn(OpenProjectAccessResult.authenticated(this.client));
        // The macro is edited on a page the current user can edit.
        when(this.pageEditRightChecker.canEdit(WIKI, DOCUMENT)).thenReturn(true);
        // The current user can connect their OpenProject account to the connection, coming back to the edited page.
        when(this.pageEditRightChecker.getConnectURL(INSTANCE, DOCUMENT)).thenReturn(CONNECT_URL);
        when(this.l10n.getTranslationPlain("openproject.oauth.notauthorized.link")).thenReturn(CONNECT_LABEL);
    }

    @Test
    void withoutEditRight()
    {
        when(this.pageEditRightChecker.canEdit(WIKI, DOCUMENT)).thenReturn(false);

        assertEquals(403, this.resource.getLinkDescription(WIKI, INSTANCE, WORK_PACKAGE_LINK, DOCUMENT, null)
            .getStatus());
        verifyNoInteractions(this.configuration);
    }

    @Test
    void savedQuery() throws Exception
    {
        when(this.client.getQuery("5")).thenReturn(query("{\"name\":\"Open tasks\",\"_links\":{"
            + "\"project\":{\"href\":\"/api/v3/projects/9\",\"title\":\"Demo\"},"
            + "\"results\":{\"href\":\"/api/v3/projects/9/work_packages?filters=%5B%5D\"}}}"));
        when(this.l10n.getTranslationPlain(DESCRIBE + SAVED_QUERY, QUERY_NAME, "Demo")).thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, SAVED_QUERY, LEVEL, INFO, MESSAGE, TRANSLATED, "name", QUERY_NAME),
            describe(INSTANCE, SAVED_QUERY_LINK));
    }

    @Test
    void savedQueryOfAllTheProjects() throws Exception
    {
        when(this.client.getQuery("5")).thenReturn(query("{\"name\":\"Open tasks\",\"_links\":{"
            + "\"project\":{\"href\":null},\"results\":{\"href\":\"/api/v3/work_packages\"}}}"));
        when(this.l10n.getTranslationPlain(DESCRIBE + "savedQuery.allProjects", QUERY_NAME)).thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, SAVED_QUERY, LEVEL, INFO, MESSAGE, TRANSLATED, "name", QUERY_NAME),
            describe(INSTANCE, SERVER_URL + "/work_packages?query_id=5"));
    }

    /**
     * A work package list can't express these saved queries, which the macro uses as they are.
     */
    @ParameterizedTest
    @ValueSource(strings = { "includeSubprojects=false", "timestamps=2026-11-01T00%3A00%3A00Z%2CPT0S",
        "sortBy=%5B%5B%22manualSorting%22%2C%22asc%22%5D%5D" })
    void savedQueryKeptAsIs(String parameter) throws Exception
    {
        when(this.client.getQuery("5")).thenReturn(query("{\"name\":\"Open tasks\",\"_links\":{"
            + "\"project\":{\"href\":\"/api/v3/projects/9\",\"title\":\"Demo\"},"
            + "\"results\":{\"href\":\"/api/v3/projects/9/work_packages?" + parameter + "\"}}}"));
        when(this.l10n.getTranslationPlain(DESCRIBE + "savedQuery.keptAsIs", QUERY_NAME)).thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, SAVED_QUERY, LEVEL, INFO, MESSAGE, TRANSLATED, "name", QUERY_NAME),
            describe(INSTANCE, SAVED_QUERY_LINK));
    }

    @Test
    void savedQueryWithFiltersThatCantBeRead() throws Exception
    {
        when(this.client.getQuery("5")).thenReturn(query("{\"name\":\"Open tasks\",\"_links\":{"
            + "\"results\":{\"href\":\"/api/v3/work_packages?filters=%5Bnot%20json\"}}}"));
        when(this.l10n.getTranslationPlain(DESCRIBE + "savedQuery.keptAsIs", QUERY_NAME)).thenReturn(TRANSLATED);

        assertEquals(TRANSLATED, describe(INSTANCE, SAVED_QUERY_LINK).get(MESSAGE));
    }

    @ParameterizedTest
    @ValueSource(ints = { 403, 404 })
    void savedQueryNotVisible(int statusCode) throws Exception
    {
        when(this.client.getQuery("5")).thenThrow(new WorkItemRetrievalException("Not found", statusCode));
        when(this.l10n.getTranslationPlain(UNAVAILABLE_KEY, "5")).thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, "unavailable", LEVEL, WARNING, MESSAGE, TRANSLATED),
            describe(INSTANCE, SAVED_QUERY_LINK));
    }

    /**
     * The description links to the authentication, as the macro does.
     */
    @Test
    void savedQueryWhenTheUserIsNotConnected()
    {
        when(this.openProjectAccess.resolve(INSTANCE)).thenReturn(OpenProjectAccessResult.notConnected());
        when(this.l10n.getTranslationPlain(NOT_CONNECTED_KEY, "5")).thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, NOT_CONNECTED, LEVEL, WARNING, MESSAGE, TRANSLATED, AUTHENTICATION_LINK,
            Map.of("url", CONNECT_URL, "label", CONNECT_LABEL)), describe(INSTANCE, SAVED_QUERY_LINK));
    }

    @Test
    void savedQueryWhenTheTokenOfTheUserIsRejected() throws Exception
    {
        when(this.client.getQuery("5")).thenThrow(new WorkItemRetrievalException("Unauthorized", 401));
        when(this.l10n.getTranslationPlain(NOT_CONNECTED_KEY, "5")).thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, NOT_CONNECTED, LEVEL, WARNING, MESSAGE, TRANSLATED, AUTHENTICATION_LINK,
            Map.of("url", CONNECT_URL, "label", CONNECT_LABEL)), describe(INSTANCE, SAVED_QUERY_LINK));
    }

    /**
     * E.g. the connection doesn't let the users connect their OpenProject account.
     */
    @Test
    void savedQueryWhenTheUserCantConnectTheirAccount()
    {
        when(this.openProjectAccess.resolve(INSTANCE)).thenReturn(OpenProjectAccessResult.notConnected());
        when(this.pageEditRightChecker.getConnectURL(INSTANCE, DOCUMENT)).thenReturn(null);
        when(this.l10n.getTranslationPlain(NOT_CONNECTED_KEY, "5")).thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, NOT_CONNECTED, LEVEL, WARNING, MESSAGE, TRANSLATED),
            describe(INSTANCE, SAVED_QUERY_LINK));
    }

    /**
     * The chart and the calendar use the saved query as it is: they have no table showing its filters.
     */
    @ParameterizedTest
    @ValueSource(strings = { "openprojectcalendar", "openprojectchart" })
    void savedQueryOfAMacroWithoutTable(String macro) throws Exception
    {
        when(this.client.getQuery("5")).thenReturn(query("{\"name\":\"Open tasks\",\"_links\":{"
            + "\"project\":{\"href\":\"/api/v3/projects/9\",\"title\":\"Demo\"},"
            + "\"results\":{\"href\":\"/api/v3/projects/9/work_packages?includeSubprojects=false\"}}}"));
        when(this.l10n.getTranslationPlain(DESCRIBE + "savedQuery.withoutTable", QUERY_NAME, "Demo"))
            .thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, SAVED_QUERY, LEVEL, INFO, MESSAGE, TRANSLATED, "name", QUERY_NAME),
            describe(INSTANCE, SAVED_QUERY_LINK, macro));
    }

    @Test
    void savedQueryOfAllTheProjectsOfAMacroWithoutTable() throws Exception
    {
        when(this.client.getQuery("5")).thenReturn(query("{\"name\":\"Open tasks\",\"_links\":{"
            + "\"project\":{\"href\":null},\"results\":{\"href\":\"/api/v3/work_packages\"}}}"));
        when(this.l10n.getTranslationPlain(DESCRIBE + "savedQuery.allProjects.withoutTable", QUERY_NAME))
            .thenReturn(TRANSLATED);

        assertEquals(TRANSLATED,
            describe(INSTANCE, SERVER_URL + "/work_packages?query_id=5", "openprojectcalendar").get(MESSAGE));
    }

    @Test
    void savedQueryOfTheTableMacro() throws Exception
    {
        when(this.client.getQuery("5")).thenReturn(query("{\"name\":\"Open tasks\",\"_links\":{"
            + "\"project\":{\"href\":null},\"results\":{\"href\":\"/api/v3/work_packages\"}}}"));
        when(this.l10n.getTranslationPlain(DESCRIBE + "savedQuery.allProjects", QUERY_NAME)).thenReturn(TRANSLATED);

        assertEquals(TRANSLATED, describe(INSTANCE, SAVED_QUERY_LINK, "openproject").get(MESSAGE));
    }

    @Test
    void savedQueryWhenOpenProjectFails() throws Exception
    {
        when(this.client.getQuery("5"))
            .thenThrow(new WorkItemRetrievalException("Failed at https://op.example.com/secret", 500));

        Response response = this.resource.getLinkDescription(WIKI, INSTANCE, SAVED_QUERY_LINK, DOCUMENT, null);

        assertEquals(500, response.getStatus());
        assertEquals("Failed to retrieve the saved query from OpenProject.", response.getEntity());
        assertEquals("Failed to retrieve the saved query [5] of the OpenProject connection [main]. Root cause: "
            + "[WorkItemRetrievalException: Failed at https://op.example.com/secret]", this.logCapture.getMessage(0));
    }

    @Test
    void listWithFilters()
    {
        String link = SERVER_URL + "/work_packages?query_props=" + encode("{\"f\":[{\"n\":\"status\",\"o\":\"o\","
            + "\"v\":[]},{\"n\":\"type\",\"o\":\"=\",\"v\":[\"1\"]}],\"t\":\"id:desc\"}");
        when(this.l10n.getTranslationPlain(DESCRIBE + "list", 2)).thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, "list", LEVEL, INFO, MESSAGE, TRANSLATED, "filterCount", 2),
            describe(INSTANCE, link));
        verifyNoInteractions(this.openProjectAccess);
    }

    @Test
    void project()
    {
        when(this.l10n.getTranslationPlain(DESCRIBE + "project", "demo", 0)).thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, "project", LEVEL, INFO, MESSAGE, TRANSLATED, "filterCount", 0),
            describe(INSTANCE, PROJECT_LIST_LINK));
    }

    /**
     * OpenProject answers 404 for the projects that don't exist and for the ones the user isn't allowed to see.
     */
    @ParameterizedTest
    @ValueSource(ints = { 403, 404 })
    void projectNotVisible(int statusCode) throws Exception
    {
        when(this.client.getProject("demo")).thenThrow(new WorkItemRetrievalException("Not found", statusCode));
        when(this.l10n.getTranslationPlain(PROJECT_UNAVAILABLE_KEY, "demo")).thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, "unavailable", LEVEL, WARNING, MESSAGE, TRANSLATED),
            describe(INSTANCE, PROJECT_LIST_LINK));
    }

    /**
     * The project can't be checked: the link is described as when it's visible, the macro reporting the failure.
     */
    @Test
    void projectWhenOpenProjectFails() throws Exception
    {
        when(this.client.getProject("demo")).thenThrow(new WorkItemRetrievalException("Failed", 500));
        when(this.l10n.getTranslationPlain(DESCRIBE + "project", "demo", 0)).thenReturn(TRANSLATED);

        assertEquals("project", describe(INSTANCE, PROJECT_LIST_LINK).get(KIND));
    }

    /**
     * The project can't be checked: the description says why, with the link to authenticate, as the macro does.
     */
    @Test
    void projectWhenTheUserIsNotConnected()
    {
        when(this.openProjectAccess.resolve(INSTANCE)).thenReturn(OpenProjectAccessResult.notConnected());
        when(this.l10n.getTranslationPlain(PROJECT_NOT_CONNECTED_KEY, "demo")).thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, NOT_CONNECTED, LEVEL, WARNING, MESSAGE, TRANSLATED, AUTHENTICATION_LINK,
            Map.of("url", CONNECT_URL, "label", CONNECT_LABEL)), describe(INSTANCE, PROJECT_LIST_LINK));
    }

    @Test
    void projectWhenTheTokenOfTheUserIsRejected() throws Exception
    {
        when(this.client.getProject("demo")).thenThrow(new WorkItemRetrievalException("Unauthorized", 401));
        when(this.l10n.getTranslationPlain(PROJECT_NOT_CONNECTED_KEY, "demo")).thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, NOT_CONNECTED, LEVEL, WARNING, MESSAGE, TRANSLATED, AUTHENTICATION_LINK,
            Map.of("url", CONNECT_URL, "label", CONNECT_LABEL)), describe(INSTANCE, PROJECT_LIST_LINK));
    }

    @Test
    void workPackage()
    {
        when(this.l10n.getTranslationPlain(DESCRIBE + "workPackage", "12")).thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, "workPackage", LEVEL, INFO, MESSAGE, TRANSLATED),
            describe(INSTANCE, SERVER_URL + "/projects/demo/work_packages/12/activity"));
    }

    @Test
    void otherServer()
    {
        when(this.l10n.getTranslationPlain("openproject.link.otherServer", "other.example.com:8080", INSTANCE,
            SERVER_URL)).thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, "otherServer", LEVEL, WARNING, MESSAGE, TRANSLATED),
            describe(INSTANCE, "https://other.example.com:8080/projects/demo/work_packages?query_id=5"));
        verifyNoInteractions(this.openProjectAccess);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "5, 6", "op.example.com/work_packages" })
    void invalid(String link)
    {
        when(this.l10n.getTranslationPlain(DESCRIBE + "invalid")).thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, "invalid", LEVEL, WARNING, MESSAGE, TRANSLATED), describe(INSTANCE, link));
    }

    /**
     * The macro can't read the parameters of these links either.
     */
    @ParameterizedTest
    @ValueSource(strings = { "/work_packages?query_props=%5B%5D", "/work_packages?query_props=%7Bnot",
        "/projects/demo/work_packages?query_id=abc" })
    void invalidParameters(String path)
    {
        when(this.l10n.getTranslationPlain("openproject.link.invalidParameters")).thenReturn(TRANSLATED);

        assertEquals(Map.of(KIND, "invalid", LEVEL, WARNING, MESSAGE, TRANSLATED), describe(INSTANCE,
            SERVER_URL + path));
    }

    @Test
    void untranslatedMessage()
    {
        // The key stands for a message that isn't translated.
        assertEquals(DESCRIBE + "invalid", describe(INSTANCE, "not a link").get(MESSAGE));
    }

    @Test
    void defaultConnection()
    {
        when(this.configuration.getDefaultConnection())
            .thenReturn(new OpenProjectConnection(INSTANCE, SERVER_URL, null, null, null));
        when(this.l10n.getTranslationPlain(DESCRIBE + "workPackage", "12")).thenReturn(TRANSLATED);

        assertEquals("workPackage", describe("", SERVER_URL + "/wp/12").get(KIND));
    }

    @Test
    void noConnection()
    {
        Response response = this.resource.getLinkDescription(WIKI, "unknown", SERVER_URL + "/wp/12", DOCUMENT,
            null);

        assertEquals(404, response.getStatus());
        assertNull(response.getEntity());
        response = this.resource.getLinkDescription(WIKI, null, SERVER_URL + "/wp/12", DOCUMENT, null);
        assertEquals(404, response.getStatus());
    }

    private Map<String, Object> describe(String instance, String link)
    {
        return describe(instance, link, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> describe(String instance, String link, String macro)
    {
        Response response = this.resource.getLinkDescription(WIKI, instance, link, DOCUMENT, macro);
        assertEquals(200, response.getStatus());
        return (Map<String, Object>) response.getEntity();
    }

    private static SavedQuery query(String json) throws Exception
    {
        return new SavedQuery(MAPPER.readTree(json));
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
