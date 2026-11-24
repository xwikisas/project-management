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
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const SOURCE = resolve(import.meta.dirname, "../../../main/resources/openproject/js/versionSuggestions.js");

const VERSIONS_URL = "/xwiki/rest/wikis/xwiki/openproject/instance/QA/suggest/versions?search={encodedQuery}";

// Loads the module served to the browsers (a named AMD module without dependency).
function loadVersionSuggestions() {
  let module;
  const define = (name, dependencies, factory) => {
    module = factory();
  };
  new Function("define", readFileSync(SOURCE, "utf-8"))(define);
  return module;
}

const versionSuggestions = loadVersionSuggestions();

describe("isVersionSearch", () => {
  it("tells the filters suggesting versions", () => {
    expect(versionSuggestions.isVersionSearch(VERSIONS_URL)).toBe(true);
    expect(versionSuggestions.isVersionSearch(VERSIONS_URL.replace("versions", "projects"))).toBe(false);
    expect(versionSuggestions.isVersionSearch(undefined)).toBe(false);
  });
});

describe("getSearchURL", () => {
  it("suggests the versions of the projects that the filters keep", () => {
    const projectFilter = {
      property: "project",
      constraints: [
        { operator: "=", value: "7" },
        { operator: "=", value: "qa150-t1" },
        // The excluded projects don't restrict the versions.
        { operator: "!", value: "8" },
        { operator: "=", value: "" },
      ],
    };

    expect(versionSuggestions.getSearchURL(VERSIONS_URL, projectFilter))
      .toBe(VERSIONS_URL + "&projects=7&projects=qa150-t1");
  });

  it("encodes the projects", () => {
    expect(versionSuggestions.getSearchURL(VERSIONS_URL, { constraints: [{ operator: "=", value: "a&b" }] }))
      .toBe(VERSIONS_URL + "&projects=a%26b");
  });

  it("suggests all the versions without filter on the projects", () => {
    expect(versionSuggestions.getSearchURL(VERSIONS_URL, undefined)).toBe(VERSIONS_URL);
    expect(versionSuggestions.getSearchURL(VERSIONS_URL, { constraints: [{ operator: "*", value: "" }] }))
      .toBe(VERSIONS_URL);
  });
});
