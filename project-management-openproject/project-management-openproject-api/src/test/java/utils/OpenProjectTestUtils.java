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
    private OpenProjectTestUtils()
    {

    }

    public static String getWorkPackagesValidResponse() throws IOException
    {
        return getJsonFromResource("openProjectWorkPackagesApiResponse.json");
    }

    public static String getUsersValidResponse() throws IOException
    {
        return getJsonFromResource("openProjectUsersApiResponse.json");
    }

    public static String getProjectsValidResponse() throws IOException
    {
        return getJsonFromResource("openProjectProjectsApiResponse.json");
    }

    public static String getTypesValidResponse() throws IOException
    {
        return getJsonFromResource("openProjectTypesApiResponse.json");
    }

    public static String getStatusesValidResponse() throws IOException
    {
        return getJsonFromResource("openProjectStatusesApiResponse.json");
    }

    public static String getPrioritiesValidResponse() throws IOException
    {
        return getJsonFromResource("openProjectPrioritiesApiResponse.json");
    }

    public static String getCreateWorkPackageProjectsFormResponse() throws IOException
    {
        return getJsonFromResource("openProjectCreateWorkPackageProjectsFormResponse.json");
    }

    public static String getCreateWorkPackageValidationFailsApiResponse() throws IOException
    {
        return getJsonFromResource("openProjectCreateWorkPackageValidationFailsApiResponse.json");
    }

    public static String getCreateWorkPackageValidationFailsResponse() throws IOException
    {
        return getJsonFromResource("openProjectCreateWorkPackageValidationFailsResponse.json");
    }

    public static String getCreateWorkPackageRequestExample() throws IOException
    {
        return getJsonFromResource("openProjectCreateWorkPackageRequestExample.json");
    }

    public static String getCreateWorkPackageValidationSuccessResponse() throws IOException
    {
        return getJsonFromResource("openProjectCreateWorkPackageValidationSuccessResponse.json");
    }

    private static String getJsonFromResource(String fileName) throws IOException
    {
        InputStream stream = OpenProjectTestUtils.class.getClassLoader().getResourceAsStream(fileName);

        if (stream == null) {
            throw new RuntimeException(String.format("Could not find %s", fileName));
        }

        return IOUtils.toString(stream, Charset.defaultCharset());
    }
}
