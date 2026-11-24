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

describe("showsLegacyFilters", () => {
  it("tells when the query way shows the legacy filters: the macro has no query", () => {
    const state = model.readInitial({ filters: LEGACY_FILTERS });
    expect(model.showsLegacyFilters(state)).toBe(true);

    state.values.query = "status = open";
    expect(model.showsLegacyFilters(state)).toBe(false);

    expect(model.showsLegacyFilters(model.readInitial({ content: "status = open", filters: LEGACY_FILTERS })))
      .toBe(false);
    expect(model.showsLegacyFilters(model.readInitial({}))).toBe(false);
    expect(model.showsLegacyFilters(model.readInitial({ filters: "[{}]", isChart: true }))).toBe(false);
  });
});

describe("getEditedParameters", () => {
  it("hides the parameters that the widget edits, the legacy filters included", () => {
    expect(model.getEditedParameters(model.readInitial({ filters: LEGACY_FILTERS })))
      .toEqual(["$content", "identifier", "filters"]);
  });

  it("doesn't hide the series of a chart", () => {
    expect(model.getEditedParameters(model.readInitial({ filters: "[{}]", isChart: true })))
      .toEqual(["$content", "identifier"]);
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

  it("keeps the legacy filters and identifier on their own way until a content replaces them", () => {
    const state = model.readInitial({ filters: LEGACY_FILTERS, identifier: "12" });
    // The macro is opened on the way of the identifier, which keeps it, without the filters of the matching filters.
    expect(state.way).toBe("list");
    expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, identifier: "12" });
    expect(model.droppedValues(state)).toEqual([{ type: "filters", value: LEGACY_FILTERS }]);

    // The matching filters keep the legacy filters, not the identifier.
    state.way = "query";
    expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, filters: LEGACY_FILTERS });
    expect(model.droppedValues(state)).toEqual([{ type: "identifier", value: "12" }]);

    // The query replacing the legacy filters is written from them.
    state.values.query = "status = open";
    expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, content: "status = open" });
    expect(model.droppedValues(state)).toEqual([{ type: "identifier", value: "12" }]);

    // Except their conditions that the filter builder can't show.
    state.values.unsupportedFilters = ["Description empty", "Subject notEquals a"];
    expect(model.droppedValues(state)).toEqual([
      { type: "unsupportedFilters", value: "Description empty, Subject notEquals a" },
      { type: "identifier", value: "12" },
    ]);

    // Removing all the filters drops them too.
    state.values.unsupportedFilters = [];
    state.values.query = "";
    state.values.queryEdited = true;
    expect(model.toFields(state)).toEqual(EMPTY_FIELDS);
    expect(model.showsLegacyFilters(state)).toBe(false);
    expect(model.droppedValues(state)).toEqual([
      { type: "filters", value: LEGACY_FILTERS },
      { type: "identifier", value: "12" },
    ]);

    // Another way drops them.
    state.values.query = "status = open";
    state.way = "list";
    state.values.list = ["7"];
    expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, content: "7", source: "list" });
    expect(model.droppedValues(state)).toEqual([
      { type: "query", value: "status = open" },
      { type: "filters", value: LEGACY_FILTERS },
      { type: "identifier", value: "12" },
    ]);
  });

  it.each([
    ["specific work items without any picked", "list"],
    ["link left empty", "url"],
  ])("drops the legacy filters on the %s, like a query", (name, way) => {
    const state = model.readInitial({ filters: LEGACY_FILTERS });
    state.way = way;
    expect(model.toFields(state)).toEqual(EMPTY_FIELDS);
    expect(model.droppedValues(state)).toEqual([{ type: "filters", value: LEGACY_FILTERS }]);

    const query = model.readInitial({ content: "status = open" });
    query.way = way;
    expect(model.toFields(query)).toEqual(EMPTY_FIELDS);
    expect(model.droppedValues(query)).toEqual([{ type: "query", value: "status = open" }]);
  });

  it("keeps the legacy identifier only on the way it converts to", () => {
    const state = model.readInitial({ identifier: "http://op/wp?query_id=2" });
    expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, identifier: "http://op/wp?query_id=2" });

    for (const way of ["query", "list"]) {
      state.way = way;
      expect(model.toFields(state)).toEqual(EMPTY_FIELDS);
      expect(model.droppedValues(state)).toEqual([{ type: "identifier", value: "http://op/wp?query_id=2" }]);
    }
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

  it("is false while the legacy identifier is kept", () => {
    const state = model.readInitial({ identifier: "12, 13" });
    expect(state.way).toBe("list");
    expect(model.isEmptyList(state)).toBe(false);

    // The identifier of a link is dropped by the list.
    expect(model.isEmptyList({ ...model.readInitial({ identifier: "http://op/wp" }), way: "list" })).toBe(true);
  });
});

describe("getWarning", () => {
  // The messages of the OpenProject macros (see projectmanagementworkitemsource/edit.vm).
  const L10N = {
    dropped: "Submit drops {0}.",
    "dropped.separator": "; ",
    "dropped.query": "the filters ({0})",
    "dropped.list": "the {0} picked work packages",
    "dropped.list.singular": "the picked work package",
    "dropped.filters": "the filters of the deprecated Filters parameter",
    "dropped.identifier": "the deprecated Work Packages parameter ({0})",
    "dropped.unsupportedSeries": "the unsupported conditions of the series ({0})",
    emptyList: "No work package is picked: all the work packages will be shown.",
  };

  it("names a single picked work package without number", () => {
    const state = model.readInitial({ source: "list", content: "236" });
    state.way = "query";
    state.values.query = "status = open";
    expect(model.getWarning(state, L10N, true)).toBe("Submit drops the picked work package.");

    state.way = "url";
    expect(model.getWarning(state, L10N, true))
      .toBe("Submit drops the filters (status = open); the picked work package.");

    state.values.list = ["236", "237"];
    expect(model.getWarning(state, L10N, true))
      .toBe("Submit drops the filters (status = open); the 2 picked work packages.");
  });

  it("tells that the legacy filters are dropped when no work package is picked", () => {
    const state = model.readInitial({ filters: LEGACY_FILTERS });
    state.way = "list";
    expect(model.getWarning(state, L10N, true)).toBe("No work package is picked: all the work packages will be "
      + "shown. Submit drops the filters of the deprecated Filters parameter.");
  });

  it("tells that the legacy identifier is dropped by the matching filters", () => {
    const state = model.readInitial({ filters: LEGACY_FILTERS, identifier: "237,239" });
    state.way = "query";
    expect(model.getWarning(state, L10N, true)).toBe("Submit drops the deprecated Work Packages parameter (237,239).");
  });

  it("tells nothing about the selection that isn't written", () => {
    const state = model.readInitial({ filters: LEGACY_FILTERS, identifier: "237,239" });
    expect(model.getWarning(state, L10N, false)).toBe("");
  });

  it("lists the conditions dropped from the series once", () => {
    const state = model.readInitial({ filters: "[{}]", isChart: true });
    state.values.unsupportedSeries = ["Description empty", "Description empty", "Subject startsWith a"];
    expect(model.getWarning(state, L10N, false))
      .toBe("Submit drops the unsupported conditions of the series (Description empty, Subject startsWith a).");

    state.values.query = "status = open";
    state.way = "list";
    state.values.list = ["7"];
    expect(model.getWarning(state, L10N, true)).toBe("Submit drops the filters (status = open); the unsupported "
      + "conditions of the series (Description empty, Subject startsWith a).");
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

  describe("of a macro with an advanced Live Data configuration", () => {
    const LINK = "http://localhost:8084/projects/qa150-t3/work_packages";
    const PARAMETERS = ["properties", "limit"];

    it("moves its properties and limit to their parameters", () => {
      const initial = model.readInitial({ identifier: LINK,
        content: '{"query":{"properties":["identifier","summary","status"],"limit":3}}' });
      expect(initial.locked).toBe(true);
      const state = model.withConvertedIdentifier(initial, PARAMETERS);
      expect(state.way).toBe("url");
      expect(state.locked).toBe(false);
      expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, content: LINK, source: "url" });
      expect(state.values.liveData).toEqual({ parameters: { properties: "identifier,summary,status", limit: "3" },
        rest: "" });
      expect(model.droppedValues(state)).toEqual([]);
    });

    it("drops the rest of its configuration", () => {
      const state = model.withConvertedIdentifier(model.readInitial({ identifier: "12, 13",
        content: '{"query":{"properties":["status"],"sort":[{"property":"status"}]},"meta":{"pagination":{}}}' }),
      PARAMETERS);
      expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, content: "12, 13", source: "list" });
      expect(state.values.liveData.parameters).toEqual({ properties: "status" });
      expect(model.droppedValues(state)).toEqual([{ type: "liveData",
        value: '{"query":{"sort":[{"property":"status"}]},"meta":{"pagination":{}}}' }]);
    });

    it("drops the settings that the macro has no parameter for", () => {
      const state = model.withConvertedIdentifier(model.readInitial({ identifier: LINK,
        content: '{"query":{"properties":["status"],"limit":3}}' }), ["limit"]);
      expect(state.values.liveData.parameters).toEqual({ limit: "3" });
      expect(model.droppedValues(state)).toEqual([{ type: "liveData", value: '{"query":{"properties":["status"]}}' }]);
    });

    it("keeps the configuration when the advanced way is chosen again", () => {
      const content = '{"query":{"limit":3}}';
      const state = model.withConvertedIdentifier(model.readInitial({ identifier: LINK, content }), PARAMETERS);
      state.way = "advanced";
      expect(model.toFields(state)).toEqual({ ...EMPTY_FIELDS, content });
      expect(model.droppedValues(state)).toEqual([{ type: "url", value: LINK }]);
    });
  });
});

describe("splitLiveDataConfiguration", () => {
  it.each([
    ['{"query":{"properties":[" status ","type"],"limit":10,"offset":5}}', { properties: "status,type", limit: "10" },
      '{"query":{"offset":5}}'],
    // Values that the parameters can't hold are kept in the rest.
    ['{"query":{"properties":[],"limit":0}}', {}, '{"query":{"properties":[],"limit":0}}'],
    ['{"query":{"properties":"status","limit":"3"}}', {}, '{"query":{"properties":"status","limit":"3"}}'],
    ['{"meta":{}}', {}, '{"meta":{}}'],
    ["{not json", {}, "{not json"],
    ["{}", {}, "{}"],
  ])("splits %s", (content, parameters, rest) => {
    expect(model.splitLiveDataConfiguration(content, ["properties", "limit"])).toEqual({ parameters, rest });
  });
});
