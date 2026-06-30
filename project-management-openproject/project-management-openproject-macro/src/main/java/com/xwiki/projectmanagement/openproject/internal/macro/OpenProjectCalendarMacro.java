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

import com.xwiki.projectmanagement.calendar.internal.macro.AbstractProjectManagementCalendarMacro;
import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.openproject.OpenProjectEventType;
import com.xwiki.projectmanagement.openproject.internal.UserTokenChecker;
import com.xwiki.projectmanagement.openproject.internal.displayer.StylingSetupManager;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectCalendarMacroParameters;
import org.apache.http.client.utils.URIBuilder;
import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Display data retrieved from OpenProject in calendars.
 *
 * @version $Id$
 * @since 1.2.0-rc-9
 */
@Singleton
@Component
@Named("openprojectcalendar")
public class OpenProjectCalendarMacro extends AbstractProjectManagementCalendarMacro<OpenProjectCalendarMacroParameters>
{
    @Inject
    private UserTokenChecker userTokenChecker;

    @Inject
    @Named("jsrx")
    private SkinExtension jsrx;

    @Inject
    @Named("jsx")
    private SkinExtension jsx;

    @Inject
    private StylingSetupManager stylingSetupManager;

    /**
     * Default constructor.
     */
    public OpenProjectCalendarMacro()
    {
        super("OpenProject Calendar", "Display OpenProject events as a calendar",
            OpenProjectCalendarMacroParameters.class);
    }

    @Override
    public List<Block> execute(OpenProjectCalendarMacroParameters parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {
        List<Block> warningBlock = this.userTokenChecker.getWarningBlock(parameters.getInstance());
        if (!warningBlock.isEmpty()) {
            return warningBlock;
        }
        this.stylingSetupManager.setupInstanceStyles();
        this.stylingSetupManager.useInstanceStyle(parameters.getInstance());
        // insert extra params for context.
        parameters.setClient("openproject");
        if (this.macroContext instanceof DefaultProjectManagementClientExecutionContext) {
            Map<String, Object> clientContext = populateContext(parameters);
            ((DefaultProjectManagementClientExecutionContext) this.macroContext).setContext(clientContext);
        }
        this.jsrx.use("openproject/js/calendarEvent.js");
        this.jsx.use("OpenProject.Code.ViewAction");
        return Collections.singletonList(new GroupBlock(super.execute(parameters, content, context),
            Map.of("class", "openproject-calendar-macro", "data-instance", parameters.getInstance())));
    }

    @Override
    protected void updateUrl(URIBuilder sb, OpenProjectCalendarMacroParameters parameters)
    {
        boolean hasWorkPackage = parameters.getTypes().contains(OpenProjectEventType.WORK_PACKAGE);
        if (!hasWorkPackage) {
            excludeWorkItems(sb);
        }
    }

    private Map<String, Object> populateContext(OpenProjectCalendarMacroParameters parameters)
    {
        List<OpenProjectEventType> types = parameters.getTypes();
        boolean hasSprint = types.contains(OpenProjectEventType.SPRINT);
        boolean hasVersion = types.contains(OpenProjectEventType.VERSION);
        String versionColor = parameters.getVersionColor() == null ? "" : parameters.getVersionColor();
        String sprintColor = parameters.getSprintColor() == null ? "" : parameters.getSprintColor();
        return Map.of("instance", parameters.getInstance(), "sprint", hasSprint, "version", hasVersion, "versionColor",
            versionColor, "sprintColor", sprintColor);
    }

}
