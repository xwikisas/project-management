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
package com.xwiki.projectmanagement.openproject.internal.macro;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Provider;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.observation.ObservationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.XWikiRequest;
import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.openproject.OpenProjectEventType;
import com.xwiki.projectmanagement.openproject.internal.LicenseChecker;
import com.xwiki.projectmanagement.openproject.internal.UserTokenChecker;
import com.xwiki.projectmanagement.openproject.internal.displayer.StylingSetupManager;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectCalendarMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectCalendarMacro}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectCalendarMacroTest
{
    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @InjectMockComponents
    private OpenProjectCalendarMacro macro;

    @MockComponent
    private LicenseChecker licenseChecker;

    @MockComponent
    private UserTokenChecker userTokenChecker;

    @MockComponent
    private ObservationManager observationManager;

    @MockComponent
    private StylingSetupManager stylingSetupManager;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    @Named("jsrx")
    private SkinExtension jsrx;

    @MockComponent
    @Named("jsx")
    private SkinExtension jsx;

    @Mock
    private XWikiContext xcontext;

    @Mock
    private XWikiRequest request;

    private final MacroTransformationContext context = new MacroTransformationContext();

    private final DefaultProjectManagementClientExecutionContext executionContext =
        new DefaultProjectManagementClientExecutionContext();

    private final OpenProjectCalendarMacroParameters parameters = new OpenProjectCalendarMacroParameters();

    @BeforeComponent
    void registerExecutionContext() throws Exception
    {
        this.componentManager.registerComponent(ProjectManagementClientExecutionContext.class,
            this.executionContext);
    }

    @BeforeEach
    void setUp()
    {
        this.executionContext.setContext(null);
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getRequest()).thenReturn(this.request);
        when(this.xcontext.getWikiId()).thenReturn("mywiki");
        when(this.request.getContextPath()).thenReturn("/xwiki");
        this.parameters.setInstance("main");
        when(this.licenseChecker.getMissingLicenseBlock(this.context)).thenReturn(List.of());
    }

    private MacroBlock getCalendarMacro(List<Block> result)
    {
        assertEquals(1, result.size());
        GroupBlock group = (GroupBlock) result.get(0);
        assertEquals(Map.of("class", "openproject-calendar-macro", "data-instance", "main"), group.getParameters());
        assertEquals(1, group.getChildren().size());
        return (MacroBlock) group.getChildren().get(0);
    }

    private static Map<String, String> getQueryParameters(String url) throws Exception
    {
        URIBuilder uriBuilder = new URIBuilder(url);
        assertEquals("/xwiki/rest/wikis/mywiki/projectmanagement/openproject/calendar", uriBuilder.getPath());
        return uriBuilder.getQueryParams().stream()
            .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
    }

    @Test
    void descriptor()
    {
        assertFalse(this.macro.supportsInlineMode());
        assertEquals(Set.of("OpenProject", "Content"), this.macro.getDescriptor().getDefaultCategories());
    }

    @Test
    void displaysTheWorkPackagesByDefault() throws Exception
    {
        MacroBlock calendar = getCalendarMacro(this.macro.execute(this.parameters, "", this.context));

        assertEquals("calendar", calendar.getId());
        assertEquals("false", calendar.getParameter("editable"));
        assertEquals(Map.of("instance", "main", "sprint", "false", "version", "false", "versionColor", "",
            "sprintColor", "", "limit", "25"), getQueryParameters(calendar.getParameter("json")));
        assertEquals("openproject", this.parameters.getClient());
        verify(this.observationManager).notify(any(), same(this.macro), same(this.parameters));
        verify(this.stylingSetupManager).setupInstanceStyles();
        verify(this.stylingSetupManager).useInstanceStyle("main");
        verify(this.jsrx).use("openproject/js/calendarMacro.js");
        verify(this.jsx).use("OpenProject.Code.ViewAction");
    }

    @Test
    void displaysSprintsAndVersionsOnly() throws Exception
    {
        this.parameters.setTypes(List.of(OpenProjectEventType.SPRINT, OpenProjectEventType.VERSION));
        this.parameters.setSprintColor("#ff0000");
        this.parameters.setVersionColor("#00ff00");
        this.parameters.setFilters("{\"query\":{}}");
        this.parameters.setLimit(10);

        MacroBlock calendar = getCalendarMacro(this.macro.execute(this.parameters, "", this.context));

        // Without the work package type, the work items are excluded from the calendar events.
        assertEquals(Map.of("instance", "main", "sprint", "true", "version", "true", "versionColor", "#00ff00",
            "sprintColor", "#ff0000", "limit", "10", "filters", "{\"query\":{}}", "excludeWorkItems", "true"),
            getQueryParameters(calendar.getParameter("json")));
    }

    @Test
    void returnsTheMissingLicenseMessage() throws Exception
    {
        List<Block> licenseBlock = List.of(new WordBlock("license"));
        when(this.licenseChecker.getMissingLicenseBlock(this.context)).thenReturn(licenseBlock);

        assertSame(licenseBlock, this.macro.execute(this.parameters, "", this.context));

        verifyNoInteractions(this.observationManager, this.userTokenChecker, this.stylingSetupManager, this.jsrx,
            this.jsx);
        assertNull(this.executionContext.get("instance"));
    }

    @Test
    void returnsTheUserTokenWarning() throws Exception
    {
        List<Block> warningBlock = List.of(new WordBlock("warning"));
        when(this.userTokenChecker.getWarningBlock("main", this.context)).thenReturn(warningBlock);

        assertSame(warningBlock, this.macro.execute(this.parameters, "", this.context));

        verifyNoInteractions(this.stylingSetupManager, this.jsrx, this.jsx);
    }
}
