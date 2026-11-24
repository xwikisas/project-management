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
// Mounts the work item picker (workItemPicker.js, the script served to the browsers) in the DOM of jsdom (the test
// files using it run in the jsdom environment), with the libraries that XWiki gives it: jQuery and Tom Select (the
// suggest widget of XWiki 18+), and its translations.
import jquery from "jquery";
import TomSelect from "tom-select";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { loadAmdModule } from "./amdModule.js";

const SOURCES = resolve(import.meta.dirname, "../../main/resources/js/projectmanagement");
const TRANSLATIONS = resolve(import.meta.dirname, "../../main/resources/ApplicationResources.properties");
const PREFIX = "projectmanagement.macro.workItemPicker.";

// The suggest widget of XWiki (xwiki-selectize), with its settings that matter here: a Tom Select instance exposed as
// the selectize property of the input, whose suggestions are displayed in the page body.
jquery.fn.xwikiSelectize = function (settings) {
  return this.each((index, input) => {
    input.selectize = new TomSelect(input, {
      dropdownParent: "body",
      labelField: "label",
      searchField: ["value", "label", "hint"],
      persist: true,
      highlight: false,
      loadThrottle: 500,
      ...settings,
    });
  });
};

// The translations of the picker (the xwiki-l10n module), {0} being replaced by the given parameter.
function loadTranslations() {
  const translations = new Map();
  readFileSync(TRANSLATIONS, "utf-8").split("\n").filter((line) => line.startsWith(PREFIX))
    .forEach((line) => translations.set(line.substring(PREFIX.length, line.indexOf("=")),
      line.substring(line.indexOf("=") + 1)));
  return {
    get: (key, parameter) => String(translations.get(key) || key).replace("{0}", () => parameter),
  };
}

function loadWorkItemPicker() {
  const workItemList = loadAmdModule("macro/workItemList.js");
  let picker;
  const define = (...args) => {
    const factory = args[args.length - 1];
    if (typeof factory === "function") {
      picker = factory(jquery, workItemList, loadTranslations());
    }
  };
  new Function("define", readFileSync(resolve(SOURCES, "macro/workItemPicker.js"), "utf-8"))(define);
  return picker;
}

const workItemPicker = loadWorkItemPicker();

/**
 * Mounts a work item picker, replacing the content of the page.
 *
 * @param options the options of the picker (see workItemPicker.js#mount)
 * @return {picker: the mounted picker, element: the element of the picker, selectize: its suggest widget}
 */
export function mountWorkItemPicker(options) {
  document.body.innerHTML = '<span id="pm-picker-label">Work packages</span><div class="pm-picker-container"></div>';
  const picker = workItemPicker.mount(document.querySelector(".pm-picker-container"), options);
  return {
    picker,
    element: document.querySelector(".pm-workitem-picker"),
    selectize: document.querySelector(".pm-workitem-picker-input").selectize,
  };
}

export { jquery as $ };
