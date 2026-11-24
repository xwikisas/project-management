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
import { beforeEach, describe, expect, it } from "vitest";
import { displayFilterBuilder } from "../filterBuilderPage.js";

const TEXT_FILTER = { id: "text", operators: [{ id: "~", name: "contains" }] };
const PROPERTIES = [
  { id: "summary", name: "Subject", filter: TEXT_FILTER },
  { id: "description", name: "Description", filter: TEXT_FILTER },
];

let addFilterList;

function dispatch(event) {
  addFilterList.dispatchEvent(event);
  return event;
}

function keyDown(key) {
  return dispatch(new KeyboardEvent("keydown", { key, bubbles: true, cancelable: true }));
}

function keyUp(key) {
  return dispatch(new KeyboardEvent("keyup", { key, bubbles: true, cancelable: true }));
}

// The change of the selected property, as signaled by the browsers.
function select(value) {
  addFilterList.value = value;
  dispatch(new Event("input", { bubbles: true }));
  dispatch(new Event("change", { bubbles: true }));
}

// Moves to a property with a key of the keyboard (e.g. type-ahead, arrows), as the browsers do on a closed select.
function moveWithKey(key, value) {
  keyDown(key);
  select(value);
  keyUp(key);
}

function getFilteredProperties() {
  return [...document.querySelectorAll(".proj-manag-constraints .proj-manag-constraint-name")]
    .map((input) => input.value);
}

describe("adding a filter", () => {
  beforeEach(async () => {
    await displayFilterBuilder(PROPERTIES);
    addFilterList = document.querySelector("select.proj-manag-add-filter");
  });

  it("adds the property picked with the mouse at once", () => {
    dispatch(new MouseEvent("mousedown", { bubbles: true }));
    select("description");

    expect(getFilteredProperties()).toEqual(["description"]);
    expect(addFilterList.value).toBe("");
  });

  it("lets the keyboard move through the properties and adds the selected one on Enter", () => {
    moveWithKey("s", "summary");
    moveWithKey("ArrowDown", "description");

    expect(getFilteredProperties()).toEqual([]);
    expect(addFilterList.value).toBe("description");

    const enter = keyDown("Enter");
    keyUp("Enter");

    expect(getFilteredProperties()).toEqual(["description"]);
    expect(addFilterList.value).toBe("");
    // Enter doesn't submit the form holding the filter builder, e.g. the macro editor.
    expect(enter.defaultPrevented).toBe(true);
  });

  it("adds nothing on Enter when no property is selected", () => {
    const enter = keyDown("Enter");

    expect(getFilteredProperties()).toEqual([]);
    expect(enter.defaultPrevented).toBe(true);
  });

  it("goes back to its placeholder when left without adding the selected property", () => {
    moveWithKey("ArrowDown", "summary");
    dispatch(new FocusEvent("blur"));

    expect(getFilteredProperties()).toEqual([]);
    expect(addFilterList.value).toBe("");
  });

  it("adds the property picked with the mouse after using the keyboard", () => {
    moveWithKey("ArrowDown", "summary");
    dispatch(new MouseEvent("mousedown", { bubbles: true }));
    select("description");

    expect(getFilteredProperties()).toEqual(["description"]);
  });
});
