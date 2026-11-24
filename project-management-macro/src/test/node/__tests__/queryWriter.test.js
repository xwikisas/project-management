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
import { loadQueryWriter, readParsedFilters, readRoundTripCases, toNames } from "../queryWriter.js";

const queryWriter = loadQueryWriter();

function filter(property, operator, ...values) {
  return { property, constraints: values.map((value) => ({ operator, value })) };
}

/**
 * @return the constraints of each property, sorted (the builder keeps the rows of a property together)
 */
function describeFilters(filters) {
  const constraints = {};
  filters.forEach((propertyFilter) => {
    const propertyConstraints = constraints[propertyFilter.property] || [];
    propertyFilter.constraints.forEach((constraint) => {
      propertyConstraints.push(`${constraint.operator} ${constraint.value}`);
    });
    constraints[propertyFilter.property] = propertyConstraints.sort();
  });
  return constraints;
}

describe("queryWriter", () => {
  const cases = readRoundTripCases();

  /*
   * The cases are shared with QueryWriterRoundTripTest, which checks that the Java parser reads each query into its
   * parsed filters: together, the two tests check the round trip builder filters, query, REST resource, builder
   * filters.
   */
  describe.each([["table", cases.table], ["random", cases.random]])("round trip of the %s cases", (kind, kindCases) => {
    it.each(kindCases.map((roundTripCase) => [roundTripCase.query, roundTripCase]))("%s", (query, roundTripCase) => {
      expect(queryWriter.toQuery(roundTripCase.filters, toNames(roundTripCase.names))).toBe(query);
      // The parsed filters are read back into the filters of the builder, which are written as the same query.
      const read = readParsedFilters(queryWriter, roundTripCase.parsed);
      expect(describeFilters(read.filters)).toEqual(describeFilters(roundTripCase.filters));
      expect(queryWriter.toQuery(read.filters, read.names)).toBe(query);
    });
  });

  it("has random cases", () => {
    expect(cases.random.length).toBeGreaterThan(100);
  });

  // The filters that the builder doesn't write, but may read from the filters parameter.
  it.each([
    [[filter("dueDate", "t+", "3")], "dueDate = 3d"],
    [[filter("dueDate", "t-", "3")], "dueDate = -3d"],
    [[filter("dueDate", ">t+", "3")], "dueDate >= 3d"],
    [[filter("dueDate", "<t-", "3")], "dueDate <= -3d"],
    [[filter("dueDate", "<t+", "3")], "dueDate >= 0d AND dueDate <= 3d"],
    [[filter("dueDate", ">t-", "3")], "dueDate >= -3d AND dueDate <= 0d"],
    [[filter("dueDate", "after", "2026-11-01")], "dueDate >= 2026-11-01"],
    [[filter("dueDate", "before", "2026-11-01")], "dueDate <= 2026-11-01"],
    [[filter("dueDate", "=d", "2026-11-21 10:00")], "dueDate = 2026-11-21"],
    [[filter("dueDate", "between", "2026-11-21")], "dueDate >= 2026-11-21 AND dueDate <= 2026-11-21"],
    // The open, locked and closed versions (of the built-in version filter only).
    [[filter("targetVersion", "o", "")], "targetVersion = open"],
    [[filter("targetVersion", "l", "")], "targetVersion = locked"],
    [[filter("targetVersion", "c", "")], "targetVersion = closed"],
    [[filter("summary", "contains", "a b"), filter("type", "startsWith", "x")], "summary ~ \"a b\" AND type ~ x"],
    // Strict, as in the OpenProject client: greater than 5 is >= 6 there, and > 5 too.
    [[filter("progress", "greater", "5"), filter("estimatedTime", "less", "9")], "progress > 5 AND estimatedTime < 9"],
    [[filter("progress", ">", "5"), filter("progress", "<", "9")], "progress > 5 AND progress < 9"],
    [[filter("type", "empty", ""), filter("project", "equals", "1")], "type IS EMPTY AND project = 1"],
    [[{ property: "type" }], ""],
    // A filter without value, e.g. a row of the builder whose value isn't chosen yet, isn't written.
    [[filter("status", "=", ""), filter("assignees", "!", "")], ""],
    [[filter("status", "=", ""), filter("project", "=", "7"), filter("dueDate", "t", "")],
      "project = 7 AND dueDate = today()"],
    [[], ""],
  ])("writes %j as [%s]", (filters, expectedQuery) => {
    expect(queryWriter.toQuery(filters, new Map())).toBe(expectedQuery);
  });

  it("writes without names", () => {
    expect(queryWriter.toQuery([filter("project", "=", "12")])).toBe("project = 12");
  });

  it("writes the names of the values instead of their ids", () => {
    const labels = { project: { 5: "Atlas mobile app" }, status: { 1: "New", 7: "2024" }, assignees: { me: "Me" } };
    const getName = (property, value) => labels[property] && labels[property][value];
    const names = new Map();
    const filters = queryWriter.withValueNames([filter("project", "=", "5"), filter("status", "=", "1", "7", "9"),
      filter("assignees", "=", "me"), filter("summary", "~", "5")], getName, names);
    // A name made of digits is quoted, so that it isn't read as an id; a value without name is kept.
    expect(queryWriter.toQuery(filters, names))
      .toBe('project = "Atlas mobile app" AND status IN (New, "2024", 9) AND assignees = me AND summary ~ 5');
  });
});

/*
 * The filters of the filters parameter written by hand or by an older version use the generic operators of the Live
 * Data, that the builder shows with the operators of the properties (the ones of the OpenProject client here), and
 * writes in the query with the same meaning, as the client reads them.
 */
describe("toBuilderFilter", () => {
  const TEXT = ["~", "!~"];
  const TEXT_ALL = ["~", "!~", "=", "!", "*", "!*"];
  const LIST = ["=", "!", "*", "!*"];
  const NUMBER = ["=", "!", ">=", "<=", "*", "!*"];
  const DATE = ["t", "w", "=d", "between", "!*"];
  const BOOLEAN = ["=", "!"];

  it.each([
    ["text", TEXT, "summary", "contains", "task", "~", "task", "summary ~ task"],
    ["text", TEXT, "summary", "startsWith", "task", "~", "task", "summary ~ task"],
    // The subject can't be equal to a text in OpenProject, only contain it.
    ["text", TEXT, "summary", "equals", "task", "~", "task", "summary ~ task"],
    ["text", TEXT_ALL, "description", "equals", "task", "=", "task", "description = task"],
    ["text", TEXT_ALL, "description", "empty", "", "!*", "", "description IS EMPTY"],
    ["list", LIST, "status", "equals", "12", "=", "12", "status = 12"],
    ["list", LIST, "status", "contains", "12", "=", "12", "status = 12"],
    ["list", LIST, "status", "startsWith", "12", "=", "12", "status = 12"],
    ["list", LIST, "assignees", "empty", "", "!*", "", "assignees IS EMPTY"],
    ["list", LIST, "status", "notEquals", "12", "!", "12", "status != 12"],
    ["boolean", BOOLEAN, "flagged", "equals", "true", "=", "true", "flagged = true"],
    ["boolean", BOOLEAN, "flagged", "notEquals", "true", "!", "true", "flagged != true"],
    ["number", NUMBER, "progress", "equals", "50", "=", "50", "progress = 50"],
    ["number", NUMBER, "progress", "notEquals", "50", "!", "50", "progress != 50"],
    ["text", TEXT_ALL, "description", "notEquals", "task", "!", "task", "description != task"],
    // Strict, as in the OpenProject client: greater than 5 is at least 6.
    ["number", NUMBER, "progress", "greater", "5", ">=", "6", "progress >= 6"],
    ["number", NUMBER, "progress", ">", "5", ">=", "6", "progress >= 6"],
    ["number", NUMBER, "progress", "less", "9", "<=", "8", "progress <= 8"],
    ["number", NUMBER, "progress", "<", "9", "<=", "8", "progress <= 8"],
    ["number", NUMBER, "estimatedTime", "greater", "2.5", ">=", "2.5", "estimatedTime >= 2.5"],
    ["number", NUMBER, "progress", "empty", "", "!*", "", "progress IS EMPTY"],
    ["date", DATE, "dueDate", "after", "2026-11-01", "between", "2026-11-01/3000-01-01", "dueDate >= 2026-11-01"],
    ["date", DATE, "dueDate", "before", "2026-11-01", "between", "1970-01-01/2026-11-01", "dueDate <= 2026-11-01"],
    ["date", DATE, "dueDate", "contains", "2026-11-21", "=d", "2026-11-21", "dueDate = 2026-11-21"],
    ["date", DATE, "dueDate", "contains", "2026-11-01/2026-11-30", "between", "2026-11-01/2026-11-30",
      "dueDate >= 2026-11-01 AND dueDate <= 2026-11-30"],
    ["date", DATE, "dueDate", "equals", "2026-11-21", "=d", "2026-11-21", "dueDate = 2026-11-21"],
    ["date", DATE, "dueDate", "empty", "", "!*", "", "dueDate IS EMPTY"],
    // The operators of the property are kept.
    ["text", TEXT, "summary", "!~", "task", "!~", "task", "summary !~ task"],
    ["date", DATE, "dueDate", "between", "2026-11-01/2026-11-30", "between", "2026-11-01/2026-11-30",
      "dueDate >= 2026-11-01 AND dueDate <= 2026-11-30"],
    // Without equivalent for the property, the operator is kept, and written as it is.
    ["date", ["t", "w", "=d", "between"], "startDate", "empty", "", "empty", "", "startDate IS EMPTY"],
    ["text", TEXT, "summary", "empty", "", "empty", "", "summary IS EMPTY"],
    ["date", DATE, "dueDate", ">t-", "3", ">t-", "3", "dueDate >= -3d AND dueDate <= 0d"],
  ])("reads a %s filter %j on %s: %s %s as %s %s", (type, operators, property, operator, value, builderOperator,
    builderValue, query) => {
    const builderFilter = queryWriter.toBuilderFilter(filter(property, operator, value), type, operators);
    expect(builderFilter).toEqual(filter(property, builderOperator, builderValue));
    expect(queryWriter.toQuery([builderFilter], new Map())).toBe(query);
  });

  it("keeps the constraints without operator: the builder chooses the operator of their row", () => {
    const withoutOperator = { property: "summary", constraints: [{ operator: null, value: "task" }] };
    expect(queryWriter.toBuilderFilter(withoutOperator, "text", TEXT)).toEqual(withoutOperator);
  });

  it("converts each constraint of a filter", () => {
    const statusFilter = { property: "status", constraints: [{ operator: "equals", value: "1" },
      { operator: "!", value: "2" }, { operator: "contains", value: "3" }] };
    expect(queryWriter.toQuery([queryWriter.toBuilderFilter(statusFilter, "list", LIST)], new Map()))
      .toBe("status IN (1, 3) AND status != 2");
  });

  it("keeps the meaning of the filters parameter of 1.4.1 once the filters are edited", () => {
    const legacy = JSON.parse('{"query":{"filters":[{"property":"project","constraints":[{"operator":"=","value":'
      + '"9"}]},{"property":"summary","constraints":[{"operator":"contains","value":"task"}]}]}}');
    const types = { project: ["list", LIST], summary: ["text", TEXT] };
    const builderFilters = legacy.query.filters.map((legacyFilter) =>
      queryWriter.toBuilderFilter(legacyFilter, ...types[legacyFilter.property]));
    // The user adds a filter on the status.
    builderFilters.push(filter("status", "o", ""));
    expect(queryWriter.toQuery(builderFilters, new Map())).toBe("project = 9 AND summary ~ task AND status = open");
  });
});

/*
 * The builder drops the conditions of the filters parameter whose generic operator has no equivalent for their
 * property, which the OpenProject client ignores, instead of writing them in the query, which OpenProject would reject.
 */
describe("splitSupported", () => {
  const TEXT = ["~", "!~"];
  const LIST = ["=", "!", "o", "c", "*"];

  it.each([
    // OpenProject can't filter the description on being empty, nor the subject on not being equal to a text.
    ["text", TEXT, filter("description", "empty", ""), [], [{ operator: "empty", value: "" }]],
    ["text", TEXT, filter("summary", "notEquals", "task"), [], [{ operator: "notEquals", value: "task" }]],
    ["text", TEXT, filter("summary", "contains", "task"), [{ operator: "~", value: "task" }], []],
    ["list", LIST, filter("status", "notEquals", "1"), [{ operator: "!", value: "1" }], []],
    ["list", LIST, filter("status", "empty", ""), [], [{ operator: "empty", value: "" }]],
    // An operator of the tool that the builder doesn't propose is kept.
    ["date", ["t", "w", "=d", "between"], filter("startDate", ">t-", "3"), [{ operator: ">t-", value: "3" }], []],
  ])("splits a %s filter %j: %j", (type, operators, legacyFilter, supported, unsupported) => {
    expect(queryWriter.splitSupported(legacyFilter, type, operators)).toEqual({
      filter: { property: legacyFilter.property, constraints: supported },
      unsupported,
    });
  });

  it("splits each constraint of a filter, and keeps the ones without operator", () => {
    const summaryFilter = { property: "summary", constraints: [{ operator: "contains", value: "a" },
      { operator: "empty", value: "" }, { value: "b" }] };
    expect(queryWriter.splitSupported(summaryFilter, "text", TEXT)).toEqual({
      filter: { property: "summary", constraints: [{ operator: "~", value: "a" }, { value: "b" }] },
      unsupported: [{ operator: "empty", value: "" }],
    });
  });

  it("writes a query that OpenProject accepts once the filters parameter of 1.4.1 is edited", () => {
    const legacy = JSON.parse('{"query":{"filters":[{"property":"project","constraints":[{"operator":"=","value":'
      + '"9"}]},{"property":"description","constraints":[{"operator":"empty","value":""}]},{"property":"status",'
      + '"constraints":[{"operator":"notEquals","value":"1"}]}]}}');
    const types = { project: ["list", LIST], description: ["text", TEXT], status: ["list", LIST] };
    const splits = legacy.query.filters.map((legacyFilter) =>
      queryWriter.splitSupported(legacyFilter, ...types[legacyFilter.property]));
    const builderFilters = splits.map((split) => split.filter);
    // The user adds a filter on the assignees.
    builderFilters.push(filter("assignees", "=", "me"));
    expect(queryWriter.toQuery(builderFilters, new Map())).toBe("project = 9 AND status != 1 AND assignees = me");
    expect(splits.flatMap((split) => split.unsupported)).toEqual([{ operator: "empty", value: "" }]);
  });
});
