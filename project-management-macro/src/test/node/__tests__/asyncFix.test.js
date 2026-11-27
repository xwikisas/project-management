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
import { JSDOM } from "jsdom";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { resolve } from "node:path";
import { beforeEach, describe, expect, it } from "vitest";

const ASYNC_FIX = readFileSync(resolve(import.meta.dirname,
  "../../../main/resources/js/projectmanagement/asyncFix.js"), "utf-8");
const JQUERY = readFileSync(createRequire(import.meta.url).resolve("jquery"), "utf-8");

const PLACEHOLDER = '<div class="xwiki-async" data-xwiki-async-id="job"></div>';
const RESULT = '<div class="result">Work items</div>';

let window;
let document;
// The elements of each xwiki:dom:updated event.
let events;

// Each test has its own page, where the script didn't run yet.
beforeEach(() => {
  window = new JSDOM("<!DOCTYPE html><html><body></body></html>", { runScripts: "outside-only" }).window;
  document = window.document;
  window.eval(JQUERY);
  window.require = (dependencies, factory) => factory(window.jQuery);
  events = [];
  window.jQuery(document).on("xwiki:dom:updated", (event, data) => events.push(data.elements));
});

// Loads the script in the page, like XWiki does, as many times as the page loads it.
function loadScript() {
  window.eval(ASYNC_FIX);
}

// The MutationObserver reports the mutations in a microtask.
function mutationsReported() {
  return new Promise((resolve) => setTimeout(resolve));
}

function wrapper(content, placeholder = true) {
  return `<div class="proj-manag-async-wrapper"${placeholder ? ' data-async-placeholder="true"' : ""}>${content}</div>`;
}

function replacePlaceholder() {
  document.querySelector(".xwiki-async").outerHTML = RESULT;
}

describe("asyncFix", () => {
  it("initializes a result that replaced its placeholder before the script was loaded", async () => {
    // E.g. the in-place editor reloads the content of a page that had no work item macro: the script comes with the
    // asynchronous result, once it is inserted.
    document.body.innerHTML = `<div id="xwikicontent">${wrapper(PLACEHOLDER)}</div>`;
    replacePlaceholder();

    loadScript();
    await mutationsReported();

    expect(events).toEqual([[document.querySelector(".proj-manag-async-wrapper")]]);
  });

  it("doesn't initialize again a result displayed without placeholder", async () => {
    // The page initialized it when it was loaded.
    document.body.innerHTML = wrapper(RESULT, false);

    loadScript();
    await mutationsReported();

    expect(events).toEqual([]);
  });

  it("initializes a result once its placeholder is replaced", async () => {
    document.body.innerHTML = wrapper(PLACEHOLDER);

    loadScript();
    await mutationsReported();
    expect(events).toEqual([]);

    replacePlaceholder();
    await mutationsReported();

    expect(events).toEqual([[document.querySelector(".proj-manag-async-wrapper")]]);
  });

  it("initializes a result once when the script is loaded twice", async () => {
    // E.g. by the page and by the asynchronous result.
    document.body.innerHTML = wrapper(RESULT) + wrapper(PLACEHOLDER);

    loadScript();
    loadScript();
    replacePlaceholder();
    await mutationsReported();

    expect(events).toEqual([...document.querySelectorAll(".proj-manag-async-wrapper")].map((element) => [element]));
  });

  it("doesn't initialize again a result that XWiki initialized after it replaced the placeholder", async () => {
    document.body.innerHTML = wrapper(PLACEHOLDER);
    loadScript();

    replacePlaceholder();
    const content = [document.body];
    window.jQuery(document).trigger("xwiki:dom:updated", { elements: content });
    await mutationsReported();

    expect(events).toEqual([content]);
  });
});
