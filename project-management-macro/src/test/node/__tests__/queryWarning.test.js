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
import { afterEach, describe, expect, it, vi } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { $, displayFilterBuilder } from "../filterBuilderPage.js";
import { loadQueryWriter } from "../queryWriter.js";
import { loadAmdModule } from "../amdModule.js";

const FILTER_PARAMETER = resolve(import.meta.dirname,
  "../../../main/resources/js/projectmanagement/macro/filterParameter.js");

const STATUS = {
  id: "status",
  name: "Status",
  filter: { id: "list", operators: [{ id: "=", name: "is one of" }], options: [{ value: "1", label: "New" }] },
};

const L10N = {
  summary: "Selects the work packages where {0}.",
  summaryAll: "No filter: all the work packages you can see.",
  summaryLoading: "Loading the filters…",
  and: "and",
  or: "or",
  "list.=": "is one of",
};

// The warnings of the widget of the source parameter (see projectmanagementworkitemsource/edit.vm).
const WARNINGS = {
  "data-query-error": "The content can't be shown as filters: {0} Editing the filters here replaces it.",
  "data-query-failed": "The content can't be shown as filters ({0}). Editing the filters here replaces it.",
  "data-query-failed-no-reason": "The content can't be shown as filters. Editing the filters here replaces it.",
};

const QUERY = "project = qa150-t3 AND status = open";

// The parts of the XWiki JavaScript API that the script uses.
const XWIKI = {
  Document: function () {
    this.getURL = () => "";
  },
  Model: {
    // Called as a constructor.
    resolve: function () {
      return {};
    },
  },
  EntityType: { DOCUMENT: "document" },
  contextPath: "/xwiki",
  currentWiki: "xwiki",
};

/**
 * Displays the matching filters of the source parameter of a macro whose query the server can't read, as the macro
 * editor does: the filter builder in its container, bound by filterParameter.js, which reads the query.
 *
 * @param response the failed request reading the query
 */
async function editQuery(response) {
  await displayFilterBuilder([STATUS], L10N);
  $(".proj-manag-constraint-builder").wrap($("<div data-builder-role='query'></div>").attr(WARNINGS));
  $("[data-builder-role='query']").data("pmPendingInput", { type: "query", value: QUERY });
  vi.spyOn($, "get").mockImplementation(() => $.Deferred().reject(response).promise());
  const dependencies = [$, window.FilterBuilder, loadQueryWriter(), loadAmdModule("macro/queryEdits.js")];
  const require = (names, onLoad) => onLoad(...(names.length > 1 ? dependencies : []));
  require.config = () => {};
  new Function("setTimeout", "require", "XWiki", readFileSync(FILTER_PARAMETER, "utf-8"))((run) => run(), require,
    XWIKI);
}

function getWarning() {
  return $(".proj-manag-query-error").text();
}

function getSummary() {
  return $(".proj-manag-filter-summary").text();
}

describe("the matching filters of a query that the server can't read", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    $(document).off();
  });

  it("tell the status of the failed request in a sentence", async () => {
    await editQuery({ status: 500, statusText: "Internal Server Error" });

    expect(getWarning()).toBe("The content can't be shown as filters (Internal Server Error). Editing the filters "
      + "here replaces it.");
  });

  it("tell that the query can't be shown when the failed request has no status text", async () => {
    // jQuery gives "error" when the response has no status text, e.g. with HTTP/2.
    await editQuery({ status: 500, statusText: "error" });
    expect(getWarning()).toBe("The content can't be shown as filters. Editing the filters here replaces it.");

    $(document).off();
    await editQuery({ status: 0, statusText: "" });
    expect(getWarning()).toBe("The content can't be shown as filters. Editing the filters here replaces it.");
  });

  it("tell why the server can't show the query", async () => {
    await editQuery({
      status: 400,
      statusText: "Bad Request",
      responseJSON: { message: "OR (at position 13) is not supported yet: the query can only combine conditions "
        + "with AND." },
    });

    expect(getWarning()).toBe("The content can't be shown as filters: OR (at position 13) is not supported yet: the "
      + "query can only combine conditions with AND. Editing the filters here replaces it.");
  });

  it("don't describe the filters while the query is kept", async () => {
    await editQuery({ status: 500, statusText: "Internal Server Error" });
    const builder = window.FilterBuilder.instances.get($(".proj-manag-constraint-builder")[0]);

    expect(getSummary()).toBe("");
    // A filter without value doesn't replace the query.
    builder.addFilter({ property: "status" });
    expect(getSummary()).toBe("");
    expect(getWarning()).not.toBe("");

    // A filter with a value replaces it.
    $(".proj-manag-constraints .proj-manag-constraint-value").val("1").trigger("change");
    expect(getWarning()).toBe("");
    expect(getSummary()).toBe("Selects the work packages where Status is one of New.");
  });
});
