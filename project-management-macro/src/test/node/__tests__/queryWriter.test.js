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
    [[], ""],
  ])("writes %j as [%s]", (filters, expectedQuery) => {
    expect(queryWriter.toQuery(filters, new Map())).toBe(expectedQuery);
  });

  it("writes without names", () => {
    expect(queryWriter.toQuery([filter("project", "=", "12")])).toBe("project = 12");
  });
});
