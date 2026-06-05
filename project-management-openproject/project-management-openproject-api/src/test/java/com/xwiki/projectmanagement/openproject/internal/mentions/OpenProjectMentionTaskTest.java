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
package com.xwiki.projectmanagement.openproject.internal.mentions;

public class OpenProjectMentionTaskTest
{
    void noMacrosInContentAndNoObjects()
    {
        // Nothing should happen. The document shouldnt be saved.
    }

    void macrosWithIdsInContentAndNoObjects()
    {
        // N objects should be created.
    }

    void macrosWithoutIdsInContentAndNoObjects()
    {
        // N - M macros should be created. Where N is total and M is macros without ids.
    }

    void macrosInContentAndAllAssociatedObjects()
    {
        // No changes in the content. Nothing should happen.
    }

    void macrosRemovedFromContent()
    {
        // There are objects of macros that were deleted. The objects should be deleted as well.
    }

    void macroIdUpdated()
    {
        // There is the same number of objects as macros. One of the macro was updated. The associated object should
        // be updated.
    }
}
