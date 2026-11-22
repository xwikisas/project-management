package com.xwiki.projectmanagement.internal.source;

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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.source.WorkItemSourceContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FiltersWorkItemSource}.
 *
 * @version $Id$
 */
@ComponentTest
class FiltersWorkItemSourceTest
{
    private final WorkItemSourceContext context = new WorkItemSourceContext("test");

    @InjectMockComponents
    private FiltersWorkItemSource source;

    @MockComponent
    private LiveDataConfigurationResolver<String> configurationResolver;

    @Test
    void readsTheFiltersOfTheConfiguration() throws Exception
    {
        LiveDataConfiguration configuration = new LiveDataConfiguration();
        configuration.setQuery(new LiveDataQuery());
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("status", "equals", "1"));
        configuration.getQuery().setFilters(filters);
        when(this.configurationResolver.resolve("json")).thenReturn(configuration);

        assertEquals(filters, this.source.read("json", this.context).getFilters());
    }

    @Test
    void readsNoFilterFromAConfigurationWithoutQuery() throws Exception
    {
        when(this.configurationResolver.resolve("{}")).thenReturn(new LiveDataConfiguration());

        assertTrue(this.source.read("{}", this.context).getFilters().isEmpty());
    }

    @Test
    void failsOnMalformedJsonWithoutTheParserDetails() throws Exception
    {
        String content = "{\"query\":{\"filters\":[{\"property\":\"project\"";
        mockParsingFailure(content);

        WorkItemSourceException exception =
            assertThrows(WorkItemSourceException.class, () -> this.source.read(content, this.context));

        assertEquals("The macro content isn't valid JSON (line 1, column 43): unexpected end-of-input.",
            exception.getMessage());
        // The macro error displays the message of the root cause, which would then be the one of the parser.
        assertNull(exception.getCause());
    }

    @Test
    void failsOnContentThatIsNotJson() throws Exception
    {
        String content = "project = 1";
        mockParsingFailure(content);

        assertEquals("The macro content isn't valid JSON (line 1, column 8): unrecognized token 'project'.",
            assertThrows(WorkItemSourceException.class, () -> this.source.read(content, this.context)).getMessage());
    }

    @Test
    void failsOnJsonThatIsNotALiveDataConfiguration() throws Exception
    {
        String content = "{\"query\":{\"filters\":\"project\"}}";
        mockParsingFailure(content);

        assertEquals("The macro content isn't valid Live Data JSON.",
            assertThrows(WorkItemSourceException.class, () -> this.source.read(content, this.context)).getMessage());
    }

    @Test
    void failsOnInvalidJson() throws Exception
    {
        when(this.configurationResolver.resolve("{")).thenThrow(new LiveDataException("Unexpected end of input"));

        assertEquals("The macro content isn't valid Live Data JSON: LiveDataException: Unexpected end of input",
            assertThrows(WorkItemSourceException.class, () -> this.source.read("{", this.context)).getMessage());
    }

    /**
     * Fail like the Live Data configuration resolver, which reads the JSON tree before the configuration, and wraps
     * the failure of the JSON parser.
     */
    private void mockParsingFailure(String content) throws Exception
    {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.treeToValue(objectMapper.readTree(content), LiveDataConfiguration.class);
        } catch (JsonProcessingException e) {
            when(this.configurationResolver.resolve(content))
                .thenThrow(new LiveDataException("Failed to read the configuration.", e));
            return;
        }
        throw new AssertionError("The content is valid: " + content);
    }
}
