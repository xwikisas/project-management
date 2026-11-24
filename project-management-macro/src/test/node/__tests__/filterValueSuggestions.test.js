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

// The tool finds the projects whose name or identifier contains the searched text.
function suggestProjects(url) {
  const search = new URL(url, "http://localhost").searchParams.get("search").toLowerCase();
  return Promise.resolve(PROJECTS.filter((project) => project.label.toLowerCase().includes(search)
    || project.queryValue.includes(search)));
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
