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

import javax.naming.OperationNotSupportedException;

import org.junit.jupiter.api.BeforeAll;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.test.docker.junit5.TestConfiguration;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.SuggestInputElement;
import org.xwiki.test.ui.po.editor.WYSIWYGEditPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base class of the OpenProject functional tests. All the test classes share the same OpenProject instance, see
 * {@link OpenProjectFixture}.
 * <p>
 * The {@code @UITest} configuration of the subclasses must be the same, so that the XWiki instance is not restarted
 * when they are run together from {@code AllIT}. Use the constants of this class to declare it.
 *
 * @version $Id$
 * @since 1.3.0-rc-2
 */
public abstract class AbstractOpenProjectIT
{
    /**
     * The scheduler UI needs programming rights.
     */
    public static final String PRCHECKER_EXCLUDE_PROPERTY =
        "xwikiPropertiesAdditionalProperties=test.prchecker.excludePattern=xwiki:Scheduler\\.WebHome";

    /**
     * Add the Scheduler plugin used by the Style sync job and the jsrx and ssrx plugins used by the app.
     */
    public static final String PLUGINS_PROPERTY = "xwikiCfgPlugins=com.xpn.xwiki.plugin.scheduler.SchedulerPlugin,"
        + "com.xpn.xwiki.plugin.skinx.JsResourceSkinExtensionPlugin,"
        + "com.xpn.xwiki.plugin.skinx.CssResourceSkinExtensionPlugin";

    /**
     * Because of https://jira.xwiki.org/browse/XWIKI-17972 we need to install the jython jar manually in WEB-INF/lib.
     */
    public static final String JYTHON_JAR = "org.python:jython-slim:2.7.3";

    /**
     * Needed by the scheduler plugin, otherwise it fails.
     */
    public static final String SCHEDULER_API_JAR = "org.xwiki.platform:xwiki-platform-scheduler-api:15.10";

    protected static final String CONNECTION_ID = OpenProjectFixture.CONNECTION_ID;

    protected OpenProjectInstance openProject;

    @BeforeAll
    void setUpOpenProject(TestUtils setup, TestConfiguration testConfiguration) throws Exception
    {
        setup.loginAsSuperAdmin();
        this.openProject = OpenProjectFixture.ensureReady(setup, testConfiguration);
    }

    protected static TableLayoutElement saveAndGetFirstOPMacro(WYSIWYGEditPage editPage)
        throws OperationNotSupportedException
    {
        editPage.clickSaveAndView();
        ViewPageWithOpenProjectMacro page = new ViewPageWithOpenProjectMacro();

        List<OpenProjectMacroElement> macros = page.getOpenProjectMacros();
        assertEquals(1, macros.size());
        TableLayoutElement ld = macros.get(0).getLivedata().getTableLayout();
        ld.waitUntilReady();
        return ld;
    }

    protected static void selectInstanceFromModal(OpenProjectMacroEditModal macroModal, String connection)
    {
        SuggestInputElement instanceSuggest = macroModal.getSuggestInput("instance").waitForSuggestions();
        assertTrue(instanceSuggest.getSuggestions().stream()
            .anyMatch(suggestion -> connection.equals(suggestion.getLabel())));

        instanceSuggest.selectByValue(connection);
        // The Live Data page object needs the macro to have an id.
        macroModal.setRandomId();
    }
}
