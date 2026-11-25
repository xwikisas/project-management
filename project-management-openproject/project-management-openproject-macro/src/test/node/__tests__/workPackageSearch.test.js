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
import { afterEach, describe, expect, it, vi } from "vitest";

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

const NOT_AUTHORIZED = '<div class="box warningmessage">You need to be authenticated to OpenProject.</div>';

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
 * @param access the access of the user to the connection, changed by the test: whether they can read its data
 *     (authorized), whether the searches fail for another reason (failing), and the number of times the message telling
 *     why they can't read it was requested (checks)
 * @return the autocomplete of the work package search, once created, with a type(text) method that types the text
 *     after the marker and returns the entries that it opens (null if it doesn't search them), and a newMarker() method
 *     that starts a new search, after a marker typed elsewhere
 */
function startWorkPackageSearch(workPackages, access = { authorized: true, failing: false, checks: 0 }) {
  // The REST resources: the work packages whose subject contains a text, the work package of an id or key, and the
  // message telling the user why they can't read the data of the connection (rendered by the plugin page).
  const getJSON = (url, data) => {
    if (url.startsWith(WORK_ITEMS) && !access.authorized) {
      return Promise.reject({ status: 401 });
    }
    if (url.startsWith(WORK_ITEMS) && access.failing) {
      return Promise.reject({ status: 500 });
    }
    if (url === "/xwiki/bin/get/OpenProject.Code.CKEditorPlugin?outputSyntax=plain&connection=" + CONNECTION) {
      access.checks++;
      return Promise.resolve({ notAuthorizedMessage: access.authorized ? "" : NOT_AUTHORIZED });
    }
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
  // The element displaying a message, without the links that the script changes.
  const getElement = () => {
    let html = "";
    const element = {
      html(value) {
        html = value ?? html;
        return value === undefined ? html : element;
      },
      children: () => ({ length: 0 }),
      find: () => ({ each: () => {} }),
      text: () => html.replace(/<[^>]*>/g, ""),
    };
    return element;
  };
  const $ = Object.assign(getElement, { getJSON, param: (params) => new URLSearchParams(params).toString() });
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

    // Types the marker elsewhere: the text typed after it is searched again.
    newMarker() {
      typed = "";
      range.startOffset++;
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

function getEntryIds(entries) {
  return entries.map((entry) => entry.id);
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

describe("the message of the WYSIWYG editor telling that the user can't search the work packages", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("is requested again once a search succeeded", async () => {
    // The failed searches are logged.
    vi.spyOn(console, "log").mockImplementation(() => {});
    const access = { authorized: false, failing: false, checks: 0 };
    const search = await startWorkPackageSearch([AC_1], access);

    expect(getEntryIds(await search.type("#abc"))).toEqual(["openproject-work-package-search-notAuthorized"]);
    search.newMarker();
    expect(getEntryIds(await search.type("#abd"))).toEqual(["openproject-work-package-search-notAuthorized"]);
    expect(access.checks).toBe(1);

    // The user connects their OpenProject account from the message.
    access.authorized = true;
    search.newMarker();
    expect(getSuggestions(await search.type("#Mob"))).toEqual([
      { value: "157", label: "Mobile offline mode", content: "AC-1" },
    ]);

    // A search failing for another reason.
    access.failing = true;
    search.newMarker();
    expect(getEntryIds(await search.type("#xyz"))).toEqual(["openproject-work-package-search-searchFailed"]);
    expect(access.checks).toBe(2);
  });

  it("is displayed once the access of the user expired", async () => {
    vi.spyOn(console, "log").mockImplementation(() => {});
    const access = { authorized: true, failing: true, checks: 0 };
    const search = await startWorkPackageSearch([AC_1], access);

    expect(getEntryIds(await search.type("#abc"))).toEqual(["openproject-work-package-search-searchFailed"]);

    access.authorized = false;
    search.newMarker();
    expect(getEntryIds(await search.type("#abd"))).toEqual(["openproject-work-package-search-notAuthorized"]);
    expect(access.checks).toBe(2);
  });
});
