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
package com.xwiki.projectmanagement.test.openproject;

import java.util.List;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test the creation of a page of the "OpenProject Space" type.
 *
 * @version $Id$
 */
@UITest(
    properties = {
        AbstractOpenProjectIT.PRCHECKER_EXCLUDE_PROPERTY,
        AbstractOpenProjectIT.PLUGINS_PROPERTY
    },
    extraJARs = {
        AbstractOpenProjectIT.JYTHON_JAR,
        AbstractOpenProjectIT.SCHEDULER_API_JAR
    }
)
public class OpenProjectSpaceIT extends AbstractOpenProjectIT
{
    @Test
    @Order(1)
    void projectPickerWithUnauthorizedConnection(TestUtils setup, TestReference testReference) throws Exception
    {
        // A connection that the current user hasn't authorized: the requests of the project picker fail.
        String unauthorizedConnection = "spaceUnauthorized";
        DocumentReference connectionReference = getConnectionReference("SpaceUnauthorizedConnection");
        saveConnection(setup, connectionReference, unauthorizedConnection);
        try {
            setup.deletePage(testReference, true);
            OpenProjectSpaceEditPage editPage = OpenProjectSpaceEditPage.create(testReference);

            // The search ends, without suggestion, instead of loading forever.
            editPage.setConnection(unauthorizedConnection);
            assertEquals(List.of(), editPage.searchProjects("Demo"));

            editPage.clickCancel();
        } finally {
            deleteConnection(setup, connectionReference);
        }
    }
}
