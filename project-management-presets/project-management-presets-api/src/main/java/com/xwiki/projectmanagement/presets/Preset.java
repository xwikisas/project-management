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
package com.xwiki.projectmanagement.presets;

import java.util.Arrays;

import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.objects.BaseObject;

/**
 * Represents the store object of a Preset. A preset is an already made filter that can be used by the various Project
 * Management macros.
 *
 * @version $Id$
 * @since 1.3.0
 */
public class Preset
{
    /**
     * The full class name of the preset.
     */
    public static final String CLASS_NAME = "ProjectManagement.Code.Presets.PresetClass";

    /**
     * Reference of the xwiki class.
     */
    public static final LocalDocumentReference REFERENCE = new LocalDocumentReference(Arrays.asList("ProjectManagement",
        "Code", "Presets"), "PresetClass");

    public static final String FIELD_ID = "id";

    public static final String FIELD_NAME = "name";

    public static final String FIELD_FILTER = "filter";

    public static final String FIELD_CLIENT = "client";

    public static final String FIELD_MULTIPLE = "isMultiple";

    private final BaseObject xobject;

    /**
     * @param xobject the xwiki object from which information will be retrieved.
     */
    public Preset(BaseObject xobject)
    {
        this.xobject = xobject;
    }

    /**
     * @return the unique id of this Preset.
     */
    public int getId()
    {
        return this.xobject.getIntValue(FIELD_ID);
    }

    /**
     * @return the name/id of the preset.
     */
    public String getName()
    {
        return this.xobject.getStringValue(FIELD_NAME);
    }

    /**
     * @return the filter value that can be used for the various ProjectManagement macros.
     */
    public String getFilter()
    {
        return this.xobject.getLargeStringValue(FIELD_FILTER);
    }

    /**
     * @return the list of client that support this preset. If empty, this preset is available to all client.
     */
    public String getClient()
    {
        return this.xobject.getStringValue(FIELD_CLIENT);
    }

    /**
     * @return whether this Preset is to be used by charts or not.
     */
    public Boolean isMultiple()
    {
        return this.xobject.getIntValue(FIELD_MULTIPLE) == 1;
    }
}
