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
package com.xwiki.projectmanagement.openproject.internal.macro;

import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.OpenProjectProjectDetailsProperty;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectDetailsMacroParameters;
import com.xwiki.projectmanagement.openproject.model.Project;

import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.date;
import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.links;
import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectProjectDetailsMacro}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectProjectDetailsMacroTest extends AbstractDirectMacroTest
{
    private static final String LABELS_PREFIX = "openproject.openproject-project-details.labels.";

    private static final String DESCRIPTION_HTML = "<p>Versions &gt;= 1.6</p>";

    // What the OpenProject displayer produces for the description.
    private static final Block DESCRIPTION_PARAGRAPH = new ParagraphBlock(List.of(new WordBlock("Versions >= 1.6")));

    @InjectMockComponents
    private OpenProjectProjectDetailsMacro macro;

    @MockComponent
    private ContextualLocalizationManager l10n;

    @MockComponent
    @Named("openproject")
    private WorkItemPropertyDisplayerManager displayerManager;

    private final OpenProjectProjectDetailsMacroParameters parameters = new OpenProjectProjectDetailsMacroParameters();

    @BeforeEach
    void setUp()
    {
        this.parameters.setInstance("main");
        this.parameters.setProject("7");
        when(this.l10n.getTranslationPlain(LABELS_PREFIX + "id")).thenReturn("ID");
        when(this.l10n.getTranslationPlain(LABELS_PREFIX + "name")).thenReturn("Name");
        when(this.l10n.getTranslationPlain(LABELS_PREFIX + "identifier")).thenReturn("Identifier");
        when(this.l10n.getTranslationPlain(LABELS_PREFIX + "description")).thenReturn("Description");
        when(this.l10n.getTranslationPlain(LABELS_PREFIX + "status")).thenReturn("Status");
        when(this.l10n.getTranslationPlain(LABELS_PREFIX + "created")).thenReturn("Created");
    }

    @Test
    void displaysAllTheDetails() throws Exception
    {
        mockAlpha();
        this.parameters.setProperties(List.of(OpenProjectProjectDetailsProperty.values()));

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        String[] lines = text(result).split("\n");
        assertEquals(7, lines.length);
        assertEquals("ID: 7", lines[0]);
        assertEquals("Name: Alpha", lines[1]);
        assertEquals("Identifier: alpha", lines[2]);
        assertEquals("Description: ", lines[3]);
        assertEquals("Versions >= 1.6", lines[4]);
        assertEquals("Status: On track", lines[5]);
        // The creation date uses the date format configured in the wiki.
        assertEquals("Created: 2026-01-02", lines[6]);
        assertEquals(List.of("Alpha -> " + SERVER_URL + "/projects/alpha"), links(result));
    }

    @Test
    void displaysTheNameTheDescriptionAndTheStatusByDefault() throws Exception
    {
        mockAlpha();

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals("Name: Alpha\nDescription: \nVersions >= 1.6\nStatus: On track\n", text(result));
    }

    @Test
    void displaysTheSelectedPropertiesInTheirOrder() throws Exception
    {
        mockAlpha();
        this.parameters.setProperties(
            List.of(OpenProjectProjectDetailsProperty.CREATED, OpenProjectProjectDetailsProperty.IDENTIFIER));

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals("Identifier: alpha\nCreated: 2026-01-02\n", text(result));
    }

    @Test
    void displaysTheDescriptionFromItsHTML() throws Exception
    {
        mockAlpha();
        this.parameters.setProperties(List.of(OpenProjectProjectDetailsProperty.DESCRIPTION));

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        // The description is displayed from the cleaned HTML, as the work package descriptions, not from its raw
        // Markdown source, which holds HTML entities.
        assertEquals(1, result.size());
        List<Block> details = result.get(0).getChildren();
        assertEquals(2, details.size());
        assertEquals(GroupBlock.class, details.get(1).getClass());
        assertEquals(List.of(DESCRIPTION_PARAGRAPH), details.get(1).getChildren());
    }

    @Test
    void skipsTheMissingDetails() throws Exception
    {
        Project project = new Project();
        project.setId(7);
        project.setSelf(new Linkable(" ", SERVER_URL + "/projects/alpha"));
        project.setDescription("");
        project.setDescriptionHtml("");
        when(this.apiClient.getProject(7)).thenReturn(project);
        this.parameters.setProperties(List.of(OpenProjectProjectDetailsProperty.values()));

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals("ID: 7\n", text(result));
        verifyNoInteractions(this.displayerManager);
    }

    @Test
    void skipsTheDescriptionDisplayedAsNothing() throws Exception
    {
        mockAlpha();
        when(this.displayerManager.displayProperty(WorkItem.KEY_DESCRIPTION, DESCRIPTION_HTML, Map.of()))
            .thenReturn(List.of());
        this.parameters.setProperties(
            List.of(OpenProjectProjectDetailsProperty.NAME, OpenProjectProjectDetailsProperty.DESCRIPTION));

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals("Name: Alpha\n", text(result));
    }

    private void mockAlpha() throws Exception
    {
        Project project = new Project();
        project.setId(7);
        project.setName("Alpha");
        project.setSelf(new Linkable("Alpha", SERVER_URL + "/projects/alpha"));
        project.setIdentifier("alpha");
        project.setDescription("Versions &gt;= 1.6");
        project.setDescriptionHtml(DESCRIPTION_HTML);
        project.setStatus(new Linkable("On track", SERVER_URL + "/statuses/on_track"));
        project.setCreatedAt(date(2026, 1, 2));
        when(this.displayerManager.displayProperty(WorkItem.KEY_DESCRIPTION, DESCRIPTION_HTML, Map.of()))
            .thenReturn(List.of(DESCRIPTION_PARAGRAPH));
        when(this.apiClient.getProject(7)).thenReturn(project);
    }
}
