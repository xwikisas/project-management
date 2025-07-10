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
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;

import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayer;

/**
 * Displays a date property taking in consideration some configured display format.
 *
 * @version $Id$
 */
public class DatePropertyDisplayer implements WorkItemPropertyDisplayer
{
    private static final String DEFAULT_DATE_FORMAT = "dd/MM/yyyy hh:mm:ss";

    private final Logger logger = LoggerFactory.getLogger(DatePropertyDisplayer.class);

    private final Parser parser;

    private SimpleDateFormat dateFormatter;

    /**
     * @param parser used to parse the serialized date value as a list of blocks.
     */
    public DatePropertyDisplayer(Parser parser)
    {
        this.parser = parser;
        dateFormatter = new SimpleDateFormat(DEFAULT_DATE_FORMAT);
    }

    /**
     * @param parser used to parse the serialized date value as a list of blocks.
     * @param dateFormat the format that should be used to serialize the date.
     */
    public DatePropertyDisplayer(Parser parser, String dateFormat)
    {
        this(parser);
        try {
            dateFormatter = new SimpleDateFormat(dateFormat);
        } catch (IllegalArgumentException ignored) {
            dateFormatter = new SimpleDateFormat(DEFAULT_DATE_FORMAT);
        }
    }

    @Override
    public List<Block> display(Object property, Map<String, String> params)
    {
        Date dateVal = (Date) property;
        SimpleDateFormat formatter = dateFormatter;

        String paramsFormat = params.get("format");
        if (paramsFormat != null) {
            try {
                formatter = new SimpleDateFormat(paramsFormat);
            } catch (IllegalArgumentException e) {
                formatter = dateFormatter;
            }
        }

        String formattedDate = formatter.format(dateVal);
        try {
            return parser.parse(new StringReader(formattedDate)).getChildren();
        } catch (ParseException e) {
            logger.warn("Failed to parse the formatted date [{}]. Cause: [{}].", formattedDate,
                ExceptionUtils.getRootCauseMessage(e));
            return Collections.emptyList();
        }
    }
}
