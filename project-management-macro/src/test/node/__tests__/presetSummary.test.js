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
// @vitest-environment jsdom
import { afterEach, describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import TomSelect from "tom-select";
import { $, displayFilterBuilder } from "../filterBuilderPage.js";
import { loadQueryWriter } from "../queryWriter.js";
import { loadAmdModule } from "../amdModule.js";

const MACRO_SCRIPTS = resolve(import.meta.dirname, "../../../main/resources/js/projectmanagement/macro");

const STATUS = {
  id: "status",
  name: "Status",
  filter: { id: "list", operators: [{ id: "=", name: "is one of" }], options: [{ value: "1", label: "New" }] },
};

const L10N = {
  summary: "Selects the work packages where {0}.",
  summaryAll: "No filter: all the work packages you can see.",
  summaryPreset: "Selects the work packages of the preset \"{1}\" where {0}.",
  summaryAllPreset: "Selects the work packages of the preset \"{0}\".",
  summaryIncomplete: "The filters without a value are ignored.",
  and: "and",
  or: "or",
  "list.=": "is one of",
};

// The parts of the XWiki JavaScript API that the scripts use.
const XWIKI = {
  Document: function () {
    this.getURL = () => "";
  },
  Model: {
    // Called as a constructor.
    resolve: function () {
      return {};
    },
  },
  EntityType: { DOCUMENT: "document" },
  contextPath: "/xwiki",
  currentWiki: "xwiki",
};

function getSummary() {
  return $(".proj-manag-filter-summary").text();
}

function getBuilder() {
  return window.FilterBuilder.instances.get($(".proj-manag-constraint-builder")[0]);
}

// Binds the builder to its container as in the macro editor (filterParameter.js), the container holding what the
// widget of the source parameter gave before the script loaded.
function bindBuilder() {
  const dependencies = [$, window.FilterBuilder, loadQueryWriter(), loadAmdModule("macro/queryEdits.js")];
  const require = (names, onLoad) => onLoad(...(names.length > 1 ? dependencies : []));
  require.config = () => {};
  new Function("setTimeout", "require", "XWiki", readFileSync(resolve(MACRO_SCRIPTS, "filterParameter.js"), "utf-8"))(
    (run) => run(), require, XWIKI);
}

// The Filter Preset field listing the presets.
function getPresetPickerHTML(presetId) {
  const option = (value, name) => `<option value="${value}"${value === presetId ? " selected" : ""}>
    ${name}
  </option>`;
  return `<select name="presetId">${option("", "")}${option("1", "Open bugs")}${option("2", "Closed tasks")}</select>`;
}

// The Filter Preset field listing no preset, as the macro editor fills it for a macro with the given preset: it adds
// the missing option of the preset.
function getNoPresetFieldHTML(presetId) {
  const option = presetId ? `<option value="${presetId}" selected>${presetId}</option>` : "";
  return `<select id="parameter-presetId" name="presetId" class="form-control op-preset-select" disabled="disabled">
    <option value="">No preset available for this macro</option>${option}
  </select>`;
}

// The macro editor of a work item macro: the widget of the source parameter, its matching filters (without builder:
// the widget only talks to their container), the content and the Filter Preset field.
function getMacroEditorHTML(presetField) {
  return `<div class="modal macro-editor-modal">
    <div class="macro-editor">
      <div class="macro-parameter">
        <div class="pm-work-item-source" data-l10n="{}">
          <input type="hidden" name="source" value=""/>
          <div class="pm-source-identifier"><p class="pm-source-identifier-message"></p></div>
          <select class="pm-source-way-select">
            <option value="query">Matching filters</option>
            <option value="list">Specific work packages</option>
          </select>
          <div class="pm-source-editor" data-way="query">
            <div class="pm-source-query-builder" data-builder-role="query"></div>
            <div class="pm-source-query-text"><textarea class="pm-source-query-input"></textarea></div>
            <button type="button" class="pm-source-toggle-text"></button>
            <div class="pm-source-preset-slot"></div>
          </div>
          <div class="pm-source-editor" data-way="list"><div class="pm-source-list-picker"></div></div>
        </div>
      </div>
      <textarea name="$content"></textarea>
      ${presetField}
    </div>
    <div class="modal-footer"></div>
  </div>`;
}

/**
 * Opens the macro editor of a work item macro with the given preset, binding the widget of its source parameter
 * (workItemSourceParameter.js).
 *
 * @param presetId the preset of the macro
 * @param presetField the HTML of the Filter Preset field holding this preset, the field listing the presets by default
 * @return the container of the matching filters
 */
function editMacro(presetId, presetField = getPresetPickerHTML(presetId)) {
  document.body.innerHTML = getMacroEditorHTML(presetField);
  let bindEditor;
  const layout = {
    hideParameter() {},
    moveParameter() {},
    activateGroup() {},
    onMacroEditor(filter, callback) {
      bindEditor = callback;
    },
  };
  const picker = { setIds() {}, setSingle() {}, refresh() {}, destroy() {} };
  const dependencies = [$, layout, loadAmdModule("macro/workItemSelection.js"), loadAmdModule("macro/workItemList.js"),
    { mount: () => picker }];
  const require = (names, onLoad) => onLoad(...dependencies);
  require.config = () => {};
  new Function("require", "XWiki", readFileSync(resolve(MACRO_SCRIPTS, "workItemSourceParameter.js"), "utf-8"))(
    require, XWIKI);
  bindEditor($(".macro-editor"));
  return $("[data-builder-role='query']");
}

// The suggest widget of XWiki 18+ (Tom Select) on the Filter Preset field.
function usePresetPicker() {
  const field = document.querySelector("select[name='presetId']");
  field.selectize = new TomSelect(field, { labelField: "label" });
  return field.selectize;
}

describe("the summary of a filter builder with a preset", () => {
  it("names the preset, whose filters apply too", async () => {
    const builder = await displayFilterBuilder([STATUS], L10N);

    builder.setPreset("Open bugs");
    expect(getSummary()).toBe("Selects the work packages of the preset \"Open bugs\".");

    builder.addFilter({ property: "status", constraints: [{ operator: "=", value: "1" }] });
    expect(getSummary()).toBe("Selects the work packages of the preset \"Open bugs\" where Status is one of New.");

    builder.setPreset("");
    expect(getSummary()).toBe("Selects the work packages where Status is one of New.");
  });

  it("inserts the name of the preset as it is", async () => {
    const builder = await displayFilterBuilder([STATUS], L10N);
    builder.addFilter({ property: "status" });

    builder.setPreset("Bugs {0} $& co");

    expect(getSummary()).toBe("Selects the work packages of the preset \"Bugs {0} $& co\". The filters without a "
      + "value are ignored.");
  });
});

describe("the matching filters of a macro with a preset", () => {
  afterEach(() => {
    $(document).off();
  });

  it("name the preset chosen before and after the builder is ready", async () => {
    await displayFilterBuilder([STATUS], L10N);
    const container = $(".proj-manag-constraint-builder").wrap("<div data-builder-role='query'></div>").parent();
    container.data({ pmPendingInput: { type: "query", value: "" }, pmPendingPreset: "Open bugs" });

    bindBuilder();
    expect(getSummary()).toBe("Selects the work packages of the preset \"Open bugs\".");

    container.trigger("pm:setPreset", ["Closed tasks"]);
    expect(getSummary()).toBe("Selects the work packages of the preset \"Closed tasks\".");

    container.trigger("pm:setPreset", [""]);
    expect(getSummary()).toBe("No filter: all the work packages you can see.");
    expect(getBuilder().getConstraints()).toEqual({});
  });
});

describe("the widget of the source parameter", () => {
  afterEach(() => {
    $(document).off();
  });

  it("gives the matching filters the name of the chosen preset", () => {
    const container = editMacro("1");
    const preset = usePresetPicker();
    expect(container.data("pmPendingPreset")).toBe("Open bugs");

    preset.setValue("2");
    expect(container.data("pmPendingPreset")).toBe("Closed tasks");

    preset.clear();
    expect(container.data("pmPendingPreset")).toBe("");
  });

  it("gives no preset to the matching filters while another way is selected", () => {
    const container = editMacro("1");
    usePresetPicker();
    const names = [];
    container.on("pm:setPreset", (event, name) => names.push(name));

    $(".pm-source-way-select").val("list").trigger("change");
    $(".pm-source-way-select").val("query").trigger("change");

    expect(names).toEqual(["", "Open bugs"]);
  });

  it("reads the name of the preset from a plain select", () => {
    const container = editMacro("");
    expect(container.data("pmPendingPreset")).toBe("");

    $("select[name='presetId']").val("2").trigger("change");
    expect(container.data("pmPendingPreset")).toBe("Closed tasks");
  });

  it("keeps a preset that the field doesn't list, the user being able to clear it", () => {
    // E.g. a deleted preset, the last one of the wiki: the field lists none.
    const container = editMacro("4201", getNoPresetFieldHTML("4201"));

    // The macro editor writes the fields it can submit, which a disabled field isn't.
    expect($(".macro-editor :input").serializeArray()).toContainEqual({ name: "presetId", value: "4201" });
    expect(container.data("pmPendingPreset")).toBe("4201");

    $("select[name='presetId']").val("").trigger("change");
    expect(container.data("pmPendingPreset")).toBe("");
  });

  it("leaves the field listing no preset disabled for a macro without preset", () => {
    editMacro("", getNoPresetFieldHTML(""));

    expect($("select[name='presetId']").prop("disabled")).toBe(true);
    expect($(".macro-editor :input").serializeArray().map((parameter) => parameter.name)).not.toContain("presetId");
  });
});
