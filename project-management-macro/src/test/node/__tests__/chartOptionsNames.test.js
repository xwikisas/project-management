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
import { describe, expect, it } from "vitest";
import { displayChartOptions, selectChartType } from "../chartOptionsPage.js";

const LINE_OPTIONS = [
  {
    id: "metric",
    label: "Measure",
    description: "What each value measures.",
    values: ["count", "cumulative"],
    valueLabels: { count: "Count", cumulative: "Cumulative" },
  },
  { id: "period", label: "Date Grouping", values: ["DAILY", "WEEKLY"] },
];

// The accessible name of an element: the text of the elements it references with aria-labelledby, or of its labels.
function getName(element) {
  const labelledBy = element.getAttribute("aria-labelledby");
  const labels = labelledBy ? labelledBy.split(" ").map((id) => document.getElementById(id)) : [...element.labels];
  return labels.map((label) => label.textContent).join(" ");
}

function expectNamedOptions() {
  for (const [name, label] of [["metric", "Measure"], ["period", "Date Grouping"]]) {
    const field = document.querySelector(`.proj-manag-chart-type-param input[name=${name}]`);
    // The field, hidden by the picker but seen by the screen readers, and the text input of the picker.
    expect(getName(field)).toBe(label);
    expect(getName(field.selectize.control_input)).toBe(label);
  }
}

describe("the chart options", () => {
  it("are named after their label", () => {
    displayChartOptions({ line: LINE_OPTIONS }, "line", '{"metric":"cumulative"}');

    expectNamedOptions();
    expect(document.querySelector("input[name=metric]").value).toBe("cumulative");
  });

  it("keep their names when the chart type changes", () => {
    displayChartOptions({ line: LINE_OPTIONS, bar: LINE_OPTIONS }, "bar");

    selectChartType("line");

    expectNamedOptions();
    // The lists of values of the previous options are still in the page.
    const ids = [...document.querySelectorAll("[id]")].map((element) => element.id);
    expect(ids.filter((id, index) => ids.indexOf(id) !== index)).toEqual([]);
  });
});
