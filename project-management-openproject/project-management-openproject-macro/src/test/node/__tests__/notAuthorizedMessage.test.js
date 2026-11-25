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
function createNotifier(connection, getMessage, forgetMessage = vi.fn()) {
  const state = { connection, displayed: [] };
  state.notifier = notAuthorizedMessage.createNotifier({
    getConnection: () => state.connection,
    getMessage,
    forgetMessage,
    show: (message, context) => state.displayed.push({ message, context }),
    remove: () => {
      state.displayed = [];
    },
  });
  return state;
}

/**
 * @param answers the successive answers of the check of the connection: the message telling that the user can't read
 *     its data, or an empty message when they can
 * @return a notifier of the QA connection whose messages are loaded by a message loader, with the checks it requested
 */
function createLoadingNotifier(...answers) {
  const fetchMessage = vi.fn();
  answers.forEach((answer) => fetchMessage.mockReturnValueOnce(Promise.resolve(answer)));
  const messages = notAuthorizedMessage.createMessageLoader(fetchMessage);
  const state = createNotifier("QA", messages.get, messages.forget);
  state.checks = fetchMessage.mock.calls;
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

  it("checks again whether the user can read the data once the suggestions were retrieved", async () => {
    // The user isn't authenticated to OpenProject, then connects their account in another tab.
    const state = createLoadingNotifier(MESSAGE, "");
    await state.notifier.suggestionsFailed("picker");
    state.notifier.suggestionsLoaded();

    // E.g. OpenProject fails to answer.
    await state.notifier.suggestionsFailed("picker");

    expect(state.checks).toEqual([["QA"], ["QA"]]);
    expect(state.displayed).toEqual([]);
  });

  it("displays the message once the user can't read the data anymore", async () => {
    // E.g. OpenProject fails to answer, then the access of the user expires.
    const state = createLoadingNotifier("", MESSAGE);
    await state.notifier.suggestionsFailed("picker");
    expect(state.displayed).toEqual([]);

    await state.notifier.suggestionsFailed("picker");

    expect(state.checks).toEqual([["QA"], ["QA"]]);
    expect(state.displayed).toEqual([{ message: MESSAGE, context: "picker" }]);
  });

  it("checks once whether the user can read the data until the suggestions are retrieved", async () => {
    const state = createLoadingNotifier(MESSAGE, MESSAGE);

    await state.notifier.suggestionsFailed("picker");
    await state.notifier.suggestionsFailed("filters");

    expect(state.checks).toEqual([["QA"]]);
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
    const getMessage = notAuthorizedMessage.createMessageLoader(fetchMessage).get;

    expect(await getMessage("QA")).toBe(MESSAGE + "QA");
    expect(await getMessage("QA")).toBe(MESSAGE + "QA");
    expect(await getMessage("Other")).toBe(MESSAGE + "Other");

    expect(fetchMessage.mock.calls).toEqual([["QA"], ["Other"]]);
  });

  it("requests the message again after a failure, which gives no message", async () => {
    const fetchMessage = vi.fn()
      .mockReturnValueOnce(Promise.reject(new Error("Not found")))
      .mockReturnValueOnce(Promise.resolve(MESSAGE));
    const getMessage = notAuthorizedMessage.createMessageLoader(fetchMessage).get;

    expect(await getMessage("QA")).toBe("");
    expect(await getMessage("QA")).toBe(MESSAGE);
  });

  it("gives no message for a response without message", async () => {
    const getMessage = notAuthorizedMessage.createMessageLoader(() => Promise.resolve(undefined)).get;

    expect(await getMessage("QA")).toBe("");
  });

  it("requests the message again after an empty message", async () => {
    const fetchMessage = vi.fn().mockReturnValueOnce(Promise.resolve("")).mockReturnValueOnce(Promise.resolve(MESSAGE));
    const getMessage = notAuthorizedMessage.createMessageLoader(fetchMessage).get;

    expect(await getMessage("QA")).toBe("");
    expect(await getMessage("QA")).toBe(MESSAGE);
  });

  it("requests the message again once forgotten", async () => {
    const fetchMessage = vi.fn().mockReturnValueOnce(Promise.resolve(MESSAGE)).mockReturnValueOnce(Promise.resolve(""));
    const messages = notAuthorizedMessage.createMessageLoader(fetchMessage);
    expect(await messages.get("QA")).toBe(MESSAGE);

    messages.forget("QA");

    expect(await messages.get("QA")).toBe("");
  });

  it("shares the answer of the requests made at once", async () => {
    const fetchMessage = vi.fn(() => Promise.resolve(""));
    const getMessage = notAuthorizedMessage.createMessageLoader(fetchMessage).get;

    expect(await Promise.all([getMessage("QA"), getMessage("QA")])).toEqual(["", ""]);
    expect(fetchMessage).toHaveBeenCalledTimes(1);
  });

  it("keeps the message requested after a forget when an earlier request ends without message", async () => {
    const answers = [];
    // The first two requests answer when the test says so, the next ones at once.
    const fetchMessage = vi.fn(() => answers.length < 2 ? new Promise((resolve) => answers.push(resolve))
      : Promise.resolve(""));
    const messages = notAuthorizedMessage.createMessageLoader(fetchMessage);
    const first = messages.get("QA");
    // A request on the connection succeeds while the message is loading, then another one fails.
    messages.forget("QA");
    const second = messages.get("QA");

    answers[0]("");
    answers[1](MESSAGE);

    expect(await first).toBe("");
    expect(await second).toBe(MESSAGE);
    expect(await messages.get("QA")).toBe(MESSAGE);
    expect(fetchMessage).toHaveBeenCalledTimes(2);
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
