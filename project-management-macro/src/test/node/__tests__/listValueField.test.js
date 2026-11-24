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
// The style of the value field of a list filter, displayed by Tom Select (the suggest widget of XWiki 18+), with the
// style sheets of Tom Select and of the filter builder. jsdom has no layout: the tests check the styles that the
// browsers lay out the field with.
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { resolve } from "node:path";
import TomSelect from "tom-select";
import { beforeEach, describe, expect, it } from "vitest";

const STYLE_SHEETS = [
  createRequire(import.meta.url).resolve("tom-select/dist/css/tom-select.css"),
  resolve(import.meta.dirname, "../../../main/resources/css/projectmanagement/filterBuilder.css"),
];

// Displays the value field of a list filter holding the given values, as the filter displayer does.
function displayListValue(values) {
  document.head.innerHTML = STYLE_SHEETS.map((path) => `<style>${readFileSync(path, "utf-8")}</style>`).join("");
  document.body.innerHTML = `<div class="proj-manag-container proj-manag-constraint-value-container">
      <input class="proj-manag-constraint-value" type="text" value="${values.join(",")}"/>
    </div>`;
  return new TomSelect(document.querySelector("input"), {
    create: true,
    maxItems: null,
    options: values.map((value) => ({ value, text: value })),
  });
}

function getSearchMinWidth(tomSelect) {
  return parseFloat(getComputedStyle(tomSelect.control_input).minWidth);
}

// The room that the search field takes on the line of the values, before it grows into what they leave: its width,
// then its left and right margins.
function getSearchRoom(tomSelect) {
  const style = getComputedStyle(tomSelect.control_input);
  return [style.flexBasis, style.marginLeft, style.marginRight];
}

describe("the field searching the values of a list filter", () => {
  let tomSelect;

  beforeEach(() => {
    tomSelect = displayListValue(["QA150 Unique", "QA150 Twin"]);
  });

  it("takes no room on the line of the values while the value field isn't focused", () => {
    // The values fitting on one line keep the value field one line high, however little room they leave, e.g. four
    // statuses: the empty search field doesn't wrap below them.
    expect(getSearchMinWidth(tomSelect)).toBe(0);
    expect(getSearchRoom(tomSelect)).toEqual(["0px", "0px", "0px"]);
  });

  it("has room for a few characters while the value field is focused", async () => {
    tomSelect.focus();
    // Tom Select marks the field as focused once the focus is moved.
    await new Promise((resolve) => setTimeout(resolve));

    expect(tomSelect.wrapper.classList.contains("focus")).toBe(true);
    expect(getComputedStyle(tomSelect.control_input).minWidth).toBe("4em");
    expect(getSearchRoom(tomSelect)).toEqual(["auto", "4px", "4px"]);
  });

  it("keeps the minimum width of Tom Select without value", () => {
    tomSelect.clear();

    expect(getComputedStyle(tomSelect.control_input).minWidth).toBe("7rem");
  });
});
