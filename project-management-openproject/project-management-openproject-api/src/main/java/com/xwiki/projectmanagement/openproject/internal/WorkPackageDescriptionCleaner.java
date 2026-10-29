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

import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xwiki.component.annotation.Component;
import org.xwiki.xml.html.HTMLCleaner;
import org.xwiki.xml.html.HTMLCleanerConfiguration;
import org.xwiki.xml.html.HTMLUtils;
import org.xwiki.xml.html.filter.AbstractHTMLFilter;
import org.xwiki.xml.html.filter.HTMLFilter;

/**
 * Cleans the HTML description of a work package, as rendered by OpenProject, before it is displayed in the wiki:
 * <ul>
 * <li>the HTML is cleaned in restricted mode, so that only the elements and attributes allowed by the wiki's HTML
 * sanitizer are kept (the formatting, lists, links, tables, code...);</li>
 * <li>the permalinks that OpenProject adds to the headings (empty links to an anchor of the page) are removed, since
 * they would be displayed with a generated label;</li>
 * <li>the relative links and images (e.g. the {@code /work_packages/1234} link of a {@code #1234} work package
 * reference) are made absolute against the URL of the OpenProject instance, so that they don't point to the
 * wiki.</li>
 * </ul>
 *
 * @version $Id$
 * @since 1.3.1
 */
@Component(roles = WorkPackageDescriptionCleaner.class)
@Singleton
public class WorkPackageDescriptionCleaner
{
    private static final String HREF = "href";

    private static final String ANCHOR_PREFIX = "#";

    private static final String HTML_START = "<html>";

    private static final String HTML_END = "</html>";

    @Inject
    private HTMLCleaner htmlCleaner;

    /**
     * @param html the description of a work package, as HTML rendered by OpenProject
     * @param serverUrl the URL of the OpenProject instance, against which the relative URLs are resolved
     * @return the cleaned HTML, without the {@code html} and {@code body} elements
     */
    public String clean(String html, String serverUrl)
    {
        if (StringUtils.isBlank(html)) {
            return html;
        }

        HTMLCleanerConfiguration configuration = this.htmlCleaner.getDefaultConfiguration();
        Map<String, String> parameters = new HashMap<>(configuration.getParameters());
        parameters.put(HTMLCleanerConfiguration.RESTRICTED, Boolean.TRUE.toString());
        parameters.put(HTMLCleanerConfiguration.HTML_VERSION, "5");
        configuration.setParameters(parameters);
        // Run before the default filters, so that the sanitizer checks the final URLs.
        List<HTMLFilter> filters = new ArrayList<>();
        filters.add(new OpenProjectLinksFilter(serverUrl));
        filters.addAll(configuration.getFilters());
        configuration.setFilters(filters);

        Document document = this.htmlCleaner.clean(new StringReader(html), configuration);
        HTMLUtils.stripHTMLEnvelope(document);
        String result = HTMLUtils.toString(document, true, true).trim();
        if (result.startsWith(HTML_START) && result.endsWith(HTML_END)) {
            result = result.substring(HTML_START.length(), result.length() - HTML_END.length());
        }
        return result;
    }

    /**
     * Removes the heading permalinks and makes the relative URLs absolute.
     */
    private static final class OpenProjectLinksFilter extends AbstractHTMLFilter
    {
        private static final String SRC = "src";

        private final URI baseUri;

        OpenProjectLinksFilter(String serverUrl)
        {
            this.baseUri = toBaseUri(serverUrl);
        }

        @Override
        public void filter(Document document, Map<String, String> cleaningParameters)
        {
            List<Element> permalinks = new ArrayList<>();
            for (Element link : filterDescendants(document.getDocumentElement(), new String[] { "a" })) {
                if (isPermalink(link)) {
                    permalinks.add(link);
                }
            }
            permalinks.forEach(link -> link.getParentNode().removeChild(link));

            if (this.baseUri != null) {
                makeAbsolute(document.getDocumentElement().getElementsByTagName("*"));
            }
        }

        private boolean isPermalink(Element link)
        {
            if (!link.getAttribute(HREF).startsWith(ANCHOR_PREFIX)) {
                return false;
            }
            NodeList children = link.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.TEXT_NODE || StringUtils.isNotBlank(child.getNodeValue())) {
                    return false;
                }
            }
            return true;
        }

        private void makeAbsolute(NodeList elements)
        {
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                makeAbsolute(element, HREF);
                makeAbsolute(element, SRC);
            }
        }

        private void makeAbsolute(Element element, String attribute)
        {
            String value = element.getAttribute(attribute);
            if (StringUtils.isBlank(value) || value.startsWith(ANCHOR_PREFIX)) {
                return;
            }
            try {
                URI uri = new URI(value.trim());
                if (!uri.isAbsolute()) {
                    element.setAttribute(attribute, this.baseUri.resolve(uri).toString());
                }
            } catch (URISyntaxException e) {
                // Leave the value as it is, it's up to the sanitizer to decide whether it's kept.
            }
        }

        private static URI toBaseUri(String serverUrl)
        {
            if (StringUtils.isBlank(serverUrl)) {
                return null;
            }
            try {
                URI uri = new URI(StringUtils.appendIfMissing(serverUrl.trim(), "/"));
                return uri.isAbsolute() ? uri : null;
            } catch (URISyntaxException e) {
                return null;
            }
        }
    }
}
