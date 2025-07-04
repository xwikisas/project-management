package com.xwiki.projectmanagement.internal.utility;

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

import java.util.ArrayList;
import java.util.List;

import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.TableBlock;
import org.xwiki.rendering.block.TableCellBlock;
import org.xwiki.rendering.block.TableRowBlock;

/**
 * Utility class used for building table blocks.
 *
 * @version $Id$
 */
public class TableBuilder
{
    private TableBlock root;

    private TableRowBlock currentRow;

    /**
     * Create a new table builder.
     */
    public TableBuilder()
    {
        root = new TableBlock(new ArrayList<>());
    }

    /**
     * Add a new row to the table - this should be called before adding any cells.
     *
     * @return this object.
     */
    public TableBuilder newRow()
    {
        if (currentRow != null) {
            root.addChild(currentRow);
        }
        currentRow = new TableRowBlock(new ArrayList<>());
        return this;
    }

    /**
     * Create a new cell inside the current row.
     *
     * @param content the list of blocks that should be added to this table cell.
     * @return this object.
     * @throws UnsupportedOperationException if no row was created before adding a cell.
     */
    public TableBuilder newCell(List<Block> content)
    {
        if (currentRow == null) {
            throw new UnsupportedOperationException("Can't create a new cell without having a row.");
        }
        currentRow.addChild(new TableCellBlock(content));
        return this;
    }

    /**
     * @return the built table structure.
     */
    public TableBlock build()
    {
        if (currentRow != null) {
            root.addChild(currentRow);
        }
        return root;
    }
}
