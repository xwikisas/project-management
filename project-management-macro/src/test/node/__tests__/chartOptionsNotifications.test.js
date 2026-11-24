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
import { describe, expect, it, vi } from "vitest";
import { displayChartOptions, selectChartType } from "../chartOptionsPage.js";

const LINE_OPTIONS = [{ id: "period", label: "Date Grouping", values: ["DAILY", "WEEKLY"] }];

function getShown(notifications) {
  return notifications.filter((notification) => notification.shown)
    .map((notification) => ({ type: notification.type, text: notification.text }));
}

describe("the chart options", () => {
  it("are displayed without notification", () => {
    const notifications = displayChartOptions({ line: LINE_OPTIONS, bar: [] });

    selectChartType("bar");
    selectChartType("line");

    // Displaying the options of the chosen chart type is an internal refresh of the editor.
    expect(notifications).toEqual([]);
    expect(document.querySelectorAll(".proj-manag-chart-type-param input[name=period]").length).toBe(1);
  });

  it("tell when they can't be loaded", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});

    const notifications = displayChartOptions({});

    expect(getShown(notifications)).toEqual([
      { type: "error", text: "failed" },
    ]);
    // The value of the parameter can still be edited, as text.
    expect(document.getElementById("proj-manag-chart-type-params").classList.contains("hidden")).toBe(false);
  });
});
