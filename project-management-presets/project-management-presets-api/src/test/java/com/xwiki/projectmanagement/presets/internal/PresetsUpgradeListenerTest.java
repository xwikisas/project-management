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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.event.ExtensionInstalledEvent;
import org.xwiki.extension.event.ExtensionUpgradedEvent;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryExecutor;
import org.xwiki.query.QueryManager;
import org.xwiki.query.internal.DefaultQuery;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xwiki.projectmanagement.presets.Preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PresetsUpgradeListener}.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
class PresetsUpgradeListenerTest
{
    private static final String VISIBLE_PRESETS = "select distinct doc.fullName from Document doc, "
        + "doc.object('ProjectManagement.Code.Presets.PresetClass') as obj "
        + "where (doc.hidden <> true or doc.hidden is null)";

    private static final ExtensionId PRESETS_UI =
        new ExtensionId("com.xwiki.projectmanagement:project-management-presets-ui", "1.5.0");

    private static final DocumentReference PRESET =
        new DocumentReference("xwiki", Arrays.asList("ProjectManagement", "Code", "Presets"), "Preset1");

    private static final DocumentReference SUBWIKI_PRESET =
        new DocumentReference("subwiki", Arrays.asList("ProjectManagement", "Code", "Presets"), "Preset2");

    private static final DocumentReference USER = new DocumentReference("xwiki", "XWiki", "User");

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @InjectMockComponents
    private PresetsUpgradeListener listener;

    @MockComponent
    private QueryManager queryManager;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    /**
     * The wikis on which the visible presets were queried.
     */
    private final List<String> queriedWikis = new ArrayList<>();

    private UserReference userReference;

    @BeforeEach
    void setUp() throws Exception
    {
        UserReferenceResolver<DocumentReference> userResolver =
            this.componentManager.getInstance(UserReferenceResolver.TYPE_DOCUMENT_REFERENCE, "document");
        this.userReference = userResolver.resolve(USER);
        // The installing user is an administrator, unlike the author of the presets.
        this.oldcore.getXWikiContext().setUserReference(new DocumentReference("xwiki", "XWiki", "Admin"));
    }

    @Test
    void hidesTheVisiblePresetsWhenThePresetsAreUpgraded() throws Exception
    {
        savePreset(PRESET);
        stubVisiblePresets(Map.of("xwiki", List.of("ProjectManagement.Code.Presets.Preset1")));

        this.listener.onEvent(new ExtensionUpgradedEvent(PRESETS_UI, "wiki:xwiki"), null, null);

        XWikiDocument preset = loadPreset(PRESET);
        assertTrue(preset.isHidden());
        // The preset is kept, with its author: the presets of the authors who aren't administrators stay ignored.
        assertEquals(this.userReference, preset.getAuthors().getEffectiveMetadataAuthor());
        assertEquals("default", preset.getXObject(Preset.REFERENCE).getStringValue(Preset.FIELD_NAME));
        assertEquals(List.of("xwiki"), this.queriedWikis);
    }

    @Test
    void hidesTheVisiblePresetsOfAllTheWikisWhenThePresetsAreInstalledOnTheFarm() throws Exception
    {
        // E.g. the presets are installed again after being uninstalled.
        savePreset(PRESET);
        savePreset(SUBWIKI_PRESET);
        when(this.oldcore.getWikiDescriptorManager().getAllIds()).thenReturn(List.of("xwiki", "subwiki"));
        stubVisiblePresets(Map.of("xwiki", List.of("ProjectManagement.Code.Presets.Preset1"), "subwiki",
            List.of("ProjectManagement.Code.Presets.Preset2")));

        this.listener.onEvent(new ExtensionInstalledEvent(PRESETS_UI, null), null, null);

        assertTrue(loadPreset(PRESET).isHidden());
        assertTrue(loadPreset(SUBWIKI_PRESET).isHidden());
    }

    @Test
    void leavesThePresetsAloneWhenAnotherExtensionIsUpgraded() throws Exception
    {
        savePreset(PRESET);
        stubVisiblePresets(Map.of("xwiki", List.of("ProjectManagement.Code.Presets.Preset1")));

        this.listener.onEvent(new ExtensionUpgradedEvent(
            new ExtensionId("com.xwiki.projectmanagement:project-management-relations-ui", "1.5.0"), "wiki:xwiki"),
            null, null);

        assertFalse(loadPreset(PRESET).isHidden());
        assertEquals(List.of(), this.queriedWikis);
    }

    @Test
    void logsTheFailureToHideThePresets() throws Exception
    {
        when(this.queryManager.createQuery(VISIBLE_PRESETS, Query.XWQL))
            .thenReturn(new DefaultQuery(VISIBLE_PRESETS, Query.XWQL, new QueryExecutor()
            {
                @Override
                public <T> List<T> execute(Query query) throws QueryException
                {
                    throw new QueryException("Store failure", query, null);
                }
            }));

        // The upgrade isn't interrupted.
        this.listener.onEvent(new ExtensionUpgradedEvent(PRESETS_UI, "wiki:xwiki"), null, null);

        assertEquals("Failed to hide the preset pages of the namespace [wiki:xwiki]. They are hidden the next time "
            + "they are saved. Root cause: [Store failure. Query statement = [" + VISIBLE_PRESETS + "]]",
            this.logCapture.getMessage(0));
    }

    private void savePreset(DocumentReference reference) throws Exception
    {
        XWikiContext context = this.oldcore.getXWikiContext();
        XWikiDocument document = new XWikiDocument(reference);
        document.getAuthors().setEffectiveMetadataAuthor(this.userReference);
        document.newXObject(Preset.REFERENCE, context).setStringValue(Preset.FIELD_NAME, "default");
        this.oldcore.getSpyXWiki().saveDocument(document, context);
    }

    private XWikiDocument loadPreset(DocumentReference reference) throws Exception
    {
        return this.oldcore.getSpyXWiki().getDocument(reference, this.oldcore.getXWikiContext());
    }

    /**
     * Answers the query of the visible presets of each wiki with the given page names.
     */
    private void stubVisiblePresets(Map<String, List<String>> presetsByWiki) throws QueryException
    {
        QueryExecutor executor = new QueryExecutor()
        {
            @Override
            @SuppressWarnings("unchecked")
            public <T> List<T> execute(Query query)
            {
                queriedWikis.add(query.getWiki());
                return (List<T>) new ArrayList<>(presetsByWiki.getOrDefault(query.getWiki(), List.of()));
            }
        };
        when(this.queryManager.createQuery(VISIBLE_PRESETS, Query.XWQL))
            .thenAnswer(invocation -> new DefaultQuery(VISIBLE_PRESETS, Query.XWQL, executor));
    }
}
