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
package com.xwiki.projectmanagement.openproject;

import java.util.List;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.script.ModelScriptService;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService;
import com.xwiki.urlshortener.script.URLShortenerScriptService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Page test for {@code OpenProject.Code.Relations.OPMentionsResultPage}, the source of the list of work packages
 * mentioning a page.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@ComponentList({
    ModelScriptService.class
})
class OPMentionsResultPageTest extends PageTest
{
    private static final String WIKI = "xwiki";

    private static final DocumentReference RESULT_PAGE =
        new DocumentReference(WIKI, List.of("OpenProject", "Code", "Relations"), "OPMentionsResultPage");

    private static final DocumentReference MENTIONED_PAGE = new DocumentReference(WIKI, "Space", "Page");

    @MockComponent(classToMock = OpenProjectScriptService.class)
    @Named("openproject")
    private ScriptService openProjectScriptService;

    @MockComponent(classToMock = URLShortenerScriptService.class)
    @Named("urlshortener")
    private ScriptService urlShortenerScriptService;

    @BeforeEach
    void setUp()
    {
        when(((OpenProjectScriptService) this.openProjectScriptService).getFilterBuilder())
            .thenAnswer(invocation -> new FilterBuilder());
        this.context.setAction("get");
        this.request.put("instance", "main");
        this.request.put("docRef", "xwiki:Space.Page");
    }

    @Test
    void useTheExistingIdOfThePage() throws Exception
    {
        XWikiDocument mentionedPage = new XWikiDocument(MENTIONED_PAGE);
        mentionedPage.newXObject(new DocumentReference(WIKI, List.of("URLShortener", "Code"), "URLShortenerClass"),
            this.context).setStringValue("pageID", "abcd");
        this.xwiki.saveDocument(mentionedPage, this.context);

        renderPage(RESULT_PAGE);

        verify((OpenProjectScriptService) this.openProjectScriptService).getMentioningWorkPackages(eq("main"),
            eq(0), eq(10), eq("[{\"identifier\":{\"operator\":\"=\",\"values\":[\"abcd\"]}}]"));
        verify((URLShortenerScriptService) this.urlShortenerScriptService, never()).createShortenedURL(any());
    }

    @Test
    void dontCreateAnIdForThePage() throws Exception
    {
        renderPage(RESULT_PAGE);

        verify((URLShortenerScriptService) this.urlShortenerScriptService, never()).createShortenedURL(any());
        verify((OpenProjectScriptService) this.openProjectScriptService, never())
            .getMentioningWorkPackages(anyString(), anyInt(), anyInt(), any());
        assertFalse(this.xwiki.exists(MENTIONED_PAGE, this.context));
    }
}
