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
import jquery from "jquery";
import TomSelect from "tom-select";
import { beforeEach, describe, expect, it } from "vitest";
import { loadAmdModule } from "../amdModule.js";

const layout = loadAmdModule("macro/macroEditorLayout.js");

// The fields of a macro editor whose picker (Tom Select, on XWiki 18+) is created by the test.
const FIELDS = `<label for="status">Status</label><input id="status" type="text" value="open">
  <label for="property">Group By</label>
  <select id="property"><option value="status">Status</option><option value="type">Type</option></select>
  <label for="sort">Sort</label>
  <select id="sort" multiple><option value="status:asc">Status - Ascending</option></select>`;

// The names of the fields that the assistive technologies list: the fields outside of the elements hidden from them.
function getAccessibleFields() {
  return [...document.querySelectorAll("input, select")].filter((field) => !field.closest("[aria-hidden=true]"))
    .map((field) => {
      const labelledBy = field.getAttribute("aria-labelledby");
      return labelledBy ? document.getElementById(labelledBy).textContent : [...field.labels]
        .map((label) => label.textContent).join(" ");
    });
}

function enhance(selector) {
  return new TomSelect(document.querySelector(selector), {});
}

// The mutations of the DOM are observed asynchronously.
function waitForMutations() {
  return new Promise((resolve) => setTimeout(resolve));
}

describe("hideEnhancedFields", () => {
  let editor;

  beforeEach(() => {
    document.body.innerHTML = `<div class="macro-editor" data-macroId="openprojectchart/xwiki/2.1">${FIELDS}</div>`;
    editor = jquery(".macro-editor");
  });

  it("hides the fields from the assistive technologies once a picker enhances them", async () => {
    layout.hideEnhancedFields(editor);
    expect(getAccessibleFields()).toEqual(["Status", "Group By", "Sort"]);

    ["#status", "#property", "#sort"].forEach(enhance);
    // Tom Select adds an option without text to a select when nothing is selected.
    document.querySelector("#property").tomselect.clear();
    await waitForMutations();

    // The inputs of the pickers only.
    expect(getAccessibleFields()).toEqual(["Status", "Group By", "Sort"]);
    expect(document.querySelectorAll("[aria-hidden=true]").length).toBe(3);
  });

  it("hides the fields already enhanced", () => {
    enhance("#property");

    layout.hideEnhancedFields(editor);

    expect(document.querySelector("#property").getAttribute("aria-hidden")).toBe("true");
  });

  it("shows a field again once its picker is destroyed", async () => {
    layout.hideEnhancedFields(editor);
    const picker = enhance("#status");
    await waitForMutations();

    picker.destroy();
    await waitForMutations();

    expect(document.querySelector("#status").hasAttribute("aria-hidden")).toBe(false);
  });

  it("leaves the fields that the picker hides entirely, as Selectize does", async () => {
    layout.hideEnhancedFields(editor);
    const field = document.querySelector("#status");
    field.classList.add("selectized");
    field.style.display = "none";
    await waitForMutations();

    expect(field.hasAttribute("aria-hidden")).toBe(false);
  });

  it("leaves the fields of another macro displayed by the same editor", async () => {
    layout.hideEnhancedFields(editor);
    editor.attr("data-macroId", "include/xwiki/2.1");

    enhance("#property");
    await waitForMutations();

    expect(document.querySelector("#property").hasAttribute("aria-hidden")).toBe(false);
  });
});
