## Project Management

Provides the tools necessary for integrating project management software into XWiki.

* Project Lead: [Teo Caras](https://github.com/trrenty)
* [Documentation](https://store.xwiki.com/xwiki/bin/view/Extension/ProjectManagement)
* Communication: [Forum and mailing list](http://dev.xwiki.org/xwiki/bin/view/Community/MailingLists), [chat](http://dev.xwiki.org/xwiki/bin/view/Community/IRC)
* [Development Practices](http://dev.xwiki.org)
* License: LGPL 2.1+
* Minimal XWiki version supported: XWiki 15.10
* Translations: N/A
* Sonar Dashboard: N/A
* Continuous Integration Status: [![Build Status](http://ci.xwikisas.com/view/All/job/xwikisas/job/project-management/job/master/badge/icon)](https://ci.xwikisas.com/view/All/job/xwikisas/job/project-management/job/main/)

# Release

```
mvn release:prepare -Pintegration-tests -DskipTests -Darguments="-N"
mvn release:perform -Pintegration-tests -DskipTests -Darguments="-DskipTests"
```

# Functional tests

The Docker based functional tests live in `project-management-test/project-management-test-docker`. They need Docker and start an XWiki instance, a browser and an OpenProject instance. Starting OpenProject takes a few minutes, but it is started only once and shared by all the test classes.

```
# All the tests (AllIT)
mvn clean install -Pintegration-tests,docker
# A single test class, from the project-management-test-docker directory, once the other modules are installed
mvn clean install -Pdocker -Dit.test=OpenProjectChartMacroIT
```

To reuse an OpenProject instance between runs instead of starting a new one each time, start it as described in `ExternalOpenProjectInstance` and pass:

* `-Dopenproject.url=http://172.17.0.1:8082`: the URL of the instance, reachable from the XWiki and browser containers
* `-Dopenproject.username` and `-Dopenproject.password`: the admin credentials (`Admin` / `admin` by default)
* `-Dopenproject.clientId` and `-Dopenproject.clientSecret`: an existing OAuth application, to avoid creating a new one on each run
