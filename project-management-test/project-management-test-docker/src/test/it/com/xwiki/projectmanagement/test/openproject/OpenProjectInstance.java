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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.apache.commons.lang3.RandomStringUtils;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    // OpenProject requires lowercase, uppercase, numeric and special characters in passwords.
    private static final String PASSWORD_NEW = "Admin-admin-1";

    private static final String NETWORK_ALIAS = "openproject";

    private static final By OP_LOGO = By.className("op-logo");

    private static final String LOGIN_PATH = "/login";

    private static final By USERNAME_INPUT = By.id("username");

    private static final By AUTHORIZE_BUTTON =
        By.xpath("//form[@action='/oauth/authorize']//*[contains(@class, '-primary')]");

    protected String clientId;

    protected String currentPassword = PASSWORD_OLD;

    protected String userName = DEFAULT_USERNAME;

    protected String clientSecret;

    protected String baseUrl;

    /**
     * The URL of the instance as seen by the tests, used to call its API. It differs from {@link #baseUrl} when
     * OpenProject runs in a container.
     */
    protected String apiBaseUrl;

    private final String networkAlias;

    private GenericContainer<?> container;

    /**
     * An instance started in a Docker container, reached through the {@value #NETWORK_ALIAS} network alias.
     */
    public OpenProjectInstance()
    {
        this(NETWORK_ALIAS);
    }

    /**
     * @param networkAlias the network alias of the container, which must be unique among the instances started by
     *     the tests
     */
    public OpenProjectInstance(String networkAlias)
    {
        this.networkAlias = networkAlias;
    }

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
        GenericContainer<?> openProject =
            new GenericContainer<>(DockerImageName.parse("openproject/openproject:17.9.4"))
            // OpenProject refuses to start with a weak key. Each run starts from an empty database, so a new key can be
            // generated for each run.
            .withEnv("SECRET_KEY_BASE", RandomStringUtils.randomAlphanumeric(128))
            .withEnv("OPENPROJECT_HTTPS", "false")
            .withEnv("OPENPROJECT_DEFAULT__LANGUAGE", "en")
            // One web worker (instead of 2) is enough for the tests, and saves memory, which matters when the tests start
            // several instances.
            .withEnv("OPENPROJECT_WEB_WORKERS", "1")
            // Let anonymous users see the public projects, to test showing the public data to XWiki users who haven't
            // connected their OpenProject account.
            .withEnv("OPENPROJECT_LOGIN__REQUIRED", "false")
            .withExposedPorts(80)
            // Join the network of the browser and XWiki containers, so that both reach OpenProject by its alias.
            .withNetwork(Network.SHARED)
            .withNetworkAliases(this.networkAlias)
            // The home page fails (HTTP 500, "Missing host to link to") for anonymous users when OpenProject doesn't
            // require users to log in and no host name is configured, so wait for the login page instead. The host
            // name can't be configured: OpenProject would then reject the requests made with another host.
            .waitingFor(Wait.forHttp(LOGIN_PATH)
                .forPort(80)
                .withStartupTimeout(Duration.ofMinutes(5)))
            // We need to disable the https/localhost requirement for the oauth redirect uri.
            .withCopyFileToContainer(MountableFile.forClasspathResource("doorkeeper.rb"),
                "/app/config/initializers/doorkeeper.rb");

        DockerTestUtils.startContainer(openProject, testConfiguration);
        this.container = openProject;

        // OpenProject has to be reached with the same URL by the browser and by XWiki. A containerized XWiki shares the
        // network of the browser, while an XWiki running outside Docker reaches the containers through the Docker
        // bridge (Linux only).
        this.baseUrl = testConfiguration.getServletEngine().isOutsideDocker()
            ? "http://172.17.0.1:" + openProject.getMappedPort(80) : "http://" + this.networkAlias;
        // The tests themselves run outside Docker.
        this.apiBaseUrl = "http://" + openProject.getHost() + ':' + openProject.getMappedPort(80);
        LOGGER.info("The OpenProject container is available at [{}].", this.baseUrl);

        // The container answers on its mapped port before the browser container can resolve its network alias.
        XWikiWebDriver driver = testUtils.getDriver();
        new WebDriverWait(driver, Duration.ofMinutes(2), Duration.ofSeconds(5)).ignoring(WebDriverException.class)
            .until(webDriver -> {
                webDriver.get(getBaseUrl() + LOGIN_PATH);
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
     * Identify the work packages by their key (e.g. {@code PROJ-123}) instead of their id ("Project-based semantic
     * identifiers"). This setting applies to the whole instance, and converts the identifiers of all its projects,
     * e.g. {@code demo-project} becomes {@code DP}.
     *
     * @param driver the webdriver used to navigate the instance, with the admin user logged in
     * @throws Exception if the conversion doesn't finish in time
     */
    public void useSemanticIdentifiers(XWikiWebDriver driver) throws Exception
    {
        driver.get(getBaseUrl() + "/admin/settings/work_packages_identifier");
        waitPageLoad(driver);
        // The form asks for a confirmation in a dialog before submitting the setting, so submit it directly.
        driver.executeScript("const form = document.getElementById('wp-identifier-settings-form');"
            + "for (const input of form.querySelectorAll(\"[name='settings[work_packages_identifier]']\")) {"
            + "  if (input.type === 'radio') { input.checked = input.value === 'semantic'; }"
            + "  else { input.value = 'semantic'; }"
            + "}"
            + "form.submit();");
        waitPageLoad(driver);

        // The identifiers are converted by a background job, which ends by switching the setting.
        String workPackagesPath = "/work_packages?pageSize=1&filters=%5B%5D";
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(2).toMillis();
        JsonNode workPackage = getApiResource(workPackagesPath).get("_embedded").get("elements").get(0);
        while (workPackage.get("displayId").asText().equals(workPackage.get("id").asText())) {
            if (System.currentTimeMillis() > deadline) {
                throw new TimeoutException("OpenProject didn't switch to semantic identifiers in time.");
            }
            Thread.sleep(1000);
            workPackage = getApiResource(workPackagesPath).get("_embedded").get("elements").get(0);
        }
        LOGGER.info("OpenProject at [{}] uses semantic identifiers.", getBaseUrl());
    }

    /**
     * Stop the container of the instance, if it was started by the tests.
     */
    public void stop()
    {
        if (this.container != null) {
            this.container.stop();
            this.container = null;
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
            // The home page doesn't redirect to the login page when OpenProject doesn't require users to log in.
            String location = getBaseUrl() + LOGIN_PATH;

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

        WebElement submitElem =
            driver.findElement(By.cssSelector("[data-test-selector='user-login--form'] button[type='submit']"));
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

    /**
     * Get a resource of the OpenProject API (v3) as an anonymous user, e.g. to compute the expected results of a test
     * from the demo data, which changes between OpenProject versions. Anonymous users see the public demo projects,
     * since the instance doesn't require users to log in.
     *
     * @param path the path of the resource, relative to {@code /api/v3}, with its query string (already encoded)
     * @return the JSON response
     * @throws Exception if the request fails or OpenProject doesn't answer with a success
     */
    public JsonNode getApiResource(String path) throws Exception
    {
        if (this.apiBaseUrl == null) {
            throw new IllegalStateException("Can't call the API without having started the instance.");
        }
        URI uri = URI.create(this.apiBaseUrl + "/api/v3" + path);
        HttpResponse<String> response =
            HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                String.format("Failed to get [%s]: %s %s", uri, response.statusCode(), response.body()));
        }
        return new ObjectMapper().readTree(response.body());
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
        String newApplicationUrl = getBaseUrl() + "/admin/oauth/applications/new";
        driver.get(newApplicationUrl);
        waitPageLoad(driver);
        // The first login of the admin (from the login page, without a page to go back to) starts the onboarding tour
        // of the demo projects, whose overlay then covers the pages opened next. OpenProject keeps the state of the
        // tour in the session storage, so clear it and reload the page to stop the tour.
        driver.executeScript("window.sessionStorage.removeItem('openProject-onboardingTour');");
        driver.get(newApplicationUrl);
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
