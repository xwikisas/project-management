package com.xwiki.projectmanagement.openproject.internal.macro;

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

import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.internal.macro.AbstractProjectManagementChartMacro;
import com.xwiki.projectmanagement.openproject.internal.UserTokenChecker;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectChartMacroParameters;

/**
 * Hello there.
 *
 * @version $Id$
 * @since 1.0
 */
@Singleton
@Component
@Named("openprojectchart")
public class OpenProjectChartMacro extends AbstractProjectManagementChartMacro<OpenProjectChartMacroParameters>
{
    @Inject
    private UserTokenChecker userTokenChecker;

    /**
     * Default constructor.
     */
    public OpenProjectChartMacro()
    {
        super("Open Project Chart Macro", "desc", OpenProjectChartMacroParameters.class);
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public List<Block> execute(OpenProjectChartMacroParameters parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {
        List<Block> warningBlock = userTokenChecker.getWarningBlock(parameters.getInstance());
        if (!warningBlock.isEmpty()) {
            return warningBlock;
        }
        parameters.setClient("openproject");
        if (macroContext instanceof DefaultProjectManagementClientExecutionContext) {
            Map<String, Object> clientContext = Map.of("instance", parameters.getInstance());
            ((DefaultProjectManagementClientExecutionContext) macroContext).setContext(clientContext);
        }
        return super.execute(parameters, content, context);
    }
}
