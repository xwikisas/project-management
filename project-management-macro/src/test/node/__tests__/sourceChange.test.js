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

// The properties of a source (e.g. an OpenProject instance): the assignees are suggested from it, the statuses are its
// own.
function getProperties(source) {
  return [
    {
      id: "assignee",
      name: "Assignee",
      filter: {
        id: "list",
        operators: [{ id: "=", name: "is one of" }],
        searchURL: `/rest/${source}/suggest/users?search={encodedQuery}`,
      },
    },
    {
      id: "status",
      name: "Status",
      filter: {
        id: "list",
        operators: [{ id: "=", name: "is one of" }],
        options: [{ value: "1", label: `New in ${source}` }],
      },
    },
  ];
}

// The URLs of the suggestion requests.
let searches;

// Resolves the pending request of the properties of the new source.
let loadProperties;

// Types the given text in the field searching the values of a filter, as a user.
function type(selectize, text) {
  const input = selectize.control_input;
  input.value += text;
  input.dispatchEvent(new Event("input"));
  // The suggestions are requested once the user stops typing.
  vi.runAllTimers();
}

function getValueField(property) {
  const row = [...document.querySelectorAll(".proj-manag-constraints .proj-manag-constraint")]
    .find((element) => element.querySelector(".proj-manag-constraint-name").value === property);
  return row.querySelector("input.proj-manag-constraint-value").selectize;
}

describe("the filters of a builder whose source changes", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    searches = [];
    vi.spyOn($, "getJSON").mockImplementation((url) => {
      if (url.includes("/suggest/")) {
        searches.push(url);
        return Promise.resolve([]);
      }
      // The properties of the new source, loaded slowly.
      return new Promise((resolve) => {
        loadProperties = resolve;
      });
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it("suggest no value of the previous source to a filter added while the properties load", async () => {
    const builder = await displayFilterBuilder(getProperties("old"));
    const loaded = builder.setSourceParameters({ instance: "new" }, true);

    builder.addFilter({ property: "assignee" }, true);
    type(getValueField("assignee"), "Pri");
    builder.addFilter({ property: "status" }, true);

    expect(searches).toEqual([]);
    expect(Object.keys(getValueField("status").options)).toEqual([]);

    loadProperties(getProperties("new"));
    await loaded;
    // The filters are displayed again, with the values of the new source.
    type(getValueField("assignee"), "Pri");
    expect(searches).toEqual(["/rest/new/suggest/users?search=Pri"]);
    expect(getValueField("status").options["1"].label).toBe("New in new");
  });

  it("suggest no value of the previous source to the filters already added while the properties load", async () => {
    const builder = await displayFilterBuilder(getProperties("old"));
    builder.addFilter({ property: "assignee", constraints: [{ operator: "=", value: "9" }] });
    // The label of the value, loaded from the previous source.
    getValueField("assignee").updateOption("9", { value: "9", label: "Priya Shah", queryValue: "Priya Shah" });

    builder.setSourceParameters({ instance: "new" }, true);
    const assignee = getValueField("assignee");
    type(assignee, "Pri");

    expect(searches).toEqual([]);
    // The value is kept, with its label, until the properties of the new source are loaded.
    expect(assignee.items).toEqual(["9"]);
    expect(assignee.options["9"].label).toBe("Priya Shah");
    expect(builder.getValueName("assignee", "9")).toBe("Priya Shah");
    expect(builder.getConstraints()).toEqual({
      assignee: { property: "assignee", constraints: [{ operator: "=", value: "9" }] },
    });
  });
});
