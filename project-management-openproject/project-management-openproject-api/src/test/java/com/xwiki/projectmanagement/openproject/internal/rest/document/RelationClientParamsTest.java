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
package com.xwiki.projectmanagement.openproject.internal.rest.document;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link RelationClientParams}.
 *
 * @version $Id$
 */
class RelationClientParamsTest
{
    @Test
    void ofInstance()
    {
        assertEquals("{\"instance\":\"Main connection\"}", RelationClientParams.ofInstance("Main connection"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "a\"b", "a\\", "a\",\"other\":\"b", "été ☃" })
    void ofInstanceWithSpecialCharacters(String instance) throws Exception
    {
        Map<String, String> clientParams = new ObjectMapper().readValue(RelationClientParams.ofInstance(instance),
            new TypeReference<Map<String, String>>()
            {
            });

        assertEquals(Map.of("instance", instance), clientParams);
    }
}
