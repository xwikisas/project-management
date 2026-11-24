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

function getMeasureOptions(workItems) {
  return [{ id: "metric", label: "Measure", description: `Default: the number of ${workItems}.`, values: ["count"] }];
}

// The options of each chart type, the generic ones and the ones worded by OpenProject.
const CHART_OPTIONS = {
  line: getMeasureOptions("work items"),
  "line?client=openproject": getMeasureOptions("work packages"),
  bar: getMeasureOptions("work items"),
  "bar?client=openproject": getMeasureOptions("work packages"),
};

function getMeasureDescription() {
  return document.querySelector(".proj-manag-chart-type-param-description").textContent;
}

describe("the chart options", () => {
  it("are worded by the client of the macro", () => {
    displayChartOptions(CHART_OPTIONS, "line", "", "openproject");

    expect(getMeasureDescription()).toBe("Default: the number of work packages.");

    selectChartType("bar");

    expect(getMeasureDescription()).toBe("Default: the number of work packages.");
  });

  it("have the generic wording without client", () => {
    displayChartOptions(CHART_OPTIONS);

    expect(getMeasureDescription()).toBe("Default: the number of work items.");
  });
});
