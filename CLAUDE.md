# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

XWiki extension (groupId `com.xwiki.projectmanagement`, parent `com.xwiki.parent:xwikisas-parent-platform`) that integrates project management tools into XWiki. Minimal supported XWiki version: 15.10. OpenProject is currently the only concrete integration.

## Build and test

Standard XWiki Maven build (Checkstyle, Revapi and JaCoCo coverage checks come from the parent POM; per-module minimum coverage is set by `xwiki.jacoco.instructionRatio` in the module poms).

```
mvn clean install                       # all modules, with unit tests and quality checks
mvn clean install -pl project-management-macro -am
mvn test -pl project-management-livedata -Dtest=ProjectManagementEntryStoreTest
mvn test -pl <module> -Dtest=ProjectManagementEntryStoreTest#someMethod
mvn xar:format -pl project-management-openproject/project-management-openproject-ui   # after editing wiki page XML
```

`project-management-macro` also has rendering tests (`IntegrationTests`, `RenderingTestSuite`) driven by the `*.test` files in `src/test/resources`, using a fake `test` client (`internal/TestClient`, data in `testclient/workitems.json`).

### Functional (Docker) tests

They live in `project-management-test/project-management-test-docker` (tests under `src/test/it`, page objects in `project-management-test-pageobjects`). They only run in the `integration-tests` profile, and need Docker: they start XWiki, a browser and an OpenProject instance (OpenProject takes minutes to start, it is started once and shared by all test classes through `AllIT`).

```
mvn clean install -Pintegration-tests,docker                 # all tests (AllIT)
# a single class, from project-management-test-docker, once the other modules are installed
mvn clean install -Pdocker -Dit.test=OpenProjectChartMacroIT
```

To reuse an existing OpenProject instance (see `ExternalOpenProjectInstance`), pass `-Dopenproject.url=http://172.17.0.1:8082`, `-Dopenproject.username` / `-Dopenproject.password` (default `Admin` / `admin`), and optionally `-Dopenproject.clientId` / `-Dopenproject.clientSecret` to reuse an OAuth application.

The `project-management-test` poms declare their own parent version and are not bumped automatically with the root version. Before running functional tests, check that they match the root `pom.xml` version and install the root modules first (`mvn install -DskipTests`), otherwise stale XARs from `~/.m2` get tested.

### Release

```
mvn release:prepare -Pintegration-tests -DskipTests -Darguments="-N"
mvn release:perform -Pintegration-tests -DskipTests -Darguments="-DskipTests"
```

## Architecture

The generic layer is tool-agnostic; a tool integration plugs in by providing components with its own hint (e.g. `openproject`).

- **`project-management-api`**: the `ProjectManagementClient` role (get/create/update/delete work items, `projects()`), the generic `WorkItem` / `Project` / `PaginatedResult` model, and `ProjectManagementManager`, which looks up the client component by its hint (the `client` string passed around everywhere). `ProjectManagementClientExecutionContext` carries per-execution values such as `client` and `translationPrefix`. Also exposes generic REST resources and `WorkItemPropertyDisplayer`s (string, date, list, linkable).
- **`project-management-livedata`**: a LiveData source with hint `projectmanagement`. Its source parameters include the client id; the entry store delegates to `ProjectManagementManager`, and the configuration resolver lets each client contribute its own LiveData configuration (e.g. `OpenProjectLivedataConfigurationResolver`).
- **`project-management-macro`**: abstract macros that integrations extend. `AbstractProjectManagementMacro` turns its parameters into a LiveData macro call (source `projectmanagement`, client added via `addToSourceParams`) and chooses a displayer (`WorkItemsDisplayer`: LiveData table/cards, inline, single); it supports async rendering. `AbstractProjectManagementChartMacro` renders Chart.js charts (pie/bar/line/doughnut displayers). Also holds the shared filter/property/preset parameter types and the filter builder JS.
- **`project-management-calendar`**: abstract calendar macro (FullCalendar) fed by a `CalendarEventProvider` per client, with its REST resource.
- **`project-management-presets`**: saved filter/property presets stored as wiki objects (`PresetsManager`, `-ui` XAR).
- **`project-management-relations`**: relations between wiki pages and external work items (`RelationsManager`, REST, `-ui` XAR).
- **`project-management-openproject`**: the OpenProject integration.
  - `-api`: `OpenProjectClient` (the `openproject` `ProjectManagementClient`), the REST API client (`DefaultOpenProjectApiClient`, wrapped by `CachingOpenProjectApiClient`, built through `OpenProjectApiClientFactory`), OAuth/bearer authentication, connection configuration (`OpenProjectConfiguration`, `OpenProjectConnection`), filter/sort mapping to OpenProject queries (`internal/processing`), and OpenProject-specific REST resources.
  - `-macro`: the concrete macros (`openproject`, chart, calendar, open/closed, assigned-to-me, news, projects, spent time, create work package, ...). Macro parameter labels/descriptions are in `src/main/resources/ApplicationResources.properties`. Some macros extend the generic abstract macros, the "direct" ones extend `AbstractOpenProjectDirectMacro`. `LicenseChecker` gates them on the XWiki SAS licensing.
  - `-ui`: wiki pages (XAR) under `OpenProject/`: administration/connection classes and sheets, OAuth renewal, CKEditor plugin, templates, translations.
  - `-xip`: packaged XIP bundle for offline install.

`documentation.md` documents the REST endpoints used by the OpenProject side of the integration (get/create pages by id, link pages to work packages/projects, search linked or mentioning pages, create a space from a template, instance id), exposed under `/xwiki/rest`.
