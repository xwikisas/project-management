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
// The work package search of the WYSIWYG editor (the text typed after the marker, e.g. #157), whose script is the skin
// extension of the OpenProject.Code.CKEditorPlugin page of project-management-openproject-ui. That module has no
// JavaScript tests: the script served to the browsers is run here, with a fake CKEditor and fake REST resources.
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const PLUGIN_PAGE = resolve(import.meta.dirname,
  "../../../../../project-management-openproject-ui/src/main/resources/OpenProject/Code/CKEditorPlugin.xml");

const WORK_ITEMS = "/xwiki/rest/wikis/xwiki/projectmanagement/openproject/workitems";

const CONNECTION = "QA";

function getWorkPackage(id, key, subject) {
  return {
    identifier: { value: id },
    key,
    summary: { value: subject },
    type: "Epic",
    project: { value: "Atlas CRM" },
  };
}

const AC_1 = getWorkPackage("157", "AC-1", "Mobile offline mode");

const RELEASE = getWorkPackage("12", "AC-12", "Release 157 notes");

// The code of the skin extension of the page, as served to the browsers.
function getPluginCode() {
  const page = readFileSync(PLUGIN_PAGE, "utf-8");
  const code = page.match(/<property>\s*<code>([\s\S]*?)<\/code>\s*<\/property>/)[1];
  return code.replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&quot;", '"').replaceAll("&apos;", "'")
    .replaceAll("&amp;", "&");
}

/**
 * Runs the work package search on a CKEditor instance, in a page linked to the OpenProject connection QA, whose search
 * marker is #.
 *
 * @param workPackages the work packages of the connection
 * @return the autocomplete of the work package search, once created, with a type(text) method that types the text
 *     after the marker and returns the entries that it opens (null if it doesn't search them)
 */
function startWorkPackageSearch(workPackages) {
  // The REST resources: the work packages whose subject contains a text, and the work package of an id or key.
  const getJSON = (url, data) => {
    if (url === WORK_ITEMS && data.instance === CONNECTION) {
      const text = JSON.parse(data.filter).query.filters[0].constraints[0].value;
      return Promise.resolve({ items: workPackages.filter((workPackage) => workPackage.summary.value.includes(text)) });
    }
    if (url.startsWith(WORK_ITEMS + "/") && data.instance === CONNECTION) {
      const id = decodeURIComponent(url.substring(WORK_ITEMS.length + 1));
      const found = workPackages.find((workPackage) => workPackage.identifier.value === id
        || workPackage.key === id.toUpperCase());
      return found ? Promise.resolve(found) : Promise.reject({ status: 404 });
    }
    if (url.endsWith("/workPackageSearchMarker")) {
      return Promise.resolve({ value: "#" });
    }
    return Promise.reject({ status: 500 });
  };
  const $ = { getJSON, param: (params) => new URLSearchParams(params).toString() };
  const XWiki = {
    contextPath: "/xwiki",
    currentWiki: "xwiki",
    currentDocument: { documentReference: "Space.Page" },
    EntityType: { DOCUMENT: 0 },
    Model: { resolve: (reference) => reference, serialize: (reference) => reference },
    Document: class {
      constructor(reference) {
        this.reference = reference;
      }

      getURL(action, query) {
        return `/xwiki/bin/${action}/${this.reference}?${query}`;
      }

      getRestURL(path) {
        return `/xwiki/rest/${this.reference}/${path}`;
      }
    },
  };
  const pageRelation = { get: () => Promise.resolve({ clientParams: { instance: CONNECTION } }) };

  // The text typed in the editor, matched by the autocomplete from the marker, at the same position.
  let typed = "";
  const range = { startContainer: { $: {} }, startOffset: 0 };
  let created;
  const autoCompleteCreated = new Promise((resolve) => {
    created = resolve;
  });
  class AdvancedAutoComplete {
    constructor(editor, config) {
      this.textWatcher = { callback: () => ({ text: typed, range }) };
      this.model = { dataCallback: config.dataCallback, getItemById: () => null };
      this.view = { createItem: () => null };
      this.commit = () => {};
      this.onKeyDown = () => {};
      created(this);
    }

    // Types a text after the marker, and searches the work packages if the autocomplete matches it.
    type(text) {
      typed += text;
      const match = this.textWatcher.callback(range);
      if (!match) {
        return Promise.resolve(null);
      }
      return new Promise((resolve) => this.model.dataCallback({ query: match.text, range: match.range }, resolve));
    }
  }
  const editor = { status: "ready", on: () => {} };
  const ckeditor = {
    instances: { content: editor },
    on: () => {},
    template: function () {},
    plugins: { AdvancedAutoComplete, textMatch: { match: () => null } },
  };

  const require = (dependencies, factory) => factory(Promise.resolve(ckeditor), $, pageRelation, { get: (key) => key });
  require.config = () => {};
  new Function("require", "define", "XWiki", getPluginCode())(require, () => {}, XWiki);
  return autoCompleteCreated;
}

function getSuggestions(entries) {
  return entries.map((entry) => ({ value: entry.value, label: entry.label, content: entry.command?.data?.content }));
}

describe("the work package search of the WYSIWYG editor", () => {
  it("suggests the work package of a typed id first", async () => {
    const search = await startWorkPackageSearch([RELEASE, AC_1]);

    // Then the work packages whose subject contains the typed text.
    expect(getSuggestions(await search.type("#157"))).toEqual([
      { value: "157", label: "Mobile offline mode", content: "AC-1" },
      { value: "12", label: "Release 157 notes", content: "AC-12" },
    ]);
  });

  it("suggests the work package of a typed id once", async () => {
    const search = await startWorkPackageSearch([getWorkPackage("158", "AC-2", "Crash 158")]);

    expect(getSuggestions(await search.type("#158"))).toEqual([{ value: "158", label: "Crash 158", content: "AC-2" }]);
  });

  it("suggests the work package of a typed key, in any case", async () => {
    const search = await startWorkPackageSearch([RELEASE, AC_1]);

    expect(getSuggestions(await search.type("#ac-1"))).toEqual([
      { value: "157", label: "Mobile offline mode", content: "AC-1" },
    ]);
  });

  it("suggests the work packages whose subject contains an id that no work package has", async () => {
    const search = await startWorkPackageSearch([RELEASE]);

    expect(getSuggestions(await search.type("#157"))).toEqual([
      { value: "12", label: "Release 157 notes", content: "AC-12" },
    ]);
  });

  it("looks up an id whose beginning matches no work package", async () => {
    const search = await startWorkPackageSearch([getWorkPackage("1501", "AC-2", "Login page")]);

    expect(await search.type("#150")).toEqual([]);
    // The text that extends a query without match isn't searched, unless it can still be an id.
    expect(getSuggestions(await search.type("1"))).toEqual([{ value: "1501", label: "Login page", content: "AC-2" }]);
    expect(await search.type(" is")).toBeNull();
  });
});
