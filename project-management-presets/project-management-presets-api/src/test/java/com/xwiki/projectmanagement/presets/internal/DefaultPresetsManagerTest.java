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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryExecutor;
import org.xwiki.query.QueryFilter;
import org.xwiki.query.QueryManager;
import org.xwiki.query.internal.DefaultQuery;
import org.xwiki.query.internal.DocumentQueryFilter;
import org.xwiki.query.internal.ViewableQueryFilter;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xwiki.projectmanagement.presets.Preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultPresetsManager}, on preset pages stored in the wiki.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
@ComponentList({ DocumentQueryFilter.class, ViewableQueryFilter.class })
class DefaultPresetsManagerTest
{
    private static final String PRESET_OBJECTS = "from doc.object('ProjectManagement.Code.Presets.PresetClass') as obj";

    private static final String NORMAL_PRESETS = PRESET_OBJECTS + " where obj.isMultiple = 0";

    private static final String CHART_PRESETS = PRESET_OBJECTS + " where obj.isMultiple = 1";

    private static final String NORMAL_CLIENT_PRESETS =
        PRESET_OBJECTS + " where obj.client = :client and obj.isMultiple = 0";

    private static final String CHART_CLIENT_PRESETS =
        PRESET_OBJECTS + " where obj.client = :client and obj.isMultiple = 1";

    private static final String PRESET_BY_ID = PRESET_OBJECTS + " where obj.id = :presetid";

    private static final String MAX_ID = "select max(obj.id) from Document as doc, "
        + "doc.object('ProjectManagement.Code.Presets.PresetClass') as obj where obj.id is not null";

    private static final String CLIENT = "openproject";

    private static final Map<String, Object> CLIENT_PARAMETER = Map.of("client", CLIENT);

    private static final Map<String, Object> ID_PARAMETER = Map.of("presetid", 10);

    private static final DocumentReference DEFAULT_PRESET = new DocumentReference("xwiki", "Preset", "MyPreset");

    private static final DocumentReference OTHER_PRESET = new DocumentReference("xwiki", "Space", "OtherPreset");

    private static final DocumentReference ADMIN = new DocumentReference("xwiki", "XWiki", "Admin");

    private static final DocumentReference USER = new DocumentReference("xwiki", "XWiki", "User");

    private static final DocumentReference CONTENT_AUTHOR = new DocumentReference("xwiki", "XWiki", "Author");

    private static final String NOT_ADMIN_WARNING = "Ignoring the preset stored on [xwiki:Space.OtherPreset] because "
        + "its author [xwiki:XWiki.User] doesn't have administration right on the wiki.";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @InjectMockComponents
    private DefaultPresetsManager presetsManager;

    @MockComponent
    private QueryManager queryManager;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @BeforeEach
    void setUp() throws Exception
    {
        XWikiContext context = this.oldcore.getXWikiContext();
        context.setUserReference(USER);
        // The current content author is the author of the page holding the macro that uses the preset.
        XWikiDocument macroPage = new XWikiDocument(new DocumentReference("xwiki", "Space", "MacroPage"));
        macroPage.setContentAuthorReference(CONTENT_AUTHOR);
        context.setDoc(macroPage);

        when(this.oldcore.getMockAuthorizationManager().hasAccess(Right.ADMIN, ADMIN, new WikiReference("xwiki")))
            .thenReturn(true);
        savePreset(DEFAULT_PRESET, ADMIN, "default");
    }

    private void savePreset(DocumentReference reference, DocumentReference author, String name) throws Exception
    {
        XWikiContext context = this.oldcore.getXWikiContext();
        XWikiDocument document = new XWikiDocument(reference);
        UserReferenceResolver<DocumentReference> userResolver =
            this.componentManager.getInstance(UserReferenceResolver.TYPE_DOCUMENT_REFERENCE, "document");
        document.getAuthors().setEffectiveMetadataAuthor(userResolver.resolve(author));
        BaseObject preset = document.newXObject(Preset.REFERENCE, context);
        preset.setIntValue(Preset.FIELD_ID, 10);
        preset.setStringValue(Preset.FIELD_NAME, name);
        preset.setStringValue(Preset.FIELD_CLIENT, CLIENT);
        this.oldcore.getSpyXWiki().saveDocument(document, context);
        // By default, both the current user and the current content author can view the preset.
        setViewableByCurrentUser(reference, true);
        when(this.oldcore.getMockAuthorizationManager().hasAccess(Right.VIEW, CONTENT_AUTHOR, reference))
            .thenReturn(true);
    }

    private void setViewableByCurrentUser(DocumentReference reference, boolean viewable)
    {
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(Right.VIEW, reference))
            .thenReturn(viewable);
    }

    /**
     * Answers the given statement with the given rows (page names, as selected by the document filter), but only when
     * it is executed with the given parameters and pagination. Like the store's query executor, the result filters
     * added to the query are applied to the rows.
     */
    private void stubQuery(String statement, Map<String, Object> parameters, int offset, int limit,
        Object... rows) throws QueryException
    {
        when(this.queryManager.createQuery(statement, Query.XWQL))
            .thenReturn(new DefaultQuery(statement, Query.XWQL, new QueryExecutor()
            {
                @Override
                @SuppressWarnings("unchecked")
                public <T> List<T> execute(Query query)
                {
                    if (!parameters.equals(query.getNamedParameters()) || query.getOffset() != offset
                        || query.getLimit() != limit)
                    {
                        return Collections.emptyList();
                    }
                    List<Object> results = new ArrayList<>(List.of(rows));
                    for (QueryFilter filter : query.getFilters()) {
                        results = filter.filterResults(results);
                    }
                    return (List<T>) results;
                }
            }));
    }

    private void stubFailingQuery(String statement) throws QueryException
    {
        when(this.queryManager.createQuery(statement, Query.XWQL))
            .thenReturn(new DefaultQuery(statement, Query.XWQL, new QueryExecutor()
            {
                @Override
                public <T> List<T> execute(Query query) throws QueryException
                {
                    throw new QueryException("Store failure", query, null);
                }
            }));
    }

    private static List<String> names(List<Preset> presets)
    {
        return presets.stream().map(Preset::getName).collect(Collectors.toList());
    }

    @Test
    void getNormalPresets() throws Exception
    {
        stubQuery(NORMAL_PRESETS, Map.of(), 20, 10, "Preset.MyPreset");

        assertEquals(List.of("default"), names(this.presetsManager.getPresets(false, 20, 10)));
    }

    @Test
    void getChartPresets() throws Exception
    {
        stubQuery(CHART_PRESETS, Map.of(), 20, 10, "Preset.MyPreset");

        assertEquals(List.of("default"), names(this.presetsManager.getPresets(true, 20, 10)));
    }

    @Test
    void getPresetsWhenNoneExist() throws Exception
    {
        stubQuery(CHART_PRESETS, Map.of(), 0, 10);

        assertEquals(List.of(), this.presetsManager.getPresets(true, 0, 10));
    }

    @Test
    void getPresetsWhenTheQueryFails() throws Exception
    {
        stubFailingQuery(CHART_PRESETS);

        RuntimeException exception =
            assertThrows(RuntimeException.class, () -> this.presetsManager.getPresets(true, 0, 10));
        assertInstanceOf(QueryException.class, exception.getCause());
    }

    @Test
    void getNormalClientPresets() throws Exception
    {
        stubQuery(NORMAL_CLIENT_PRESETS, CLIENT_PARAMETER, 20, 10, "Preset.MyPreset");

        assertEquals(List.of("default"), names(this.presetsManager.getClientPresets(CLIENT, false, 20, 10)));
    }

    @Test
    void getChartClientPresets() throws Exception
    {
        stubQuery(CHART_CLIENT_PRESETS, CLIENT_PARAMETER, 20, 10, "Preset.MyPreset");

        assertEquals(List.of("default"), names(this.presetsManager.getClientPresets(CLIENT, true, 20, 10)));
    }

    @Test
    void getClientPresetsWhenNoneExist() throws Exception
    {
        stubQuery(CHART_CLIENT_PRESETS, CLIENT_PARAMETER, 0, 10);

        assertEquals(List.of(), this.presetsManager.getClientPresets(CLIENT, true, 0, 10));
    }

    @Test
    void getClientPresetsWhenTheQueryFails() throws Exception
    {
        stubFailingQuery(NORMAL_CLIENT_PRESETS);

        RuntimeException exception =
            assertThrows(RuntimeException.class, () -> this.presetsManager.getClientPresets(CLIENT, false, 0, 10));
        assertInstanceOf(QueryException.class, exception.getCause());
    }

    @Test
    void getPresetById() throws Exception
    {
        stubQuery(PRESET_BY_ID, ID_PARAMETER, 0, 0, "Preset.MyPreset");

        Preset preset = this.presetsManager.getPreset(10);

        assertEquals("default", preset.getName());
        assertEquals(10, preset.getId());
        assertEquals(CLIENT, preset.getClient());
    }

    @Test
    void getPresetReturnsNullWhenNotFound() throws Exception
    {
        stubQuery(PRESET_BY_ID, ID_PARAMETER, 0, 0);

        assertNull(this.presetsManager.getPreset(10));
    }

    @Test
    void getPresetReturnsNullWhenThePageHasNoPresetObject() throws Exception
    {
        this.oldcore.getSpyXWiki().saveDocument(new XWikiDocument(OTHER_PRESET), this.oldcore.getXWikiContext());
        stubQuery(PRESET_BY_ID, ID_PARAMETER, 0, 0, "Space.OtherPreset");

        assertNull(this.presetsManager.getPreset(10));
    }

    @Test
    void getPresetWhenTheQueryFails() throws Exception
    {
        stubFailingQuery(PRESET_BY_ID);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> this.presetsManager.getPreset(10));
        assertInstanceOf(QueryException.class, exception.getCause());
    }

    @Test
    void getNextIdIncrementsTheMaxExistingId() throws Exception
    {
        stubQuery(MAX_ID, Map.of(), 0, 0, 10);

        assertEquals(11, this.presetsManager.getNextId());
    }

    @Test
    void getNextIdWithNoExistingPreset() throws Exception
    {
        stubQuery(MAX_ID, Map.of(), 0, 0);

        assertEquals(1, this.presetsManager.getNextId());
    }

    @Test
    void getNextIdWhenTheQueryFails() throws Exception
    {
        stubFailingQuery(MAX_ID);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> this.presetsManager.getNextId());
        assertInstanceOf(QueryException.class, exception.getCause());
    }

    @Test
    void getPresetIgnoresPresetWhoseAuthorIsNotAdmin() throws Exception
    {
        savePreset(OTHER_PRESET, USER, "other");
        stubQuery(PRESET_BY_ID, ID_PARAMETER, 0, 0, "Space.OtherPreset", "Preset.MyPreset");

        assertEquals("default", this.presetsManager.getPreset(10).getName());
        assertEquals(NOT_ADMIN_WARNING, this.logCapture.getMessage(0));
    }

    @Test
    void getPresetReturnsNullWhenOnlyPresetAuthorIsNotAdmin() throws Exception
    {
        savePreset(OTHER_PRESET, USER, "other");
        stubQuery(PRESET_BY_ID, ID_PARAMETER, 0, 0, "Space.OtherPreset");

        assertNull(this.presetsManager.getPreset(10));
        assertEquals(NOT_ADMIN_WARNING, this.logCapture.getMessage(0));
    }

    @Test
    void getPresetsIgnoresPresetsWhoseAuthorIsNotAdmin() throws Exception
    {
        savePreset(OTHER_PRESET, USER, "other");
        stubQuery(NORMAL_PRESETS, Map.of(), 0, 10, "Space.OtherPreset", "Preset.MyPreset");

        assertEquals(List.of("default"), names(this.presetsManager.getPresets(false, 0, 10)));
        assertEquals(NOT_ADMIN_WARNING, this.logCapture.getMessage(0));
    }

    @Test
    void getClientPresetsIgnoresPresetsWhoseAuthorIsNotAdmin() throws Exception
    {
        savePreset(OTHER_PRESET, USER, "other");
        stubQuery(NORMAL_CLIENT_PRESETS, CLIENT_PARAMETER, 0, 10, "Space.OtherPreset", "Preset.MyPreset");

        assertEquals(List.of("default"), names(this.presetsManager.getClientPresets(CLIENT, false, 0, 10)));
        assertEquals(NOT_ADMIN_WARNING, this.logCapture.getMessage(0));
    }

    @Test
    void getPresetsOnlyReturnsPresetsTheCurrentUserCanView() throws Exception
    {
        savePreset(OTHER_PRESET, ADMIN, "other");
        setViewableByCurrentUser(OTHER_PRESET, false);
        stubQuery(NORMAL_PRESETS, Map.of(), 0, 10, "Space.OtherPreset", "Preset.MyPreset");

        assertEquals(List.of("default"), names(this.presetsManager.getPresets(false, 0, 10)));
    }

    @Test
    void getClientPresetsOnlyReturnsPresetsTheCurrentUserCanView() throws Exception
    {
        savePreset(OTHER_PRESET, ADMIN, "other");
        setViewableByCurrentUser(DEFAULT_PRESET, false);
        stubQuery(NORMAL_CLIENT_PRESETS, CLIENT_PARAMETER, 0, 10, "Space.OtherPreset", "Preset.MyPreset");

        assertEquals(List.of("other"), names(this.presetsManager.getClientPresets(CLIENT, false, 0, 10)));
    }

    @Test
    void getPresetReturnsNullWhenTheContentAuthorCannotViewIt() throws Exception
    {
        when(this.oldcore.getMockAuthorizationManager().hasAccess(Right.VIEW, CONTENT_AUTHOR, DEFAULT_PRESET))
            .thenReturn(false);
        stubQuery(PRESET_BY_ID, ID_PARAMETER, 0, 0, "Preset.MyPreset");

        assertNull(this.presetsManager.getPreset(10));
    }

    @Test
    void getPresetDoesNotDependOnTheCurrentUserRights() throws Exception
    {
        setViewableByCurrentUser(DEFAULT_PRESET, false);
        stubQuery(PRESET_BY_ID, ID_PARAMETER, 0, 0, "Preset.MyPreset");

        assertEquals("default", this.presetsManager.getPreset(10).getName());
    }

    @Test
    void getPresetDoesNotFallBackOnAnotherPresetWhenTheContentAuthorCannotViewTheFirstOne() throws Exception
    {
        savePreset(OTHER_PRESET, ADMIN, "other");
        when(this.oldcore.getMockAuthorizationManager().hasAccess(Right.VIEW, CONTENT_AUTHOR, OTHER_PRESET))
            .thenReturn(false);
        stubQuery(PRESET_BY_ID, ID_PARAMETER, 0, 0, "Space.OtherPreset", "Preset.MyPreset");

        assertNull(this.presetsManager.getPreset(10));
    }
}
