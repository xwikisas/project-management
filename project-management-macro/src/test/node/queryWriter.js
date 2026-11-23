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
// Loads the query writer served to the browsers (an AMD module without dependency), and the cases of the round trip
// shared with the Java tests.
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { loadAmdModule } from "./amdModule.js";

export const ROUND_TRIP_CASES = resolve(import.meta.dirname, "../resources/queryWriter/roundTripCases.json");

export function loadQueryWriter() {
  return loadAmdModule("macro/queryWriter.js");
}

export function readRoundTripCases() {
  return JSON.parse(readFileSync(ROUND_TRIP_CASES, "utf-8"));
}

export function writeRoundTripCases(cases) {
  writeFileSync(ROUND_TRIP_CASES, JSON.stringify(cases, null, 2) + "\n");
}

/**
 * Reads the filters parsed by the REST resource as the macro editor does, the constraints of a property being grouped
 * into one filter as the filter builder does.
 */
export function readParsedFilters(queryWriter, parsedFilters) {
  const names = new Map();
  const filters = new Map();
  queryWriter.fromParsedFilters(parsedFilters, names).forEach((filter) => {
    const grouped = filters.get(filter.property) || { property: filter.property, constraints: [] };
    grouped.constraints.push(...filter.constraints);
    filters.set(filter.property, grouped);
  });
  return { filters: Array.from(filters.values()), names };
}

export function toNames(namesObject) {
  return new Map(Object.entries(namesObject || {}).map(([property, values]) => [property, new Set(values)]));
}
