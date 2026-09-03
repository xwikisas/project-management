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
package com.xwiki.projectmanagement.internal.displayers;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.internal.WorkItemsDisplayer;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;

/**
 * Wraps the LIVEDATA macro in order to use it with the
 * {@link com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncExecutor}.
 *
 * @version $Id$
 * @since 1.3.0
 */
@Singleton
@Named(WorkItemLivedataDisplayer.ROLE_HINT)
@Component
public class WorkItemLivedataDisplayer extends AbstractMacro<ProjectManagementMacroParameters>
{
    /**
     * The hint of this component.
     */
    public static final String ROLE_HINT = "proj-manag-liveData";

    @Inject
    private ComponentManager componentManager;

    /**
     * Default constructor.
     */
    public WorkItemLivedataDisplayer()
    {
        super("Work Items Livedata macro", "Wraps the livedata macro to provide project management logic.");
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public List<Block> execute(ProjectManagementMacroParameters parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {
        try {
            Macro<ProjectManagementAsyncMacroParams> displayerMacro =
                componentManager.getInstance(Macro.class, "liveData");
            String newContent = content;
            if (parameters.getFilters() != null && !parameters.getFilters().isEmpty()) {
                newContent = parameters.getFilters();
                parameters.setFilters("");
            }
            if (WorkItemsDisplayer.liveDataCards.equals(parameters.getWorkItemsDisplayer())) {
                parameters.setLayouts("cards,table");
            }
            return displayerMacro.execute(parameters, newContent, context);
        } catch (ComponentLookupException e) {
            throw new MacroExecutionException("Could not find the [liveData] macro.", e);
        }
    }

    @Override
    protected void setDefaultCategories(Set<String> defaultCategories)
    {
        super.setDefaultCategories(Collections.singleton("Internal"));
    }
}
