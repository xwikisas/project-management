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
package com.xwiki.projectmanagement.internal.chart.displayer;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * The order of the labels of a chart: the numbers (e.g. the progress of the work items) by value, so that "20" comes
 * before "100", then the other labels in alphabetical order.
 *
 * @version $Id$
 * @since 1.4.0
 */
final class ChartLabelComparator implements Comparator<String>, Serializable
{
    private static final long serialVersionUID = 1L;

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

    @Override
    public int compare(String first, String second)
    {
        boolean isFirstNumber = NUMBER.matcher(first).matches();
        boolean isSecondNumber = NUMBER.matcher(second).matches();
        int result;
        if (isFirstNumber && isSecondNumber) {
            result = new BigDecimal(first).compareTo(new BigDecimal(second));
        } else {
            result = Boolean.compare(!isFirstNumber, !isSecondNumber);
        }
        // Different texts of the same number (e.g. "1" and "1.0") are distinct labels.
        return result != 0 ? result : first.compareTo(second);
    }
}
