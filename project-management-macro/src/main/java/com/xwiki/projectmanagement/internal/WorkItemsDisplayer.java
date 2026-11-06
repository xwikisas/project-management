package com.xwiki.projectmanagement.internal;

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

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;
import com.xwiki.projectmanagement.internal.displayers.WorkItemLivedataDisplayer;

/**
 * An enum listing the different ways a list of work items can be displayed. The value of the {@code displayer} macro
 * parameter is the lower case name of the constant (e.g. {@code displayer="single"}), matched ignoring the case; the
 * deprecated {@code workItemsDisplayer} parameter takes the {@link #getLegacyName() legacy names} instead.
 *
 * @version $Id$
 */
public enum WorkItemsDisplayer
{
    /**
     * Display the work items using a livedata.
     */
    TABLE("liveData"),
    /**
     * Display the work items using a livedata in the card layout.
     */
    CARDS("liveDataCards"),
    /**
     * Display a single work item in a page structure.
     */
    SINGLE("workItemsSingle"),
    /**
     * Display a single work item as an inline badge.
     */
    INLINE("workItemInline"),
    /**
     * Display the work items as a bulleted list, one work item per line with its linked identifier and its summary.
     *
     * @since 1.4.0
     */
    LIST("workItemsList"),
    /**
     * Display the linked identifiers of the work items separated by commas, usable inline in a sentence.
     *
     * @since 1.4.0
     */
    ENUM("workItemsEnum");

    private final String legacyName;

    WorkItemsDisplayer(String legacyName)
    {
        this.legacyName = legacyName;
    }

    /**
     * @return the value of the deprecated {@code workItemsDisplayer} macro parameter for this displayer, which is also
     *     the hint of the macro component of the displayers other than the Live Data ones
     * @since 1.4.0
     */
    public String getLegacyName()
    {
        return this.legacyName;
    }

    /**
     * @return the hint of the macro component that displays the work items this way
     * @since 1.4.0
     */
    public String getMacroHint()
    {
        if (isLiveData()) {
            return WorkItemLivedataDisplayer.ROLE_HINT;
        }
        return this.legacyName;
    }

    /**
     * @return {@code true} if the work items are displayed with the Live Data macro (table or cards)
     * @since 1.4.0
     */
    public boolean isLiveData()
    {
        return this == TABLE || this == CARDS;
    }

    /**
     * @param value a displayer value, either the value of the {@code displayer} parameter (e.g. {@code single}) or a
     *     legacy name (e.g. {@code workItemsSingle}), ignoring the case
     * @return the matching displayer, or {@code null} if there's none
     * @since 1.4.0
     */
    public static WorkItemsDisplayer fromString(String value)
    {
        if (value != null) {
            for (WorkItemsDisplayer displayer : values()) {
                if (displayer.name().equalsIgnoreCase(value) || displayer.legacyName.equalsIgnoreCase(value)) {
                    return displayer;
                }
            }
        }
        return null;
    }

    /**
     * @return the value of the {@code displayer} macro parameter for this displayer, e.g. {@code single}; also its JSON
     *     serialization, so that the macro editor recognizes the default value among the options of the displayer field
     */
    @JsonValue
    @Override
    public String toString()
    {
        return name().toLowerCase(Locale.ROOT);
    }
}
