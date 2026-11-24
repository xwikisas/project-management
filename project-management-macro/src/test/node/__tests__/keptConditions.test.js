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
// The conditions of a query on a property that the filter builder doesn't have by this name: another name of one of
// its properties (e.g. subject, the OpenProject filter of the summary), or a name that it doesn't know at all.
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { $, displayFilterBuilder } from "../filterBuilderPage.js";

const PROJECT = {
  id: "project",
  name: "Project",
  filter: { id: "list", operators: [{ id: "=", name: "is one of" }] },
};

// The summary, which a query can also name after its OpenProject filter.
const SUMMARY = {
  id: "summary",
  name: "Subject",
  filter: { id: "text", operators: [{ id: "~", name: "Contains" }, { id: "!~", name: "Does not contain" }],
    aliases: ["subject"] },
};

// A custom field of the chosen instance, which a query can also name after its OpenProject filter.
const LABELS = {
  id: "Labels",
  name: "Labels",
  filter: { id: "list", operators: [{ id: "=", name: "is one of" }], aliases: ["customField5"] },
};

const L10N = {
  "list.=": "is one of",
  "text.~": "contains",
  "text.*": "is set",
  and: "and",
  or: "or",
  summary: "Selects the work items where {0}.",
  summaryAll: "Selects all the work items.",
  summaryKept: "The conditions that the filters can't show are kept as written.",
  setConditionUnsupported: "Filtering on whether {0} is set isn't supported: change or remove this filter.",
  keptCondition: "The filters can't show this condition, which is kept as written: {0}",
};

// The rows of the builder: the name of their property and their operator, or the condition kept as written.
function getRows() {
  return [...document.querySelectorAll(".proj-manag-constraints > div")].map((row) => {
    const title = row.querySelector(".proj-manag-constraint-title").textContent;
    const operator = row.querySelector("select.proj-manag-constraint-operator");
    const message = row.querySelector(".proj-manag-constraint-unsupported, .proj-manag-constraint-kept-message");
    return [title, operator ? $(operator).find("option:selected").text() : null, message && message.textContent]
      .filter((part) => part !== null).join(" | ");
  });
}

function getSummary() {
  return document.querySelector(".proj-manag-filter-summary").textContent;
}

const PROJECT_FILTER = { property: "project", constraints: [{ operator: "=", value: "3" }] };

describe("a condition of a query on a property that the builder doesn't have by this name", () => {
  let builder;

  beforeEach(async () => {
    builder = await displayFilterBuilder([PROJECT, SUMMARY], L10N);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("is shown on the row of the property having this other name", () => {
    builder.addFilter(PROJECT_FILTER);
    builder.addFilter({ property: "subject", constraints: [{ operator: "~", value: "abc" }] });

    expect(getRows()).toEqual(["Project | is one of", "Subject | contains"]);
    expect(getSummary()).toBe('Selects the work items where Project is one of 3, and Subject contains "abc".');
    // Written back under the name it was given.
    expect(builder.getConstraints()).toEqual({
      project: PROJECT_FILTER,
      subject: { property: "subject", constraints: [{ operator: "~", value: "abc" }] },
    });
  });

  it("is shown on that row once the properties having the other names are loaded, unchanged", async () => {
    // The macro editor reads the query with the properties of no instance, without the other names, which come with
    // the properties of the chosen instance.
    let loadProperties;
    vi.spyOn($, "getJSON").mockImplementation(() => new Promise((resolve) => {
      loadProperties = resolve;
    }));
    const withoutAliases = { ...SUMMARY, filter: { ...SUMMARY.filter, aliases: undefined } };
    builder = await displayFilterBuilder([PROJECT, withoutAliases], L10N);
    builder.addFilter({ property: "subject", constraints: [{ operator: "~", value: "abc" }] });
    const read = builder.getConstraints();
    expect(getRows())
      .toEqual(["subject | The filters can't show this condition, which is kept as written: subject ~ abc"]);

    const loaded = builder.setSourceParameters({ instance: "QA" }, false);
    loadProperties([PROJECT, SUMMARY]);
    await loaded;

    expect(getRows()).toEqual(["Subject | contains"]);
    // The filters given aren't changed: the query isn't rewritten until the user edits the filters.
    expect(builder.getConstraints()).toEqual(read);
  });

  it("is flagged on that row when the property can't be filtered so", () => {
    builder.addFilter(PROJECT_FILTER);
    builder.addFilter({ property: "subject", constraints: [{ operator: "*", value: "" }] });

    expect(getRows()).toEqual(["Project | is one of",
      "Subject | is set | Filtering on whether Subject is set isn't supported: change or remove this filter."]);
  });

  it("is listed as written, and kept, when the property is unknown", () => {
    builder.addFilter(PROJECT_FILTER);
    builder.addFilter({ property: "foo", constraints: [{ operator: "=", value: "bar" }] });

    expect(getRows()).toEqual(["Project | is one of",
      "foo | The filters can't show this condition, which is kept as written: foo = bar"]);
    // The summary doesn't claim to describe all the selected work items.
    expect(getSummary()).toBe("Selects the work items where Project is one of 3. The conditions that the filters "
      + "can't show are kept as written.");
    // In the order of the rows, so that an edited query keeps them in place.
    expect(Object.values(builder.getConstraints())).toEqual([PROJECT_FILTER,
      { property: "foo", constraints: [{ operator: "=", value: "bar" }] }]);
  });

  it("is the only thing the summary tells about when it's the only condition", () => {
    builder.addFilter({ property: "foo", constraints: [{ operator: "=", value: "bar" }] });

    expect(getSummary()).toBe("The conditions that the filters can't show are kept as written.");
  });

  it("is removed with its row", () => {
    builder.addFilter(PROJECT_FILTER);
    builder.addFilter({ property: "foo", constraints: [{ operator: "=", value: "bar" }] });
    const updates = [];
    builder.element.on("constraintsUpdated", (event, constraints) => updates.push(Object.keys(constraints)));

    document.querySelector(".proj-manag-constraint-kept .proj-manag-delete-filter").click();

    expect(getRows()).toEqual(["Project | is one of"]);
    expect(updates).toEqual([["project"]]);
  });

  it("waits for the properties being loaded, which can have it", async () => {
    let loadProperties;
    vi.spyOn($, "getJSON").mockImplementation(() => new Promise((resolve) => {
      loadProperties = resolve;
    }));
    const loaded = builder.setSourceParameters({ instance: "QA" }, false);
    builder.addFilter({ property: "customField5", constraints: [{ operator: "=", value: "12" }] });
    builder.addFilter({ property: "foo", constraints: [{ operator: "=", value: "bar" }] });

    expect(getRows()).toEqual([]);

    loadProperties([PROJECT, SUMMARY, LABELS]);
    await loaded;

    expect(getRows()).toEqual(["Labels | is one of",
      "foo | The filters can't show this condition, which is kept as written: foo = bar"]);
  });

  it("is listed when the properties can't be loaded", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    vi.spyOn($, "getJSON").mockImplementation(() => Promise.reject(new Error("Not found")));
    const loaded = builder.setSourceParameters({ instance: "QA" }, false);
    builder.addFilter({ property: "customField5", constraints: [{ operator: "=", value: "12" }] });

    await loaded;

    expect(getRows()).toEqual([
      "customField5 | The filters can't show this condition, which is kept as written: customField5 = 12"]);
  });
});
