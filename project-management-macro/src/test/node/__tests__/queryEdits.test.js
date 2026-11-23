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
import { beforeEach, describe, expect, it } from "vitest";
import { loadAmdModule } from "../amdModule.js";

const queryEdits = loadAmdModule("macro/queryEdits.js");

/**
 * A minimal element, matched by its classes only (the module selects elements by class).
 */
function element(className, parent, value) {
  const children = [];
  const node = {
    className,
    parent,
    value,
    children,
    matches: (selector) => selector.split(",").some((part) => part.trim() === "." + className),
    closest(selector) {
      let current = node;
      while (current && !current.matches(selector)) {
        current = current.parent;
      }
      return current || null;
    },
    querySelector(selector) {
      for (const child of children) {
        const found = child.matches(selector) ? child : child.querySelector(selector);
        if (found) {
          return found;
        }
      }
      return null;
    },
  };
  if (parent) {
    parent.children.push(node);
  }
  return node;
}

/**
 * A builder with one filter on the given property.
 */
function builder(property) {
  const builderElement = element("proj-manag-constraint-builder");
  const row = element("proj-manag-constraint", builderElement);
  element("proj-manag-constraint-name", row, property);
  const value = element("proj-manag-constraint-value-container", row);
  const input = element("input", value);
  const addButton = element("button", builderElement);
  return { element: builderElement, input, addButton };
}

const STATUS_OPEN = [{ property: "status", constraints: [{ operator: "o", value: "" }] }];
const STATUS_CLOSED = [{ property: "status", constraints: [{ operator: "c", value: "" }] }];

describe("createTracker", () => {
  let edits;
  let query;
  let series;

  beforeEach(() => {
    edits = queryEdits.createTracker();
    query = builder("status");
    series = builder("type");
    edits.setReadFilters(query.element, STATUS_OPEN);
    edits.setReadFilters(series.element, []);
  });

  it("doesn't edit a builder when the user acts on another one", () => {
    expect(edits.recordAction(series.addButton, series.addButton)).toBe(series.element);

    expect(edits.isEdited(query.element, STATUS_OPEN)).toBe(false);
    expect(edits.isEdited(series.element, [])).toBe(true);
  });

  it("edits the builder of the date picker of its focused filter", () => {
    const datePicker = element("daterangepicker");
    const day = element("td", datePicker);

    expect(edits.recordAction(day, query.input)).toBe(query.element);

    expect(edits.isEdited(query.element, STATUS_OPEN)).toBe(true);
    expect(edits.getEditedProperty(query.element)).toBe("status");
    expect(edits.isEdited(series.element, [])).toBe(false);
  });

  it("ignores the actions outside of the builders", () => {
    expect(edits.recordAction(element("button"), null)).toBe(null);
    expect(edits.isEdited(query.element, STATUS_OPEN)).toBe(false);
  });

  it("keeps the property of the filter last changed, not of a filter added", () => {
    edits.recordAction(query.input, query.input);
    expect(edits.getEditedProperty(query.element)).toBe("status");

    edits.recordAction(query.addButton, query.addButton);
    expect(edits.getEditedProperty(query.element)).toBe(null);

    edits.recordAction(query.input, query.input);
    edits.clearEditedProperty(query.element);
    expect(edits.getEditedProperty(query.element)).toBe(null);
  });

  it("edits a builder whose filters differ from the ones read, whatever their order", () => {
    const twoFilters = [STATUS_OPEN[0], { property: "type", constraints: [{ operator: "equals", value: "1" }] }];
    edits.setReadFilters(query.element, twoFilters);
    expect(edits.isChanged(query.element, [...twoFilters].reverse())).toBe(false);

    expect(edits.isChanged(query.element, STATUS_CLOSED)).toBe(true);
    expect(edits.isEdited(query.element, STATUS_CLOSED)).toBe(true);
    // Once edited, the builder stays edited.
    expect(edits.isEdited(query.element, twoFilters)).toBe(true);
  });

  it("doesn't report changes while the filters are being read", () => {
    edits.reset(query.element);

    expect(edits.isChanged(query.element, STATUS_CLOSED)).toBe(false);
    expect(edits.isEdited(query.element, STATUS_CLOSED)).toBe(false);
  });

  it("forgets the edits of every builder", () => {
    edits.recordAction(query.input, query.input);
    edits.recordAction(series.addButton, series.addButton);

    edits.clear();

    expect(edits.isEdited(query.element, STATUS_OPEN)).toBe(false);
    expect(edits.isEdited(series.element, [])).toBe(false);
  });
});
