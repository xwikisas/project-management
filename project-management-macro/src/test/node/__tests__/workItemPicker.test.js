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
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { loadAmdModule } from "../amdModule.js";
import { $, mountWorkItemPicker } from "../workItemPickerPage.js";

const workItemList = loadAmdModule("macro/workItemList.js");

// The work items of each connection.
const WORK_ITEMS = {
  OpenProject: [
    { identifier: { value: 157 }, key: "AC-1", summary: { value: "Mobile offline mode" },
      project: { value: "Atlas CRM" } },
    { identifier: { value: 164 }, key: "AC-8", summary: { value: "Calendar sync" }, project: { value: "Atlas CRM" } },
  ],
  // The work items of this connection have no key.
  "OpenProject QA": [
    { identifier: { value: 242 }, summary: { value: "Alpha release" }, project: { value: "QA" } },
    { identifier: { value: 164 }, summary: { value: "Bulk task 85" }, project: { value: "QA" } },
  ],
};

// The connection of the macro.
let connection;

// The URLs of the requests of the picker.
let requests;

// Answers the requests of the picker, see search.
let answer;

// Answers a request of the picker: the work items of the connection whose subject contains the searched text, or the
// work item looked up by its id or key.
function search(url) {
  const { pathname, searchParams } = new URL(url, "http://localhost");
  const items = WORK_ITEMS[searchParams.get("instance")];
  if (!searchParams.has("filter")) {
    const id = decodeURIComponent(pathname.substring(pathname.lastIndexOf("/") + 1));
    const item = items.find((candidate) => [String(candidate.identifier.value), candidate.key].includes(id));
    return item ? Promise.resolve(item) : Promise.reject({ status: 404 });
  }
  const text = JSON.parse(searchParams.get("filter")).query.filters[0].constraints[0].value;
  return Promise.resolve({ items: items.filter((item) => item.summary.value.includes(text)) });
}

function mount(options) {
  const urls = () => workItemList.restUrls("/xwiki/rest", "xwiki", "openproject", { instance: connection });
  return mountWorkItemPicker({
    searchUrl: (text) => urls().searchUrl(text),
    itemUrl: (id) => urls().itemUrl(id),
    ...options,
  });
}

// Types the given text in the picker, as a user, and waits for the work items found.
async function type(selectize, text) {
  const input = selectize.control_input;
  input.value = text;
  input.dispatchEvent(new Event("input"));
  // The work items are searched once the user stops typing.
  await vi.runAllTimersAsync();
}

// Types the given id in the picker and presses Enter at once, before the work items are found, as a user.
async function typeAndEnter(selectize, id) {
  const input = selectize.control_input;
  input.value = id;
  input.dispatchEvent(new Event("input"));
  // The suggestions are displayed once the user stops typing, before the work items are searched.
  vi.advanceTimersByTime(selectize.settings.refreshThrottle);
  input.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", keyCode: 13, bubbles: true }));
  await vi.runAllTimersAsync();
}

// Chooses another connection, as the user does in the macro editor.
async function changeConnection(picker, newConnection) {
  connection = newConnection;
  picker.refresh();
  await vi.runAllTimersAsync();
}

function getPickedLabels(selectize) {
  return selectize.items.map((id) => selectize.options[id].label);
}

function getSuggestions(selectize) {
  return [...selectize.dropdown.querySelectorAll(".pm-workitem-picker-option")].map((option) => option.textContent);
}

// Waits for the observers of the DOM changes.
function flush() {
  return new Promise((resolve) => queueMicrotask(resolve));
}

// The accessible name of a field, given by the attributes that the picker sets.
function getAccessibleName(field) {
  const labelledBy = field.getAttribute("aria-labelledby");
  if (labelledBy) {
    return labelledBy.split(" ").map((id) => document.getElementById(id).textContent).join(" ");
  }
  return field.getAttribute("aria-label");
}

function getAccessibleDescription(field) {
  return (field.getAttribute("aria-describedby") || "").split(" ").filter((id) => id)
    .map((id) => document.getElementById(id).textContent).join(" ");
}

describe("the work item picker", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    connection = "OpenProject";
    requests = [];
    answer = search;
    vi.spyOn($, "getJSON").mockImplementation((url) => {
      requests.push(url);
      return answer(url);
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it("searches the work items of the new connection once the connection changes", async () => {
    const { picker, selectize } = mount();
    await type(selectize, "Mobile");
    expect(getSuggestions(selectize)).toEqual(["AC-1 Mobile offline modeAtlas CRM"]);
    await type(selectize, "");

    connection = "OpenProject QA";
    picker.refresh();
    requests = [];
    await type(selectize, "Mobile");

    expect(requests.map((url) => new URL(url, "http://localhost").searchParams.get("instance")))
      .toEqual(["OpenProject QA"]);
    expect(getSuggestions(selectize)).toEqual([]);
    expect(Object.keys(selectize.options)).toEqual([]);
  });

  it("keeps the picked work items once the connection changes, looked up on the new connection", async () => {
    const { picker, selectize } = mount();
    await type(selectize, "Mobile");
    selectize.addItem("AC-1");

    connection = "OpenProject QA";
    requests = [];
    picker.refresh();
    await vi.runAllTimersAsync();

    expect(picker.getIds()).toEqual(["AC-1"]);
    expect(requests).toEqual(["/xwiki/rest/wikis/xwiki/projectmanagement/openproject/workitems/AC-1"
      + "?instance=OpenProject%20QA"]);
    expect(selectize.options["AC-1"].state).toBe("notFound");
  });

  it("ignores the answer of a search sent before the connection changed", async () => {
    const { picker, selectize } = mount();
    let answerSearch;
    answer = () => new Promise((resolve) => {
      answerSearch = resolve;
    });
    const reports = [];
    $(".pm-workitem-picker").on("suggestionsLoaded suggestionsFailed", (event) => reports.push(event.type));
    await type(selectize, "Mobile");

    connection = "OpenProject QA";
    picker.refresh();
    answerSearch(await search(requests[0]));
    await vi.runAllTimersAsync();

    expect(Object.keys(selectize.options)).toEqual([]);
    // Nor does it tell whether the work items of the new connection can be retrieved (e.g. so that the client removes
    // the message telling why they can't).
    expect(reports).toEqual([]);
  });

  it("searches again a text whose search failed", async () => {
    const { selectize } = mount();
    connection = "OpenProject QA";
    // OpenProject doesn't answer, or the user isn't authenticated to it yet.
    answer = () => Promise.reject({ status: 500 });
    await type(selectize, "Bulk");
    expect(getSuggestions(selectize)).toEqual([]);
    await type(selectize, "");

    answer = search;
    requests = [];
    await type(selectize, "Bulk");

    expect(requests).toHaveLength(1);
    expect(getSuggestions(selectize)).toEqual(["#164 Bulk task 85QA"]);
  });

  it("doesn't search again a text whose search found nothing", async () => {
    const { selectize } = mount();
    connection = "OpenProject QA";
    // A work item not found isn't a failure.
    await type(selectize, "999");
    await type(selectize, "");

    requests = [];
    await type(selectize, "999");

    expect(requests).toEqual([]);
  });

  it("replaces a typed id by the key of its work item", async () => {
    const { picker, selectize } = mount();

    await typeAndEnter(selectize, "164");

    expect(picker.getIds()).toEqual(["AC-8"]);
    expect(getPickedLabels(selectize)).toEqual(["AC-8 Calendar sync"]);
  });

  it("keeps a typed id when the connection changes, looking its work item up on each connection", async () => {
    const { picker, selectize } = mount();
    connection = "OpenProject QA";
    await typeAndEnter(selectize, "164");
    expect(picker.getIds()).toEqual(["164"]);
    expect(getPickedLabels(selectize)).toEqual(["#164 Bulk task 85"]);

    // On a connection where the work item has a key, the id isn't replaced by it.
    await changeConnection(picker, "OpenProject");
    expect(picker.getIds()).toEqual(["164"]);
    expect(getPickedLabels(selectize)).toEqual(["AC-8 Calendar sync"]);

    await changeConnection(picker, "OpenProject QA");
    expect(picker.getIds()).toEqual(["164"]);
    expect(getPickedLabels(selectize)).toEqual(["#164 Bulk task 85"]);
    expect(selectize.options["164"].state).toBeUndefined();
  });

  it("ignores the answer of a lookup sent before the connection changed", async () => {
    const { picker, selectize } = mount();
    connection = "OpenProject QA";
    // The lookup of the typed id answers late; the search of the typed text, which looks it up too, answers at once.
    let answerLookup;
    answer = (url) => !answerLookup && url.includes("/workitems/164?") ? new Promise((resolve) => {
      answerLookup = resolve;
    }) : search(url);
    await typeAndEnter(selectize, "164");

    await changeConnection(picker, "OpenProject");
    answerLookup(WORK_ITEMS["OpenProject QA"][1]);
    await vi.runAllTimersAsync();

    expect(picker.getIds()).toEqual(["164"]);
    expect(getPickedLabels(selectize)).toEqual(["AC-8 Calendar sync"]);
  });

  it("keeps its suggestions under its field when a message is displayed above it", async () => {
    const { element, selectize } = mount();
    // The field is 79px lower while the message is displayed above it.
    selectize.control.getBoundingClientRect = () => ({
      top: element.querySelector(".message") ? 79 : 0,
      left: 10,
      width: 300,
      height: 30,
    });
    connection = "OpenProject QA";
    await type(selectize, "Alpha");
    expect(selectize.isOpen).toBe(true);
    expect(selectize.dropdown.style.top).toBe("0px");

    $(element).prepend('<div class="message">You need to be authenticated to OpenProject.</div>');
    await flush();
    expect(selectize.dropdown.style.top).toBe("79px");

    element.querySelector(".message").remove();
    await flush();
    expect(selectize.dropdown.style.top).toBe("0px");
  });

  it("is named by its label and described by the search hint", () => {
    const { selectize } = mount({ labelId: "pm-picker-label" });

    // WCAG 2.5.3 (Label in Name): the name of the field holds the label displayed above it.
    expect(getAccessibleName(selectize.control_input)).toBe("Work packages");
    expect(getAccessibleDescription(selectize.control_input)).toBe("Search by subject, id or key");
  });

  it("is named by the search hint without a label", () => {
    const { selectize } = mount();

    expect(getAccessibleName(selectize.control_input)).toBe("Search by subject, id or key");
    expect(getAccessibleDescription(selectize.control_input)).toBe("");
  });
});
