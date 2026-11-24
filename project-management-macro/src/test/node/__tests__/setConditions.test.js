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
// The conditions on whether a property is set (IS NOT EMPTY, IS EMPTY in a query), which the tool can apply only on
// the properties proposing them, e.g. not on the description of the OpenProject work packages.
import { beforeEach, describe, expect, it } from "vitest";
import { $, displayFilterBuilder } from "../filterBuilderPage.js";

const PROPERTIES = [
  {
    id: "description",
    name: "Description",
    filter: { id: "text", operators: [{ id: "~", name: "Contains" }, { id: "!~", name: "Does not contain" }] },
  },
  {
    id: "assignees",
    name: "Assignee",
    filter: { id: "list", operators: [{ id: "=", name: "Equals" }, { id: "*", name: "Not empty" },
      { id: "!*", name: "Not set" }] },
  },
];

const L10N = {
  "text.!*": "is not set",
  "text.*": "is set",
  setConditionUnsupported: "Filtering on whether {0} is set isn't supported: change or remove this filter.",
};

function getRow(property) {
  return [...document.querySelectorAll(".proj-manag-constraints .proj-manag-constraint")]
    .find((element) => element.querySelector(".proj-manag-constraint-name").value === property);
}

// The warning of the row of a filter, as the assistive technologies get it from its operator.
function getOperatorWarning(property) {
  const operator = getRow(property).querySelector("select.proj-manag-constraint-operator");
  const warning = document.getElementById(operator.getAttribute("aria-describedby"));
  return operator.getAttribute("aria-invalid") === "true" && warning ? warning.textContent : null;
}

describe("a filter on whether a property is set", () => {
  let builder;

  beforeEach(async () => {
    builder = await displayFilterBuilder(PROPERTIES, L10N);
  });

  it("is flagged on a property that can't be filtered so, and kept as it is", () => {
    builder.addFilter({ property: "description", constraints: [{ operator: "!*", value: "" }] });

    expect(getOperatorWarning("description"))
      .toBe("Filtering on whether Description is set isn't supported: change or remove this filter.");
    expect(getRow("description").querySelector(".proj-manag-constraint-unsupported").textContent)
      .toBe(getOperatorWarning("description"));
    expect($(getRow("description")).find("select.proj-manag-constraint-operator option:selected").text())
      .toBe("is not set");
    expect(builder.getConstraints()).toEqual({
      description: { property: "description", constraints: [{ operator: "!*", value: "" }] },
    });
  });

  it("is flagged until the operator is changed to one of the property", () => {
    builder.addFilter({ property: "description", constraints: [{ operator: "*", value: "" }] });
    expect(getOperatorWarning("description")).not.toBeNull();

    $(getRow("description")).find("select.proj-manag-constraint-operator").val("~").trigger("change");

    expect(getOperatorWarning("description")).toBeNull();
    expect(getRow("description").querySelector(".proj-manag-constraint-unsupported")).toBeNull();
  });

  it("isn't flagged on a property that can be filtered so", () => {
    builder.addFilter({ property: "assignees", constraints: [{ operator: "!*", value: "" }] });

    expect(getOperatorWarning("assignees")).toBeNull();
    expect(getRow("assignees").querySelector(".proj-manag-constraint-unsupported")).toBeNull();
  });
});
