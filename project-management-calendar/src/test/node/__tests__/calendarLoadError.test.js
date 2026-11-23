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
import { beforeEach, describe, expect, it } from "vitest";

const SOURCE = resolve(import.meta.dirname, "../../../main/resources/projectmanagercalendar/js/calendarLoadError.js");

/**
 * @return the module defined by the script served to the browsers, without running the code that needs a page
 */
function loadModule() {
  let module;
  const define = (name, dependencies, factory) => {
    module = factory();
  };
  const require = () => {};
  new Function("define", "require", readFileSync(SOURCE, "utf-8"))(define, require);
  return module;
}

const loadError = loadModule();

const CALENDAR_URL = "/xwiki/rest/wikis/xwiki/projectmanagement/openproject/calendar";

const EVENTS_URL = CALENDAR_URL + "?instance=main&sprintColor=";

describe("calendarLoadError", () => {
  describe("isEventsRequest", () => {
    it.each([
      // The calendar macro adds its parameters, starting with xpage, then FullCalendar adds the calendar window.
      [EVENTS_URL, EVENTS_URL + "&xpage=plain&outputSyntax=plain&form_token=t&start=2026-11-01&_=1", true],
      [CALENDAR_URL, CALENDAR_URL + "?xpage=plain&start=2026-11-01", true],
      // Another calendar, whose URL starts with the URL of this calendar.
      [EVENTS_URL, EVENTS_URL + "&limit=5&xpage=plain&start=2026-11-01", false],
      [EVENTS_URL, EVENTS_URL + "red&xpage=plain", false],
      [EVENTS_URL, CALENDAR_URL + "?instance=other&xpage=plain", false],
      // Another request.
      [EVENTS_URL, "/xwiki/bin/get/Main/WebHome?xpage=plain", false],
      [EVENTS_URL, undefined, false],
    ])("%s requested with %s", (eventsURL, requestURL, expected) => {
      expect(loadError.isEventsRequest(eventsURL, requestURL)).toBe(expected);
    });

    it("ignores a calendar without URL", () => {
      expect(loadError.isEventsRequest("", "?xpage=plain")).toBe(false);
      expect(loadError.isEventsRequest(undefined, "?xpage=plain")).toBe(false);
    });
  });

  describe("getMessageStatus", () => {
    const statuses = ["400", "401", "403", "other"];

    it.each([
      [400, "400"],
      [401, "401"],
      [403, "403"],
      // The other failures, including an unreachable server, have the same message.
      [500, "other"],
      [404, "other"],
    ])("%s", (status, expected) => {
      expect(loadError.getMessageStatus(statuses, status)).toBe(expected);
    });
  });

  describe("onError and onSuccess", () => {
    const OTHER_URL = CALENDAR_URL + "?instance=other";

    const messages = (status) => ["400", "401", "403", "other"].map((messageStatus) =>
      `<div class="projectmanagement-calendar-load-error" data-status="${messageStatus}" hidden="hidden">`
      + `${status}-${messageStatus}</div>`).join("");

    beforeEach(() => {
      // Two calendars, the second one being displayed in an event of the first one.
      document.body.innerHTML = `
        <div class="projectmanagement-calendar" id="first" data-events-url="${EVENTS_URL}">
          ${messages("first")}
          <div class="xwiki-fullcalendar">
            <div class="projectmanagement-calendar" id="second" data-events-url="${OTHER_URL}">
              ${messages("second")}
            </div>
          </div>
        </div>`;
    });

    const visibleMessages = () => Array.from(document.querySelectorAll(".projectmanagement-calendar-load-error"))
      .filter((message) => !message.hidden).map((message) => message.textContent);

    it.each([
      [401, ["first-401"]],
      [403, ["first-403"]],
      [400, ["first-400"]],
      [500, ["first-other"]],
    ])("shows the message of the status %s above the calendar of the request", (status, expected) => {
      loadError.onError(document, { status }, EVENTS_URL + "&xpage=plain&start=2026-11-01");

      expect(visibleMessages()).toEqual(expected);
    });

    it("shows the message of the last failure only", () => {
      loadError.onError(document, { status: 401 }, EVENTS_URL + "&xpage=plain");
      loadError.onError(document, { status: 500 }, EVENTS_URL + "&xpage=plain");

      expect(visibleMessages()).toEqual(["first-other"]);
    });

    it("shows the messages of the nested calendar in the nested calendar", () => {
      loadError.onError(document, { status: 403 }, OTHER_URL + "&xpage=plain");

      expect(visibleMessages()).toEqual(["second-403"]);
    });

    it("ignores the requests without response", () => {
      // A request canceled when leaving the page, or aborted.
      loadError.onError(document, { status: 0, statusText: "error" }, EVENTS_URL + "&xpage=plain");
      loadError.onError(document, { status: 0, statusText: "abort" }, EVENTS_URL + "&xpage=plain");
      loadError.onError(document, undefined, EVENTS_URL + "&xpage=plain");

      expect(visibleMessages()).toEqual([]);
    });

    it("ignores the other requests", () => {
      loadError.onError(document, { status: 500 }, "/xwiki/bin/get/Main/WebHome?xpage=plain");
      loadError.onError(document, { status: 500 }, undefined);

      expect(visibleMessages()).toEqual([]);
    });

    it("hides the messages once the events of the calendar are loaded", () => {
      loadError.onError(document, { status: 401 }, EVENTS_URL + "&xpage=plain");
      loadError.onError(document, { status: 401 }, OTHER_URL + "&xpage=plain");

      loadError.onSuccess(document, EVENTS_URL + "&xpage=plain&start=2026-12-01");

      // The messages of the other calendar are kept.
      expect(visibleMessages()).toEqual(["second-401"]);
    });
  });
});
