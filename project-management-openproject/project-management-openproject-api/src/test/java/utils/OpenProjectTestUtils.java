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
package utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;

public class OpenProjectTestUtils
{
    public String getWorkPackagesValidResponse() throws IOException
    {
        return getJsonFromResource("openProjectWorkPackagesApiResponse.json");
    }

    public String getUsersValidResponse() throws IOException
    {
        return getJsonFromResource("openProjectUsersApiResponse.json");
    }

    public String getProjectsValidResponse() throws IOException
    {
        return getJsonFromResource("openProjectProjectsApiResponse.json");
    }

    public String getTypesValidResponse() throws IOException
    {
        return getJsonFromResource("openProjectTypesApiResponse.json");
    }

    public String getStatusesValidResponse() throws IOException{
        return getJsonFromResource("openProjectStatusesApiResponse.json");
    }

    public String getPrioritiesValidResponse() throws IOException
    {
        return getJsonFromResource("openProjectPrioritiesApiResponse.json");
    }

    private String getJsonFromResource(String fileName) throws IOException
    {
        InputStream stream = this.getClass().getClassLoader().getResourceAsStream(fileName);

        if (stream == null) {
            throw new RuntimeException(String.format("Could not find %s", fileName));
        }

        return IOUtils.toString(stream, Charset.defaultCharset());
    }

}
