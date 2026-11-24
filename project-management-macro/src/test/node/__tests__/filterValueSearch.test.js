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
import { displayFilterBuilder } from "../filterBuilderPage.js";

const STATUS = {
  id: "status",
  name: "Status",
  filter: {
    id: "list",
    operators: [{ id: "=", name: "is one of" }],
    options: [
      { value: "1", label: "New" },
      { value: "7", label: "In progress" },
      { value: "12", label: "Closed" },
    ],
  },
};

// Types the given text in the field searching the values of a filter, as a user.
function type(selectize, text) {
  const input = selectize.control_input;
  input.value += text;
  input.dispatchEvent(new Event("input"));
  // The suggestions are refreshed once the user stops typing.
  vi.runAllTimers();
}

function pressEnter(selectize) {
  selectize.control_input.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", keyCode: 13, bubbles: true }));
}

describe("the search of the values of a list filter", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("is cleared once a suggestion is picked", async () => {
    const builder = await displayFilterBuilder([STATUS]);
    builder.addFilter({ property: "status" }, true);
    const selectize = document.querySelector("input.proj-manag-constraint-value").selectize;

    type(selectize, "in pro");
    pressEnter(selectize);

    expect(selectize.control_input.value).toBe("");
    // The next search doesn't start with the text of the previous one.
    type(selectize, "New");
    pressEnter(selectize);
    expect(builder.getConstraints()).toEqual({
      status: {
        property: "status",
        constraints: [
          { operator: "=", value: "7" },
          { operator: "=", value: "1" },
        ],
      },
    });
  });
});
