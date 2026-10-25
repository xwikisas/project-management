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
package com.xwiki.projectmanagement.openproject.internal.access;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.internal.event.XObjectAddedEvent;
import com.xpn.xwiki.internal.event.XObjectDeletedEvent;
import com.xpn.xwiki.internal.event.XObjectUpdatedEvent;
import com.xpn.xwiki.objects.BaseObjectReference;

/**
 * Clears the cached public data of a wiki when its OpenProject connections or its OpenProject configuration (which
 * holds the duration of the cache) change, so that the change applies right away.
 *
 * @version $Id$
 * @since 1.3.1
 */
@Component
@Singleton
@Named(PublicDataCacheInvalidationListener.NAME)
public class PublicDataCacheInvalidationListener extends AbstractEventListener
{
    /**
     * The name of the listener.
     */
    public static final String NAME =
        "com.xwiki.projectmanagement.openproject.internal.access.PublicDataCacheInvalidationListener";

    private static final EntityReference CONNECTION_CLASS =
        BaseObjectReference.any("OpenProject.Code.OpenProjectConnectionClass");

    private static final EntityReference CONFIGURATION_CLASS =
        BaseObjectReference.any("OpenProject.Code.OpenProjectConfigurationClass");

    @Inject
    private PublicDataClientProvider publicDataClientProvider;

    /**
     * Default constructor.
     */
    public PublicDataCacheInvalidationListener()
    {
        super(NAME, List.of(new XObjectAddedEvent(CONNECTION_CLASS), new XObjectUpdatedEvent(CONNECTION_CLASS),
            new XObjectDeletedEvent(CONNECTION_CLASS), new XObjectAddedEvent(CONFIGURATION_CLASS),
            new XObjectUpdatedEvent(CONFIGURATION_CLASS)));
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        XWikiDocument document = (XWikiDocument) source;
        this.publicDataClientProvider.clearCache(document.getDocumentReference().getWikiReference().getName());
    }
}
