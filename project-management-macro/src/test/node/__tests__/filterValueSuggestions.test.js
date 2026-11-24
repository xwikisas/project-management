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
  filter: {
    id: "list",
    operators: [{ id: "=", name: "is one of" }],
    searchURL: "/rest/openproject/instance/QA/suggest/projects?search={encodedQuery}",
  },
};

// The projects of the tool, as suggested: their identifier is the text written for them in a query.
const PROJECTS = [
  { value: "7", label: "QA 150 T1", queryValue: "qa150-t1" },
  { value: "10", label: "QA 150 T2 bis", queryValue: "qa150-t2b" },
  { value: "3", label: "Demo project", queryValue: "demo-project" },
];

const STATUS = {
  id: "status",
  name: "Status",
  filter: {
    id: "list",
    operators: [{ id: "=", name: "is one of" }],
    searchURL: "/rest/openproject/instance/QA/suggest/statuses?search={encodedQuery}",
  },
};

// The statuses of the tool, all suggested whatever the search: their name is the text written for them in a query.
const STATUSES = [
  { value: "1", label: "New", queryValue: "New" },
  { value: "4", label: "On hold", queryValue: "On hold" },
  { value: "12", label: "Closed", queryValue: "Closed" },
];

// The tool finds the projects whose name or identifier contains the searched text. The selected project (given by its
// id or its identifier) is suggested first, with the value it's given, the other projects by id. All the statuses are
// suggested.
function suggestProjects(url) {
  if (url.includes("/statuses")) {
    return Promise.resolve(STATUSES);
  }
  const parameters = new URL(url, "http://localhost").searchParams;
  const search = parameters.get("search").toLowerCase();
  const selectedItem = parameters.get("selectedItem");
  const selected = PROJECTS.find((project) => selectedItem
    && [project.value, project.queryValue].includes(selectedItem));
  const found = PROJECTS.filter((project) => project !== selected && (project.label.toLowerCase().includes(search)
    || project.queryValue.includes(search)));
  return Promise.resolve(selected ? [{ ...selected, value: selectedItem }, ...found] : found);
}

// Loads the labels of the values of the field, as the suggest widget of XWiki does once the field is displayed: each
// value is looked up on its own, the options found being added to the field, or updating it.
async function loadSelectedValues(selectize) {
  for (const value of selectize.items.slice()) {
    await new Promise((resolve) => selectize.settings.loadSelected(value, (options) => {
      options.forEach((option) => {
        if (Object.hasOwn(selectize.options, option.value)) {
          selectize.updateOption(option.value, option);
        } else {
          selectize.addOption(option);
        }
      });
      resolve();
    }));
  }
}

function getValueField(property) {
  const row = [...document.querySelectorAll(".proj-manag-constraints .proj-manag-constraint")]
    .find((element) => element.querySelector(".proj-manag-constraint-name").value === property);
  return row.querySelector("input.proj-manag-constraint-value").selectize;
}

// Types the given text in the field searching the values of a filter, as a user, and waits for the suggestions.
async function type(selectize, text) {
  const input = selectize.control_input;
  input.value += text;
  input.dispatchEvent(new Event("input"));
  await vi.runAllTimersAsync();
}

// The labels of the suggestions displayed by the field, sorted (the field orders them by relevance), without the one
// adding the typed text as it is.
function getSuggestions(selectize) {
  return Array.from(selectize.dropdown_content.querySelectorAll(".option:not(.create)"))
    .map((option) => option.textContent).sort();
}

describe("the suggestions of the values of a list filter", () => {
  let selectize;

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.spyOn($, "getJSON").mockImplementation(suggestProjects);
    const builder = await displayFilterBuilder([PROJECT]);
    builder.addFilter({ property: "project" }, true);
    selectize = document.querySelector("input.proj-manag-constraint-value").selectize;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it("include the values found by the text written for them in a query", async () => {
    await type(selectize, "qa15");

    expect(getSuggestions(selectize)).toEqual(["QA 150 T1", "QA 150 T2 bis"]);
  });

  it("include the values found by a part of that text", async () => {
    await type(selectize, "t2b");

    expect(getSuggestions(selectize)).toEqual(["QA 150 T2 bis"]);
  });

  it("include the values found by their label", async () => {
    await type(selectize, "QA 1");

    expect(getSuggestions(selectize)).toEqual(["QA 150 T1", "QA 150 T2 bis"]);
  });
});

// A value can be written in a query by the text written for it (the queryValue of its suggestion, e.g. the identifier
// of a project, the name of a status) instead of its id, e.g. by hand: it's then read so from the query.
describe("the suggestions of the values read from a query by the text written for them", () => {
  let builder;

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.spyOn($, "getJSON").mockImplementation(suggestProjects);
    builder = await displayFilterBuilder([PROJECT, STATUS]);
    builder.addFilter({ property: "project", constraints: [{ operator: "=", value: "qa150-t1" },
      { operator: "=", value: "demo-project" }] });
    // Written by hand, in another case than the name of the status, and a name written between quotes in the query,
    // since unquoted it would be read as a keyword (the closed statuses).
    builder.addFilter({ property: "status", constraints: [{ operator: "=", value: "new" },
      { operator: "=", value: "Closed" }] });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it("don't include these values, before their labels are loaded", async () => {
    await type(getValueField("project"), "qa15");
    await type(getValueField("status"), "New");

    expect(getSuggestions(getValueField("project"))).toEqual(["QA 150 T2 bis"]);
    expect(getSuggestions(getValueField("status"))).toEqual([]);
  });

  it("don't include these values once their labels are loaded", async () => {
    const project = getValueField("project");
    await loadSelectedValues(project);
    await loadSelectedValues(getValueField("status"));

    // A single option per value, whose label is loaded.
    expect(Object.keys(project.options).sort()).toEqual(["10", "demo-project", "qa150-t1"]);
    expect(project.options["qa150-t1"].label).toBe("QA 150 T1");
    expect(getValueField("status").options["new"].label).toBe("New");
    await type(project, "qa15");
    expect(getSuggestions(project)).toEqual(["QA 150 T2 bis"]);
    await type(getValueField("status"), "o");
    expect(getSuggestions(getValueField("status"))).toEqual(["On hold"]);
  });

  it("can't add these values a second time", async () => {
    const project = getValueField("project");
    await type(project, "qa15");
    project.dropdown_content.querySelectorAll(".option:not(.create)").forEach((option) => {
      option.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(builder.getConstraints().project.constraints.map((constraint) => constraint.value))
      .toEqual(["qa150-t1", "demo-project", "10"]);
  });
});
