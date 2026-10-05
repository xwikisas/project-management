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
package com.xwiki.projectmanagement.test.openproject;

import java.util.List;

import javax.naming.OperationNotSupportedException;

import org.junit.jupiter.api.Test;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.editor.WYSIWYGEditPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the filter presets that can be used by the OpenProject macros.
 *
 * @version $Id$
 * @since 1.3.0-rc-2
 */
@UITest(
    properties = {
        AbstractOpenProjectIT.PRCHECKER_EXCLUDE_PROPERTY,
        AbstractOpenProjectIT.PLUGINS_PROPERTY
    },
    extraJARs = {
        AbstractOpenProjectIT.JYTHON_JAR,
        AbstractOpenProjectIT.SCHEDULER_API_JAR
    }
)
public class OpenProjectPresetIT extends AbstractOpenProjectIT
{
    private static final String PRESET_CLASS = "ProjectManagement.Code.Presets.PresetClass";

    // Filters that are displayed using a suggest input are not expected to work in the preset editor.
    private static final String SUMMARY = "summary";

    private static final String SUBJECT_COLUMN = "Subject";

    @Test
    void listPresetLifecycle(TestUtils setup, TestReference testReference) throws OperationNotSupportedException
    {
        String presetName = "Speakers list preset";

        // Create the preset from the administration section. The name is mandatory.
        PresetCreateModal createModal = OpenProjectAdminPage.gotoPage().clickCreatePreset();
        createModal.clickCreate();
        assertTrue(createModal.isDisplayed());
        PresetEditPage editPage = createModal.setName(presetName).setChartPreset(false).create();
        assertEquals(presetName, editPage.getName());
        String presetId = editPage.getPresetId();
        editPage.getFilterBuilders().get(0).addFilter(SUMMARY).typeValue(1, "speakers");
        editPage.waitUntilFilterValueContains("\"speakers\"");
        PresetViewPage viewPage = editPage.clickSaveAndViewPreset();

        // The saved filters are displayed, but they can't be changed.
        assertFalse(viewPage.isChartPreset());
        viewPage.waitUntilFiltersAreDisplayed(1);
        List<FilterBuilderParameter> builders = viewPage.getFilterBuilders();
        assertEquals(1, builders.size());
        assertTrue(builders.get(0).isReadOnly());
        assertEquals(1, builders.get(0).getFilters().size());
        DocumentReference presetReference =
            new DocumentReference(setup.resolveDocumentReference(viewPage.getPresetReference()));

        // The preset is listed in the administration section.
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        int presetIndex = adminPage.getPresetIndex(presetName);
        assertTrue(presetIndex > 0);
        assertEquals("No",
            adminPage.getPresetsLivedata().getTableLayout().getCell("Chart preset?", presetIndex).getText());

        // Use the preset in a macro that also has its own filter: the preset takes precedence.
        setup.deletePage(testReference);
        OpenProjectMacroEditModal macroModal = new OpenProjectMacroEditModal(setup, testReference);
        selectInstanceFromModal(macroModal, CONNECTION_ID);
        WYSIWYGEditPage wysiwygEditPage = macroModal.getEditPage();
        macroModal.clickMore();
        List<String> presetOptions = macroModal.getPresetOptions();
        assertTrue(presetOptions.contains(presetName), presetOptions.toString());
        macroModal.selectPreset(presetName);
        macroModal.getFilterBuilder().addFilter("status").setSuggestValue(1, "New");
        macroModal.clickSubmit();
        TableLayoutElement ld = saveAndGetFirstOPMacro(wysiwygEditPage);
        assertSubjectsContain(ld, "speakers");
        assertEquals("Entries 1 - 1 out of 1",
            getPaginationEntriesText(new ViewPageWithOpenProjectMacro().getOpenProjectMacros().get(0)));

        // The preset is still selected when editing the macro.
        macroModal = new OpenProjectMacroEditModal(setup, testReference);
        macroModal.clickMore();
        assertEquals(presetName, macroModal.getSelectedPreset());
        macroModal.clickSubmit();
        macroModal.getEditPage().clickSaveAndView();

        // The macro uses the updated preset filters.
        setup.gotoPage(presetReference, "edit");
        editPage = new PresetEditPage();
        editPage.getFilterBuilders().get(0).getFilters().get(0).typeValue(1, "sp");
        editPage.waitUntilFilterValueContains("\"sp\"");
        editPage.clickSaveAndViewPreset();
        setup.gotoPage(testReference);
        ViewPageWithOpenProjectMacro macroPage = new ViewPageWithOpenProjectMacro();
        OpenProjectMacroElement macro = macroPage.getOpenProjectMacros().get(0);
        assertEquals("Entries 1 - 3 out of 3", getPaginationEntriesText(macro));
        assertSubjectsContain(macro.getLivedata().getTableLayout(), "sp");

        // The macro displays an error once the preset is deleted.
        setup.deletePage(presetReference);
        setup.gotoPage(testReference);
        List<String> errors = new ViewPageWithOpenProjectMacro().waitForMacroErrors(1);
        assertTrue(errors.get(0).contains(String.format("There is no preset with id [%s].", presetId)),
            errors.get(0));
    }

    @Test
    void chartPresetWithMultipleDatasets(TestUtils setup, TestReference testReference)
    {
        // A list preset, which should not be proposed by the chart macro.
        String listPresetName = "List preset hidden from charts";
        DocumentReference listPresetReference =
            new DocumentReference("ListPreset", testReference.getLastSpaceReference());
        setup.deletePage(listPresetReference);
        setup.createPage(listPresetReference, "");
        setup.addObject(listPresetReference, PRESET_CLASS, "id", "900001", "name", listPresetName, "client",
            "openproject", "isMultiple", "0", "filter", "{\"query\":{\"filters\":[]}}");

        // Create a chart preset with one dataset per filter.
        String chartPresetName = "Two datasets chart preset";
        PresetEditPage editPage = OpenProjectAdminPage.gotoPage().clickCreatePreset().setName(chartPresetName)
            .setChartPreset(true).create();
        List<FilterBuilderParameter> builders = editPage.getFilterBuilders();
        assertEquals(1, builders.size());
        builders.get(0).addFilter(SUMMARY).typeValue(1, "speakers");
        editPage.addDataset().addFilter(SUMMARY).typeValue(1, "sp");
        editPage.waitUntilFilterValueContains("\"sp\"");
        PresetViewPage viewPage = editPage.clickSaveAndViewPreset();

        assertTrue(viewPage.isChartPreset());
        viewPage.waitUntilFiltersAreDisplayed(2);
        builders = viewPage.getFilterBuilders();
        assertEquals(2, builders.size());
        assertEquals("Dataset #1", builders.get(0).getTitle());
        assertEquals("Dataset #2", builders.get(1).getTitle());
        assertTrue(builders.stream().allMatch(FilterBuilderParameter::isReadOnly));

        // The chart macro proposes only the chart presets and displays one dataset per preset filter.
        setup.deletePage(testReference);
        OpenProjectChartMacroEditModal modal = new OpenProjectChartMacroEditModal(setup, testReference);
        modal.selectInstance(CONNECTION_ID);
        modal.clickMore();
        List<String> presetOptions = modal.getPresetOptions();
        assertTrue(presetOptions.contains(chartPresetName), presetOptions.toString());
        assertFalse(presetOptions.contains(listPresetName), presetOptions.toString());
        modal.selectPreset(chartPresetName);
        modal.clickSubmit();
        modal.getEditPage().clickSaveAndView();

        new ViewPageWithOpenProjectMacro().waitUntilPageIsReady();
        assertEquals(2, new ChartJSCanvas().getDatasetCount());
    }

    @Test
    void invalidPresetIds(TestUtils setup, TestReference testReference)
    {
        setup.createPage(testReference, String.format("{{openproject instance=\"%1$s\" presetId=\"abc\"/}}%n%n"
            + "{{openproject instance=\"%1$s\" presetId=\"999999\"/}}%n%n"
            + "{{openprojectchart instance=\"%1$s\" presetId=\"abc\"/}}%n%n"
            + "{{openprojectchart instance=\"%1$s\" presetId=\"999999\"/}}", CONNECTION_ID));
        setup.gotoPage(testReference);

        List<String> errors = new ViewPageWithOpenProjectMacro().waitForMacroErrors(4);
        assertEquals(4, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("The preset id should be a number."), errors.get(0));
        assertTrue(errors.get(1).contains("There is no preset with id [999999]."), errors.get(1));
        assertTrue(errors.get(2).contains("The preset id should be a number."), errors.get(2));
        assertTrue(errors.get(3).contains("No preset was found with the given id."), errors.get(3));
    }

    private static void assertSubjectsContain(TableLayoutElement ld, String text)
    {
        for (int i = 1; i <= ld.countRows(); i++) {
            String subject = ld.getCell(SUBJECT_COLUMN, i).getText();
            assertTrue(subject.toLowerCase().contains(text), subject);
        }
    }
}
