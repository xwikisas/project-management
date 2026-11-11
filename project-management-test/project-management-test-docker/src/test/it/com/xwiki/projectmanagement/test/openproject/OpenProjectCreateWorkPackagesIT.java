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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the creation of OpenProject work packages from the content of a page.
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
public class OpenProjectCreateWorkPackagesIT extends AbstractOpenProjectIT
{
    @Test
    @Order(1)
    void createWorkPackagesFromTable(TestUtils setup, TestReference testReference) throws Exception
    {
        // Characters that break the URLs of the requests of the form when the connection name isn't encoded.
        String connection = CONNECTION_ID + " #2 & co";
        DocumentReference connectionReference = getConnectionReference("SpecialCharactersConnection");
        saveConnection(setup, connectionReference, connection);
        try {
            OpenProjectAdminPage.gotoPage().authorizeConnection(connection);
            this.openProject.authorize(setup.getDriver());
            // A column name that breaks the HTML of the form when it isn't escaped.
            String column = "<b>Owner</b> & \"Due\" 'date'";
            setup.rest().savePage(testReference, String.format("|=Subject|=%s\n|First task|Alice", column), "");
            setup.gotoPage(testReference);

            CreateWorkPackagesModal form = new ViewPageWithOpenProjectMacro().openCreateWorkPackagesFromTable()
                .selectConnection(connection)
                .selectProject("Demo project")
                .addMapping();
            assertEquals(List.of("Subject", column), form.getMappingTableColumns());
            // The work package fields come from the form of the selected OpenProject project.
            List<String> fields = form.getMappingWorkPackageFields();
            assertTrue(fields.contains("Subject"), fields::toString);
        } finally {
            deleteConnection(setup, connectionReference);
        }
    }
}
