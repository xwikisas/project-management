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

import java.net.IDN;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;

/**
 * A link to an OpenProject page, as copied from the browser and given to the OpenProject macros: the link of a work
 * package (e.g. {@code https://op.example.com/projects/demo/work_packages/5/activity}), of a work package list with its
 * filters ({@code https://op.example.com/projects/demo/work_packages?query_props=...}) or of a saved query
 * ({@code https://op.example.com/work_packages?query_id=5}). It reads what the link selects, and tells whether it
 * belongs to the server of a connection. The hosts are compared after their IDNA2003 conversion to ASCII, which maps
 * a few distinct internationalized names to the same host (e.g. {@code ß} and {@code ss}): a rare case, accepted.
 *
 * @version $Id$
 * @since 1.5.0
 */
public final class OpenProjectLink
{
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE);

    /**
     * A second link, in a list of links separated by commas (e.g.
     * {@code https://op.example.com/wp/5,https://other.example.com/wp/6}), which isn't a link itself.
     */
    private static final Pattern LINK_LIST_PATTERN = Pattern.compile(",\\s*https?://", Pattern.CASE_INSENSITIVE);

    /**
     * The path of the page of a single work package, e.g. {@code /work_packages/5},
     * {@code /projects/demo/work_packages/5/activity} or {@code /wp/5}.
     */
    private static final Pattern WORK_PACKAGE_PATH_PATTERN =
        Pattern.compile("/(?:work_packages|wp)/(\\d+)(?:/[^/]+)?/?$");

    private static final Pattern PROJECT_PATH_PATTERN = Pattern.compile("/projects/([^/]+)/");

    private static final Pattern SAVED_QUERY_PATTERN = Pattern.compile("^query_id=(\\d+)$");

    private static final Pattern QUERY_PROPS_PATTERN = Pattern.compile("(?:^|&)query_props=([^&]+)");

    private static final String SLASH = "/";

    private static final String DOT = ".";

    private final URL url;

    private OpenProjectLink(URL url)
    {
        this.url = url;
    }

    /**
     * @param value the value to read, e.g. the identifier of an OpenProject macro
     * @return the link, or {@code null} when the value isn't a single absolute HTTP(S) link (e.g. a list of work
     *     package ids, or a list of links, whose path holds a comma)
     */
    public static OpenProjectLink parse(String value)
    {
        String link = StringUtils.trim(value);
        if (link == null || !URL_PATTERN.matcher(link).matches() || LINK_LIST_PATTERN.matcher(link).find()) {
            return null;
        }
        try {
            URL url = new URL(link);
            return StringUtils.isEmpty(url.getHost()) || url.getPath().contains(",") ? null : new OpenProjectLink(url);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    /**
     * Tells whether a macro would request the work packages selected by a link from another server than the one the
     * link belongs to: the work packages are always requested from the server of the connection of the macro.
     *
     * @param identifier the work packages selected by a macro: a list of work packages, or a link
     * @param connection the connection of the macro, or {@code null} if it doesn't exist
     * @return the link, when the identifier is the link of another server than the one of the connection, or
     *     {@code null} otherwise (including when the identifier isn't a link, or the connection doesn't exist)
     */
    public static OpenProjectLink getLinkOfAnotherServer(String identifier, OpenProjectConnection connection)
    {
        OpenProjectLink link = parse(identifier);
        return link != null && connection != null && !link.belongsTo(connection.getServerURL()) ? link : null;
    }

    /**
     * @param serverUrl the URL of an OpenProject server, e.g. the server URL of a connection, possibly with a path when
     *     OpenProject runs under a sub-path (e.g. {@code https://example.com/openproject})
     * @return {@code true} if the link points to a page of this server, or if the server URL can't be read (the
     *     servers can then not be told apart). The spellings of the same server are ignored, without resolving the
     *     host names: the scheme (a port that is the default one of its scheme is ignored, but {@code http://h:443}
     *     is not {@code https://h}), the case of the host, a trailing dot, the form of an IPv6 address, an
     *     internationalized host name written in Unicode or in Punycode, a trailing slash and the percent-encoding of
     *     the path
     */
    public boolean belongsTo(String serverUrl)
    {
        OpenProjectLink server = parse(serverUrl);
        if (server == null) {
            return true;
        }
        return getNormalizedHost().equals(server.getNormalizedHost()) && getPort() == server.getPort()
            && isUnder(server.getDecodedPath());
    }

    /**
     * @param serverPath the decoded path of the server, empty when OpenProject doesn't run under a sub-path
     * @return {@code true} if the path of the link is the path of the server or one of its sub-paths
     */
    private boolean isUnder(String serverPath)
    {
        String prefix = StringUtils.stripEnd(serverPath, SLASH);
        String path = getDecodedPath();
        return prefix.isEmpty() || path.equals(prefix) || path.startsWith(prefix + SLASH);
    }

    private String getDecodedPath()
    {
        String path = this.url.getPath();
        try {
            // A plus sign is not a space in a path.
            return URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return path;
        }
    }

    /**
     * @return the server the link points to, as its host followed by its port when it isn't the default one (e.g.
     *     {@code op.example.com} or {@code op.example.com:8080}), to name it in the messages
     */
    public String getServer()
    {
        return getPort() < 0 ? getHost() : getHost() + ':' + getPort();
    }

    /**
     * @return the id of the work package the link points to, or {@code null} if it doesn't point to a single work
     *     package
     */
    public String getWorkPackageId()
    {
        Matcher matcher = WORK_PACKAGE_PATH_PATTERN.matcher(this.url.getPath());
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * @return the identifier (or id) of the project whose work packages the link lists, or {@code null} if it lists
     *     the work packages of all the projects
     */
    public String getProject()
    {
        Matcher matcher = PROJECT_PATH_PATTERN.matcher(this.url.getPath());
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * @return the id of the saved query the link points to, or {@code null} if it isn't the link of a saved query; a
     *     saved query tweaked in the browser has other parameters than {@code query_id}, and is then read as a list of
     *     work packages with its own filters
     */
    public String getSavedQueryId()
    {
        String query = getQuery();
        if (query == null) {
            return null;
        }
        Matcher matcher = SAVED_QUERY_PATTERN.matcher(query.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    /**
     * @return the query string of the link, or {@code null} if it has none
     */
    public String getQuery()
    {
        return this.url.getQuery();
    }

    /**
     * @return the decoded {@code query_props} parameter of the link, in which OpenProject puts the filters, the
     *     sorting and the columns of a work package list (e.g. {@code {"f":[...],"t":"id:asc"}}), or {@code null} if
     *     the link has none
     * @throws IllegalArgumentException if the parameter isn't correctly encoded
     */
    public String getQueryProperties()
    {
        Matcher matcher = QUERY_PROPS_PATTERN.matcher(StringUtils.defaultString(getQuery()));
        return matcher.find() ? URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8) : null;
    }

    /**
     * @param queryProperties the {@code query_props} parameter of the returned link (e.g. {@code {"f":[...]}}), or
     *     {@code null} for none
     * @return this link without its query string and its fragment, with the given {@code query_props} parameter, if
     *     any, e.g. to select the same work packages with fewer filters
     */
    public String withQueryProperties(String queryProperties)
    {
        String link = StringUtils.substringBefore(StringUtils.substringBefore(this.url.toString(), "#"), "?");
        return queryProperties == null ? link
            : link + "?query_props=" + URLEncoder.encode(queryProperties, StandardCharsets.UTF_8);
    }

    private String getHost()
    {
        return StringUtils.removeEnd(this.url.getHost().toLowerCase(Locale.ROOT), DOT);
    }

    /**
     * @return the host in a form that is the same for all its spellings, without resolving it: an IPv6 address in its
     *     full form, and an internationalized host name in Punycode
     */
    private String getNormalizedHost()
    {
        String host = getHost();
        if (host.startsWith("[")) {
            try {
                // An IPv6 literal is parsed, without any DNS lookup.
                return InetAddress.getByName(host).getHostAddress();
            } catch (UnknownHostException e) {
                return host;
            }
        }
        try {
            return IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return host;
        }
    }

    /**
     * @return the port of the link, or -1 when it's the default port of its scheme
     */
    private int getPort()
    {
        int port = this.url.getPort();
        return port == this.url.getDefaultPort() ? -1 : port;
    }

    @Override
    public String toString()
    {
        return this.url.toString();
    }
}
