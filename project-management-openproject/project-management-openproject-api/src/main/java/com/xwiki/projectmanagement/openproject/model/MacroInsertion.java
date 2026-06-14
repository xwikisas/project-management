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
package com.xwiki.projectmanagement.openproject.model;

import java.util.Map;

/**
 * Models the location of a selected text in a page.
 *
 * @version $Id$
 * @since 1.2.0
 */
public class MacroInsertion
{
    private String selectedText;

    private int offset;

    private String fullLine;

    private String macroId;

    private boolean force;

    private Map<String, String> macroParameters;

    /**
     * @return the text that was selected by the user.
     */
    public String getSelectedText()
    {
        return selectedText;
    }

    /**
     * @param selectedText see {@link #getSelectedText()}.
     */
    public void setSelectedText(String selectedText)
    {
        this.selectedText = selectedText;
    }

    /**
     * @return the offset of the selection in the page.
     */
    public int getOffset()
    {
        return offset;
    }

    /**
     * @param offset see {@link #getOffset()}.
     */
    public void setOffset(int offset)
    {
        this.offset = offset;
    }

    /**
     * @return the macro id that is used to call/execute the macro.
     */
    public String getMacroId()
    {
        return macroId;
    }

    /**
     * @param macroId {@link #getMacroId()}.
     */
    public void setMacroId(String macroId)
    {
        this.macroId = macroId;
    }

    /**
     * @return a map of parameters that will be passed to the macro.
     */
    public Map<String, String> getMacroParameters()
    {
        return macroParameters;
    }

    /**
     * @param macroParameters see {@link #getMacroParameters()}.
     */
    public void setMacroParameters(Map<String, String> macroParameters)
    {
        this.macroParameters = macroParameters;
    }

    /**
     * @return true if the insertion should be made even if the document is locked by another user.
     */
    public boolean isForce()
    {
        return force;
    }

    /**
     * @param force see {@link #isForce()}.
     */
    public void setForce(boolean force)
    {
        this.force = force;
    }

    /**
     * @return the full line of the selected text.
     */
    public String getFullLine()
    {
        return fullLine;
    }

    /**
     * @param fullLine see {@link #getFullLine()}.
     */
    public void setFullLine(String fullLine)
    {
        this.fullLine = fullLine;
    }
}
