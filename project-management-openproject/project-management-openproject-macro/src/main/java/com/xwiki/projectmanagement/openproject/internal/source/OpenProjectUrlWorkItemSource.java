package com.xwiki.projectmanagement.openproject.internal.source;

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

import java.util.Map;
import java.util.regex.Pattern;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;

import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceReader;
import com.xwiki.projectmanagement.source.WorkItemSelection;
import com.xwiki.projectmanagement.source.WorkItemSource;
import com.xwiki.projectmanagement.source.WorkItemSourceContext;

/**
 * The {@code url} source of the OpenProject macros: the content is the link of an OpenProject work package, of a work
 * package list (with its filters) or of a saved query, as copied from the browser. The link is handled like the
 * {@code identifier} parameter that the content replaces.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component
@Named("openproject/url")
@Singleton
public class OpenProjectUrlWorkItemSource implements WorkItemSource
{
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);

    @Override
    public WorkItemSelection read(String content, WorkItemSourceContext context) throws WorkItemSourceException
    {
        String url = content.trim();
        if (!URL_PATTERN.matcher(url).matches()) {
            throw new WorkItemSourceException(String.format(
                "The macro content [%s] isn't the link of an OpenProject work package list or saved query.", url));
        }
        return WorkItemSelection.ofClientScope(Map.of(WorkItemSourceReader.IDENTIFIER, url));
    }
}
