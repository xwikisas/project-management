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

import org.junit.jupiter.api.Test;

import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.BooleanClass;
import com.xpn.xwiki.objects.classes.NumberClass;
import com.xpn.xwiki.objects.classes.StringClass;
import com.xpn.xwiki.objects.classes.TextAreaClass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Unit tests for {@link PresetClassInitializer}.
 *
 * @version $Id$
 */
class PresetClassInitializerTest
{
    @Test
    void createClass()
    {
        BaseClass xclass = new BaseClass();

        new PresetClassInitializer().createClass(xclass);

        assertEquals(5, xclass.getFieldList().size());
        assertEquals("integer", assertInstanceOf(NumberClass.class, xclass.get(Preset.FIELD_ID)).getNumberType());
        assertInstanceOf(StringClass.class, xclass.get(Preset.FIELD_NAME));
        assertInstanceOf(TextAreaClass.class, xclass.get(Preset.FIELD_FILTER));
        assertInstanceOf(StringClass.class, xclass.get(Preset.FIELD_CLIENT));
        BooleanClass multiple = assertInstanceOf(BooleanClass.class, xclass.get(Preset.FIELD_MULTIPLE));
        assertEquals("yesno", multiple.getDisplayType());
        assertEquals(0, multiple.getDefaultValue());
    }
}
