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

package com.xwiki.projectmanagement.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.xwiki.test.docker.junit5.UITest;

import com.xwiki.projectmanagement.test.openproject.OpenProjectAdministrationIT;
import com.xwiki.projectmanagement.test.openproject.OpenProjectCKEditorIT;
import com.xwiki.projectmanagement.test.openproject.OpenProjectChartMacroIT;
import com.xwiki.projectmanagement.test.openproject.OpenProjectMacroIT;

/**
 * Groups all the integration tests for the project management implementations. The nested test classes share the same
 * XWiki instance and the same OpenProject instance.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
@UITest
public class AllIT
{
    @Nested
    @Order(1)
    @DisplayName("OpenProject administration")
    class NestedOpenProjectAdministrationIT extends OpenProjectAdministrationIT
    {
    }

    @Nested
    @Order(2)
    @DisplayName("OpenProject macro")
    class NestedOpenProjectMacroIT extends OpenProjectMacroIT
    {
    }

    @Nested
    @Order(3)
    @DisplayName("OpenProject chart macro")
    class NestedOpenProjectChartMacroIT extends OpenProjectChartMacroIT
    {
    }

    @Nested
    @Order(4)
    @DisplayName("OpenProject CKEditor plugin")
    class NestedOpenProjectCKEditorIT extends OpenProjectCKEditorIT
    {
    }
}
