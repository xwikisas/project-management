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
import moment from "moment";
import { $, displayFilterBuilder, waitForDatePickers } from "../filterBuilderPage.js";

const START_DATE = {
  id: "startDate",
  name: "Start Date",
  filter: {
    id: "date",
    operators: [
      { id: "t", name: "Today" },
      { id: "=d", name: "On" },
      { id: "between", name: "Between" },
    ],
  },
};

const L10N = {
  summary: "Selects the work items where {0}.",
  summaryAll: "Selects all the work items.",
  summaryIncomplete: "The filters without a value are ignored.",
  and: "and",
  "date.=d": "is on",
};

async function addDateFilter(constraints = [{ operator: "=d" }]) {
  const builder = await displayFilterBuilder([START_DATE], L10N);
  builder.addFilter({ property: "startDate", constraints });
  await waitForDatePickers();
  return builder;
}

function getDateField() {
  return $(".proj-manag-date-val");
}

function getSummary() {
  return $(".proj-manag-filter-summary").text();
}

describe("a date filter", () => {
  it("has no date until one is picked", async () => {
    const builder = await addDateFilter();

    // The date picker selects the current day, which isn't the value of the filter.
    expect(getDateField().val()).toBe("");
    expect(builder.getConstraints()).toEqual({});
    expect(getSummary()).toBe("Selects all the work items. The filters without a value are ignored.");
  });

  it("takes the date shown by the date picker once it's closed", async () => {
    const builder = await addDateFilter();

    getDateField().trigger("click");
    getDateField()[0].dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", keyCode: 13, bubbles: true }));

    const today = moment().startOf("day");
    expect(getDateField().val()).toBe(today.format("YYYY/MM/DD HH:mm"));
    expect(builder.getConstraints()).toEqual({
      startDate: { property: "startDate", constraints: [{ operator: "=d", value: today.format() }] },
    });
    expect(getSummary()).toBe(`Selects the work items where Start Date is on ${today.format("YYYY/MM/DD HH:mm")}.`);
  });

  it("stays without date when the date picker is canceled", async () => {
    const builder = await addDateFilter();

    getDateField().trigger("click");
    $(".daterangepicker .cancelBtn").trigger("click");

    expect(getDateField().val()).toBe("");
    expect(builder.getConstraints()).toEqual({});
  });

  it("displays its saved date", async () => {
    const builder = await addDateFilter([{ operator: "=d", value: "2024-01-10T10:30:00+02:00" }]);

    expect(getDateField().val()).toBe("2024/01/10 10:30");
    expect(builder.getConstraints()).toEqual({
      startDate: { property: "startDate", constraints: [{ operator: "=d", value: "2024-01-10T10:30:00+02:00" }] },
    });
  });

  // The client retrieves the work items of the day of a date without operator, e.g. in the filters source written by
  // hand, rather than with the first operator of the property (today, which takes no date).
  it("displays a saved date without operator as on that day", async () => {
    const builder = await addDateFilter([{ value: "2026-11-30" }]);

    expect($("select.proj-manag-constraint-operator").val()).toBe("=d");
    expect(builder.getConstraints()).toEqual({
      startDate: { property: "startDate", constraints: [{ operator: "=d", value: "2026-11-30" }] },
    });
  });
});
