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
package com.xwiki.projectmanagement.internal.rest;

import java.io.File;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xwiki.projectmanagement.internal.source.QueryWorkItemSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The Java half of the round trip of the filter builder: the queries that the query writer of the macro editor
 * ({@code js/projectmanagement/macro/queryWriter.js}) writes for the filters of the cases of
 * {@code src/test/resources/queryWriter/roundTripCases.json} are parsed by {@link WorkItemQueryResource} into the
 * {@code parsed} filters of the cases. The JavaScript half ({@code src/test/node}, run with Vitest) checks that the
 * writer writes these queries, and reads the parsed filters back into the same filters of the builder.
 * <p>
 * After a change of the parser, run this test with {@code -DupdateRoundTripCases=true} to write the new parsed
 * filters into the cases (then check their diff); after a change of the random cases, regenerate them with
 * {@code pnpm run generate:round-trip} in {@code src/test/node} first.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(QueryWorkItemSource.class)
class QueryWriterRoundTripTest
{
    private static final File CASES = new File("src/test/resources/queryWriter/roundTripCases.json");

    private static final String PARSED = "parsed";

    private final ObjectMapper mapper = new ObjectMapper();

    @InjectMockComponents
    private WorkItemQueryResource resource;

    @Test
    void parseTheQueriesOfTheBuilder() throws Exception
    {
        JsonNode cases = this.mapper.readTree(CASES);
        boolean update = Boolean.getBoolean("updateRoundTripCases");
        int count = 0;
        for (String kind : new String[] { "table", "random" }) {
            for (JsonNode roundTripCase : cases.get(kind)) {
                String query = roundTripCase.get("query").asText();
                Response response = this.resource.getFilters(query);
                assertEquals(200, response.getStatus(), () -> query + " -> " + response.getEntity());
                JsonNode parsed = this.mapper.readTree((String) response.getEntity()).get("filters");
                if (update) {
                    ((ObjectNode) roundTripCase).set(PARSED, parsed);
                } else {
                    assertEquals(roundTripCase.get(PARSED), parsed, query);
                }
                count++;
            }
        }
        assertFalse(count == 0);
        if (update) {
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter().withoutSpacesInObjectEntries()
                .withObjectIndenter(new DefaultIndenter("  ", "\n")).withArrayIndenter(new DefaultIndenter("  ", "\n"));
            this.mapper.writer(printer).writeValue(CASES, cases);
        }
    }
}
