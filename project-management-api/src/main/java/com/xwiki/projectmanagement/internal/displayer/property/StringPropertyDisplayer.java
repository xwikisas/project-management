package com.xwiki.projectmanagement.internal.displayer.property;

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

import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;

import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayer;

/**
 * Display the given property as a simple string.
 *
 * @version $Id$
 */
public class StringPropertyDisplayer implements WorkItemPropertyDisplayer
{
    private Logger logger = LoggerFactory.getLogger(StringPropertyDisplayer.class);

    private final Parser plainTextParser;

    /**
     * @param parser the parser that will be used to process the displayed value.
     */
    public StringPropertyDisplayer(Parser parser)
    {
        plainTextParser = parser;
    }

    @Override
    public List<Block> display(Object property, Map<String, String> params)
    {
        try {
            List<Block> blocks = plainTextParser.parse(new StringReader(property.toString())).getChildren();
            if (blocks == null) {
                return Collections.emptyList();
            }

            if (!blocks.isEmpty() && blocks.get(0) instanceof ParagraphBlock) {
                blocks = blocks.get(0).getChildren();
            }
            return blocks;
        } catch (ParseException e) {
            logger.warn("Failed to parse the property with value [{}] of a work item.", property);
            return Collections.emptyList();
        }
    }
}
