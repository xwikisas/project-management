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
import com.xwiki.projectmanagement.openproject.internal.UserTokenChecker;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectCalendarMacroParameters;
import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;

/**
 * Display data retrieved from OpenProject in calendars.
 *
 * @version $Id$
 * @since 1.2.0-rc-1
 */
@Singleton
@Component
@Named("openprojectcalendar")
public class OpenProjectCalendarMacro extends AbstractProjectManagementCalendarMacro<OpenProjectCalendarMacroParameters>
{
    @Inject
    private UserTokenChecker userTokenChecker;

    /**
     * Default constructor.
     */
    public OpenProjectCalendarMacro()
    {
        super("Open Project Calendar", "A macro to display the Open Project calendar",
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
        // insert extra params for context
        parameters.setClient("openproject");
        if (this.macroContext instanceof DefaultProjectManagementClientExecutionContext) {
            Map<String, Object> clientContext = Map.of("instance", parameters.getInstance());
            ((DefaultProjectManagementClientExecutionContext) this.macroContext).setContext(clientContext);
        }
        return super.execute(parameters, content, context);
    }
}
