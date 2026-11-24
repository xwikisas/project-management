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
import { describe, expect, it } from "vitest";

const TRANSLATIONS = resolve(import.meta.dirname, "../../../main/resources/ApplicationResources.properties");

function readTranslations() {
  const translations = new Map();
  readFileSync(TRANSLATIONS, "utf-8").split("\n").filter(line => line.includes("=") && !line.startsWith("#"))
    .forEach(line => translations.set(line.substring(0, line.indexOf("=")), line.substring(line.indexOf("=") + 1)));
  return translations;
}

describe("the work item picker", () => {
  it("names its text area (Edit as text) with the visible label of the specific work items", () => {
    // WCAG 2.5.3 (Label in Name): the label displayed above the picker and its text area is in the accessible name of
    // the text area.
    const translations = readTranslations();
    const visibleLabel = translations.get("projectmanagement.macro.source.list.inputLabel");
    const accessibleName = translations.get("projectmanagement.macro.workItemPicker.pasteLabel");

    expect(visibleLabel).toBe("Work items");
    expect(accessibleName).toMatch(new RegExp("^" + visibleLabel));
  });
});
