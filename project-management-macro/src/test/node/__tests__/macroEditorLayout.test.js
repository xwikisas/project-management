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

const layout = loadAmdModule("macro/macroEditorLayout.js");

describe("getMacroId", () => {
  it("drops the syntax of the data-macroId attribute", () => {
    expect(layout.getMacroId("openproject/xwiki/2.1")).toBe("openproject");
    expect(layout.getMacroId("openprojectchart")).toBe("openprojectchart");
  });

  it("returns an empty id without attribute", () => {
    expect(layout.getMacroId(undefined)).toBe("");
  });
});

describe("getTabToActivate", () => {
  it("keeps the active tab when it is shown", () => {
    expect(layout.getTabToActivate([
      { shown: true, active: false },
      { shown: true, active: true },
    ])).toBe(-1);
  });

  it("activates the first shown tab when the active one is hidden", () => {
    expect(layout.getTabToActivate([
      { shown: false, active: true },
      { shown: false, active: false },
      { shown: true, active: false },
      { shown: true, active: false },
    ])).toBe(2);
  });

  it("activates the first shown tab when none is active", () => {
    expect(layout.getTabToActivate([
      { shown: true, active: false },
      { shown: true, active: false },
    ])).toBe(0);
  });

  it("does nothing when no tab is shown", () => {
    expect(layout.getTabToActivate([{ shown: false, active: true }])).toBe(-1);
    expect(layout.getTabToActivate([])).toBe(-1);
  });
});

describe("isMoreOptionsExpanded", () => {
  it("is collapsed when no parameter is set", () => {
    expect(layout.isMoreOptionsExpanded(["", "  ", undefined, null])).toBe(false);
    expect(layout.isMoreOptionsExpanded([])).toBe(false);
  });

  it("is expanded when a parameter is set", () => {
    expect(layout.isMoreOptionsExpanded(["", "10,25,50"])).toBe(true);
  });
});
