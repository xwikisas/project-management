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

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;

import com.xpn.xwiki.doc.AbstractMandatoryClassInitializer;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.NumberClass;

import static com.xwiki.projectmanagement.presets.Preset.FIELD_CLIENT;
import static com.xwiki.projectmanagement.presets.Preset.FIELD_FILTER;
import static com.xwiki.projectmanagement.presets.Preset.FIELD_ID;
import static com.xwiki.projectmanagement.presets.Preset.FIELD_MULTIPLE;
import static com.xwiki.projectmanagement.presets.Preset.FIELD_NAME;

/**
 * Initializes the preset class.
 *
 * @version $Id$
 * @since 1.3.0
 */
@Component
@Named(Preset.CLASS_NAME)
@Singleton
public class PresetClassInitializer extends AbstractMandatoryClassInitializer
{
    /**
     * Default constructor.
     */
    public PresetClassInitializer()
    {
        super(Preset.REFERENCE);
    }

    @Override
    protected void createClass(BaseClass xclass)
    {
        xclass.addNumberField(FIELD_ID, "Filter Preset ID", 10, NumberClass.TYPE_INTEGER);
        xclass.addTextField(FIELD_NAME, "Filter Preset Name", 20);
        xclass.addTextAreaField(FIELD_FILTER, "Filter value", 20, 20);
        xclass.addTextField(FIELD_CLIENT, "Project Management Client", 20);
        xclass.addBooleanField(FIELD_MULTIPLE, "Has multiple filters?", "checkbox", false);
    }
}
