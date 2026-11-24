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
// The style of the value field of a list filter, displayed by Selectize (the suggest widget of XWiki 15.10 to 17.10),
// with the style sheets of the page of XWiki 17.10 and of the filter builder. jsdom has no layout: the tests check the
// styles that the browsers lay out the field with.
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { beforeEach, describe, expect, it } from "vitest";

// The rules of XWiki 17.10 on the multi-value Selectize fields, in the order of their style sheets in the editor of an
// existing page: Selectize's (selectize.bootstrap3.css), then XWiki's (uicomponents/suggest/xwiki.selectize.css). The
// macro editor loads the style sheet of the filter builder after them.
const XWIKI_RULES = `
  .selectize-control.multi .selectize-input.has-items { padding-left: 7px; padding-right: 7px; }
  .selectize-input.has-items > input { margin: 0 4px !important; }
  .selectize-control.multi .selectize-input,
  .selectize-control.multi .selectize-input.has-items { padding-right: 32px; }
`;
const FILTER_BUILDER_STYLE_SHEET = resolve(import.meta.dirname,
  "../../../main/resources/css/projectmanagement/filterBuilder.css");

// Displays the value field of a list filter holding the given values, as Selectize builds it. Selectize sets the
// width of the search field in its style attribute, which jsdom would let override the style sheets.
function displayListValue(values) {
  document.head.innerHTML = `<style>${XWIKI_RULES}</style>
    <style>${readFileSync(FILTER_BUILDER_STYLE_SHEET, "utf-8")}</style>`;
  document.body.innerHTML = `<div class="proj-manag-container proj-manag-constraint-value-container">
      <input class="proj-manag-constraint-value selectized" type="text" tabindex="-1" style="display: none"/>
      <div class="selectize-control proj-manag-constraint-value multi">
        <div class="selectize-input items not-full has-options has-items">
          ${values.map((value) => `<div class="item" data-value="${value}">${value}</div>`).join("")}
          <input type="text" autocomplete="new-password"/>
        </div>
      </div>
    </div>`;
  return document.querySelector(".selectize-input");
}

// The room that the search field takes on the line of the values: its width, then its left and right margins.
function getSearchRoom(field) {
  const style = getComputedStyle(field.querySelector("input"));
  return [style.width, style.marginLeft, style.marginRight];
}

describe("the value field of a list filter displayed by Selectize", () => {
  let field;

  beforeEach(() => {
    field = displayListValue(["New", "Closed", "On hold", "Tested"]);
  });

  it("gives the whole line to the values, without the room of the loading indicator of XWiki", () => {
    // The values fitting on one line, e.g. four statuses, keep the value field one line high.
    expect(getComputedStyle(field).paddingRight).toBe("7px");
  });

  it("takes no room for the search field while the value field isn't focused", () => {
    expect(getSearchRoom(field)).toEqual(["0px", "0px", "0px"]);
  });

  it("gives room to the search field while the value field is focused", () => {
    field.classList.add("focus", "input-active");

    // Selectize sizes it to the typed text.
    const [width, ...margins] = getSearchRoom(field);
    expect(width).not.toBe("0px");
    expect(margins).toEqual(["4px", "4px"]);
  });
});
