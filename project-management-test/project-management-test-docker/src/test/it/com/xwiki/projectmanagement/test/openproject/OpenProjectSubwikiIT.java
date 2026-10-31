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

import org.junit.jupiter.api.Order;
import org.junit.jupiter.params.ParameterizedTest;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.test.docker.junit5.TestLocalReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.docker.junit5.WikisSource;
import org.xwiki.test.ui.TestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test the OpenProject integration in a subwiki where the extension isn't installed: the tests install it on the main
 * wiki only.
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
public class OpenProjectSubwikiIT extends AbstractOpenProjectIT
{
    @ParameterizedTest
    @WikisSource(value = 1, mainWiki = false)
    @Order(1)
    void workPackageButtonsAreOnlyInTheWikisOfTheExtension(WikiReference subwiki, TestUtils setup,
        TestLocalReference testLocalReference) throws Exception
    {
        // Logged in, since the work package buttons aren't displayed to guests.
        DocumentReference mainWikiPage = new DocumentReference(testLocalReference, new WikiReference("xwiki"));
        setup.rest().savePage(mainWikiPage, "Page of the main wiki", "Main wiki page");
        setup.gotoPage(mainWikiPage);
        ViewPageWithOpenProjectMacro page = new ViewPageWithOpenProjectMacro();
        assertEquals(1, page.getCreateWorkPackagesMenuCount());
        assertEquals(1, page.getEditWorkPackageDialogCount());

        String currentWiki = setup.getCurrentWiki();
        setup.setCurrentWiki(subwiki.getName());
        try {
            DocumentReference subwikiPage = new DocumentReference(testLocalReference, subwiki);
            setup.rest().savePage(subwikiPage, "Page of the subwiki", "Subwiki page");
            setup.gotoPage(subwikiPage);
            page = new ViewPageWithOpenProjectMacro();
            assertEquals(0, page.getCreateWorkPackagesMenuCount());
            assertEquals(0, page.getEditWorkPackageDialogCount());
        } finally {
            setup.setCurrentWiki(currentWiki);
        }
    }
}
