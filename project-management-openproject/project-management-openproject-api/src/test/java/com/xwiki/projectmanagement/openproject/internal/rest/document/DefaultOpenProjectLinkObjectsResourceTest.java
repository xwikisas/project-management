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
package com.xwiki.projectmanagement.openproject.internal.rest.document;

import java.net.URI;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.component.util.ReflectionUtils;
import org.xwiki.model.internal.reference.DefaultStringSpaceReferenceResolver;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.observation.ObservationManager;
import org.xwiki.rest.internal.JAXBConverter;
import org.xwiki.rest.internal.ModelFactory;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.user.CurrentUserReference;
import org.xwiki.user.UserReferenceResolver;
import org.xwiki.user.UserReferenceSerializer;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.model.WorkPackageLink;
import com.xwiki.projectmanagement.relations.store.ProjectManagementRelation;
import com.xwiki.urlshortener.URLShortenerException;
import com.xwiki.urlshortener.URLShortenerManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultOpenProjectLinkObjectsResource}.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
@ComponentList({ ModelFactory.class, DefaultStringSpaceReferenceResolver.class })
class DefaultOpenProjectLinkObjectsResourceTest
{
    private static final DocumentReference PAGE = new DocumentReference("xwiki", "Space", "Page");

    private static final DocumentReference RELATION_CLASS =
        new DocumentReference(ProjectManagementRelation.CLASS_REFERENCE, PAGE.getWikiReference());

    private static final String PAGE_ID = "id";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @InjectMockComponents
    private DefaultOpenProjectLinkObjectsResource resource;

    @MockComponent
    private URLShortenerManager urlShortenerManager;

    @MockComponent
    private OpenProjectConfiguration openProjectConfiguration;

    // Needed by the REST model factory.
    @MockComponent
    private JAXBConverter jaxbConverter;

    @MockComponent
    private UserReferenceSerializer<String> userReferenceSerializer;

    // Needed to save the page through its API (missing from MockitoOldcore in XWiki 15.10).
    @MockComponent
    private UserReferenceResolver<CurrentUserReference> currentUserReferenceResolver;

    @MockComponent
    private ObservationManager observationManager;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @BeforeEach
    void setUp() throws Exception
    {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost/xwiki/rest/"));
        // Injected by JAX-RS in production.
        ReflectionUtils.setFieldValue(this.resource, "uriInfo", uriInfo);

        XWikiContext context = this.oldcore.getXWikiContext();
        XWikiDocument classDocument = new XWikiDocument(RELATION_CLASS);
        BaseClass relationClass = classDocument.getXClass();
        relationClass.addTextField(ProjectManagementRelation.FIELD_CLIENT, "Client", 30);
        relationClass.addTextField(ProjectManagementRelation.FIELD_PROJECT, "Project", 30);
        relationClass.addTextField(ProjectManagementRelation.FIELD_WORK_ITEM, "Work item", 30);
        relationClass.addTextAreaField(ProjectManagementRelation.FIELD_CLIENT_PARAMS, "Client parameters", 30, 5);
        this.oldcore.getSpyXWiki().saveDocument(classDocument, context);
        this.oldcore.getSpyXWiki().saveDocument(new XWikiDocument(PAGE), context);

        when(this.urlShortenerManager.getDocumentReference(null, PAGE_ID)).thenReturn(PAGE);
        when(this.openProjectConfiguration.getOpenProjectConnections()).thenReturn(List.of(
            new OpenProjectConnection("Main", "https://op.example.org", "clientId", "secret", "instance-1")));

        // Everything is allowed, except what a test denies.
        allow("view");
        allow("edit");
        when(this.oldcore.getMockAuthorizationManager().hasAccess(eq(Right.EDIT), any(), any())).thenReturn(true);
    }

    @Test
    void linkCreatesTheRelation() throws Exception
    {
        Response response = this.resource.link(PAGE_ID, false, link("Demo", "42"));

        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        BaseObject relation = getRelation();
        assertEquals("openproject", relation.getStringValue(ProjectManagementRelation.FIELD_CLIENT));
        assertEquals("Demo", relation.getStringValue(ProjectManagementRelation.FIELD_PROJECT));
        assertEquals("42", relation.getStringValue(ProjectManagementRelation.FIELD_WORK_ITEM));
        // The instance id sent by OpenProject is stored as the name of the matching connection.
        assertEquals(RelationClientParams.ofInstance("Main"),
            relation.getLargeStringValue(ProjectManagementRelation.FIELD_CLIENT_PARAMS));
    }

    @Test
    void linkUpdatesTheExistingRelation() throws Exception
    {
        this.resource.link(PAGE_ID, false, link("Demo", "42"));

        Response response = this.resource.link(PAGE_ID, false, link("Demo", "43"));

        assertEquals(Response.Status.ACCEPTED.getStatusCode(), response.getStatus());
        assertEquals("43", getRelation().getStringValue(ProjectManagementRelation.FIELD_WORK_ITEM));
        assertEquals(1, getPage().getXObjects(RELATION_CLASS).size());
    }

    @Test
    void linkWhenTheRelationIsUnchanged() throws Exception
    {
        this.resource.link(PAGE_ID, false, link("Demo", "42"));
        String version = getPage().getVersion();

        Response response = this.resource.link(PAGE_ID, false, link("Demo", "42"));

        assertEquals(Response.Status.NOT_MODIFIED.getStatusCode(), response.getStatus());
        assertEquals(version, getPage().getVersion());
    }

    @Test
    void linkWhenThePageIsNotEditable() throws Exception
    {
        // Only the edit right of the current user on the page is missing.
        when(this.oldcore.getMockRightService().hasAccessLevel(eq("edit"), anyString(), anyString(), any()))
            .thenReturn(false);

        Response response = this.resource.link(PAGE_ID, false, link("Demo", "42"));

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        assertNull(getRelation());
    }

    @Test
    void linkWhenThePageIsNotEditableAndTheRelationIsUnchanged() throws Exception
    {
        this.resource.link(PAGE_ID, false, link("Demo", "42"));
        when(this.oldcore.getMockRightService().hasAccessLevel(eq("edit"), anyString(), anyString(), any()))
            .thenReturn(false);

        // The rights are checked before comparing the relation: nothing tells whether the page is linked or not.
        Response response = this.resource.link(PAGE_ID, false, link("Demo", "42"));

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    @Test
    void linkWhenTheInstanceIsUnknown() throws Exception
    {
        WorkPackageLink link = link("Demo", "42");
        link.setInstance("unknown");

        Response response = this.resource.link(PAGE_ID, false, link);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("Could not find a configured instance for the id [unknown].", response.getEntity());
        assertNull(getRelation());
    }

    @Test
    void getLinkWhenFailing() throws Exception
    {
        when(this.urlShortenerManager.getDocumentReference(null, PAGE_ID))
            .thenThrow(new URLShortenerException("Internal details"));

        WebApplicationException exception =
            assertThrows(WebApplicationException.class, () -> this.resource.getLink(PAGE_ID));

        assertEquals(500, exception.getResponse().getStatus());
        assertEquals("Failed to retrieve the link of the page.", exception.getResponse().getEntity());
        assertEquals("Failed to retrieve the link of the page.", this.logCapture.getMessage(0));
    }

    @Test
    void getLinkWhenPageIsMissing()
    {
        WebApplicationException exception =
            assertThrows(WebApplicationException.class, () -> this.resource.getLink("unknown"));

        assertEquals(404, exception.getResponse().getStatus());
        assertNull(exception.getResponse().getEntity());
    }

    private void allow(String right) throws Exception
    {
        when(this.oldcore.getMockRightService().hasAccessLevel(eq(right), anyString(), anyString(), any()))
            .thenReturn(true);
    }

    private WorkPackageLink link(String project, String workPackage)
    {
        WorkPackageLink link = new WorkPackageLink();
        link.setProject(project);
        link.setWorkPackage(workPackage);
        link.setInstance("instance-1");
        return link;
    }

    private XWikiDocument getPage() throws Exception
    {
        return this.oldcore.getSpyXWiki().getDocument(PAGE, this.oldcore.getXWikiContext());
    }

    private BaseObject getRelation() throws Exception
    {
        return getPage().getXObject(RELATION_CLASS);
    }
}
