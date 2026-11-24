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

const SOURCE = resolve(import.meta.dirname, "../../../main/resources/projectmanagercalendar/js/timeInterval.js");

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

const timeInterval = loadModule();

describe("timeInterval", () => {
  describe("toInputs", () => {
    it.each([
      ["06:00-20:00", ["06:00", "20:00"]],
      [" 08:30 - 18:00 ", ["08:30", "18:00"]],
      // A time input can't hold 24:00, the end of the day: an end of 00:00 stands for it.
      ["06:00-24:00", ["06:00", "00:00"]],
      ["06:00-24:00:00", ["06:00", "00:00"]],
      // A time input only holds two-digit hours.
      ["6:00-9:30", ["06:00", "09:30"]],
      ["08:00:30-20:00", ["08:00:30", "20:00"]],
      // The values written by hand, kept as they are by the macro editor until a time changes.
      ["07:00-", ["07:00", ""]],
      ["-19:00", ["", "19:00"]],
      ["24:00-", ["24:00", ""]],
      ["06:00-24:30", ["06:00", "24:30"]],
      ["", ["", ""]],
      [undefined, ["", ""]],
    ])("%s", (value, expected) => {
      expect(timeInterval.toInputs(value)).toEqual(expected);
    });
  });

  describe("toParameter", () => {
    it.each([
      ["06:00", "20:00", "06:00-20:00"],
      ["08:00:30", "18:30", "08:00:30-18:30"],
      // An end of 00:00 is the end of the day.
      ["06:00", "00:00", "06:00-24:00"],
      ["06:00", "00:00:00", "06:00-24:00"],
      ["00:00", "00:00", "00:00-24:00"],
      // A missing time is the start or the end of the day.
      ["07:00", "", "07:00-24:00"],
      ["", "19:00", "00:00-19:00"],
      // No parameter.
      ["", "", ""],
    ])("%s and %s", (start, end, expected) => {
      expect(timeInterval.toParameter(start, end)).toEqual(expected);
    });
  });

  describe("bind", () => {
    let field;
    let hiddenInput;
    let startInput;
    let endInput;

    // The field displayed by the macro editor, whose value is set before the script binds it.
    const display = (value) => {
      document.body.innerHTML = `
        <div class="macro-parameter-field">
          <input type="hidden" name="timeInterval" class="time-interval-hidden"/>
          <div class="time-interval-inputs">
            <input type="time" class="time-interval-min"/>
            <span class="time-interval-separator">–</span>
            <input type="time" class="time-interval-max"/>
          </div>
        </div>`;
      field = document.querySelector(".macro-parameter-field");
      hiddenInput = field.querySelector(".time-interval-hidden");
      startInput = field.querySelector(".time-interval-min");
      endInput = field.querySelector(".time-interval-max");
      hiddenInput.value = value;
      timeInterval.bind(field);
    };

    const change = (input, value) => {
      input.value = value;
      input.dispatchEvent(new Event("change"));
    };

    beforeEach(() => {
      document.body.innerHTML = "";
    });

    it("displays the end of the day as 00:00 and writes it back as 24:00", () => {
      display("06:00-24:00");

      expect([startInput.value, endInput.value]).toEqual(["06:00", "00:00"]);

      change(startInput, "07:00");

      expect(hiddenInput.value).toBe("07:00-24:00");
    });

    it("writes an end of 00:00 as the end of the day", () => {
      display("06:00-20:00");

      change(endInput, "00:00");

      expect(hiddenInput.value).toBe("06:00-24:00");
    });

    it("never writes half an interval", () => {
      // A new calendar.
      display("");

      change(startInput, "07:00");

      expect(hiddenInput.value).toBe("07:00-24:00");

      change(startInput, "");
      change(endInput, "19:00");

      expect(hiddenInput.value).toBe("00:00-19:00");

      change(endInput, "");

      // The macro editor writes no parameter.
      expect(hiddenInput.value).toBe("");
    });

    it("writes the times while they're typed", () => {
      display("06:00-20:00");

      endInput.value = "21:00";
      endInput.dispatchEvent(new Event("input"));

      expect(hiddenInput.value).toBe("06:00-21:00");
    });

    it("keeps the value of the macro until a time changes", () => {
      // The time inputs can't hold the times past the end of the day, reported by the calendar macro.
      display("06:00-24:30");

      expect([startInput.value, endInput.value]).toEqual(["06:00", ""]);
      expect(hiddenInput.value).toBe("06:00-24:30");

      change(startInput, "07:00");

      expect(hiddenInput.value).toBe("07:00-24:00");
    });

    it("binds a field once", () => {
      display("06:00-20:00");
      change(endInput, "21:00");

      timeInterval.bind(field);

      // The times aren't displayed again from the value.
      expect(endInput.value).toBe("21:00");
      expect(hiddenInput.value).toBe("06:00-21:00");
    });

    it("ignores an element without time inputs", () => {
      document.body.innerHTML = '<div class="macro-parameter-field"><input type="hidden"/></div>';

      expect(() => timeInterval.bind(document.querySelector(".macro-parameter-field"))).not.toThrow();
    });
  });
});
