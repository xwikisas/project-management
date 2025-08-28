package com.xwiki.projectmanagement.openproject.internal.displayer.property;

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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;

import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayer;
import com.xwiki.projectmanagement.openproject.internal.displayer.IdGenerator;

/**
 * Display the status property in a similar way to Open Project.
 *
 * @version $Id$
 */
public class StatusPropertyDisplayer implements WorkItemPropertyDisplayer
{
    private final Parser plainTextParser;

    private final Logger logger = LoggerFactory.getLogger(StatusPropertyDisplayer.class);

    /**
     * @param parser the parser that will be used to parse the type value.
     */
    public StatusPropertyDisplayer(Parser parser)
    {
        plainTextParser = parser;
    }

    @Override
    public List<Block> display(Object property, Map<String, String> params)
    {
        if (property == null) {
            return Collections.emptyList();
        }
        try {
            String lowerCaseProp = IdGenerator.generate(property.toString());
            String instance = params.getOrDefault("instance", "");
            if (!instance.isEmpty()) {
                lowerCaseProp = lowerCaseProp + '-' + IdGenerator.generate(instance);
            }
            List<Block> typeBlocks = plainTextParser.parse(new StringReader(property.toString())).getChildren();
            if (!typeBlocks.isEmpty() && typeBlocks.get(0) instanceof ParagraphBlock) {
                typeBlocks = typeBlocks.get(0).getChildren();
            }
            return Collections.singletonList(new FormatBlock(typeBlocks, Format.NONE,
                Map.of("class", "openproject-property-status openproject-property-status-" + lowerCaseProp)));
        } catch (ParseException e) {
            logger.warn("Failed to parse the work package property with value [{}]. Cause: [{}].", property,
                ExceptionUtils.getRootCauseMessage(e));
            return Collections.emptyList();
        }
    }
}
