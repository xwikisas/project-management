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

import java.util.List;
import java.util.Locale;

import javax.inject.Named;
import javax.inject.Provider;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.refactoring.internal.ModelBridge;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.user.UserReferenceResolver;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.projectmanagement.openproject.internal.AdminAuthorChecker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultOpenProjectSpaceResource}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultOpenProjectSpaceResourceTest
{
    private static final DocumentReference DOCUMENT_REFERENCE =
        new DocumentReference("xwiki", List.of("Space"), "WebHome");

    private static final DocumentReference TEMPLATE_REFERENCE =
        new DocumentReference("xwiki", List.of("OpenProject", "Code", "Template"), "WebHome");

    private static final DocumentReference TEMPLATE_CHILD_REFERENCE =
        new DocumentReference("xwiki", List.of("OpenProject", "Code", "Template", "Child"), "WebHome");

    @InjectMockComponents
    private DefaultOpenProjectSpaceResource resource;

    @MockComponent
    private DocumentReferenceResolver<String> resolver;

    @MockComponent
    private WikiDescriptorManager wikiDescriptorManager;

    @MockComponent
    private ContextualAuthorizationManager authorizationManager;

    @MockComponent
    @Named("document")
    private UserReferenceResolver<DocumentReference> userReferenceResolver;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    private ModelBridge modelBridge;

    @MockComponent
    private AdminAuthorChecker adminAuthorChecker;

    private XWikiContext context;

    @Mock
    private XWiki xwiki;

    @Mock
    private XWikiDocument document;

    @Mock
    private XWikiDocument templateDocument;

    @Mock
    private XWikiDocument templateChildDocument;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @BeforeComponent
    void beforeComponent()
    {
        // The context is needed when initializing the resource.
        this.context = mock(XWikiContext.class);
        when(this.xcontextProvider.get()).thenReturn(this.context);
    }

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.wikiDescriptorManager.getMainWikiId()).thenReturn("xwiki");
        when(this.resolver.resolve("Space.WebHome", new WikiReference("xwiki"))).thenReturn(DOCUMENT_REFERENCE);
        when(this.context.getWiki()).thenReturn(this.xwiki);
        when(this.authorizationManager.hasAccess(Right.EDIT)).thenReturn(true);
        when(this.xwiki.exists(TEMPLATE_REFERENCE, this.context)).thenReturn(true);
        when(this.xwiki.getDocument(DOCUMENT_REFERENCE, this.context)).thenReturn(this.document);
        when(this.document.getAuthors()).thenReturn(mock(DocumentAuthors.class));
        when(this.modelBridge.getDocumentReferences(TEMPLATE_REFERENCE.getLastSpaceReference()))
            .thenReturn(List.of(TEMPLATE_REFERENCE, TEMPLATE_CHILD_REFERENCE));
        when(this.xwiki.getDocument(TEMPLATE_REFERENCE, this.context)).thenReturn(this.templateDocument);
        when(this.xwiki.getDocument(TEMPLATE_CHILD_REFERENCE, this.context)).thenReturn(this.templateChildDocument);
        when(this.adminAuthorChecker.isLastSavedByAdmin(this.templateDocument)).thenReturn(true);
        when(this.adminAuthorChecker.isLastSavedByAdmin(this.templateChildDocument)).thenReturn(true);
        when(this.templateChildDocument.getDocumentReferenceWithLocale()).thenReturn(TEMPLATE_CHILD_REFERENCE);
    }

    @Test
    void createSpaceWhenTemplatePageWasNotSavedByAdmin() throws Exception
    {
        when(this.adminAuthorChecker.isLastSavedByAdmin(this.templateChildDocument)).thenReturn(false);

        Response response = this.resource.createSpace("Space.WebHome", false, null, null, null, "Title");

        assertEquals(409, response.getStatus());
        assertEquals("The OpenProject template must be saved by an administrator.", response.getEntity());
        verify(this.xwiki, never()).saveDocument(any(XWikiDocument.class), any(XWikiContext.class));
        verify(this.document, never()).readFromTemplate(any(DocumentReference.class), any(XWikiContext.class));
        assertEquals("Not creating the OpenProject space because the template page [xwiki:OpenProject.Code.Template."
            + "Child.WebHome] wasn't last saved by a user with administration right on the wiki.",
            this.logCapture.getMessage(0));
    }

    @Test
    void createSpaceWhenTemplateTranslationWasNotSavedByAdmin() throws Exception
    {
        DocumentReference translationReference = new DocumentReference(TEMPLATE_CHILD_REFERENCE, Locale.FRENCH);
        XWikiDocument translation = mock(XWikiDocument.class);
        when(translation.getDocumentReferenceWithLocale()).thenReturn(translationReference);
        when(this.templateChildDocument.getTranslationLocales(this.context)).thenReturn(List.of(Locale.FRENCH));
        when(this.xwiki.getDocument(translationReference, this.context)).thenReturn(translation);
        when(this.adminAuthorChecker.isLastSavedByAdmin(translation)).thenReturn(false);

        Response response = this.resource.createSpace("Space.WebHome", false, null, null, null, "Title");

        assertEquals(409, response.getStatus());
        assertEquals("The OpenProject template must be saved by an administrator.", response.getEntity());
        verify(this.xwiki, never()).saveDocument(any(XWikiDocument.class), any(XWikiContext.class));
        assertEquals("Not creating the OpenProject space because the template page [xwiki:OpenProject.Code.Template."
            + "Child.WebHome(fr)] wasn't last saved by a user with administration right on the wiki.",
            this.logCapture.getMessage(0));
    }

    @Test
    void createSpaceWhenSaveFails() throws Exception
    {
        doThrow(new XWikiException(XWikiException.MODULE_XWIKI_STORE,
            XWikiException.ERROR_XWIKI_STORE_HIBERNATE_SAVING_DOC, "Internal details"))
            .when(this.xwiki).saveDocument(this.document, this.context);

        Response response = this.resource.createSpace("Space.WebHome", false, null, null, null, "Title");

        assertEquals(500, response.getStatus());
        assertEquals("Failed to create the OpenProject template at [Space.WebHome].", response.getEntity());
        assertEquals("Failed to create the OpenProject template at [Space.WebHome].", this.logCapture.getMessage(0));
    }

    @Test
    void createSpaceWhenSaveIsDenied() throws Exception
    {
        doThrow(new XWikiException(XWikiException.MODULE_XWIKI_ACCESS, XWikiException.ERROR_XWIKI_ACCESS_DENIED,
            "Access denied")).when(this.xwiki).saveDocument(any(XWikiDocument.class), any(XWikiContext.class));

        Response response = this.resource.createSpace("Space.WebHome", false, null, null, null, "Title");

        assertEquals(401, response.getStatus());
        assertNull(response.getEntity());
    }
}
