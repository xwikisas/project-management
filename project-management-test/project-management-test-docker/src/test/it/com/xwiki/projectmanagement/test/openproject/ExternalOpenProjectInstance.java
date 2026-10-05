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
package com.xwiki.projectmanagement.test.openproject;

import org.xwiki.test.docker.junit5.TestConfiguration;
import org.xwiki.test.ui.TestUtils;

/**
 * Similar to {@link OpenProjectInstance} but the OpenProject instance is external (managed externally by the
 * dev/environment). It is used instead of a Docker container when the {@value OpenProjectInstance#URL_PROPERTY} system
 * property is set, e.g. {@code mvn clean install -Pdocker -Dopenproject.url=http://172.17.0.1:8082}.
 * <p>
 * The instance needs to be reachable from both the browser and the XWiki containers, and it must accept non HTTPS
 * OAuth redirect URIs. Start it with the same configuration as {@link OpenProjectInstance}, from the
 * {@code project-management-test-docker} directory:
 *
 * <pre>
 * docker run -d -p 8082:80 -e OPENPROJECT_SECRET_KEY_BASE=secret -e OPENPROJECT_HTTPS=false \
 *   -e OPENPROJECT_DEFAULT__LANGUAGE=en \
 *   -v "$PWD/src/test/resources/doorkeeper.rb:/app/config/initializers/doorkeeper.rb:ro" \
 *   openproject/openproject:16
 * </pre>
 *
 * A new OAuth application is created on each run, unless the {@value OpenProjectInstance#CLIENT_ID_PROPERTY} and
 * {@value OpenProjectInstance#CLIENT_SECRET_PROPERTY} system properties point to an existing one.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
public class ExternalOpenProjectInstance extends OpenProjectInstance
{
    /**
     * @param userName the admin username for the external OP instance.
     * @param password the admin password for the instance. Note that if it "admin" and requires changing, the setup
     *     method will change the password to the one defined in the {@link OpenProjectInstance}.
     * @param baseUrl the url where the external instance is located.
     * @param clientId the client id of an existing OAuth application, or {@code null} to create a new one
     * @param clientSecret the client secret of an existing OAuth application, or {@code null} to create a new one
     */
    public ExternalOpenProjectInstance(String userName, String password, String baseUrl, String clientId,
        String clientSecret)
    {
        this.userName = userName;
        this.currentPassword = password;
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public void startOpenProject(TestUtils testUtils, TestConfiguration testConfiguration)
    {
        // The instance is managed externally.
    }
}
