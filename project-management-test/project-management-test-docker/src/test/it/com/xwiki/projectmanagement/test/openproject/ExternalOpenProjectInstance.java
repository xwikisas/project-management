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
import org.xwiki.test.ui.XWikiWebDriver;

public class ExternalOpenProjectInstance extends OpenProjectInstance
{
    public ExternalOpenProjectInstance(String userName, String password, String baseUrl) {
        this.userName = userName;
        this.currentPassword = password;
        this.baseUrl = baseUrl;

    }
    @Override
    public void startOpenProject(TestUtils testUtils, TestConfiguration testConfiguration) throws Exception
    {
        return;
    }

    @Override
    public String getBaseUrl()
    {
        return super.getBaseUrl();
    }

    @Override
    public String getClientId()
    {
        return super.getClientId();
    }

    @Override
    public String getClientSecret()
    {
        return super.getClientSecret();
    }

    @Override
    public void maybeClickAuthorization(XWikiWebDriver driver)
    {
        super.maybeClickAuthorization(driver);
    }

    @Override
    public void maybeLogin(XWikiWebDriver driver, boolean goToPage)
    {
        super.maybeLogin(driver, goToPage);
    }
}
