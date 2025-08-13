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

import org.apache.poi.openxml4j.exceptions.InvalidOperationException;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.xwiki.test.docker.internal.junit5.DockerTestUtils;
import org.xwiki.test.docker.junit5.TestConfiguration;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.XWikiWebDriver;

public class OpenProjectInstance
{
    protected static final Logger LOGGER = LoggerFactory.getLogger(OpenProjectInstance.class);

    private static final String PASSWORD_OLD = "admin";

    private static final String PASSWORD_NEW = "adminadminadmin";

    private GenericContainer<?> openProjectContainer;

    protected String clientId;

    protected String currentPassword = PASSWORD_OLD;

    protected String userName = "Admin";

    protected String clientSecret;

    protected String baseUrl;

    public void startOpenProject(TestUtils testUtils, TestConfiguration testConfiguration) throws Exception
    {
        Network network = Network.newNetwork();
        GenericContainer<?> openProject = new GenericContainer<>(DockerImageName.parse("openproject/openproject:16"))
            .withEnv("OPENPROJECT_SECRET_KEY_BASE", "secret")
//            .withEnv("OPENPROJECT_HOST__NAME", "localhost:8082") // No need to include port
            .withEnv("OPENPROJECT_HTTPS", "false")
            .withEnv("OPENPROJECT_DEFAULT__LANGUAGE", "en")
            .withExposedPorts(80)
            .withNetwork(network)
            .withNetworkAliases("openproject")
            .waitingFor(Wait.forHttp("/")  // Waits for HTTP 200 on "/"
                .forPort(80)
                .withStartupTimeout(java.time.Duration.ofMinutes(5)));

        DockerTestUtils.startContainer(openProject, testConfiguration);

        this.openProjectContainer = openProject;

        XWikiWebDriver driver = testUtils.getDriver();

        getBaseUrl();
        LOGGER.info("!!Waiting for open project start.");
        System.out.println("!!!Waiting for open project start.");

//        Thread.sleep(120 * 1000);

        int iteration = 0;
        do {
            String location = getBaseUrl();
            try {
                driver.get(location);
            } catch (Exception e) {
                LOGGER.error("Failed to get [{}].", location, e);
            }
            if (driver.hasElement(By.cssSelector(".op-logo"))) {
                break;
            }
//            Thread.sleep(1 * 1000);
        } while (iteration++ < 5);
    }

    public void setupInstance(XWikiWebDriver driver)
    {
        maybeLogin(driver, true);
        changePassword(driver);
        maybeLogin(driver, true);
        createNewOAuthApp(driver);
    }

    public void maybeLogin(XWikiWebDriver driver, boolean goToPage)
    {
        LOGGER.info("Trying to log in to Open Project.");
        if (goToPage) {
            String location = getBaseUrl();

            driver.get(location);
            waitPageLoad(driver);
        }

        if (!driver.hasElement(By.id("username")) || !driver.hasElement(By.id("password"))) {
            LOGGER.info("Not on the login page.");
            return;
        }
        WebElement userNameElem = driver.findElement(By.id("username"));
        WebElement passElem = driver.findElement(By.id("password"));

        userNameElem.sendKeys(userName);
        passElem.sendKeys(currentPassword);

        WebElement submitElem = driver.findElement(By.cssSelector(".user-login--form .-primary"));
        submitElem.click();
        waitPageLoad(driver);
    }

    public void maybeClickAuthorization(XWikiWebDriver driver)
    {
        try {
            WebElement submitElem =
                driver.findElement(By.xpath("//form[@action='/oauth/authorize']//*[contains(@class, '-primary')]"));
            submitElem.click();
        } catch (NoSuchElementException ignored) {
        }
    }

    public String getClientSecret()
    {
        if (clientSecret == null) {
            throw new InvalidOperationException("Can't retrieve the client secret before having setup the OAuth app.");
        }
        return clientSecret;
    }

    public String getClientId()
    {
        if (clientId == null) {
            throw new InvalidOperationException("Can't retrieve the client id before having setup the OAuth app.");
        }
        return clientId;
    }

    public String getBaseUrl()
    {
        if (baseUrl != null) {
            return baseUrl;
        }
        if (openProjectContainer == null) {
            throw new InvalidOperationException("Can't retrieve the container URL without having started it.");
        }
        String host = openProjectContainer.getHost(); // usually "localhost"
        Integer port = openProjectContainer.getMappedPort(80); // random free port mapped to container's 80

        baseUrl = "http://172.17.0.1:" + port;
        LOGGER.info("!!Open Proj container is at url [{}].%n", baseUrl);
        return baseUrl;
    }

    private void changePassword(XWikiWebDriver driver)
    {
        if (!driver.hasElement(By.id("new_password_confirmation"))) {
            return;
        }
        WebElement currentPassElem = driver.findElement(By.id("password"));
        WebElement newPassElem = driver.findElement(By.id("new_password"));
        WebElement newPassConfirmElem = driver.findElement(By.id("new_password_confirmation"));

        currentPassElem.sendKeys(currentPassword);
        newPassElem.sendKeys(PASSWORD_NEW);
        newPassConfirmElem.sendKeys(PASSWORD_NEW);
        currentPassword = PASSWORD_NEW;

        WebElement submitElem =
            driver.findElement(By.xpath("//form[@action='/account/change_password']//*[contains(@class, '-primary')]"));
        submitElem.click();
        waitPageLoad(driver);
    }

    private void createNewOAuthApp(XWikiWebDriver driver)
    {
        // http://localhost:8081/admin/oauth/applications/new
        driver.get(getBaseUrl() + "/admin/oauth/applications/new");
        waitPageLoad(driver);

        WebElement nameInput = driver.findElement(By.id("application_name"));
        WebElement redirectUriInput = driver.findElement(By.id("application_redirect_uri"));
        WebElement apiV3Checkbox = driver.findElement(
            By.xpath("//input[@id='application_scopes_' and @value='api_v3']"));
        WebElement form = driver.findElement(
            By.xpath("//form[@id='new_application']"));

        nameInput.sendKeys("XWiki");
        redirectUriInput.sendKeys("http://127.0.0.1:8080/xwiki/oidc/authenticator/callback");
        if (!apiV3Checkbox.isSelected()) {
            apiV3Checkbox.click();
        }

        form.submit();

        waitPageLoad(driver);

        WebElement clientIdElem = driver.findElement(
            By.xpath("//div[@class='attributes-key-value--key' and contains(text(), 'Client ID')]/..//span"));
        WebElement clientSecretElem = driver.findElement(By.cssSelector(".attributes-key-value--value code"));

        this.clientId = clientIdElem.getText();
        LOGGER.info("!!Client id retrieved [{}].", clientIdElem);
        this.clientSecret = clientSecretElem.getText();
        LOGGER.info("!!Client secret retrieved [{}].", clientSecretElem);
    }

    private static void waitPageLoad(XWikiWebDriver driver)
    {
        driver.waitUntilCondition(ExpectedConditions.visibilityOf(driver.findElement(By.cssSelector(".op-logo"))));
    }
}
