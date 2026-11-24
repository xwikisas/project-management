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
// Displays the Chart Options parameter of the chart macro editor in the DOM of jsdom (the test files using it run in
// the jsdom environment): the HTML of its editor (projectmanagementcharttypeparams/edit.vm) and the script served to
// the browsers (chartTypeParams.js), with the libraries that XWiki gives it: jQuery and Tom Select (the suggest widget
// of XWiki 18+).
import jquery from "jquery";
import TomSelect from "tom-select";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const RESOURCES = resolve(import.meta.dirname, "../../main/resources");

// The suggest widget of XWiki (xwiki-selectize), with its settings that matter here: a Tom Select instance exposed as
// the selectize property of the input, whose list of values is appended to the body.
jquery.fn.xwikiSelectize = function (settings) {
  return this.each((index, input) => {
    input.selectize = new TomSelect(input, {
      labelField: "label",
      searchField: ["value", "label"],
      dropdownParent: "body",
      ...settings,
    });
  });
};

// The HTML of the editor of the parameter, without its Velocity code: the options are displayed in its first element,
// from the template that follows it.
function getEditorHTML() {
  const editor = readFileSync(resolve(RESOURCES,
    "templates/html_displayer/projectmanagementcharttypeparams/edit.vm"), "utf-8");
  return editor.match(/<div class="proj-manag-chart-type-params[\s\S]*<\/template>/)[0];
}

// The notifications of XWiki, recording their type, their text and whether they're shown.
function createNotificationClass(notifications) {
  return function (text, type) {
    const notification = { type, text, shown: false };
    notifications.push(notification);
    // The text of the notification is held by a styled element, that the scripts find with Prototype.js.
    const styled = {
      set textContent(value) {
        notification.text = value;
      },
    };
    this.element = { down: () => styled };
    this.show = () => {
      notification.shown = true;
    };
    this.hide = () => {
      notification.shown = false;
    };
    this.replace = (other) => {
      notification.shown = false;
      other.show();
    };
  };
}

/**
 * Displays the Chart Options of the chart macro editor, replacing the content of the page.
 *
 * @param chartOptions the options of each chart type, as returned by the REST resource listing them: the request of
 *   the options of a chart type missing from this object fails
 * @param chartType the chart type selected in the editor
 * @param value the value of the parameter, e.g. {"metric":"cumulative"}
 * @return the notifications displayed by the editor, as {type, text, shown}, the text being the translation key
 */
export function displayChartOptions(chartOptions, chartType = "line", value = "") {
  const typeOptions = Object.keys({ line: true, ...chartOptions })
    .map((type) => `<option value="${type}">${type}</option>`).join("");
  document.body.innerHTML = `<div class="macro-editor-modal">
    <div class="macro-parameter"><select name="type">${typeOptions}</select></div>
    <div class="macro-parameter">
      ${getEditorHTML()}
      <textarea id="proj-manag-chart-type-params" class="hidden"></textarea>
    </div>
  </div>`;
  jquery("select[name=type]").val(chartType);
  jquery("#proj-manag-chart-type-params").val(value);
  // The handlers of the editor displayed before.
  jquery(document).off();

  jquery.getJSON = (url) => {
    const options = chartOptions[url.substring(url.lastIndexOf("/") + 1)];
    return (options ? jquery.Deferred().resolve(options) : jquery.Deferred().reject()).promise();
  };
  const notifications = [];
  const XWiki = {
    contextPath: "/xwiki",
    currentWiki: "xwiki",
    widgets: { Notification: createNotificationClass(notifications) },
  };
  const define = () => {};
  const l10n = { get: (key) => key };
  const require = (dependencies, onLoad) => onLoad(jquery, l10n);
  new Function("define", "require", "XWiki",
    readFileSync(resolve(RESOURCES, "js/projectmanagement/macro/chartTypeParams.js"), "utf-8"))(define, require,
    XWiki);
  return notifications;
}

/**
 * Selects a chart type in the editor displayed by {@link displayChartOptions}, as a user.
 *
 * @param chartType the chart type to select
 */
export function selectChartType(chartType) {
  jquery("select[name=type]").val(chartType).trigger("change");
}
