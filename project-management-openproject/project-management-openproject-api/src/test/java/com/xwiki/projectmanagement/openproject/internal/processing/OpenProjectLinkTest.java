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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OpenProjectLink}.
 *
 * @version $Id$
 */
class OpenProjectLinkTest
{
    private static final String PROJECT_LINK = "https://op.example.com/projects/demo/work_packages";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "12", "12, XWIKI-3", "query_id=27", "/projects/demo/work_packages",
        "ftp://op.example.com/work_packages", "https://", "http://:80/work_packages" })
    void parseRejectsWhatIsNotAnAbsoluteHttpLink(String value)
    {
        assertNull(OpenProjectLink.parse(value));
    }

    @Test
    void parseTrimsTheLink()
    {
        assertEquals(PROJECT_LINK, OpenProjectLink.parse("  " + PROJECT_LINK + " ").toString());
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://op.example.com", "https://op.example.com/", "http://op.example.com",
        "https://OP.Example.com", "https://op.example.com:443", "http://op.example.com:80/" })
    void belongsToTheSameServer(String serverUrl)
    {
        assertTrue(OpenProjectLink.parse(PROJECT_LINK).belongsTo(serverUrl));
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://other.example.com", "https://example.com", "https://op.example.com:8443",
        "https://op.example.com/openproject" })
    void doesNotBelongToAnotherServer(String serverUrl)
    {
        assertFalse(OpenProjectLink.parse(PROJECT_LINK).belongsTo(serverUrl));
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://op.example.com.evil.com", "https://evil.com/op.example.com",
        "https://op.example.co", "https://xop.example.com" })
    void doesNotBelongToALookalikeServer(String serverUrl)
    {
        assertFalse(OpenProjectLink.parse(PROJECT_LINK).belongsTo(serverUrl));
    }

    @Test
    void belongsToTheSameServerWithATrailingDot()
    {
        assertTrue(OpenProjectLink.parse("https://op.example.com./projects/demo/work_packages")
            .belongsTo("https://op.example.com"));
        assertTrue(OpenProjectLink.parse(PROJECT_LINK).belongsTo("https://OP.example.com./"));
        assertEquals("op.example.com", OpenProjectLink.parse("https://op.example.com./wp/5").getServer());
    }

    @Test
    void doesNotBelongToTheOtherPortOfAnExplicitDefaultPortOfAnotherScheme()
    {
        // Only the default port of the scheme of a URL is ignored: http://h:443 is plain HTTP on the port 443, which
        // isn't the server browsed with https://h (the port 443 with TLS).
        OpenProjectLink link = OpenProjectLink.parse("http://op.example.com:443/work_packages");

        assertFalse(link.belongsTo("https://op.example.com"));
        assertTrue(link.belongsTo("http://op.example.com:443"));
        assertFalse(OpenProjectLink.parse(PROJECT_LINK).belongsTo("http://op.example.com:443"));
    }

    @Test
    void belongsToAnIpv6ServerWrittenDifferently()
    {
        OpenProjectLink link = OpenProjectLink.parse("http://[2001:db8::1]:8080/projects/demo/work_packages");

        assertTrue(link.belongsTo("http://[2001:DB8:0:0:0:0:0:1]:8080"));
        assertTrue(link.belongsTo("https://[2001:0db8::0001]:8080/"));
        assertFalse(link.belongsTo("http://[2001:db8::2]:8080"));
        assertFalse(link.belongsTo("http://[2001:db8::1]"));
        assertEquals("[2001:db8::1]:8080", link.getServer());
    }

    @Test
    void belongsToAnInternationalizedServerWrittenInPunycode()
    {
        OpenProjectLink link = OpenProjectLink.parse("https://bücher.example/projects/demo/work_packages");

        assertTrue(link.belongsTo("https://xn--bcher-kva.example"));
        assertTrue(link.belongsTo("https://BÜCHER.example"));
        assertTrue(OpenProjectLink.parse("https://xn--bcher-kva.example/wp/5").belongsTo("https://bücher.example"));
        assertFalse(link.belongsTo("https://bucher.example"));
    }

    @Test
    void belongsToASubPathWrittenEncoded()
    {
        OpenProjectLink link = OpenProjectLink.parse("https://example.com/open%20project/projects/demo/work_packages");

        assertTrue(link.belongsTo("https://example.com/open project"));
        assertTrue(link.belongsTo("https://example.com/open%20project/"));
        assertTrue(OpenProjectLink.parse("https://example.com/op+x/wp/5").belongsTo("https://example.com/op%2Bx"));
        assertFalse(OpenProjectLink.parse("https://example.com/op+x/wp/5").belongsTo("https://example.com/op x"));
        // A malformed encoding is compared as written.
        assertTrue(OpenProjectLink.parse("https://example.com/op%zz/wp/5").belongsTo("https://example.com/op%zz"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://op.example.com/projects/demo/work_packages/5,https://other.com/wp/6",
        "https://op.example.com/wp/5, HTTP://other.com/wp/6",
        "https://op.example.com/work_packages?query_id=5,https://other.com/work_packages?query_id=6",
        "https://op.example.com/wp/5,6" })
    void parseRejectsAListOfLinks(String value)
    {
        assertNull(OpenProjectLink.parse(value));
    }

    @Test
    void getLinkOfAnotherServer()
    {
        OpenProjectConnection connection = new OpenProjectConnection("main", "https://op.example.com", "", "", "");
        String otherLink = "https://other.example.com/wp/5";

        assertEquals(otherLink, OpenProjectLink.getLinkOfAnotherServer(otherLink, connection).toString());
        assertNull(OpenProjectLink.getLinkOfAnotherServer(PROJECT_LINK, connection));
        assertNull(OpenProjectLink.getLinkOfAnotherServer("5, XWIKI-3", connection));
        assertNull(OpenProjectLink.getLinkOfAnotherServer(otherLink, null));
    }

    @Test
    void belongsToAServerWithANonDefaultPort()
    {
        OpenProjectLink link = OpenProjectLink.parse("http://op.example.com:8080/work_packages/5");

        assertTrue(link.belongsTo("https://op.example.com:8080/"));
        assertFalse(link.belongsTo("http://op.example.com"));
        assertEquals("op.example.com:8080", link.getServer());
    }

    @Test
    void belongsToAServerUnderASubPath()
    {
        OpenProjectLink link = OpenProjectLink.parse("https://example.com/openproject/projects/demo/work_packages");

        assertTrue(link.belongsTo("https://example.com/openproject"));
        assertTrue(link.belongsTo("https://example.com/openproject/"));
        assertTrue(link.belongsTo("https://example.com"));
        assertFalse(link.belongsTo("https://example.com/open"));
        assertFalse(link.belongsTo("https://example.com/other"));
        assertTrue(
            OpenProjectLink.parse("https://example.com/openproject").belongsTo("https://example.com/openproject/"));
        assertEquals("demo", link.getProject());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "not a link", "https://" })
    void belongsToAServerThatCantBeRead(String serverUrl)
    {
        // The servers can't be told apart: the link is not rejected.
        assertTrue(OpenProjectLink.parse(PROJECT_LINK).belongsTo(serverUrl));
    }

    @Test
    void getServerIgnoresTheCredentialsAndTheDefaultPort()
    {
        assertEquals("op.example.com",
            OpenProjectLink.parse("https://user:secret@OP.example.com:443/work_packages").getServer());
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://op.example.com/work_packages/5", "https://op.example.com/wp/5/",
        "https://op.example.com/projects/demo/work_packages/5/activity",
        "https://op.example.com/work_packages/5?tab=relations" })
    void getWorkPackageId(String link)
    {
        assertEquals("5", OpenProjectLink.parse(link).getWorkPackageId());
    }

    @Test
    void getWorkPackageIdOfAWorkPackageList()
    {
        assertNull(OpenProjectLink.parse(PROJECT_LINK).getWorkPackageId());
        assertNull(OpenProjectLink.parse("https://op.example.com/work_packages/5/activity/12").getWorkPackageId());
    }

    @Test
    void getProject()
    {
        assertEquals("demo", OpenProjectLink.parse(PROJECT_LINK).getProject());
        assertNull(OpenProjectLink.parse("https://op.example.com/work_packages").getProject());
        assertNull(OpenProjectLink.parse("https://op.example.com/projects").getProject());
    }

    /**
     * The address of a project, as in the address bar of the browser on its overview, selects its work packages.
     */
    @ParameterizedTest
    @ValueSource(strings = { "https://op.example.com/projects/demo", "https://op.example.com/projects/demo/",
        "https://op.example.com/projects/demo/work_packages/", "https://op.example.com/projects/demo?jump=overview",
        "https://op.example.com/projects/demo#top" })
    void getProjectOfTheAddressOfAProject(String link)
    {
        assertEquals("demo", OpenProjectLink.parse(link).getProject());
    }

    @Test
    void toWorkPackageListOfTheAddressOfAProject()
    {
        assertEquals("http://example.org/openproject/projects/9/work_packages",
            OpenProjectLink.parse("http://example.org/openproject/projects/demo").toWorkPackageList("9", null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://op.example.org/work_packages?query_id=27",
        "https://op.example.org/projects/demo/work_packages?query_id=27",
        "  http://op.example.org/work_packages?query_id=27  " })
    void getSavedQueryId(String link)
    {
        assertEquals("27", OpenProjectLink.parse(link).getSavedQueryId());
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://op.example.org/work_packages",
        "https://op.example.org/work_packages?query_id=abc",
        // A tweaked saved query has to be handled as a regular URL, so that the tweaks are applied.
        "https://op.example.org/work_packages?query_id=27&query_props=%7B%22t%22%3A%22id%3Aasc%22%7D",
        "https://op.example.org/work_packages?query_props=%7B%22t%22%3A%22id%3Aasc%22%7D" })
    void getSavedQueryIdWhenNotASavedQuery(String link)
    {
        assertNull(OpenProjectLink.parse(link).getSavedQueryId());
    }

    @Test
    void getQueryProperties()
    {
        assertEquals("{\"t\":\"id:asc\"}", OpenProjectLink.parse(
            "https://op.example.org/work_packages?query_id=27&query_props=%7B%22t%22%3A%22id%3Aasc%22%7D#top")
            .getQueryProperties());
        assertNull(OpenProjectLink.parse("https://op.example.org/work_packages?query_id=27").getQueryProperties());
        assertNull(OpenProjectLink.parse(PROJECT_LINK).getQueryProperties());
        assertNull(OpenProjectLink.parse("https://op.example.org/work_packages?xquery_props=1").getQueryProperties());
        assertThrows(IllegalArgumentException.class,
            () -> OpenProjectLink.parse("https://op.example.org/work_packages?query_props=%zz").getQueryProperties());
    }

    @Test
    void toWorkPackageList()
    {
        // The list of another project than the one of the link, with filters.
        assertEquals("https://op.example.org:8443/projects/9/work_packages"
            + "?query_props=%7B%22t%22%3A%22id%3Aasc%22%7D",
            OpenProjectLink.parse("https://op.example.org:8443/projects/demo/work_packages?query_id=27#top")
                .toWorkPackageList("9", "{\"t\":\"id:asc\"}"));
        // The list of all the projects, without filters, on a server running under a sub-path.
        assertEquals("http://example.org/openproject/work_packages",
            OpenProjectLink.parse("http://example.org/openproject/projects/demo/work_packages/?query_id=27")
                .toWorkPackageList(null, null));
        assertEquals("http://example.org/openproject/projects/9/work_packages",
            OpenProjectLink.parse("http://example.org/openproject/work_packages/?query_id=27")
                .toWorkPackageList("9", null));
    }

    @Test
    void toWorkPackageListWhenTheSubPathCantBeRead()
    {
        assertNull(OpenProjectLink.parse("https://op.example.org/wp/5").toWorkPackageList("9", null));
    }
}
