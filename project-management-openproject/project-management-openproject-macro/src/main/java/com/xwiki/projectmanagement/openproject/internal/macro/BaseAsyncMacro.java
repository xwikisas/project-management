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

import javax.inject.Inject;

import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.descriptor.MacroDescriptor;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncExecutor;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;

/**
 * ASDds.
 *
 * @param <T> DASDAS.
 * @version $Id$
 * @since 1.2.0
 */
public class BaseAsyncMacro<T extends ProjectManagementAsyncMacroParams> extends AbstractMacro<T>
{
    @Inject
    private ProjectManagementAsyncExecutor asyncExecutor;

    /**
     * asdsad.
     *
     * @param name dsasdsad
     */
    public BaseAsyncMacro(String name)
    {
        super(name);
    }

    /**
     * @return sada.
     */
    public List<Block> execute()
    {
        return null;
//        asyncExecutor.execute(new)
    }

    @Override
    public int getPriority()
    {
        return 0;
    }

    @Override
    public MacroDescriptor getDescriptor()
    {
        return null;
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public List<Block> execute(T parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        return List.of();
    }

    @Override
    public int compareTo(Macro<?> macro)
    {
        return 0;
    }
}
