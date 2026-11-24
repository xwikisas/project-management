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
import { $, displayChartSeries, getDisplayedSeries } from "../filterBuilderPage.js";

// The properties of OpenProject that matter here: its description can't be filtered on being empty.
const PROPERTIES = [
  {
    id: "project",
    name: "Project",
    filter: {
      id: "list",
      operators: [{ id: "=", name: "is one of" }, { id: "!", name: "is not one of" }],
      options: [{ value: "9", label: "QA 150 T3" }],
    },
  },
  {
    id: "description",
    name: "Description",
    filter: { id: "text", operators: [{ id: "~", name: "contains" }, { id: "!~", name: "doesn't contain" }] },
  },
  {
    id: "status",
    name: "Status",
    filter: { id: "list", operators: [{ id: "=", name: "is one of" }], options: [{ value: "1", label: "New" }] },
  },
];

const PROJECT = { property: "project", constraints: [{ operator: "=", value: "9" }] };
const STATUS = { property: "status", constraints: [{ operator: "=", value: "1" }] };

// Two series written by hand with 1.4.1, whose first one filters on the description being empty, which OpenProject
// ignores.
const SERIES = JSON.stringify([
  { query: { filters: [PROJECT, { property: "description", constraints: [{ operator: "empty", value: "" }] }] } },
  { query: { filters: [PROJECT] } },
], null, 1);

// The conditions reported as dropped by the series, each time.
let reports;

function getParameterValue() {
  return $(".proj-manag-filter").val();
}

describe("the series of a chart", () => {
  beforeEach(async () => {
    await displayChartSeries(PROPERTIES, SERIES);
    reports = [];
    $("[data-builder-role=series]").on("pm:unsupportedFilters", (event, conditions) => reports.push(conditions));
  });

  it("don't show the conditions that they can't show", () => {
    expect(getDisplayedSeries()).toEqual([[PROJECT], [PROJECT]]);
  });

  it("are kept as they are until they're edited", () => {
    expect(getParameterValue()).toBe(SERIES);
    expect(reports).toEqual([]);
  });

  it("drop the conditions that they can't show once they're edited, and report them", () => {
    const [first, second] = window.FilterBuilder.instances.values();
    second.addFilter(STATUS);

    expect(JSON.parse(getParameterValue())).toEqual([
      { query: { filters: [PROJECT] } },
      { query: { filters: [PROJECT, STATUS] } },
    ]);
    expect(reports).toEqual([["Description empty"]]);

    first.addFilter(STATUS);
    expect(JSON.parse(getParameterValue())[0]).toEqual({ query: { filters: [PROJECT, STATUS] } });
    // Reported once.
    expect(reports).toEqual([["Description empty"]]);
  });

  it("drop the conditions that they can't show once a series is removed", () => {
    const second = [...window.FilterBuilder.instances.values()][1];
    // As the remove link of the series, which the builders of the test don't have, does.
    second.element.trigger("builderRemoved");
    second.element.remove();

    expect(JSON.parse(getParameterValue())).toEqual({ query: { filters: [PROJECT] } });
    expect(reports).toEqual([["Description empty"]]);
  });
});
