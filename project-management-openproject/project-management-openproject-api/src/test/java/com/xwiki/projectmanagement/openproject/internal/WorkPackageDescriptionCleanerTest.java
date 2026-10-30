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

import org.junit.jupiter.api.Test;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.xml.html.DefaultHTMLCleanerComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link WorkPackageDescriptionCleaner}.
 *
 * @version $Id$
 */
@ComponentTest
@DefaultHTMLCleanerComponentList
class WorkPackageDescriptionCleanerTest
{
    private static final String SERVER_URL = "https://op.example.org";

    @InjectMockComponents
    private WorkPackageDescriptionCleaner cleaner;

    @Test
    void keepFormatting()
    {
        assertEquals("<p class=\"op-uc-p\"><strong>bold</strong> <em>italic</em> <code class=\"op-uc-code\">code</code>"
            + "</p>\n<ul class=\"op-uc-list\">\n<li class=\"op-uc-list--item\">item</li>\n</ul>\n"
            + "<pre class=\"op-uc-code-block\"><span class=\"kt\">int</span> a;</pre>",
            this.cleaner.clean("<p class=\"op-uc-p\"><strong>bold</strong> <em>italic</em> "
                + "<code class=\"op-uc-code\">code</code></p>\n<ul class=\"op-uc-list\">\n"
                + "<li class=\"op-uc-list--item\">item</li>\n</ul>\n"
                + "<pre class=\"op-uc-code-block\"><span class=\"kt\">int</span> a;</pre>", SERVER_URL));
    }

    @Test
    void removeScriptsEventHandlersAndJavaScriptLinks()
    {
        assertEquals("<p>text <img src=\"https://op.example.org/x.png\" /> <a>link</a></p><pre>alert(1)</pre>",
            this.cleaner.clean("<p>text <img src=\"/x.png\" onerror=\"alert(1)\"> "
                + "<a href=\"javascript:alert(2)\" onclick=\"alert(3)\">link</a></p><script>alert(1)</script>",
                SERVER_URL));
    }

    @Test
    void removeHeadingPermalinks()
    {
        assertEquals("<h1 class=\"op-uc-h1\" id=\"op-frag-title\">\nTitle</h1>\n"
            + "<h2 class=\"op-uc-h2\" id=\"op-frag-sub-title\">\nSub <em>title</em>\n</h2>\n"
            + "<p>See <a href=\"#op-frag-title\">the title</a>.</p>",
            this.cleaner.clean("<h1 id=\"op-frag-title\" class=\"op-uc-h1\">\n<a href=\"#op-frag-title\" "
                + "aria-hidden=\"true\" class=\"op-uc-link_permalink icon-link op-uc-link\"></a>Title</h1>\n"
                + "<h2 id=\"op-frag-sub-title\" class=\"op-uc-h2\">\n<a href=\"#op-frag-sub-title\" "
                + "aria-hidden=\"true\" class=\"op-uc-link_permalink icon-link op-uc-link\"></a>Sub <em>title</em>\n"
                + "</h2>\n<p>See <a href=\"#op-frag-title\">the title</a>.</p>", SERVER_URL));
    }

    @Test
    void makeRelativeLinksAbsolute()
    {
        // The link of a #13 work package reference, as rendered by OpenProject.
        assertEquals("<p>See <a class=\"issue work_package op-uc-link\""
            + " href=\"https://op.example.org/work_packages/13\" target=\"_top\">#13</a> and"
            + " <a href=\"https://www.xwiki.org/\">XWiki</a>.</p>",
            this.cleaner.clean("<p>See <a class=\"issue work_package op-uc-link\" "
                + "href=\"/work_packages/13\" target=\"_top\">#13</a> and "
                + "<a href=\"https://www.xwiki.org/\">XWiki</a>.</p>", SERVER_URL));
    }

    @Test
    void makeRelativeLinksAbsoluteWithServerPath()
    {
        assertEquals("<p><a href=\"https://example.org/openproject/work_packages/1\">absolute path</a> "
            + "<a href=\"https://example.org/openproject/work_packages/2\">relative path</a></p>",
            this.cleaner.clean("<p><a href=\"/openproject/work_packages/1\">absolute path</a> "
                + "<a href=\"work_packages/2\">relative path</a></p>", "https://example.org/openproject"));
    }

    @Test
    void cleanWithoutServerUrl()
    {
        assertEquals("<p><a href=\"/work_packages/13\">#13</a></p>",
            this.cleaner.clean("<p><a href=\"/work_packages/13\" onclick=\"alert(1)\">#13</a></p>", null));
    }

    @Test
    void cleanEmptyDescription()
    {
        assertNull(this.cleaner.clean(null, SERVER_URL));
        assertEquals("", this.cleaner.clean("", SERVER_URL));
    }
}
