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
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const SOURCE = resolve(import.meta.dirname, "../../../main/resources/openproject/js/linkDescription.js");

const REST_URL = "/xwiki/rest/wikis/xwiki/openproject/link";

const LINK = "https://op.example.com/projects/demo/work_packages?query_id=5";

// Loads the module served to the browsers (a named AMD module without dependency).
function loadLinkDescription() {
  let module;
  const define = (name, dependencies, factory) => {
    module = factory();
  };
  new Function("define", readFileSync(SOURCE, "utf-8"))(define);
  return module;
}

const linkDescription = loadLinkDescription();

describe("getRequestUrl", () => {
  it("encodes the connection and the link", () => {
    expect(linkDescription.getRequestUrl(REST_URL, "Main OP", LINK)).toBe(REST_URL
      + "?instance=Main+OP&url=https%3A%2F%2Fop.example.com%2Fprojects%2Fdemo%2Fwork_packages%3Fquery_id%3D5");
  });

  it("sends an empty connection for the default one", () => {
    expect(linkDescription.getRequestUrl(REST_URL + "?media=json", undefined, "x"))
      .toBe(REST_URL + "?media=json&instance=&url=x");
  });
});

describe("getMessageClass", () => {
  it("displays the information as an XWiki information message", () => {
    expect(linkDescription.getMessageClass("info")).toBe("box infomessage");
  });

  it("displays the other levels as an XWiki warning message", () => {
    expect(linkDescription.getMessageClass("warning")).toBe("box warningmessage");
    expect(linkDescription.getMessageClass(undefined)).toBe("box warningmessage");
  });
});

describe("createDescriber", () => {
  let fetchDescription;
  let describeLink;

  beforeEach(() => {
    vi.useFakeTimers();
    fetchDescription = vi.fn((requestUrl) => Promise.resolve({ requestUrl }));
    describeLink = linkDescription.createDescriber(fetchDescription, 300);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("requests the description once the link stops changing", async () => {
    const first = describeLink(REST_URL, "", "https://op.example.com/wp/1");
    const second = describeLink(REST_URL, "", " https://op.example.com/wp/12 ");
    await vi.advanceTimersByTimeAsync(300);

    await expect(first).resolves.toBeNull();
    await expect(second).resolves.toEqual({
      requestUrl: REST_URL + "?instance=&url=https%3A%2F%2Fop.example.com%2Fwp%2F12"
    });
    expect(fetchDescription).toHaveBeenCalledTimes(1);
  });

  it("ignores the reply to an outdated request", async () => {
    let resolveFirst;
    fetchDescription.mockImplementationOnce(() => new Promise((resolve) => {
      resolveFirst = resolve;
    }));
    const first = describeLink(REST_URL, "", "https://op.example.com/wp/1");
    await vi.advanceTimersByTimeAsync(300);
    const second = describeLink(REST_URL, "", "https://op.example.com/wp/2");
    await vi.advanceTimersByTimeAsync(300);
    resolveFirst({ kind: "workPackage" });

    await expect(first).resolves.toBeNull();
    await expect(second).resolves.toEqual({
      requestUrl: REST_URL + "?instance=&url=https%3A%2F%2Fop.example.com%2Fwp%2F2"
    });
  });

  it("rejects when the latest request fails, and ignores an outdated failure", async () => {
    let rejectFirst;
    fetchDescription.mockImplementationOnce(() => new Promise((resolve, reject) => {
      rejectFirst = reject;
    }));
    fetchDescription.mockImplementationOnce(() => Promise.reject(new Error("404")));
    const first = describeLink(REST_URL, "", "https://op.example.com/wp/1");
    await vi.advanceTimersByTimeAsync(300);
    const second = describeLink(REST_URL, "", "https://op.example.com/wp/2");
    const secondResult = expect(second).rejects.toThrow("404");
    await vi.advanceTimersByTimeAsync(300);
    rejectFirst(new Error("500"));

    await expect(first).resolves.toBeNull();
    await secondResult;
  });

  it("doesn't describe an empty link", async () => {
    const pending = describeLink(REST_URL, "", LINK);

    await expect(describeLink(REST_URL, "", "  ")).resolves.toBeNull();
    await expect(pending).resolves.toBeNull();
    await vi.advanceTimersByTimeAsync(300);
    expect(fetchDescription).not.toHaveBeenCalled();
  });
});
