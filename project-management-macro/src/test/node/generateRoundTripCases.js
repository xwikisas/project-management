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
// Regenerates the random cases of src/test/resources/queryWriter/roundTripCases.json: random filters of the filter
// builder (fixed seed) and the query that the query writer writes for them. Their "parsed" filters are then filled by
// the Java test, run with -DupdateRoundTripCases=true (see QueryWriterRoundTripTest).
import { loadQueryWriter, readRoundTripCases, writeRoundTripCases } from "./queryWriter.js";

const CASE_COUNT = 400;

// Mulberry32: a small seeded generator, so that the cases are the same on each run.
function random(seed) {
  let state = seed;
  return () => {
    state = (state + 0x6d2b79f5) | 0;
    let t = Math.imul(state ^ (state >>> 15), 1 | state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const next = random(337);
const nextInt = (bound) => Math.floor(next() * bound);
const pick = (...values) => values[nextInt(values.length)];

const PIECES = ["a", "Z", "é", "日本", "1", "07", " ", " ", "\t", "\n", "\u001C", "\"", "\\", "(", ")", ",", "=",
  "!", "~", "<", ">", "and", "OR", "in", "all", "is", "Not", "empty", "order", "open", "Closed", "locked", "me", "ME",
  "2026-11-21", "3d", "+3d", "-2W", "today()", "-", "."];

function randomValue() {
  let value = "";
  const length = 1 + nextInt(3);
  for (let i = 0; i < length; i++) {
    value += PIECES[nextInt(PIECES.length)];
  }
  return value;
}

function filter(property, operator, ...values) {
  return { property, constraints: values.map((value) => ({ operator, value })) };
}

function listFilter(property, names) {
  const values = [];
  const count = 1 + nextInt(3);
  for (let i = 0; i < count; i++) {
    const value = randomValue();
    // The keywords written without quotes are read as keywords: me is the current user (in lower case), open and
    // closed are all the open or closed statuses.
    const keyword = (value.toLowerCase() === "me" && value !== "me")
      || (property === "status" && ["open", "closed"].includes(value.toLowerCase()));
    if (next() < 0.5 || keyword) {
      // A name, e.g. read from a query.
      names[property] = names[property] || [];
      names[property].push(value);
    }
    values.push(value);
  }
  return filter(property, pick("=", "!", "&="), ...values);
}

function dateFilter(property) {
  const year = nextInt(50);
  const day = `-${String(1 + nextInt(12)).padStart(2, "0")}-${String(1 + nextInt(28)).padStart(2, "0")}`;
  const lower = `20${String(year).padStart(2, "0")}${day}`;
  const upper = `20${String(year + nextInt(3)).padStart(2, "0")}${day}`;
  switch (nextInt(6)) {
    case 0:
      return [filter(property, "=d", lower)];
    case 1:
      return [filter(property, "between", `${lower}/${upper}`)];
    case 2:
      return [filter(property, "after", lower)];
    case 3:
      return [filter(property, "before", upper)];
    default:
      return [filter(property, pick("t", "w", "!*"), "")];
  }
}

function randomFilters(property, names) {
  // The status can also be filtered on the open or closed statuses.
  const kind = property === "status" ? nextInt(2) * 4 : nextInt(4);
  switch (kind) {
    case 0:
      return [listFilter(property, names)];
    case 1:
      return [filter(property, pick("~", "!~"), randomValue())];
    case 2:
      if (next() < 0.5) {
        return [filter(property, ">=", randomValue()), filter(property, "<=", randomValue())];
      } else if (next() < 0.5) {
        return [filter(property, pick("*", "!*"), "")];
      } else if (next() < 0.5) {
        return [filter(property, pick("=", "!"), String(nextInt(100)))];
      }
      return [filter(property, pick(">=", "<="), randomValue())];
    case 3:
      return dateFilter(property);
    default:
      return [filter(property, pick("o", "c", "*"), "")];
  }
}

const queryWriter = loadQueryWriter();
const cases = readRoundTripCases();
cases.random = [];
for (let i = 0; i < CASE_COUNT; i++) {
  const properties = ["status", "project", "Labels", "summary", "progress", "dueDate", "My field", "a\"b", "AND",
    "12", "Catégorie", "x(y)"];
  const names = {};
  const filters = [];
  const count = 1 + nextInt(4);
  for (let j = 0; j < count; j++) {
    const property = properties.splice(nextInt(properties.length), 1)[0];
    filters.push(...randomFilters(property, names));
  }
  const nameSets = new Map(Object.entries(names).map(([property, values]) => [property, new Set(values)]));
  cases.random.push({ filters, names, query: queryWriter.toQuery(filters, nameSets), parsed: null });
}
writeRoundTripCases(cases);
