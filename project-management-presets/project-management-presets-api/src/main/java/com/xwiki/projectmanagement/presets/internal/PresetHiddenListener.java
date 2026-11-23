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
package com.xwiki.projectmanagement.presets.internal;

import java.util.List;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.bridge.event.DocumentCreatingEvent;
import org.xwiki.bridge.event.DocumentUpdatingEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.projectmanagement.presets.Preset;

/**
 * Hides the preset pages before they are saved. A preset is configuration data stored in its own page: visible, it
 * shows up in the navigation tree, with the {@code ProjectManagement > Code > Presets} hierarchy above it. The preset
 * pages are created from a hidden template, whose hidden flag isn't copied, and the existing ones are hidden the next
 * time they are saved.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component
@Named(PresetHiddenListener.NAME)
@Singleton
public class PresetHiddenListener extends AbstractEventListener
{
    /**
     * The name of the listener.
     */
    public static final String NAME = "com.xwiki.projectmanagement.presets.internal.PresetHiddenListener";

    /**
     * Default constructor.
     */
    public PresetHiddenListener()
    {
        super(NAME, List.of(new DocumentCreatingEvent(), new DocumentUpdatingEvent()));
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        XWikiDocument document = (XWikiDocument) source;
        if (!document.isHidden() && document.getXObject(Preset.REFERENCE) != null) {
            document.setHidden(true);
        }
    }
}
