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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;

import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayer;
import com.xwiki.projectmanagement.model.Linkable;

/**
 * Generates a link block with the reference taken from the {@link Linkable#getLocation()} and the anchor constructed
 * from {@link Linkable#getValue()}.
 *
 * @version $Id$
 */
public class LinkablePropertyDisplayer implements WorkItemPropertyDisplayer
{
    private final Logger logger = LoggerFactory.getLogger(LinkablePropertyDisplayer.class);

    private final Parser plainTextParser;

    /**
     * @param parser a plain text parser used to generate the anchor blocks from the {@link Linkable#getValue()}.
     */
    public LinkablePropertyDisplayer(Parser parser)
    {
        this.plainTextParser = parser;
    }

    @Override
    public List<Block> display(Object property, Map<String, String> params)
    {
        if (!(property instanceof Linkable) || "".equals(((Linkable) property).getValue()) || "".equals(
            ((Linkable) property).getLocation()))
        {
            return Collections.emptyList();
        }
        Linkable linkableProp = (Linkable) property;
        List<Block> linkAnchorBlocks = Collections.emptyList();
        try {
            linkAnchorBlocks =
                plainTextParser.parse(new StringReader(linkableProp.getValue().toString())).getChildren();
        } catch (ParseException parseException) {
            logger.warn("Failed to parse the value [{}] of a linkable property. Cause: [{}].",
                linkableProp.getValue(), ExceptionUtils.getRootCauseMessage(parseException));
        }
        if (!linkAnchorBlocks.isEmpty() && linkAnchorBlocks.get(0) instanceof ParagraphBlock) {
            linkAnchorBlocks = linkAnchorBlocks.get(0).getChildren();
        }
        boolean freeStanding = linkAnchorBlocks.isEmpty();
        return Collections.singletonList(
            new LinkBlock(linkAnchorBlocks, new ResourceReference(linkableProp.getLocation(), ResourceType.URL),
                freeStanding));
    }
}
