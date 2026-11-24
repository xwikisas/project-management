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

const workItemList = loadAmdModule("macro/workItemList.js");

describe("parseList", () => {
  it("splits on commas, spaces and new lines, as the list source does", () => {
    expect(workItemList.parseList("236, 237\nPROJ-42\t 12,,13 ")).toEqual(["236", "237", "PROJ-42", "12", "13"]);
  });

  it("keeps the written order and the first occurrence of repeated ids", () => {
    expect(workItemList.parseList("PROJ-42 236 PROJ-42 12 236")).toEqual(["PROJ-42", "236", "12"]);
  });

  it("reads no id from an empty text", () => {
    expect(workItemList.parseList("")).toEqual([]);
    expect(workItemList.parseList(" ,\n ")).toEqual([]);
    expect(workItemList.parseList(undefined)).toEqual([]);
  });
});

describe("formatList", () => {
  it("writes the ids separated by commas", () => {
    expect(workItemList.formatList(["236", "237", "PROJ-42"])).toBe("236, 237, PROJ-42");
  });

  it("drops the empty and repeated ids", () => {
    expect(workItemList.formatList(["236", " ", "PROJ-42", "236"])).toBe("236, PROJ-42");
    expect(workItemList.formatList([])).toBe("");
  });

  it("is read back by parseList", () => {
    const ids = ["7", "PROJ-42", "a_b-3"];
    expect(workItemList.parseList(workItemList.formatList(ids))).toEqual(ids);
  });
});

describe("isIdOrKey", () => {
  it.each(["1", "236", "PROJ-42", "a-1", "X_1-007"])("accepts %s", (value) => {
    expect(workItemList.isIdOrKey(value)).toBe(true);
  });

  it.each(["", "PROJ", "PROJ-", "-42", "42-PROJ", "1PROJ-2", "PROJ-4a", " 42", "#42", undefined, 42])(
    "rejects %s", (value) => {
      expect(workItemList.isIdOrKey(value)).toBe(false);
    });
});

describe("extend", () => {
  it("adds the ids not selected yet after the selected ones", () => {
    expect(workItemList.extend(["236", "PROJ-42"], ["12", "236", "13"])).toEqual(["236", "PROJ-42", "12", "13"]);
  });
});

describe("toOption", () => {
  it("shows the id, the summary, the project and the status of a work item", () => {
    expect(workItemList.toOption({
      identifier: { value: 236 },
      summary: { value: "Fix the login" },
      project: { value: "Demo project" },
      status: "New",
    })).toEqual({ value: "236", label: "#236 Fix the login", hint: "Demo project · New" });
  });

  it("shows a key without the number sign and leaves out the missing properties", () => {
    expect(workItemList.toOption({ identifier: { value: "PROJ-42" } }))
      .toEqual({ value: "PROJ-42", label: "PROJ-42", hint: "" });
  });

  it("shows the work item with the id it is listed with, and keeps its id so that searching the id finds it", () => {
    expect(workItemList.toOption({ identifier: { value: "12" }, key: "DP-12", summary: { value: "Upload" } }, "DP-12"))
      .toEqual({ value: "DP-12", label: "DP-12 Upload", hint: "", key: "DP-12", id: "12" });
  });

  it("labels a work item listed by its id with its key", () => {
    expect(workItemList.toOption({ identifier: { value: "12" }, key: "DP-3", summary: { value: "Upload" } }))
      .toEqual({ value: "12", label: "DP-3 Upload", hint: "", key: "DP-3" });
  });

  it("ignores a work item without id", () => {
    expect(workItemList.toOption({ summary: { value: "No id" } })).toBeUndefined();
    expect(workItemList.toOption(undefined)).toBeUndefined();
  });
});

describe("canShowSuggestions", () => {
  it("shows the suggestions of a typed text", () => {
    expect(workItemList.canShowSuggestions("2")).toBe(true);
    expect(workItemList.canShowSuggestions(" ac-9 ")).toBe(true);
  });

  it("doesn't show the suggestions without a typed text, e.g. once a work item is picked and the text cleared", () => {
    expect(workItemList.canShowSuggestions("")).toBe(false);
    expect(workItemList.canShowSuggestions("  ")).toBe(false);
    expect(workItemList.canShowSuggestions(undefined)).toBe(false);
  });
});

describe("findOption", () => {
  const options = [
    { value: "157", label: "AC-1 Mobile offline mode", key: "AC-1" },
    { value: "AC-9", label: "AC-9 Export", key: "AC-9", id: "165" },
    { value: "244", label: "#244 Beta" },
  ];

  it("finds the work item of a key typed in another case", () => {
    expect(workItemList.findOption(options, "ac-9")).toBe(options[1]);
  });

  it("finds a work item picked by its id from its key, and the other way round", () => {
    expect(workItemList.findOption(options, "AC-1")).toBe(options[0]);
    expect(workItemList.findOption(options, " 165 ")).toBe(options[1]);
  });

  it("finds a work item by the id it is listed with", () => {
    expect(workItemList.findOption(options, "244")).toBe(options[2]);
  });

  it("finds nothing for another or an empty text", () => {
    expect(workItemList.findOption(options, "AC-10")).toBeUndefined();
    expect(workItemList.findOption(options, "15")).toBeUndefined();
    expect(workItemList.findOption(options, "")).toBeUndefined();
    expect(workItemList.findOption(undefined, "AC-1")).toBeUndefined();
  });
});

describe("withItemFound", () => {
  const item = (id, summary) => ({ identifier: { value: id }, summary: { value: summary } });

  it("suggests the work item with the typed id first, besides the work items whose summary contains it", () => {
    expect(workItemList.withItemFound(item(24, "Fix"), [item(124, "Bulk task 24"), item(224, "Bulk task 224")]))
      .toEqual([item(24, "Fix"), item(124, "Bulk task 24"), item(224, "Bulk task 224")]);
  });

  it("suggests the work item with the typed id once, when its summary contains it too", () => {
    expect(workItemList.withItemFound(item("266", "Many 266"), [item(266, "Many 266"), item(7, "266 too")]))
      .toEqual([item("266", "Many 266"), item(7, "266 too")]);
  });

  it("suggests the work items found by the search when no work item has the typed id", () => {
    expect(workItemList.withItemFound(undefined, [item(7, "A")])).toEqual([item(7, "A")]);
    expect(workItemList.withItemFound(item(24, "Fix"), undefined)).toEqual([item(24, "Fix")]);
    expect(workItemList.withItemFound(undefined, undefined)).toEqual([]);
  });
});

describe("resolution", () => {
  it("resolves the picked work items only once the parameters of the requests are ready", () => {
    const resolved = [];
    const resolution = workItemList.resolution(id => resolved.push(id), true);
    resolution.request("243");
    resolution.request("244");
    expect(resolved).toEqual([]);

    resolution.ready(["243", "244", "243"]);
    expect(resolved).toEqual(["243", "244"]);

    resolution.request("245");
    expect(resolved).toEqual(["243", "244", "245"]);
  });

  it("resolves right away when there is no parameter to wait for, and again when the parameters change", () => {
    const resolved = [];
    const resolution = workItemList.resolution(id => resolved.push(id), false);
    resolution.request("243");
    expect(resolved).toEqual(["243"]);

    resolution.ready(["243"]);
    expect(resolved).toEqual(["243", "243"]);
  });
});

describe("clientMessages", () => {
  it("takes the picker messages worded by the client from the messages of the widget", () => {
    expect(workItemList.clientMessages({
      "list.notFound": "Work package {0} not found",
      "list.pasteLabel": "Work package ids",
      "list.invalid": "",
      emptyList: "No work package is picked",
    })).toEqual({ notFound: "Work package {0} not found", pasteLabel: "Work package ids" });
  });

  it("has no message when the client words none", () => {
    expect(workItemList.clientMessages({})).toEqual({});
    expect(workItemList.clientMessages(undefined)).toEqual({});
  });
});

describe("restUrls", () => {
  const urls = workItemList.restUrls("/xwiki/rest", "sub wiki", "openproject", { instance: "main one", empty: "" });

  it("searches the work items whose summary contains the text, with the client parameters", () => {
    const url = new URL(urls.searchUrl('PROJ-4 "x"'), "http://localhost");
    expect(url.pathname).toBe("/xwiki/rest/wikis/sub%20wiki/projectmanagement/openproject/workitems");
    expect(url.searchParams.get("instance")).toBe("main one");
    expect(url.searchParams.has("empty")).toBe(false);
    expect(JSON.parse(url.searchParams.get("filter"))).toEqual({
      query: { filters: [{ property: "summary", constraints: [{ operator: "contains", value: 'PROJ-4 "x"' }] }] },
    });
  });

  it("gets a work item by its id or key", () => {
    expect(urls.itemUrl("PROJ-42")).toBe(
      "/xwiki/rest/wikis/sub%20wiki/projectmanagement/openproject/workitems/PROJ-42?instance=main%20one");
    expect(workItemList.restUrls("/rest", "xwiki", "test").itemUrl("1/2")).toBe(
      "/rest/wikis/xwiki/projectmanagement/test/workitems/1%2F2");
  });
});
