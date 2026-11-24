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
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { $, displayFilterBuilder } from "../filterBuilderPage.js";

const PROJECT = {
  id: "project",
  name: "Project",
  filter: { id: "list", operators: [{ id: "=", name: "is one of" }], options: [{ value: "9", label: "QA" }] },
};

// A property of the instance of the client (e.g. a custom field), unknown until its properties are loaded.
const SPRINT = {
  id: "sprint",
  name: "Sprint",
  filter: { id: "list", operators: [{ id: "=", name: "is one of" }], options: [{ value: "3", label: "Sprint 3" }] },
};

const L10N = {
  summary: "Selects the work items where {0}.",
  summaryAll: "No filter: all the work items you can see.",
  summaryLoading: "Loading the filters…",
  propertiesNotLoaded: "Some properties couldn't be loaded.",
  and: "and",
  or: "or",
  "list.=": "is one of",
};

// Settles the pending request of the properties.
let properties;

function getSummary() {
  return $(".proj-manag-filter-summary").text();
}

function getAddFilter() {
  return $(".proj-manag-add-filter");
}

function getProposedProperties() {
  return getAddFilter().find("option").not("[value='']").map((index, option) => option.value).get();
}

function getMessage() {
  return $(".proj-manag-properties-error").text();
}

function isBusy() {
  return $(".proj-manag-constraint-builder").attr("aria-busy") === "true";
}

describe("a filter builder loading its properties", () => {
  beforeEach(() => {
    vi.spyOn($, "getJSON").mockImplementation(() => new Promise((resolve, reject) => {
      properties = { resolve, reject };
    }));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("tells that the filters are loading, and adds no filter, until the properties are loaded", async () => {
    const builder = await displayFilterBuilder([PROJECT], L10N);
    const loaded = builder.setSourceParameters({ instance: "QA" }, false);
    // The deprecated filters of a macro, read while the properties load.
    builder.addFilter({ property: "project", constraints: [{ operator: "=", value: "9" }], dropUnsupported: true });
    builder.addFilter({ property: "sprint", constraints: [{ operator: "=", value: "3" }], dropUnsupported: true });

    expect(getSummary()).toBe("Loading the filters…");
    expect(getAddFilter().prop("disabled")).toBe(true);
    expect(isBusy()).toBe(true);

    properties.resolve([PROJECT, SPRINT]);
    await loaded;

    expect(getSummary()).toBe("Selects the work items where Project is one of QA, and Sprint is one of Sprint 3.");
    expect(getAddFilter().prop("disabled")).toBe(false);
    expect(isBusy()).toBe(false);
    expect(getProposedProperties()).toEqual(["project", "sprint"]);
    expect(getMessage()).toBe("");
  });

  it("tells that the filters are loading while the properties of another source load", async () => {
    const builder = await displayFilterBuilder([PROJECT], L10N);
    expect(getSummary()).toBe("No filter: all the work items you can see.");

    const loaded = builder.setSourceParameters({ instance: "Other" }, true);

    expect(getSummary()).toBe("Loading the filters…");
    expect(getAddFilter().prop("disabled")).toBe(true);

    properties.resolve([PROJECT, SPRINT]);
    await loaded;

    expect(getSummary()).toBe("No filter: all the work items you can see.");
    expect(getAddFilter().prop("disabled")).toBe(false);
  });

  it("stays loading until both its filters and its properties are loaded", async () => {
    const builder = await displayFilterBuilder([PROJECT], L10N);
    builder.setLoading(true);
    const loaded = builder.setSourceParameters({ instance: "QA" }, false);

    // The filters are read before the properties are loaded.
    builder.setLoading(false);
    expect(getSummary()).toBe("Loading the filters…");
    expect(getAddFilter().prop("disabled")).toBe(true);

    properties.resolve([PROJECT]);
    await loaded;
    expect(getSummary()).toBe("No filter: all the work items you can see.");
    expect(getAddFilter().prop("disabled")).toBe(false);
  });

  it("tells when the properties can't be loaded, and keeps the filters on the unknown properties", async () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => {});
    const builder = await displayFilterBuilder([PROJECT], L10N);
    const loaded = builder.setSourceParameters({ instance: "QA" }, false);
    builder.addFilter({ property: "sprint", constraints: [{ operator: "=", value: "3" }] });

    properties.reject(new Error("Internal Server Error"));
    await loaded;

    expect(getMessage()).toBe("Some properties couldn't be loaded.");
    expect(error).toHaveBeenCalled();
    // The builder proposes the properties it has.
    expect(getAddFilter().prop("disabled")).toBe(false);
    expect(getProposedProperties()).toEqual(["project"]);
    expect(getSummary()).toBe("No filter: all the work items you can see.");
    expect(builder.getConstraints()).toEqual({
      sprint: { property: "sprint", constraints: [{ operator: "=", value: "3" }] },
    });

    // The message goes away when the properties are loaded again, e.g. for another source.
    const reloaded = builder.setSourceParameters({ instance: "Other" }, true);
    expect(getMessage()).toBe("");
    properties.resolve([PROJECT, SPRINT]);
    await reloaded;
    expect(getMessage()).toBe("");
    expect(getProposedProperties()).toEqual(["project", "sprint"]);
  });

  it("tells when no property is loaded", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const builder = await displayFilterBuilder([PROJECT], L10N);
    const loaded = builder.setSourceParameters({ instance: "Removed" }, false);

    // The configuration of the client couldn't be resolved.
    properties.resolve([]);
    await loaded;

    expect(getMessage()).toBe("Some properties couldn't be loaded.");
    expect(getProposedProperties()).toEqual(["project"]);
  });

  it("ignores the failure of the properties of a previous source", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const builder = await displayFilterBuilder([PROJECT], L10N);
    const previous = builder.setSourceParameters({ instance: "QA" }, false);
    const previousProperties = properties;
    const loaded = builder.setSourceParameters({ instance: "Other" }, true);

    previousProperties.reject(new Error("Internal Server Error"));
    await previous;

    expect(getMessage()).toBe("");
    expect(getSummary()).toBe("Loading the filters…");

    properties.resolve([PROJECT, SPRINT]);
    await loaded;
    expect(getMessage()).toBe("");
    expect(getSummary()).toBe("No filter: all the work items you can see.");
  });
});

describe("a filter builder not showing the filters it was given", () => {
  it("doesn't describe the filters that it shows instead", async () => {
    const builder = await displayFilterBuilder([PROJECT], L10N);

    builder.setUnreadable(true);
    expect(getSummary()).toBe("");
    builder.addFilter({ property: "project", constraints: [{ operator: "=", value: "9" }] });
    expect(getSummary()).toBe("");

    builder.setUnreadable(false);
    expect(getSummary()).toBe("Selects the work items where Project is one of QA.");
  });
});
