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
package com.xwiki.projectmanagement;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.icon.IconManager;
import org.xwiki.rendering.test.integration.junit5.RenderingTests;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Run all tests found in the {@code *.test} files located in the {@code projectmanagement} folder of the classpath.
 * These {@code *.test} files must follow the conventions described in
 * {@link org.xwiki.rendering.test.integration.TestDataParser}.
 *
 * @version $Id$
 */
@AllComponents
@RenderingTests.Scope("projectmanagement")
public class IntegrationTests implements RenderingTests
{
    /**
     * The components loaded by {@link AllComponents} log errors that don't affect the rendering, such as the missing
     * {@code xwiki.cfg} file or the missing Expression Language implementation needed by the Bean Validation. Only
     * these known errors are accepted, so that any other warning or error still fails the tests.
     */
    private static final String[] EXPECTED_LOG_MESSAGES = {
        "Failed to load configuration",
        "Failed to build the Bean Validation provider. There will be no Java bean validation."
    };

    @RegisterExtension
    static LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @AfterEach
    void afterEach()
    {
        for (int i = 0; i < logCapture.size(); i++) {
            assertThat(logCapture.getMessage(i), is(oneOf(EXPECTED_LOG_MESSAGES)));
        }
        logCapture.ignoreAllMessages();
    }

    @RenderingTests.Initialized
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
        when(wikiCfg.getProperty(eq("dateformat"), anyString())).thenReturn("dd/MM/yyyy");

        componentManager.registerMockComponent(SkinExtension.class, "ssrx");
    }
}