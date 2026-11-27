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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.parser.ContentParser;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xwiki.commons.document.MacroBlockFinder;
import com.xwiki.projectmanagement.internal.source.ListWorkItemSource;
import com.xwiki.projectmanagement.openproject.internal.DocumentModificationLock;

import static com.xwiki.projectmanagement.openproject.internal.mentions.OpenProjectMentionClassInitializer.PROP_INSTANCE;
import static com.xwiki.projectmanagement.openproject.internal.mentions.OpenProjectMentionClassInitializer.PROP_WORK_PACKAGE_ID;
import static com.xwiki.projectmanagement.openproject.internal.mentions.OpenProjectMentionClassInitializer.REFERENCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectMentionTask}, on a page stored in the wiki.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
@ComponentList({ DocumentModificationLock.class, WorkPackageMentionFinder.class, ListWorkItemSource.class })
class OpenProjectMentionTaskTest
{
    private static final DocumentReference PAGE = new DocumentReference("xwiki", "Space", "Page");

    private static final String INSTANCE = "test";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @InjectMockComponents
    private OpenProjectMentionTask task;

    @MockComponent
    private MacroBlockFinder macroBlockFinder;

    /**
     * Parses the page content: the macros of the content are given by the macro block finder.
     */
    @MockComponent
    private ContentParser contentParser;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private Object executingFlagWhenSaving;

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.contentParser.parse(any(), any(), any())).thenReturn(new XDOM(List.of()));
        // The database store returns a new instance of the document, not the one held by the document cache.
        doAnswer(invocation -> {
            XWikiDocument document = invocation.getArgument(0);
            XWikiDocument savedDocument =
                this.oldcore.getDocuments().get(document.getDocumentReferenceWithLocale());
            return savedDocument != null ? savedDocument.clone() : document;
        }).when(this.oldcore.getMockStore()).loadXWikiDoc(any(XWikiDocument.class), any(XWikiContext.class));
        // Record whether the page is saved while the task is executing, so that listeners can ignore this save.
        doAnswer(invocation -> {
            this.executingFlagWhenSaving =
                invocation.<XWikiContext>getArgument(1).get(OpenProjectMentionTask.TASK_EXECUTING_KEY);
            return invocation.callRealMethod();
        }).when(this.oldcore.getSpyXWiki()).saveDocument(any(XWikiDocument.class), any(XWikiContext.class));
    }

    /**
     * Saves the page with the given mention objects, before the task is executed.
     */
    private void savePage(String... workPackageIdsAndInstances) throws Exception
    {
        XWikiContext context = this.oldcore.getXWikiContext();
        XWikiDocument page = new XWikiDocument(PAGE);
        for (int i = 0; i < workPackageIdsAndInstances.length; i += 2) {
            BaseObject mention = page.newXObject(REFERENCE, context);
            mention.setStringValue(PROP_WORK_PACKAGE_ID, workPackageIdsAndInstances[i]);
            mention.setStringValue(PROP_INSTANCE, workPackageIdsAndInstances[i + 1]);
        }
        this.oldcore.getSpyXWiki().saveDocument(page, context);
        this.executingFlagWhenSaving = null;
        clearInvocations(this.oldcore.getSpyXWiki());
    }

    private XWikiDocument getPage() throws XWikiException
    {
        return this.oldcore.getSpyXWiki().getDocument(PAGE, this.oldcore.getXWikiContext());
    }

    /**
     * @return the stored mentions, as {@code workPackageId@instance}
     */
    private List<String> getStoredMentions() throws XWikiException
    {
        // The list of xobjects holds a null entry in place of each removed object.
        return getPage().getXObjects(REFERENCE).stream().filter(Objects::nonNull)
            .map(mention -> mention.getStringValue(PROP_WORK_PACKAGE_ID) + '@' + mention.getStringValue(PROP_INSTANCE))
            .collect(Collectors.toList());
    }

    private void assertNotSavedAgain() throws Exception
    {
        verify(this.oldcore.getSpyXWiki(), never()).saveDocument(any(XWikiDocument.class), any(XWikiContext.class));
        assertNull(this.oldcore.getXWikiContext().get(OpenProjectMentionTask.TASK_EXECUTING_KEY));
    }

    /**
     * Asserts that the mentions were saved while the task was executing, without creating a new version of the page.
     */
    private void assertSavedByTheTask(String versionBefore) throws Exception
    {
        assertEquals(true, this.executingFlagWhenSaving);
        assertNull(this.oldcore.getXWikiContext().get(OpenProjectMentionTask.TASK_EXECUTING_KEY));
        assertEquals(versionBefore, getPage().getVersion());
    }

    @Test
    void noMacrosInContentAndNoObjects() throws Exception
    {
        savePage();
        withMacros();

        this.task.consume(PAGE, "1.1");

        assertEquals(List.of(), getStoredMentions());
        assertNotSavedAgain();
    }

    @Test
    void macrosWithIdsInContentAndNoObjects() throws Exception
    {
        savePage();
        String version = getPage().getVersion();
        withMacros(macro(" 12 ", INSTANCE), macro("http://op.example.org/projects/demo/work_packages/34/activity",
            "other"), macro("https://op.example.org/wp/56", INSTANCE));

        this.task.consume(PAGE, version);

        // The id of a work package URL is extracted.
        assertEquals(List.of("12@test", "34@other", "56@test"), getStoredMentions());
        assertSavedByTheTask(version);
    }

    @Test
    void macrosNotDisplayingASingleWorkPackageAreIgnored() throws Exception
    {
        savePage();
        MacroBlock withFilter = macro("12", INSTANCE);
        withFilter.setParameter("filters", "{}");
        withMacros(macro("12", null), withFilter, macro("1,2,3", INSTANCE), macro(null, INSTANCE),
            macro("http://op.example.org/projects/demo/work_packages", INSTANCE), macro("+12", INSTANCE),
            macro("http://op.example.org/work_packages/12,http://op.example.org/work_packages/34", INSTANCE),
            new MacroBlock("openprojectchart", Map.of("identifier", "12", PROP_INSTANCE, INSTANCE), false),
            macro("56", INSTANCE));

        this.task.consume(PAGE, "1.1");

        assertEquals(List.of("56@test"), getStoredMentions());
    }

    @Test
    void macrosSelectingASingleWorkPackageInTheirContent() throws Exception
    {
        savePage();
        String version = getPage().getVersion();
        withMacros(sourceMacro("list", "12", INSTANCE), sourceMacro(" LIST ", "\n 034, \n", "other"),
            sourceMacro("url", " http://op.example.org/projects/demo/work_packages/56/activity ", INSTANCE),
            sourceMacro("url", "https://op.example.org/wp/78", INSTANCE));

        this.task.consume(PAGE, version);

        // The ids are stored without their leading zeros, as OpenProject searches them.
        assertEquals(List.of("12@test", "34@other", "56@test", "78@test"), getStoredMentions());
        assertSavedByTheTask(version);
    }

    @Test
    void macrosNotSelectingASingleWorkPackageInTheirContentAreIgnored() throws Exception
    {
        savePage("12", INSTANCE);
        MacroBlock withFilters = sourceMacro("list", "12", INSTANCE);
        withFilters.setParameter("filters", "{}");
        MacroBlock withIdentifier = sourceMacro("list", "12", INSTANCE);
        withIdentifier.setParameter("identifier", "12");
        MacroBlock withPreset = sourceMacro("list", "12", INSTANCE);
        withPreset.setParameter("presetId", "3");
        withMacros(
            // A query, the default source.
            sourceMacro(null, "12", INSTANCE), sourceMacro("query", "id = 12", INSTANCE),
            sourceMacro("filters", "[]", INSTANCE),
            sourceMacro("list", "12, 34", INSTANCE), sourceMacro("list", " , ", INSTANCE),
            // Resolving a key would need a call to OpenProject.
            sourceMacro("list", "DA-71", INSTANCE),
            sourceMacro("list", "http://op.example.org/projects/demo/work_packages/12", INSTANCE),
            sourceMacro("url", "http://op.example.org/projects/demo/work_packages", INSTANCE),
            sourceMacro("url", "12", INSTANCE), sourceMacro("unknown", "12", INSTANCE),
            sourceMacro("url", "http://op.example.org/projects/demo/work_packages?query_id=5", INSTANCE),
            // The url source rejects a link on two lines, the macro fails.
            sourceMacro("url", "http://op.example.org/work_packages/12\nhttp://op.example.org/work_packages/34",
                INSTANCE),
            sourceMacro("url", "http://op.example.org/work_packages/12abc", INSTANCE),
            sourceMacro("url", "http://op.example.org/work_packages/12/activity/details", INSTANCE),
            sourceMacro("url", "ftp://op.example.org/work_packages/12", INSTANCE),
            sourceMacro("list", "+12", INSTANCE), sourceMacro("list", "-12", INSTANCE),
            // Non-ASCII digits.
            sourceMacro("list", "\u0661\u0662", INSTANCE),
            sourceMacro("list", "12", null), withFilters, withIdentifier, withPreset);

        this.task.consume(PAGE, "1.1");

        assertEquals(List.of(), getStoredMentions());
    }

    @Test
    void macroWithEmptyContentUsesTheIdentifier() throws Exception
    {
        savePage("12", INSTANCE);
        MacroBlock macro = sourceMacro("list", " ", INSTANCE);
        macro.setParameter("identifier", "12");
        withMacros(macro);

        this.task.consume(PAGE, "1.1");

        assertEquals(List.of("12@test"), getStoredMentions());
        assertNotSavedAgain();
    }

    @Test
    void macroSelectionMovedFromTheIdentifierToTheContent() throws Exception
    {
        savePage("12", INSTANCE);
        withMacros(sourceMacro("list", "12", INSTANCE));

        this.task.consume(PAGE, "1.1");

        // The mention is the same.
        assertEquals(List.of("12@test"), getStoredMentions());
        assertNotSavedAgain();
    }

    @Test
    void macrosInContentAndAllAssociatedObjects() throws Exception
    {
        savePage("12", INSTANCE, "34", INSTANCE);
        withMacros(macro("12", INSTANCE), macro("34", INSTANCE));

        this.task.consume(PAGE, "1.1");

        assertEquals(List.of("12@test", "34@test"), getStoredMentions());
        assertNotSavedAgain();
    }

    @Test
    void macrosRemovedFromContent() throws Exception
    {
        savePage("12", INSTANCE, "34", INSTANCE);
        String version = getPage().getVersion();
        withMacros(macro("12", INSTANCE));

        this.task.consume(PAGE, version);

        assertEquals(List.of("12@test"), getStoredMentions());
        assertSavedByTheTask(version);
    }

    @Test
    void macroIdUpdated() throws Exception
    {
        savePage("12", INSTANCE, "34", INSTANCE);
        String version = getPage().getVersion();
        withMacros(macro("12", INSTANCE), macro("35", INSTANCE));

        this.task.consume(PAGE, version);

        assertEquals(List.of("12@test", "35@test"), getStoredMentions());
        assertSavedByTheTask(version);
    }

    @Test
    void macroInstanceUpdated() throws Exception
    {
        savePage("12", INSTANCE);
        withMacros(macro("12", "other"));

        this.task.consume(PAGE, "1.1");

        assertEquals(List.of("12@other"), getStoredMentions());
    }

    @Test
    void mentionAddedAfterARemovedMention() throws Exception
    {
        savePageWithARemovedMention();
        withMacros(macro("12", INSTANCE), macro("56", INSTANCE));

        this.task.consume(PAGE, "1.1");

        assertEquals(List.of("12@test", "56@test"), getStoredMentions());
        assertEquals(0, this.logCapture.size());
    }

    @Test
    void sameMentionsAsTheRemainingOnesAfterARemovedMention() throws Exception
    {
        savePageWithARemovedMention();
        withMacros(macro("12", INSTANCE));

        this.task.consume(PAGE, "1.1");

        assertEquals(List.of("12@test"), getStoredMentions());
        assertNotSavedAgain();
    }

    /**
     * Saves the page with a mention, after removing another one: the list of xobjects of the page holds a null entry
     * in place of the removed object.
     */
    private void savePageWithARemovedMention() throws Exception
    {
        savePage("34", INSTANCE, "12", INSTANCE);
        XWikiDocument page = getPage().clone();
        page.removeXObject(page.getXObject(REFERENCE, 0));
        this.oldcore.getSpyXWiki().saveDocument(page, this.oldcore.getXWikiContext());
        clearInvocations(this.oldcore.getSpyXWiki());
    }

    @Test
    void saveFails() throws Exception
    {
        savePage("34", INSTANCE);
        withMacros(macro("12", INSTANCE));
        doThrow(new XWikiException()).when(this.oldcore.getSpyXWiki()).saveDocument(any(XWikiDocument.class),
            any(XWikiContext.class));

        this.task.consume(PAGE, "1.1");

        assertEquals("Failed to create the OpenProject mention objects for the document "
            + "[xwiki:Space.Page]. Cause: [XWikiException: Error number 0 in 0]", this.logCapture.getMessage(0));
        // The cached page isn't modified by the failed save.
        assertEquals(List.of("34@test"), getStoredMentions());
        assertNull(this.oldcore.getXWikiContext().get(OpenProjectMentionTask.TASK_EXECUTING_KEY));
    }

    @Test
    void waitsForTheConcurrentModificationOfThePage() throws Exception
    {
        savePage();
        withMacros(macro("12", INSTANCE));
        DocumentModificationLock lock = this.oldcore.getMocker().getInstance(DocumentModificationLock.class);
        ExecutorService otherThread = Executors.newSingleThreadExecutor();
        try {
            // A macro is being inserted in the page.
            lock.lock(PAGE);
            Future<?> taskExecution = otherThread.submit(() -> {
                this.task.consume(PAGE, "1.1");
                return null;
            });
            assertThrows(TimeoutException.class, () -> taskExecution.get(200, TimeUnit.MILLISECONDS));
            XWikiDocument page = getPage().clone();
            page.setContent("inserted macro");
            this.oldcore.getSpyXWiki().saveDocument(page, this.oldcore.getXWikiContext());
            lock.unlock(PAGE);

            taskExecution.get(10, TimeUnit.SECONDS);
        } finally {
            otherThread.shutdownNow();
        }

        // The mentions are saved without reverting the inserted macro.
        assertEquals("inserted macro", getPage().getContent());
        assertEquals(List.of("12@test"), getStoredMentions());
    }

    /**
     * The macros of the page content, as found by the macro block finder (which looks into the content and the
     * nested macro content).
     */
    @SuppressWarnings("unchecked")
    private void withMacros(MacroBlock... macros) throws Exception
    {
        doAnswer(invocation -> {
            Function<MacroBlock, MacroBlockFinder.Lookup> function = invocation.getArgument(2);
            Arrays.stream(macros).forEach(function::apply);
            return null;
        }).when(this.macroBlockFinder).find(any(), any(), any(Function.class));
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

    private static MacroBlock sourceMacro(String source, String content, String instance)
    {
        Map<String, String> parameters = new HashMap<>();
        if (source != null) {
            parameters.put("source", source);
        }
        if (instance != null) {
            parameters.put(PROP_INSTANCE, instance);
        }
        return new MacroBlock("openproject", parameters, content, false);
    }
}
