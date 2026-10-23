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

import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.xwiki.test.docker.internal.junit5.DockerTestUtils;
import org.xwiki.test.docker.junit5.TestConfiguration;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.XWikiWebDriver;

/**
 * Defines the behaviour of a OpenProject instance. It offers methods for starting an instance, setting it up (login,
 * change password, setup an oauth client), retrieve location and OAuth client details.
 * <p>
 * Use {@link #fromSystemProperties()} to get either an instance started in a Docker container (the default) or an
 * {@link ExternalOpenProjectInstance} when the {@value #URL_PROPERTY} system property is set.
 *
 * @version $Id$
 */
public class OpenProjectInstance
{
    /**
     * The system property holding the URL of an external OpenProject instance to use instead of starting one.
     */
    public static final String URL_PROPERTY = "openproject.url";

    /**
     * The system property holding the admin username of the external OpenProject instance.
     */
    public static final String USERNAME_PROPERTY = "openproject.username";

    /**
     * The system property holding the admin password of the external OpenProject instance.
     */
    public static final String PASSWORD_PROPERTY = "openproject.password";

    /**
     * The system property holding the client id of an existing OAuth application of the external OpenProject
     * instance. When both the client id and secret are set, no OAuth application is created.
     */
    public static final String CLIENT_ID_PROPERTY = "openproject.clientId";

    /**
     * The system property holding the client secret of an existing OAuth application of the external OpenProject
     * instance.
     */
    public static final String CLIENT_SECRET_PROPERTY = "openproject.clientSecret";

    protected static final Logger LOGGER = LoggerFactory.getLogger(OpenProjectInstance.class);

    private static final String DEFAULT_USERNAME = "Admin";

    private static final String PASSWORD_OLD = "admin";

    private static final String PASSWORD_NEW = "adminadminadmin";

    private static final String NETWORK_ALIAS = "openproject";

    private static final By OP_LOGO = By.className("op-logo");

    private static final By USERNAME_INPUT = By.id("username");

    private static final By AUTHORIZE_BUTTON =
        By.xpath("//form[@action='/oauth/authorize']//*[contains(@class, '-primary')]");

    protected String clientId;

    protected String currentPassword = PASSWORD_OLD;

    protected String userName = DEFAULT_USERNAME;

    protected String clientSecret;

    protected String baseUrl;

    /**
     * @return an {@link ExternalOpenProjectInstance} configured from the {@code openproject.*} system properties when
     *     {@value #URL_PROPERTY} is set, otherwise an instance that is started in a Docker container
     */
    public static OpenProjectInstance fromSystemProperties()
    {
        String url = System.getProperty(URL_PROPERTY);
        if (StringUtils.isBlank(url)) {
            return new OpenProjectInstance();
        }
        LOGGER.info("Using the external OpenProject instance at [{}].", url);
        return new ExternalOpenProjectInstance(System.getProperty(USERNAME_PROPERTY, DEFAULT_USERNAME),
            System.getProperty(PASSWORD_PROPERTY, PASSWORD_OLD), url, System.getProperty(CLIENT_ID_PROPERTY),
            System.getProperty(CLIENT_SECRET_PROPERTY));
    }

    /**
     * Start an OpenProject instance inside a docker container.
     *
     * @param testUtils the TestUtils that wrap the webdriver necessary for navigating the created instance.
     * @param testConfiguration the test configuration containing information necessary for the docker container
     *     manager.
     * @throws Exception in any issue is raised during the starting up of the container.
     */
    public void startOpenProject(TestUtils testUtils, TestConfiguration testConfiguration) throws Exception
    {
        GenericContainer<?> openProject = new GenericContainer<>(DockerImageName.parse("openproject/openproject:16"))
            .withEnv("OPENPROJECT_SECRET_KEY_BASE", "secret")
            .withEnv("OPENPROJECT_HTTPS", "false")
            .withEnv("OPENPROJECT_DEFAULT__LANGUAGE", "en")
            .withExposedPorts(80)
            // Join the network of the browser and XWiki containers, so that both reach OpenProject by its alias.
            .withNetwork(Network.SHARED)
            .withNetworkAliases(NETWORK_ALIAS)
            .waitingFor(Wait.forHttp("/")  // Waits for HTTP 200 on "/"
                .forPort(80)
                .withStartupTimeout(Duration.ofMinutes(5)))
            // We need to disable the https/localhost requirement for the oauth redirect uri.
            .withCopyFileToContainer(MountableFile.forClasspathResource("doorkeeper.rb"),
                "/app/config/initializers/doorkeeper.rb");

        DockerTestUtils.startContainer(openProject, testConfiguration);

        // OpenProject has to be reached with the same URL by the browser and by XWiki. A containerized XWiki shares the
        // network of the browser, while an XWiki running outside Docker reaches the containers through the Docker
        // bridge (Linux only).
        this.baseUrl = testConfiguration.getServletEngine().isOutsideDocker()
            ? "http://172.17.0.1:" + openProject.getMappedPort(80) : "http://" + NETWORK_ALIAS;
        LOGGER.info("The OpenProject container is available at [{}].", this.baseUrl);

        // The container answers on its mapped port before the browser container can resolve its network alias.
        XWikiWebDriver driver = testUtils.getDriver();
        new WebDriverWait(driver, Duration.ofMinutes(2), Duration.ofSeconds(5)).ignoring(WebDriverException.class)
            .until(webDriver -> {
                webDriver.get(getBaseUrl());
                return driver.hasElementWithoutWaiting(OP_LOGO);
            });
    }

    /**
     * Login, change password and setup an OAuth client.
     *
     * @param setup the test setup, used to navigate the instance and to compute the XWiki URLs seen by the browser
     */
    public void setupInstance(TestUtils setup)
    {
        XWikiWebDriver driver = setup.getDriver();
        maybeLogin(driver, true);
        changePassword(driver);
        maybeLogin(driver, true);
        if (clientId == null || clientSecret == null) {
            // OpenProject redirects the browser to XWiki once the user has authorized the access.
            createNewOAuthApp(driver,
                StringUtils.appendIfMissing(setup.getBaseURL(), "/") + "oidc/authenticator/callback");
        }
    }

    /**
     * Try to login. Do nothing if login is not required.
     *
     * @param driver the webdriver used to navigate the instance.
     * @param goToPage denotes whether the method should navigate to the login page or not.
     */
    public void maybeLogin(XWikiWebDriver driver, boolean goToPage)
    {
        LOGGER.info("Trying to log in to OpenProject.");
        if (goToPage) {
            String location = getBaseUrl();

            driver.get(location);
            waitPageLoad(driver);
        }

        if (!driver.hasElementWithoutWaiting(USERNAME_INPUT)) {
            LOGGER.info("Not on the login page.");
            return;
        }
        WebElement userNameElem = driver.findElement(USERNAME_INPUT);
        WebElement passElem = driver.findElement(By.id("password"));

        userNameElem.sendKeys(userName);
        passElem.sendKeys(currentPassword);

        WebElement submitElem = driver.findElement(By.cssSelector(".user-login--form .-primary"));
        submitElem.click();
        waitPageLoad(driver);
    }

    /**
     * Authorize XWiki on the OpenProject OAuth page the browser has been redirected to, logging in first if needed.
     *
     * @param driver the webdriver used to navigate the instance.
     */
    public void authorize(XWikiWebDriver driver)
    {
        try {
            driver.waitUntilCondition(webDriver -> driver.hasElementWithoutWaiting(USERNAME_INPUT)
                || driver.hasElementWithoutWaiting(AUTHORIZE_BUTTON));
        } catch (TimeoutException e) {
            // OpenProject doesn't ask again for an authorization it has already given.
            LOGGER.info("The OpenProject OAuth application is already authorized.");
            return;
        }
        maybeLogin(driver, false);
        if (driver.hasElementWithoutWaiting(AUTHORIZE_BUTTON)) {
            driver.findElement(AUTHORIZE_BUTTON).click();
        }
    }

    /**
     * @return the client secret of the last created OAuth client.
     */
    public String getClientSecret()
    {
        if (clientSecret == null) {
            throw new IllegalStateException("Can't retrieve the client secret before having setup the OAuth app.");
        }
        return clientSecret;
    }

    /**
     * @return the client id of the last created OAuth client.
     */
    public String getClientId()
    {
        if (clientId == null) {
            throw new IllegalStateException("Can't retrieve the client id before having setup the OAuth app.");
        }
        return clientId;
    }

    /**
     * @return the base url of the created OpenProject instance.
     */
    public String getBaseUrl()
    {
        if (baseUrl == null) {
            throw new IllegalStateException("Can't retrieve the container URL without having started it.");
        }
        return baseUrl;
    }

    private void changePassword(XWikiWebDriver driver)
    {
        if (!driver.hasElementWithoutWaiting(By.id("new_password_confirmation"))) {
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

    private void createNewOAuthApp(XWikiWebDriver driver, String redirectUri)
    {
        driver.get(getBaseUrl() + "/admin/oauth/applications/new");
        waitPageLoad(driver);

        WebElement nameInput = driver.findElement(By.id("application_name"));
        WebElement redirectUriInput = driver.findElement(By.id("application_redirect_uri"));
        WebElement apiV3Checkbox = driver.findElement(
            By.xpath("//input[@id='application_scopes_' and @value='api_v3']"));
        WebElement form = driver.findElement(
            By.xpath("//form[@id='new_application']"));

        nameInput.sendKeys("XWiki");
        redirectUriInput.sendKeys(redirectUri);
        if (!apiV3Checkbox.isSelected()) {
            apiV3Checkbox.click();
        }

        form.submit();

        waitPageLoad(driver);

        WebElement clientIdElem = driver.findElement(
            By.xpath("//div[@class='attributes-key-value--key' and contains(text(), 'Client ID')]/..//span"));
        WebElement clientSecretElem = driver.findElement(By.cssSelector(".attributes-key-value--value code"));

        this.clientId = clientIdElem.getText();
        this.clientSecret = clientSecretElem.getText();
        LOGGER.info("Created the OpenProject OAuth application with client id [{}].", this.clientId);
    }

    private static void waitPageLoad(XWikiWebDriver driver)
    {
        driver.waitUntilCondition(
            ExpectedConditions.visibilityOf(driver.findElement(By.cssSelector(".op-logo .op-logo--link"))));
    }
}
