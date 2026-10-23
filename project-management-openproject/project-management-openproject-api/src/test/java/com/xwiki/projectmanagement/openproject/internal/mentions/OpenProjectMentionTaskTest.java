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
package com.xwiki.projectmanagement.openproject.internal.mentions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.commons.document.MacroBlockFinder;

import static com.xwiki.projectmanagement.openproject.internal.mentions.OpenProjectMentionClassInitializer.PROP_INSTANCE;
import static com.xwiki.projectmanagement.openproject.internal.mentions.OpenProjectMentionClassInitializer.PROP_WORK_PACKAGE_ID;
import static com.xwiki.projectmanagement.openproject.internal.mentions.OpenProjectMentionClassInitializer.REFERENCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectMentionTask}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectMentionTaskTest
{
    private static final DocumentReference DOCUMENT_REFERENCE = new DocumentReference("wiki", "Space", "Page");

    private static final String INSTANCE = "test";

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private OpenProjectMentionTask task;

    @MockComponent
    private MacroBlockFinder macroBlockFinder;

    @MockComponent
    private Provider<XWikiContext> xWikiContextProvider;

    @Mock
    private XWikiContext context;

    @Mock
    private XWiki xwiki;

    @Mock
    private XWikiDocument document;

    private final Map<Object, Object> contextContent = new HashMap<>();

    private final List<BaseObject> createdObjects = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.xWikiContextProvider.get()).thenReturn(this.context);
        when(this.context.getWiki()).thenReturn(this.xwiki);
        doAnswer(invocation -> this.contextContent.put(invocation.getArgument(0), invocation.getArgument(1)))
            .when(this.context).put(any(), any());
        doAnswer(invocation -> this.contextContent.remove(invocation.getArgument(0))).when(this.context)
            .remove(any());
        when(this.document.newXObject(REFERENCE, this.context)).thenAnswer(invocation -> {
            BaseObject object = new BaseObject();
            this.createdObjects.add(object);
            return object;
        });
        when(this.document.getXObjects(REFERENCE)).thenReturn(Collections.emptyList());
        // The document is saved while the task is executing, so that listeners can ignore this save.
        when(this.xwiki.getDocument(DOCUMENT_REFERENCE, this.context)).thenAnswer(invocation -> {
            assertEquals(true, this.contextContent.get(OpenProjectMentionTask.TASK_EXECUTING_KEY));
            return this.document;
        });
    }

    @Test
    void noMacrosInContentAndNoObjects() throws Exception
    {
        withMacros();

        this.task.consume(DOCUMENT_REFERENCE, "1.1");

        verify(this.document, never()).removeXObjects(REFERENCE);
        verify(this.xwiki, never()).saveDocument(any(XWikiDocument.class), any(XWikiContext.class));
        assertFalse(this.contextContent.containsKey(OpenProjectMentionTask.TASK_EXECUTING_KEY));
    }

    @Test
    void macrosWithIdsInContentAndNoObjects() throws Exception
    {
        withMacros(macro("12", INSTANCE), macro("http://op.example.org/projects/demo/work_packages/34/activity",
            "other"));

        this.task.consume(DOCUMENT_REFERENCE, "1.1");

        assertEquals(2, this.createdObjects.size());
        assertMention(this.createdObjects.get(0), "12", INSTANCE);
        // The id of a work package URL is extracted.
        assertMention(this.createdObjects.get(1), "34", "other");
        // The mentions are saved without creating a new version of the document.
        verify(this.document).setMetaDataDirty(false);
        verify(this.document).setContentDirty(false);
        verify(this.xwiki).saveDocument(this.document, this.context);
        assertFalse(this.contextContent.containsKey(OpenProjectMentionTask.TASK_EXECUTING_KEY));
    }

    @Test
    void macrosNotDisplayingASingleWorkPackageAreIgnored() throws Exception
    {
        MacroBlock withFilter = macro("12", INSTANCE);
        withFilter.setParameter("filter", "{}");
        withMacros(macro("12", null), withFilter, macro("1,2,3", INSTANCE), macro(null, INSTANCE),
            macro("http://op.example.org/projects/demo/work_packages", INSTANCE),
            new MacroBlock("openprojectchart", Map.of("identifier", "12", PROP_INSTANCE, INSTANCE), false),
            macro("56", INSTANCE));

        this.task.consume(DOCUMENT_REFERENCE, "1.1");

        assertEquals(1, this.createdObjects.size());
        assertMention(this.createdObjects.get(0), "56", INSTANCE);
    }

    @Test
    void macrosInContentAndAllAssociatedObjects() throws Exception
    {
        withMacros(macro("12", INSTANCE), macro("34", INSTANCE));
        withExistingMentions(mention("12", INSTANCE), mention("34", INSTANCE));

        this.task.consume(DOCUMENT_REFERENCE, "1.1");

        verify(this.document, never()).removeXObjects(REFERENCE);
        verify(this.xwiki, never()).saveDocument(any(XWikiDocument.class), any(XWikiContext.class));
    }

    @Test
    void macrosRemovedFromContent() throws Exception
    {
        withMacros(macro("12", INSTANCE));
        withExistingMentions(mention("12", INSTANCE), mention("34", INSTANCE));

        this.task.consume(DOCUMENT_REFERENCE, "1.1");

        verify(this.document).removeXObjects(REFERENCE);
        assertEquals(1, this.createdObjects.size());
        assertMention(this.createdObjects.get(0), "12", INSTANCE);
        verify(this.xwiki).saveDocument(this.document, this.context);
    }

    @Test
    void macroIdUpdated() throws Exception
    {
        withMacros(macro("12", INSTANCE), macro("35", INSTANCE));
        withExistingMentions(mention("12", INSTANCE), mention("34", INSTANCE));

        this.task.consume(DOCUMENT_REFERENCE, "1.1");

        verify(this.document).removeXObjects(REFERENCE);
        assertEquals(2, this.createdObjects.size());
        assertMention(this.createdObjects.get(0), "12", INSTANCE);
        assertMention(this.createdObjects.get(1), "35", INSTANCE);
        verify(this.xwiki).saveDocument(this.document, this.context);
    }

    @Test
    void saveFails() throws Exception
    {
        withMacros(macro("12", INSTANCE));
        doAnswer(invocation -> {
            throw new XWikiException();
        }).when(this.xwiki).saveDocument(this.document, this.context);

        this.task.consume(DOCUMENT_REFERENCE, "1.1");

        assertEquals("Failed to create the OpenProject mention objects for the document "
            + "[wiki:Space.Page]. Cause: [XWikiException: Error number 0 in 0]", this.logCapture.getMessage(0));
        assertFalse(this.contextContent.containsKey(OpenProjectMentionTask.TASK_EXECUTING_KEY));
    }

    @SuppressWarnings("unchecked")
    private void withMacros(MacroBlock... macros) throws Exception
    {
        doAnswer(invocation -> {
            Function<MacroBlock, MacroBlockFinder.Lookup> function = invocation.getArgument(2);
            Arrays.stream(macros).forEach(function::apply);
            return null;
        }).when(this.macroBlockFinder).find(any(), any(), any(Function.class));
    }

    private void withExistingMentions(BaseObject... mentions)
    {
        when(this.document.getXObjects(REFERENCE)).thenReturn(Arrays.asList(mentions));
    }

    private static MacroBlock macro(String identifier, String instance)
    {
        Map<String, String> parameters = new HashMap<>();
        if (identifier != null) {
            parameters.put("identifier", identifier);
        }
        if (instance != null) {
            parameters.put(PROP_INSTANCE, instance);
        }
        return new MacroBlock("openproject", parameters, false);
    }

    private static BaseObject mention(String workPackageId, String instance)
    {
        BaseObject object = new BaseObject();
        object.setStringValue(PROP_WORK_PACKAGE_ID, workPackageId);
        object.setStringValue(PROP_INSTANCE, instance);
        return object;
    }

    private static void assertMention(BaseObject object, String workPackageId, String instance)
    {
        assertEquals(workPackageId, object.getStringValue(PROP_WORK_PACKAGE_ID));
        assertEquals(instance, object.getStringValue(PROP_INSTANCE));
    }
}
