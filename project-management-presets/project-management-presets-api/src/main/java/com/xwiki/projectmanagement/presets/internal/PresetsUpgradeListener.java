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

import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.event.ExtensionEvent;
import org.xwiki.extension.event.ExtensionInstalledEvent;
import org.xwiki.extension.event.ExtensionUpgradedEvent;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.query.Query;
import org.xwiki.query.QueryManager;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.projectmanagement.presets.Preset;

/**
 * Hides the preset pages saved visible before {@link PresetHiddenListener} hid them, when the presets are installed or
 * upgraded: visible, they show up in the navigation tree, with the {@code ProjectManagement > Code > Presets}
 * hierarchy above them. The presets are installed again, rather than upgraded, when their extension is uninstalled
 * then installed. Only the visible presets are saved, keeping their authors, so that hiding them is done once.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component
@Named(PresetsUpgradeListener.NAME)
@Singleton
public class PresetsUpgradeListener extends AbstractEventListener
{
    /**
     * The name of the listener.
     */
    public static final String NAME = "com.xwiki.projectmanagement.presets.internal.PresetsUpgradeListener";

    /**
     * The extension holding the preset class and the pages that create the presets.
     */
    static final String PRESETS_UI = "com.xwiki.projectmanagement:project-management-presets-ui";

    private static final String WIKI_NAMESPACE_PREFIX = "wiki:";

    private static final String VISIBLE_PRESETS = String.format(
        "select distinct doc.fullName from Document doc, doc.object('%s') as obj "
            + "where (doc.hidden <> true or doc.hidden is null)", Preset.CLASS_NAME);

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private QueryManager queryManager;

    @Inject
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    private WikiDescriptorManager wikiDescriptorManager;

    @Inject
    private Logger logger;

    /**
     * Default constructor.
     */
    public PresetsUpgradeListener()
    {
        super(NAME, List.of(new ExtensionInstalledEvent(), new ExtensionUpgradedEvent()));
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        ExtensionEvent extensionEvent = (ExtensionEvent) event;
        ExtensionId extensionId = extensionEvent.getExtensionId();
        if (extensionId == null || !PRESETS_UI.equals(extensionId.getId())) {
            return;
        }
        String namespace = extensionEvent.getNamespace();
        try {
            Collection<String> wikis;
            if (namespace == null) {
                // Installed on the farm.
                wikis = this.wikiDescriptorManager.getAllIds();
            } else if (namespace.startsWith(WIKI_NAMESPACE_PREFIX)) {
                wikis = List.of(namespace.substring(WIKI_NAMESPACE_PREFIX.length()));
            } else {
                return;
            }
            for (String wiki : wikis) {
                hidePresets(new WikiReference(wiki));
            }
        } catch (Exception e) {
            this.logger.warn("Failed to hide the preset pages of the namespace [{}]. They are hidden the next time "
                + "they are saved. Root cause: [{}]", namespace, e.getMessage());
            this.logger.debug("Full failure:", e);
        }
    }

    private void hidePresets(WikiReference wiki) throws Exception
    {
        XWikiContext context = this.contextProvider.get();
        XWiki xwiki = context.getWiki();
        List<String> presetPages =
            this.queryManager.createQuery(VISIBLE_PRESETS, Query.XWQL).setWiki(wiki.getName()).execute();
        for (String presetPage : presetPages) {
            DocumentReference reference = this.documentReferenceResolver.resolve(presetPage, wiki);
            XWikiDocument document = xwiki.getDocument(reference, context).clone();
            document.setHidden(true);
            // The authors are kept: the presets of the authors who aren't administrators stay ignored.
            xwiki.saveDocument(document, "Hide the preset page.", true, context);
        }
        if (!presetPages.isEmpty()) {
            this.logger.info("Hid [{}] preset pages on the wiki [{}].", presetPages.size(), wiki.getName());
        }
    }
}
