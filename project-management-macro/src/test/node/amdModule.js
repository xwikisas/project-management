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
// Loads the scripts served to the browsers that are AMD modules without dependency, so that they can be tested outside
// of a browser.
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const SOURCES = resolve(import.meta.dirname, "../../main/resources/js/projectmanagement");

/**
 * @param path the path of the script, relative to src/main/resources/js/projectmanagement
 * @return the value of the module defined by the script
 */
export function loadAmdModule(path) {
  let module;
  const define = (dependencies, factory) => {
    module = factory();
  };
  new Function("define", readFileSync(resolve(SOURCES, path), "utf-8"))(define);
  return module;
}
