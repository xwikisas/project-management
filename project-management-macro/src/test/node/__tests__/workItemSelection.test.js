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
import { describe, expect, it } from "vitest";
import { loadAmdModule } from "../amdModule.js";

const model = loadAmdModule("macro/workItemSelection.js");

const EMPTY_FIELDS = { content: "", source: "", presetId: "", filters: "", identifier: "" };

const LEGACY_FILTERS = '{"query":{"filters":[{"property":"status","constraints":[{"operator":"o","value":""}]}]}}';

describe("parseList and formatList", () => {
  it("split the content like the list source", () => {
    expect(model.parseList(" 236,237\n PROJ-42 ,, 7 ")).toEqual(["236", "237", "PROJ-42", "7"]);
    expect(model.parseList("")).toEqual([]);
    expect(model.parseList(undefined)).toEqual([]);
  });

  it("drop the repeated work items, like the picker", () => {
    expect(model.parseList("236, 237, 236 PROJ-42,237")).toEqual(["236", "237", "PROJ-42"]);
  });

  it("format the list separated by commas", () => {
    expect(model.formatList(["236", " 237 ", "", "PROJ-42"])).toBe("236, 237, PROJ-42");
    expect(model.formatList([])).toBe("");
  });
});

describe("convertIdentifier", () => {
  it("converts work items to the list way", () => {
    expect(model.convertIdentifier("12, 13")).toEqual({ way: "list", list: ["12", "13"], url: "" });
  });

  it("converts a link to the url way", () => {
    const url = "http://localhost:8084/projects/demo/work_packages?query_id=29";
    expect(model.convertIdentifier(` ${url} `)).toEqual({ way: "url", list: [], url });
    expect(model.convertIdentifier("/work_packages/12").way).toBe("url");
  });
});

describe("readInitial", () => {
  it.each([
    ["empty macro", {}, "query", { query: "" }],
    ["query without source", { content: "status = open" }, "query", { query: "status = open" }],
    ["explicit query source", { source: "query", content: "status = open" }, "query", { query: "status = open" }],
    ["list", { source: "list", content: "236, 237\nPROJ-42" }, "list", { list: ["236", "237", "PROJ-42"] }],
    ["link", { source: "URL", content: " http://op/work_packages " }, "url", { url: "http://op/work_packages" }],
    ["filters source", { source: "filters", content: "{}" }, "advanced",
      { advanced: { source: "filters", content: "{}" } }],
    ["legacy filters parameter", { filters: LEGACY_FILTERS }, "query", { query: "", legacyFilters: LEGACY_FILTERS }],
    ["identifier ids", { identifier: "12,13" }, "list", { identifier: "12,13", list: [] }],
    ["identifier link", { identifier: "http://op/wp?query_id=2" }, "url", { identifier: "http://op/wp?query_id=2",
      url: "" }],
    ["identifier and content", { identifier: "12", content: "status = open" }, "query",
      { identifier: "12", query: "status = open" }],
    ["preset", { presetId: "p1", content: "type = Bug" }, "query", { presetId: "p1", query: "type = Bug" }],
  ])("reads a macro with %s", (name, fields, way, values) => {
    const state = model.readInitial(fields);
    expect(state.way).toBe(way);
    expect(state.locked).toBe(false);
    expect(state.values).toMatchObject(values);
  });

  it("locks the advanced Live Data configuration of a content starting with a curly bracket without source", () => {
    const state = model.readInitial({ content: ' {"query":{"sort":[]}}' });
    expect(state.way).toBe("advanced");
    expect(state.locked).toBe(true);
    expect(state.values.advanced).toEqual({ source: "", content: '{"query":{"sort":[]}}' });
    expect(model.hasAdvancedWay(state)).toBe(true);
  });

  it("offers the advanced way only when the macro uses it", () => {
    expect(model.hasAdvancedWay(model.readInitial({ source: "filters", content: "{}" }))).toBe(true);
    expect(model.hasAdvancedWay(model.readInitial({ source: "list", content: "1" }))).toBe(false);
    expect(model.hasAdvancedWay(model.readInitial({}))).toBe(false);
  });

  it("doesn't read the series of a chart as legacy filters", () => {
    const state = model.readInitial({ filters: "[{}]", isChart: true });
    expect(state.values.legacyFilters).toBe("");
    expect(model.toFields(state).filters).toBe("[{}]");
  });
});

describe("toFields", () => {
  it("writes nothing for an empty macro", () => {
    expect(model.toFields(model.readInitial({}))).toEqual(EMPTY_FIELDS);
  });

  it("writes the query with the preset, without source", () => {
    const state = model.readInitial({ presetId: "p1" });
    state.values.query = "status = open";
    expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, content: "status = open", presetId: "p1" });
  });

  it("keeps an explicit query source", () => {
    expect(model.toFields(model.readInitial({ source: "query", content: "a = 1" })).source).toBe("query");
  });

  it("writes the list and drops the preset", () => {
    const state = model.readInitial({ presetId: "p1", content: "status = open" });
    state.way = "list";
    state.values.list = ["236", "PROJ-42"];
    expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, content: "236, PROJ-42", source: "list" });
    expect(model.droppedValues(state)).toEqual([
      { type: "query", value: "status = open" },
      { type: "preset", value: "p1" },
    ]);
  });

  it("writes the link", () => {
    const state = model.readInitial({ source: "list", content: "1 2" });
    state.way = "url";
    state.values.url = "http://op/work_packages";
    expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, content: "http://op/work_packages", source: "url" });
    expect(model.droppedValues(state)).toEqual([{ type: "list", value: "2" }]);
  });

  it("keeps the legacy filters and identifier until a content replaces them", () => {
    const state = model.readInitial({ filters: LEGACY_FILTERS, identifier: "12" });
    expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, filters: LEGACY_FILTERS, identifier: "12" });
    expect(model.droppedValues(state)).toEqual([]);

    state.way = "query";
    expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, filters: LEGACY_FILTERS, identifier: "12" });

    state.values.query = "status = open";
    expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, content: "status = open" });
    expect(model.droppedValues(state)).toEqual([
      { type: "filters", value: LEGACY_FILTERS },
      { type: "identifier", value: "12" },
    ]);
  });

  it("doesn't write the source of a way without content", () => {
    const state = model.readInitial({ source: "list", content: "1" });
    state.values.list = [];
    expect(model.toFields(state)).toEqual(EMPTY_FIELDS);
  });

  it("writes the advanced way as it is", () => {
    const state = model.readInitial({ source: "filters", content: "{}", presetId: "p" });
    expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, content: "{}", source: "filters" });
    state.way = "query";
    expect(model.droppedValues(state)).toEqual([{ type: "advanced", value: "filters" }]);
  });

  it("keeps the fields of a locked macro unchanged", () => {
    const fields = { content: "{}", source: "", presetId: "p", filters: "[]", identifier: "1" };
    const state = model.readInitial(fields);
    state.values.query = "ignored";
    expect(model.toFields(state)).toEqual(fields);
    expect(model.droppedValues(state)).toEqual([]);
  });
});

describe("isEmptyList", () => {
  it("tells when the specific work items are selected without any work item picked", () => {
    const state = model.readInitial({ source: "list", content: "236" });
    expect(model.isEmptyList(state)).toBe(false);

    state.values.list = [];
    expect(model.isEmptyList(state)).toBe(true);

    state.way = "query";
    expect(model.isEmptyList(state)).toBe(false);
  });

  it("is false for a locked macro", () => {
    const state = model.readInitial({ content: "{}" });
    state.way = "list";
    expect(model.isEmptyList(state)).toBe(false);
  });
});

describe("withConvertedIdentifier", () => {
  it("prefills the list way", () => {
    const state = model.withConvertedIdentifier(model.readInitial({ identifier: "12 13" }));
    expect(state.way).toBe("list");
    expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, content: "12, 13", source: "list" });
    expect(model.droppedValues(state)).toEqual([]);
  });

  it("prefills the url way", () => {
    const state = model.withConvertedIdentifier(model.readInitial({ identifier: "https://op/wp?query_id=2" }));
    expect(state.way).toBe("url");
    expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, content: "https://op/wp?query_id=2", source: "url" });
  });

  it("leaves a state without identifier unchanged", () => {
    const state = model.readInitial({});
    expect(model.withConvertedIdentifier(state)).toBe(state);
  });
});
