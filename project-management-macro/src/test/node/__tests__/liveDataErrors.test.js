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
import { describe, expect, it } from "vitest";
import { loadAmdModule } from "../amdModule.js";

const liveDataErrors = loadAmdModule("liveDataErrors.js");

// A request of the XWiki Live Data source that the server answered (readyState 4), or that was aborted.
const answered = (status) => ({ readyState: 4, status });
const ABORTED = { readyState: 0, status: 0, statusText: "abort" };
const ENTRIES = { count: 284, entries: [{ identifier: "1" }] };

/**
 * @return the Live Data instance of the given source and the results reported for its requests
 */
function watchLiveData(getEntries, sourceId = "projectmanagement") {
  const liveData = {
    data: { query: { source: { id: sourceId } } },
    liveDataSource: { getEntries },
  };
  const results = [];
  const watched = liveDataErrors.watch(liveData, (failure) => results.push(failure));
  return { liveData, results, watched };
}

describe("getFailure", () => {
  it("tells a request rejected by the tool from the other failures", () => {
    expect(liveDataErrors.getFailure(answered(400))).toBe("rejected");
    expect(liveDataErrors.getFailure(answered(502))).toBe("failed");
    expect(liveDataErrors.getFailure(answered(500))).toBe("failed");
  });

  it("ignores the requests that didn't complete", () => {
    expect(liveDataErrors.getFailure(ABORTED)).toBeUndefined();
    expect(liveDataErrors.getFailure(new Error("Not a request"))).toBeUndefined();
    expect(liveDataErrors.getFailure(undefined)).toBeUndefined();
  });
});

describe("watch", () => {
  it("replaces the entries of a request rejected by the tool by no entry and reports why", async () => {
    // E.g. a value typed in the filter of a column, that the tool rejects: the previous entries must not stay.
    const { liveData, results, watched } = watchLiveData(() => Promise.reject(answered(400)));

    expect(watched).toBe(true);
    await expect(liveData.liveDataSource.getEntries({})).resolves.toEqual({ count: 0, entries: [] });
    expect(results).toEqual(["rejected"]);
  });

  it("reports the failure of the tool", async () => {
    const { liveData, results } = watchLiveData(() => Promise.reject(answered(502)));

    await expect(liveData.liveDataSource.getEntries({})).resolves.toEqual({ count: 0, entries: [] });
    expect(results).toEqual(["failed"]);
  });

  it("returns the entries of a successful request and reports that it succeeded", async () => {
    let answer = () => Promise.reject(answered(400));
    const { liveData, results } = watchLiveData(function (query) {
      // The source is still called on itself, with the query.
      expect(this).toBe(liveData.liveDataSource);
      expect(query).toEqual({ offset: 0 });
      return answer();
    });

    await liveData.liveDataSource.getEntries({ offset: 0 });
    answer = () => Promise.resolve(ENTRIES);
    await expect(liveData.liveDataSource.getEntries({ offset: 0 })).resolves.toBe(ENTRIES);

    // The message of the failure is removed once a request succeeds.
    expect(results).toEqual(["rejected", undefined]);
  });

  it("lets Live Data ignore the requests that didn't complete", async () => {
    const { liveData, results } = watchLiveData(() => Promise.reject(ABORTED));

    await expect(liveData.liveDataSource.getEntries({})).rejects.toBe(ABORTED);
    expect(results).toEqual([]);
  });

  it("watches the source of an instance once", async () => {
    const { liveData, results } = watchLiveData(() => Promise.reject(answered(400)));

    // Live Data fires the event before each request.
    expect(liveDataErrors.watch(liveData, (failure) => results.push("again " + failure))).toBe(false);
    await liveData.liveDataSource.getEntries({});

    expect(results).toEqual(["rejected"]);
  });

  it("leaves the other Live Data sources alone", async () => {
    const failure = answered(400);
    const { liveData, results, watched } = watchLiveData(() => Promise.reject(failure), "liveTable");

    expect(watched).toBe(false);
    await expect(liveData.liveDataSource.getEntries({})).rejects.toBe(failure);
    expect(results).toEqual([]);
    expect(liveDataErrors.watch(undefined, () => {})).toBe(false);
  });
});
