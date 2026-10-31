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
import java.util.regex.Pattern;

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
 * <li>the work package "quick info" references ({@code ##1234} and {@code ###1234}), which OpenProject renders as
 * an empty {@code opce-macro-wp-quickinfo} element filled by its own JavaScript, are replaced with a link to the work
 * package, as the {@code #1234} references;</li>
 * <li>the checkboxes of the task lists, which the sanitizer removes, are replaced with a checked (&#x2611;) or
 * unchecked (&#x2610;) box character;</li>
 * <li>the relative links and images (e.g. the {@code /work_packages/1234} link of a {@code #1234} work package
 * reference) are made absolute against the URL of the OpenProject instance, so that they don't point to the
 * wiki.</li>
 * </ul>
 *
 * @version $Id$
 * @since 1.4.0
 */
@Component(roles = WorkPackageDescriptionCleaner.class)
@Singleton
public class WorkPackageDescriptionCleaner
{
    private static final String HREF = "href";

    private static final String ANCHOR_PREFIX = "#";

    private static final String HTML_START = "<html>";

    private static final String HTML_END = "</html>";

    /**
     * Marks the {@code span} elements that replace the {@code opce-macro-wp-quickinfo} elements before the HTML is
     * parsed: the HTML cleaner removes the elements it doesn't know, with their attributes.
     */
    private static final String QUICK_INFO_MARKER = "data-xwiki-wp-quickinfo";

    private static final Pattern QUICK_INFO_START_TAG =
        Pattern.compile("<opce-macro-wp-quickinfo\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern QUICK_INFO_END_TAG =
        Pattern.compile("</opce-macro-wp-quickinfo\\b", Pattern.CASE_INSENSITIVE);

    private static final String SPAN = "span";

    private static final String LINK = "a";

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

        String markedHtml = QUICK_INFO_START_TAG.matcher(html).replaceAll("<span " + QUICK_INFO_MARKER + "=\"\"");
        markedHtml = QUICK_INFO_END_TAG.matcher(markedHtml).replaceAll("</span");
        Document document = this.htmlCleaner.clean(new StringReader(markedHtml), configuration);
        HTMLUtils.stripHTMLEnvelope(document);
        String result = HTMLUtils.toString(document, true, true).trim();
        if (result.startsWith(HTML_START) && result.endsWith(HTML_END)) {
            result = result.substring(HTML_START.length(), result.length() - HTML_END.length());
        }
        return result;
    }

    /**
     * Removes the heading permalinks, replaces the work package quick info references and the task list checkboxes,
     * and makes the relative URLs absolute.
     */
    private static final class OpenProjectLinksFilter extends AbstractHTMLFilter
    {
        private static final String SRC = "src";

        /**
         * The ids of the work packages are numbers, or project-based identifiers such as {@code PROJ-12}.
         */
        private static final Pattern WORK_PACKAGE_ID = Pattern.compile("[\\w-]+");

        private static final String LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        private static final String CHECKED_BOX = "\u2611";

        private static final String UNCHECKED_BOX = "\u2610";

        private final URI baseUri;

        OpenProjectLinksFilter(String serverUrl)
        {
            this.baseUri = toBaseUri(serverUrl);
        }

        @Override
        public void filter(Document document, Map<String, String> cleaningParameters)
        {
            replaceQuickInfos(document);
            replaceTaskCheckboxes(document);

            List<Element> permalinks = new ArrayList<>();
            for (Element link : filterDescendants(document.getDocumentElement(), new String[] { LINK })) {
                if (isPermalink(link)) {
                    permalinks.add(link);
                }
            }
            permalinks.forEach(link -> link.getParentNode().removeChild(link));

            if (this.baseUri != null) {
                makeAbsolute(document.getDocumentElement().getElementsByTagName("*"));
            }
        }

        private void replaceQuickInfos(Document document)
        {
            for (Element quickInfo : filterDescendants(document.getDocumentElement(), new String[] { SPAN })) {
                if (!quickInfo.hasAttribute(QUICK_INFO_MARKER)) {
                    continue;
                }
                String id = quickInfo.getAttribute("data-id");
                String displayId = StringUtils.defaultIfBlank(quickInfo.getAttribute("data-display-id"), id);
                // As OpenProject, prefix the numeric ids with #, but not the project-based ones (PROJ-12).
                String label = StringUtils.containsAny(displayId, LETTERS) ? displayId : ANCHOR_PREFIX + displayId;
                Node replacement;
                if (!WORK_PACKAGE_ID.matcher(id).matches() || !WORK_PACKAGE_ID.matcher(displayId).matches()) {
                    replacement = document.createTextNode("");
                } else if (this.baseUri == null) {
                    // Without the URL of the OpenProject instance, a link would point to the wiki.
                    replacement = document.createTextNode(label);
                } else {
                    // The link that OpenProject renders for a #1234 reference. The relative URL is made absolute
                    // afterwards.
                    Element link = document.createElement(LINK);
                    link.setAttribute("class", "issue work_package op-uc-link");
                    link.setAttribute(HREF, "work_packages/" + id);
                    link.setTextContent(label);
                    replacement = link;
                }
                quickInfo.getParentNode().replaceChild(replacement, quickInfo);
            }
        }

        private void replaceTaskCheckboxes(Document document)
        {
            for (Element input : filterDescendants(document.getDocumentElement(), new String[] { "input" })) {
                if ("checkbox".equalsIgnoreCase(input.getAttribute("type"))) {
                    input.getParentNode().replaceChild(
                        document.createTextNode(input.hasAttribute("checked") ? CHECKED_BOX : UNCHECKED_BOX), input);
                }
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
