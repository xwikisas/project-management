package com.xwiki.projectmanagement;

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

import java.util.Map;

import org.junit.runner.RunWith;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.icon.IconManager;
import org.xwiki.rendering.test.integration.RenderingTestSuite;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.mockito.Mockito.when;

/**
 * Run all tests found in {@code *.test} files located in the classpath. These {@code *.test} files must follow the
 * conventions described in {@link org.xwiki.rendering.test.integration.TestDataParser}.
 *
 * @version $Id$
 */
@RunWith(RenderingTestSuite.class)
@AllComponents
public class IntegrationTests
{
    @RenderingTestSuite.Initialized
    public void initialize(MockitoComponentManager componentManager) throws Exception
    {
        componentManager.registerMockComponent(IconManager.class);

        ProjectManagementClientExecutionContext clientExecutionContext =
            componentManager.registerMockComponent(ProjectManagementClientExecutionContext.class);
        when(clientExecutionContext.getContext()).thenReturn(Map.of("client", "test", "translationPrefix",
            "projectmanagement."));
        when(clientExecutionContext.get("client")).thenReturn("test");
        when(clientExecutionContext.get("translationPrefix")).thenReturn("projectmanagement.");

        ConfigurationSource wikiCfg = componentManager.registerMockComponent(ConfigurationSource.class, "wiki");
        when(wikiCfg.getProperty("dateformat", "dd/MM/yyyy hh:mm:ss")).thenReturn("dd/MM/yyyy");

        componentManager.registerMockComponent(SkinExtension.class, "ssrx");
    }
}