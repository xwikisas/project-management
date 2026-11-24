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
import { describe, expect, it, vi } from "vitest";

const SOURCE = resolve(import.meta.dirname, "../../../main/resources/openproject/js/notAuthorizedMessage.js");

const MESSAGE = '<div class="box warningmessage">You need to be authenticated to OpenProject. '
  + '<a href="/xwiki/bin/view/OpenProject/Code/RenewOAuthConnection?connectionName=QA">Authenticate</a></div>';

// Loads the module served to the browsers (a named AMD module without dependency).
function loadNotAuthorizedMessage() {
  let module;
  const define = (name, dependencies, factory) => {
    module = factory();
  };
  new Function("define", readFileSync(SOURCE, "utf-8"))(define);
  return module;
}

const notAuthorizedMessage = loadNotAuthorizedMessage();

/**
 * @return a notifier whose connection is the given one (changeable through the returned state), recording what it
 *     displays
 */
function createNotifier(connection, getMessage) {
  const state = { connection, displayed: [] };
  state.notifier = notAuthorizedMessage.createNotifier({
    getConnection: () => state.connection,
    getMessage,
    show: (message, context) => state.displayed.push({ message, context }),
    remove: () => {
      state.displayed = [];
    },
  });
  return state;
}

describe("createNotifier", () => {
  it("displays the message of the connection when the suggestions fail", async () => {
    const getMessage = vi.fn(() => Promise.resolve(MESSAGE));
    const state = createNotifier("QA", getMessage);

    await state.notifier.suggestionsFailed("filters");

    expect(getMessage).toHaveBeenCalledWith("QA");
    expect(state.displayed).toEqual([{ message: MESSAGE, context: "filters" }]);
  });

  it("displays nothing when the user can read the data of the connection", async () => {
    // E.g. OpenProject failed to answer: the macros display their own error.
    const state = createNotifier("QA", () => Promise.resolve(""));

    await state.notifier.suggestionsFailed("filters");

    expect(state.displayed).toEqual([]);
  });

  it("displays nothing without connection", async () => {
    const getMessage = vi.fn();
    const state = createNotifier("", getMessage);

    await state.notifier.suggestionsFailed("filters");

    expect(getMessage).not.toHaveBeenCalled();
    expect(state.displayed).toEqual([]);
  });

  it("ignores the message of a connection that was changed while it was loading", async () => {
    let resolveMessage;
    const state = createNotifier("QA", () => new Promise((resolve) => {
      resolveMessage = resolve;
    }));

    const failure = state.notifier.suggestionsFailed("filters");
    state.connection = "Other";
    state.notifier.connectionChanged();
    resolveMessage(MESSAGE);
    await failure;

    expect(state.displayed).toEqual([]);
  });

  it("removes the message once the suggestions are retrieved", async () => {
    const state = createNotifier("QA", () => Promise.resolve(MESSAGE));
    await state.notifier.suggestionsFailed("filters");

    // E.g. the user connected their OpenProject account in another tab.
    state.notifier.suggestionsLoaded();

    expect(state.displayed).toEqual([]);
  });
});

describe("createMessageLoader", () => {
  it("requests the message of each connection once", async () => {
    const fetchMessage = vi.fn((connection) => Promise.resolve(MESSAGE + connection));
    const getMessage = notAuthorizedMessage.createMessageLoader(fetchMessage);

    expect(await getMessage("QA")).toBe(MESSAGE + "QA");
    expect(await getMessage("QA")).toBe(MESSAGE + "QA");
    expect(await getMessage("Other")).toBe(MESSAGE + "Other");

    expect(fetchMessage.mock.calls).toEqual([["QA"], ["Other"]]);
  });

  it("requests the message again after a failure, which gives no message", async () => {
    const fetchMessage = vi.fn()
      .mockReturnValueOnce(Promise.reject(new Error("Not found")))
      .mockReturnValueOnce(Promise.resolve(MESSAGE));
    const getMessage = notAuthorizedMessage.createMessageLoader(fetchMessage);

    expect(await getMessage("QA")).toBe("");
    expect(await getMessage("QA")).toBe(MESSAGE);
  });

  it("gives no message for a response without message", async () => {
    const getMessage = notAuthorizedMessage.createMessageLoader(() => Promise.resolve(undefined));

    expect(await getMessage("QA")).toBe("");
  });
});

describe("getLinkURL", () => {
  it("comes back to the given page", () => {
    expect(notAuthorizedMessage.getLinkURL(
      "/xwiki/bin/view/OpenProject/Code/RenewOAuthConnection?connectionName=QA&redirectUrl=%2Fother&token=t",
      "http://localhost:8080/xwiki/bin/edit/Main/", "/xwiki/bin/view/Main/"))
      .toBe("http://localhost:8080/xwiki/bin/view/OpenProject/Code/RenewOAuthConnection?connectionName=QA"
        + "&redirectUrl=%2Fxwiki%2Fbin%2Fview%2FMain%2F&token=t");
  });

  it("keeps the links that don't come back to a page", () => {
    expect(notAuthorizedMessage.getLinkURL("https://op.example.com/help", "http://localhost:8080/",
      "/xwiki/bin/view/Main/")).toBe("https://op.example.com/help");
  });
});
