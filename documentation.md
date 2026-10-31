# XWiki Endpoints Documentation

## Overview

**Base URL:** `http(s)://<your-xwiki-host>/xwiki/rest`

**Authentication:** All requests are executed with the permissions of the authenticated user. You can authenticate
using **Basic Auth** or an **OIDC Bearer Token**
(`Authorization: Bearer <token>`). The endpoints only return data and perform actions that the logged-in user has
rights to access. It works also for guests.

Note: Bearer Token authentication is only available if you configured the Token authenticator in your
wiki: https://extensions.xwiki.org/xwiki/bin/view/Extension/OpenID%20Connect/Token%20Authenticator/

**Content type:** Unless stated otherwise, the endpoints accept and return **JSON** (`application/json`) or **XML** (`application/xml`).

---

## Data Models

### `Page` (Response)

Returned by the document and space endpoints. Represents a wiki page.

```json
{
  "links": [
    {
      "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject",
      "rel": "http://www.xwiki.org/rel/space",
      "type": null,
      "hrefLang": null
    },
    {
      "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/history",
      "rel": "http://www.xwiki.org/rel/history",
      "type": null,
      "hrefLang": null
    },
    {
      "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects",
      "rel": "http://www.xwiki.org/rel/objects",
      "type": null,
      "hrefLang": null
    },
    {
      "href": "http://localhost:8080/xwiki/rest/syntaxes",
      "rel": "http://www.xwiki.org/rel/syntaxes",
      "type": null,
      "hrefLang": null
    },
    {
      "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/openproject/documents",
      "rel": "self",
      "type": null,
      "hrefLang": null
    },
    {
      "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/classes/Projects.MyProject.WebHome",
      "rel": "http://www.xwiki.org/rel/class",
      "type": null,
      "hrefLang": null
    }
  ],
  "id": "a1b2c",
  "fullName": "Projects.MyProject.WebHome",
  "wiki": "xwiki",
  "space": "Projects.MyProject",
  "name": "WebHome",
  "title": "My New Page",
  "rawTitle": "My New Page",
  "parent": "",
  "parentId": "",
  "version": "5.1",
  "author": "XWiki.JohnDoe",
  "authorName": null,
  "xwikiRelativeUrl": "http://localhost:8080/xwiki/bin/view/Projects/MyProject/",
  "xwikiAbsoluteUrl": "http://localhost:8080/xwiki/bin/view/Projects/MyProject/",
  "translations": {
    "links": [],
    "translations": [],
    "default": ""
  },
  "syntax": "xwiki/2.1",
  "language": "",
  "majorVersion": 5,
  "minorVersion": 1,
  "hidden": false,
  "enforceRequiredRights": false,
  "created": 1774958852000,
  "creator": "XWiki.JohnDoe",
  "creatorName": null,
  "modified": 1774961817000,
  "modifier": "XWiki.JohnDoe",
  "modifierName": null,
  "originalMetadataAuthor": "XWiki.JohnDoe",
  "originalMetadataAuthorName": null,
  "comment": "",
  "content": "Welcome to the project page.",
  "clazz": null,
  "objects": null,
  "attachments": null,
  "hierarchy": {
    "items": [
      {
        "label": "xwiki",
        "name": "xwiki",
        "type": "wiki",
        "url": "http://localhost:8080/xwiki/bin/view/Main/"
      },
      {
        "label": "Projects",
        "name": "Projects",
        "type": "space",
        "url": "http://localhost:8080/xwiki/bin/view/Projects/"
      },
      {
        "label": "MyProject",
        "name": "MyProject",
        "type": "space",
        "url": "http://localhost:8080/xwiki/bin/view/Projects/MyProject/"
      },
      {
        "label": "WebHome",
        "name": "WebHome",
        "type": "document",
        "url": "http://localhost:8080/xwiki/bin/view/Projects/MyProject/"
      }
    ]
  }
}
```

#### Field Reference

| Field                        | Type               | Description                                                                                                                                              |
|------------------------------|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `links`                      | `Array`            | Navigation links to related REST resources (space, history, objects, syntaxes, self, class). Each link has `href`, `rel`, `type`, and `hrefLang`.        |
| `id`                         | `String`           | The short unique identifier assigned to this page. Used to reference the page in all other endpoints.                                                    |
| `fullName`                   | `String`           | The full internal reference of the page (e.g. `Projects.MyProject.WebHome`).                                                                             |
| `wiki`                       | `String`           | The wiki this page belongs to (e.g. `xwiki`).                                                                                                            |
| `space`                      | `String`           | The space the page is in, using dot notation.                                                                                                            |
| `name`                       | `String`           | The technical name of the page (last part of the reference).                                                                                             |
| `title`                      | `String`           | The title of the page.                                                                                                                                   |
| `rawTitle`                   | `String`           | The raw title of the page.                                                                                                                               |
| `parent`                     | `String`           | The reference of the parent page (empty if none).                                                                                                        |
| `parentId`                   | `String`           | The identifier of the parent page (empty if none).                                                                                                       |
| `version`                    | `String`           | The current version number (e.g. `5.1`).                                                                                                                 |
| `author`                     | `String`           | The internal reference of the user who last edited the page.                                                                                             |
| `authorName`                 | `String`           | The display name of the last author. Present when `prettyNames=true`.                                                                                    |
| `xwikiRelativeUrl`           | `String`           | The URL to view this page in XWiki.                                                                                                                      |
| `xwikiAbsoluteUrl`           | `String`           | The absolute URL to view this page in XWiki.                                                                                                             |
| `translations`               | `Object`           | Translation metadata. Contains `links`, `translations` (array of available translations), and `default` (the default language).                          |
| `syntax`                     | `String`           | The markup syntax used (e.g. `xwiki/2.1`).                                                                                                               |
| `language`                   | `String`           | The language of the page (empty string for the default language).                                                                                        |
| `majorVersion`               | `Integer`          | The major version number.                                                                                                                                |
| `minorVersion`               | `Integer`          | The minor version number.                                                                                                                                |
| `hidden`                     | `Boolean`          | Whether the page is hidden.                                                                                                                              |
| `enforceRequiredRights`      | `Boolean`          | Whether required rights enforcement is enabled.                                                                                                          |
| `created`                    | `Long`             | When the page was first created (Unix timestamp in milliseconds).                                                                                        |
| `creator`                    | `String`           | The internal reference of the user who created the page.                                                                                                 |
| `creatorName`                | `String`           | The display name of the creator. Present when `prettyNames=true`.                                                                                        |
| `modified`                   | `Long`             | When the page was last modified (Unix timestamp in milliseconds).                                                                                        |
| `modifier`                   | `String`           | The internal reference of the user who last modified the page.                                                                                           |
| `modifierName`               | `String`           | The display name of the modifier. Present when `prettyNames=true`.                                                                                       |
| `originalMetadataAuthor`     | `String`           | The reference of the original metadata author.                                                                                                           |
| `originalMetadataAuthorName` | `String`           | The display name of the original metadata author.                                                                                                        |
| `comment`                    | `String`           | The save comment of the last edit (can be empty).                                                                                                        |
| `content`                    | `String`           | The raw content of the page in wiki syntax.                                                                                                              |
| `clazz`                      | `Object` or `null` | The class definition, when `class=true` is requested.                                                                                                    |
| `objects`                    | `Object` or `null` | The structured data objects attached to the page, when `objects=true` is requested. See the Objects structure below.                                     |
| `attachments`                | `Object` or `null` | The files attached to the page, when `attachments=true` is requested.                                                                                    |
| `hierarchy`                  | `Object`           | The breadcrumb hierarchy of the page. Contains an `items` array where each item has `label`, `name`, `type` (`wiki`, `space`, or `document`), and `url`. |

#### Objects Structure (when `objects=true`)

When the `objects` query parameter is set to `true`, the `objects` field contains an object with `links` and
`objectSummaries`. Each entry in `objectSummaries` represents one XWiki object attached to the page. The link between a page and OpenProject (see endpoint 3) is stored as a `ProjectManagement.Code.RelationClass` object (the `attributes` arrays are shortened in this example):

```json
{
  "objects": {
    "links": [],
    "objectSummaries": [
      {
        "links": [
          {
            "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects/ProjectManagement.Code.RelationClass/0",
            "rel": "self",
            "type": null,
            "hrefLang": null
          }
        ],
        "id": "xwiki:Projects.MyProject.WebHome:a0e1f2d3-b4c5-6789-abcd-ef0123456789",
        "guid": "a0e1f2d3-b4c5-6789-abcd-ef0123456789",
        "pageId": "xwiki:Projects.MyProject.WebHome",
        "pageVersion": "3.1",
        "wiki": "xwiki",
        "space": "Projects.MyProject",
        "pageName": "WebHome",
        "pageAuthor": "XWiki.JohnDoe",
        "pageAuthorName": null,
        "className": "ProjectManagement.Code.RelationClass",
        "number": 0,
        "headline": "openproject",
        "properties": [
          {
            "links": [
              {
                "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects/ProjectManagement.Code.RelationClass/0/properties/client",
                "rel": "self",
                "type": null,
                "hrefLang": null
              }
            ],
            "attributes": [
              {
                "links": [],
                "name": "name",
                "value": "client"
              },
              {
                "links": [],
                "name": "prettyName",
                "value": "Project Management Client ID"
              },
              {
                "links": [],
                "name": "size",
                "value": "20"
              }
            ],
            "value": "openproject",
            "name": "client",
            "type": "String"
          },
          {
            "links": [
              {
                "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects/ProjectManagement.Code.RelationClass/0/properties/project",
                "rel": "self",
                "type": null,
                "hrefLang": null
              }
            ],
            "attributes": [
              {
                "links": [],
                "name": "name",
                "value": "project"
              },
              {
                "links": [],
                "name": "prettyName",
                "value": "Project ID"
              },
              {
                "links": [],
                "name": "size",
                "value": "20"
              }
            ],
            "value": "45",
            "name": "project",
            "type": "String"
          },
          {
            "links": [
              {
                "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects/ProjectManagement.Code.RelationClass/0/properties/workItem",
                "rel": "self",
                "type": null,
                "hrefLang": null
              }
            ],
            "attributes": [
              {
                "links": [],
                "name": "name",
                "value": "workItem"
              },
              {
                "links": [],
                "name": "prettyName",
                "value": "Work item ID"
              },
              {
                "links": [],
                "name": "size",
                "value": "20"
              }
            ],
            "value": "125",
            "name": "workItem",
            "type": "String"
          },
          {
            "links": [
              {
                "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects/ProjectManagement.Code.RelationClass/0/properties/clientParams",
                "rel": "self",
                "type": null,
                "hrefLang": null
              }
            ],
            "attributes": [
              {
                "links": [],
                "name": "name",
                "value": "clientParams"
              },
              {
                "links": [],
                "name": "prettyName",
                "value": "Client Parameters"
              },
              {
                "links": [],
                "name": "size",
                "value": "40"
              }
            ],
            "value": "{\"instance\":\"My OpenProject\"}",
            "name": "clientParams",
            "type": "TextArea"
          }
        ]
      }
    ]
  }
}
```

The properties of a `ProjectManagement.Code.RelationClass` object:

| Property       | Description                                                                                                                                                       |
|----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `client`       | The project management tool the link points to. Always `openproject` for the links created by these endpoints.                                                    |
| `project`      | The OpenProject project ID (empty if the page isn't linked to a project).                                                                                         |
| `workItem`     | The OpenProject work package ID (empty if the page isn't linked to a work package).                                                                               |
| `clientParams` | A JSON object holding the name of the wiki's OpenProject connection the link was made for (e.g. `{"instance":"My OpenProject"}`). Empty if no instance was given. |

### `Page` (Request Body — for PUT)

When creating or updating a page, you send a `Page` object in the request body. Only a subset of fields is needed:

```json
{
  "title": "My New Page",
  "content": "This is the page content."
}
```

### `WorkPackageLink` (Request Body — for PUT link)

```json
{
  "project": "42",
  "workPackage": "123",
  "instance": "6a1fd2c3-6b7e-4c3d-9a0f-1c2d3e4f5a6b"
}
```

| Field         | Type     | Required | Description                                                                                                                                                                                                           |
|---------------|----------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `project`     | `String` | ❌        | The OpenProject project ID to link to.                                                                                                                                                                                |
| `workPackage` | `String` | ❌        | The OpenProject work package ID to link to.                                                                                                                                                                           |
| `instance`    | `String` | ❌        | The instance id of the OpenProject server. It must match the instance id of one of the OpenProject connections configured in the wiki; the name of that connection is stored in the `clientParams` property of the link. |

### `SearchResults` (Response)

Returned by the search endpoints (endpoints 4, 5 and 8). The response is a standard XWiki `SearchResults` object, built from a Solr search. Each search result entry represents a page that matches the query.

```json
{
  "links": [],
  "template": "http://localhost:8080/xwiki/rest/?q={solrquery}(&number={number})(&start={start})(&orderField={fieldname}(&order={asc|desc}))(&distinct=1)(&prettyNames={false|true})(&wikis={wikis})(&className={classname})",
  "searchResults": [
    {
      "links": [
        {
          "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome",
          "rel": "http://www.xwiki.org/rel/page",
          "type": null,
          "hrefLang": null
        }
      ],
      "type": "page",
      "id": "xwiki:Projects.MyProject.WebHome",
      "pageFullName": "Projects.MyProject.WebHome",
      "title": "My New Page",
      "wiki": "xwiki",
      "space": "Projects.MyProject",
      "pageName": "WebHome",
      "modified": 1774961817000,
      "author": "XWiki.JohnDoe",
      "authorName": null,
      "version": "3.1",
      "language": "",
      "score": 1.0
    }
  ]
}
```

Each search result entry contains:

| Field          | Type     | Description                                                                                      |
|----------------|----------|--------------------------------------------------------------------------------------------------|
| `type`         | `String` | Always `"page"`.                                                                                 |
| `id`           | `String` | The full qualified identifier of the page.                                                       |
| `pageFullName` | `String` | The full name of the page (e.g. `Projects.MyProject.WebHome`).                                   |
| `title`        | `String` | The title of the page.                                                                           |
| `wiki`         | `String` | The wiki the page belongs to.                                                                    |
| `space`        | `String` | The space the page is in.                                                                        |
| `pageName`     | `String` | The technical name of the page.                                                                  |
| `modified`     | `Long`   | Last modification timestamp (Unix milliseconds).                                                 |
| `author`       | `String` | The reference of the last author.                                                                |
| `authorName`   | `String` | The display name of the last author. Present when `prettyNames=true`.                            |
| `version`      | `String` | The page version.                                                                                |
| `language`     | `String` | The language of the page (empty for the default language).                                       |
| `score`        | `Float`  | The Solr relevance score.                                                                        |
| `hierarchy`    | `Object` | The breadcrumb hierarchy of the page. Only returned by recent XWiki versions (not by XWiki 15.10). |

The search results don't contain the matched link or mention objects. Use endpoint 11 to get the link of a page, and endpoints 12 or 13 to get its mentions.

### `Objects` (Response)

Returned by the page mentions endpoints (endpoints 12 and 13). It's a standard XWiki `Objects` object: its `objectSummaries` array holds one summary per `OpenProject.Code.MentionClass` object of the page, i.e. per work package mentioned in the page content. The summaries don't contain the object properties (`workPackageId`, the mentioned work package ID, and `instance`, the name of the OpenProject connection); `headline` holds the value of one of them, and the `http://www.xwiki.org/rel/object` link points to the full object, with its properties.

```json
{
  "links": [],
  "objectSummaries": [
    {
      "links": [
        {
          "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects/OpenProject.Code.MentionClass/0",
          "rel": "http://www.xwiki.org/rel/object",
          "type": null,
          "hrefLang": null
        }
      ],
      "id": "xwiki:Projects.MyProject.WebHome:b1c2d3e4-f5a6-7890-bcde-f01234567890",
      "guid": "b1c2d3e4-f5a6-7890-bcde-f01234567890",
      "pageId": "xwiki:Projects.MyProject.WebHome",
      "pageVersion": "3.1",
      "wiki": "xwiki",
      "space": "Projects.MyProject",
      "pageName": "WebHome",
      "pageAuthor": "XWiki.JohnDoe",
      "pageAuthorName": null,
      "className": "OpenProject.Code.MentionClass",
      "number": 0,
      "headline": "125"
    }
  ]
}
```

---

## Endpoints

---

### 1. Get Document by ID

Retrieves a wiki page using its short unique identifier.

```
GET /openproject/documents/{id}
```

**Parameters:**

| Parameter     | Location | Type      | Required | Default | Description                                                                                                           |
|---------------|----------|-----------|----------|---------|-----------------------------------------------------------------------------------------------------------------------|
| `id`          | path     | `String`  | ✅        | —       | The short unique identifier of the page.                                                                              |
| `prettyNames` | query    | `Boolean` | ❌        | `false` | If `true`, user references are added to the response as human-readable display names instead of technical references. |
| `objects`     | query    | `Boolean` | ❌        | `false` | If `true`, the response includes all structured data objects attached to the page.                                    |
| `class`       | query    | `Boolean` | ❌        | `false` | If `true`, the response includes class definition metadata.                                                           |
| `attachments` | query    | `Boolean` | ❌        | `false` | If `true`, the response includes the list of files attached to the page.                                              |

**What happens:**

1. Looks up the page associated with the given short `id`.
2. If found, returns the full page representation.
3. The response `id` field contains the short identifier (not the internal reference).

**Responses:**

| Status                        | Body                            | When                                                                     |
|-------------------------------|---------------------------------|--------------------------------------------------------------------------|
| **200 OK**                    | `Page` (JSON or XML)            | The page was found and returned successfully.                            |
| **400 Bad Request**           | `"Missing page id."`            | The `id` parameter was empty or not provided.                            |
| **401 Unauthorized**          | —                               | The current user doesn't have view right on the page.                    |
| **404 Not Found**             | —                               | No page is associated with the given identifier, or the page was deleted. |
| **500 Internal Server Error** | `"Failed to retrieve the page."` or no body | The page couldn't be retrieved (with that message), or another unexpected error occurred, e.g. while resolving the identifier (no body). |

---

### 2. Create or Update Document

Creates a new wiki page or updates an existing one, and assigns it a short unique identifier. This identifier can
then be used in all other endpoints to reference the page.

```
PUT /openproject/documents
```

**Parameters:**

| Parameter       | Location | Type          | Required | Default | Description                                                                                                                                                  |
|-----------------|----------|---------------|----------|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `docRef`        | query    | `String`      | ✅        | —       | The full page reference where the page should be created or updated (e.g. `Projects.MyProject.WebHome`). If it doesn't specify a wiki, the main wiki is used. |
| `minorRevision` | query    | `Boolean`     | ❌        | —       | If `true`, the edit is saved as a minor version (does not appear prominently in page history).                                                               |
| `create`        | query    | `Boolean`     | ❌        | `false` | If `true`, the request will fail if the document already exists.                                                                                             |
| *(body)*        | body     | `Page` (JSON) | ✅        | —       | The page data (title, content, etc.). See the `Page` request model above.                                                                                    |

**What happens:**

1. If the page at `docRef` does not exist, it is **created** with the provided content.
2. If it already exists, it is **updated** with the provided content.
3. A short unique identifier is associated with this page (the existing one is kept if the page already has one).
4. The response contains the full page data, with the `id` field set to the short identifier.

**Responses:**

| Status                        | Body                                                            | When                                                                                       |
|-------------------------------|-----------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| **201 Created**               | `Page` (JSON) with `id`                                         | A new page was created.                                                                    |
| **202 Accepted**              | `Page` (JSON) with `id`                                         | An existing page was updated (also returned when the request didn't change the page).      |
| **400 Bad Request**           | Error message                                                   | The `docRef` query parameter was missing.                                                  |
| **401 Unauthorized**          | —                                                               | The current user doesn't have view or edit right on the page.                              |
| **401 Unauthorized**          | `"Access denied in view mode. Can't attach unique identifier."` | The page was saved but the current user doesn't have view right on it.                     |
| **409 Conflict**              | —                                                               | The `create` parameter is `true` and the document already exists.                          |
| **500 Internal Server Error** | Error message                                                   | Failed to save the page (`"Failed to save the page [...]."`) or to attach its identifier.  |

---

### 3. Create Link between Page and OpenProject Entity

Links a wiki page to an OpenProject project and/or work package. A page holds at most one link to OpenProject: if the page is already linked, the existing link is updated.

```
PUT /openproject/documents/{id}/links
```

**Parameters:**

| Parameter       | Location | Type                     | Required | Default | Description                                                                              |
|-----------------|----------|--------------------------|----------|---------|------------------------------------------------------------------------------------------|
| `id`            | path     | `String`                 | ✅        | —       | The short unique identifier of the page (obtained from endpoints 2, 9 or 10).            |
| `minorRevision` | query    | `Boolean`                | ❌        | —       | If `true`, adding the link is saved as a minor version change.                           |
| *(body)*        | body     | `WorkPackageLink` (JSON) | ✅        | —       | The link data. See the `WorkPackageLink` model above.                                    |

**What happens:**

1. Looks up the page associated with the given `id`.
2. If `instance` is set, looks for the OpenProject connection of the wiki with this instance id.
3. If the page doesn't have a link to OpenProject yet, a `ProjectManagement.Code.RelationClass` object is created on the page with the provided project ID, work package ID and connection name. Otherwise, the existing object is updated with the provided values (the values that aren't provided are kept).
4. The link can later be searched for using the link search endpoints (endpoints 4 and 5), and retrieved with endpoint 11.

**Responses:**

| Status                        | Body                                                       | When                                                                                     |
|-------------------------------|------------------------------------------------------------|------------------------------------------------------------------------------------------|
| **201 Created**               | `Object` (JSON or XML)                                     | The link was created. The `Location` header contains the URL of the new link object.    |
| **202 Accepted**              | `Object` (JSON or XML)                                     | The existing link of the page was updated.                                               |
| **304 Not Modified**          | —                                                          | The page is already linked with the provided values.                                     |
| **400 Bad Request**           | `"Missing link entity"`                                    | The request body was empty.                                                              |
| **400 Bad Request**           | `"Could not find a configured instance for the id [...]."` | The `instance` doesn't match the instance id of any OpenProject connection of the wiki. |
| **401 Unauthorized**          | —                                                          | The current user doesn't have view or edit right on the page.                            |
| **404 Not Found**             | —                                                          | No page is associated with the given `id`, or the page was deleted.                      |
| **500 Internal Server Error** | `"Failed to link the page."`                               | An unexpected error occurred.                                                            |

---

### 4. Search Pages Linked to an OpenProject Project

Finds all wiki pages that have been linked to a specific OpenProject project.

```
GET /openproject/links/projects/{id}
```

**Parameters:**

| Parameter     | Location | Type      | Required | Default | Description                                                                                                                                                                                               |
|---------------|----------|-----------|----------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id`          | path     | `String`  | ✅        | —       | The OpenProject **project ID** (must be a number).                                                                                                                                                        |
| `instance`    | query    | `String`  | ❌        | —       | The instance id of an OpenProject server. Only the links made for the wiki's OpenProject connections to this instance are returned. If no connection has this instance id, the value is matched against the connection names. |
| `number`      | query    | `Integer` | ❌        | `25`    | Maximum number of results to return. A value of `0` or less doesn't remove the limit: the default page size of the Solr search applies.                                                                  |
| `start`       | query    | `Integer` | ❌        | `0`     | Offset for pagination (0-based).                                                                                                                                                                          |
| `orderField`  | query    | `String`  | ❌        | `""`    | Name of the (Solr) field to sort results by.                                                                                                                                                              |
| `order`       | query    | `String`  | ❌        | `asc`   | Sort direction: `asc` (ascending) or `desc` (descending).                                                                                                                                                 |
| `prettyNames` | query    | `Boolean` | ❌        | `false` | If `true`, user references are displayed as readable names.                                                                                                                                               |

**What happens:**

1. Searches all wiki pages (of the current wiki) that have a link object with a `project` value matching the given `id`.
2. If `instance` is not empty, results are further filtered to only include links made for this OpenProject instance.
3. Only pages the current user has permission to view are returned. Hidden pages are only returned to users with programming right, and page translations aren't returned.

**Responses:**

| Status                        | Body                                 | When                                                |
|-------------------------------|--------------------------------------|-----------------------------------------------------|
| **200 OK**                    | `SearchResults` (JSON or XML)        | Search completed. May contain zero or more results. |
| **400 Bad Request**           | `"Project id should be an integer."` | The `id` was not a valid number.                    |
| **500 Internal Server Error** | Error object                         | An unexpected error occurred.                       |

---

### 5. Search Pages Linked to an OpenProject Work Package

Finds all wiki pages that have been linked to a specific OpenProject work package. Works exactly like endpoint 4,
but matches on the **work package ID** instead of the project ID.

```
GET /openproject/links/workPackages/{id}
```

**Parameters:**

| Parameter     | Location | Type      | Required | Default | Description                                                                                    |
|---------------|----------|-----------|----------|---------|------------------------------------------------------------------------------------------------|
| `id`          | path     | `String`  | ✅        | —       | The OpenProject **work package ID** (must be a number).                                        |
| `instance`    | query    | `String`  | ❌        | —       | The instance id of an OpenProject server (see endpoint 4).                                     |
| `number`      | query    | `Integer` | ❌        | `-1`    | Max results. A value of `0` or less (the default) means the default page size of the Solr search. |
| `start`       | query    | `Integer` | ❌        | `0`     | Pagination offset.                                                                             |
| `orderField`  | query    | `String`  | ❌        | `""`    | Sort field.                                                                                    |
| `order`       | query    | `String`  | ❌        | `asc`   | Sort direction.                                                                                |
| `prettyNames` | query    | `Boolean` | ❌        | `false` | Human-readable names.                                                                          |

**What happens:**

1. Searches all wiki pages (of the current wiki) that have a link object with a `workItem` value matching the given `id`.
2. If `instance` is not empty, only links made for this OpenProject instance are returned.
3. Only pages the current user can view are returned (same filtering as endpoint 4).

**Responses:**

| Status                        | Body                                     | When                             |
|-------------------------------|------------------------------------------|----------------------------------|
| **200 OK**                    | `SearchResults` (JSON or XML)            | Search completed.                |
| **400 Bad Request**           | `"WorkPackage id should be an integer."` | The `id` was not a valid number. |
| **500 Internal Server Error** | Error object                             | Unexpected error.                |

---

### 6. Create OpenProject Space from Template

Creates a complete folder structure (space) in the wiki from a predefined OpenProject template. This is used to
quickly scaffold a set of pages pre-configured for OpenProject integration.

```
POST /openproject/spaces
```

**Parameters:**

| Parameter     | Location | Type      | Required | Default             | Description                                                                                                                                         |
|---------------|----------|-----------|----------|---------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `docRef`      | query    | `String`  | ✅        | —                   | The full page reference where the space should be created (e.g. `Projects.MyProject.WebHome`). If it doesn't specify a wiki, the main wiki is used. |
| `withId`      | query    | `Boolean` | ❌        | `false`             | If `true`, each created page will also get a short unique identifier assigned (so they can be used with endpoints 1–3).                             |
| `instance`    | query    | `String`  | ❌        | `""`                | The OpenProject instance id. If set, the created space will contain a link object that will point to the OP instance.                               |
| `project`     | query    | `Integer` | ❌        | —                   | The id of an OP project. If set, the space will contain a link to the project.                                                                      |
| `workPackage` | query    | `Integer` | ❌        | —                   | The id of a work package. If set, the space will contain a link to the work package.                                                                |
| `title`       | query    | `String`  | ❌        | `OpenProject Space` | The title of the newly created space.                                                                                                               |

**Responses:**

| Status                        | Body                                                                        | When                                                                                                                                                                                                                                                                                     |
|-------------------------------|-----------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **200 OK**                    | `List<Page>` (JSON array)                                                   | The space was created. The list contains the page at `docRef` and the pages created from the template (pages that already existed or that the user isn't allowed to create are skipped and not listed). If `withId=true`, each page's `id` field contains its assigned short identifier. |
| **400 Bad Request**           | Error message                                                               | The `docRef` query parameter was missing.                                                                                                                                                                                                                                                |
| **400 Bad Request**           | `"Could not find a configured instance for the id [...]."`                  | The `instance` query parameter doesn't match the instance id of any OpenProject connection of the wiki.                                                                                                                                                                                  |
| **401 Unauthorized**          | —                                                                           | The authenticated user does not have view and edit permissions on the `docRef` page.                                                                                                                                                                                                     |
| **406 Not Acceptable**        | `"The requested wiki does not have the OpenProject application installed."` | The OpenProject template is not available in the target wiki.                                                                                                                                                                                                                            |
| **409 Conflict**              | `"Document already exists."`                                                | A page already exists at the target location.                                                                                                                                                                                                                                            |
| **409 Conflict**              | `"The OpenProject template must be saved by an administrator."`             | A page of the OpenProject template (`OpenProject.Code.Template`) was last saved by a user who isn't a wiki admin.                                                                                                                                                                        |
| **500 Internal Server Error** | Error message                                                               | Something went wrong during page creation.                                                                                                                                                                                                                                               |

---

### 7. Get XWiki Instance ID

Retrieves the id of the XWiki instance. It doesn't check any right.

```
GET /openproject/metadata
```

**Parameters:**

No params

**Responses:**

| Status                        | Body                                       | When                                 |
|-------------------------------|--------------------------------------------|--------------------------------------|
| **200 OK**                    | `{"instanceId": "<id>"}` (JSON)            | Request was successful.              |
| **500 Internal Server Error** | `"The id was not initialized"`             | The instance id was not initialized. |

---

### 8. Search the Pages that contain a mention to a given Work Package

Finds all wiki pages that contain in their content a mention to an OpenProject work package.

```
GET /openproject/mentions
```

**Parameters:**

| Parameter     | Location | Type      | Required | Default | Description                                                                                                                                                                                                 |
|---------------|----------|-----------|----------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `workPackage` | query    | `String`  | ❌        | `""`    | A work package id (must be a number). If empty, the pages mentioning any work package are returned.                                                                                                         |
| `instance`    | query    | `String`  | ❌        | `""`    | The instance id of an OpenProject server. Only the mentions made for the wiki's OpenProject connections to this instance are returned. If no connection has this instance id, the value is matched against the connection names. |
| `number`      | query    | `Integer` | ❌        | `25`    | Maximum number of results to return. A value of `0` or less doesn't remove the limit: the default page size of the Solr search applies.                                                                    |
| `start`       | query    | `Integer` | ❌        | `0`     | Offset for pagination (0-based).                                                                                                                                                                            |
| `orderField`  | query    | `String`  | ❌        | `""`    | Name of the (Solr) field to sort results by.                                                                                                                                                                |
| `order`       | query    | `String`  | ❌        | `asc`   | Sort direction: `asc` (ascending) or `desc` (descending).                                                                                                                                                   |
| `prettyNames` | query    | `Boolean` | ❌        | `false` | If `true`, user references are displayed as readable names.                                                                                                                                                 |

Only pages the current user can view are returned (same filtering as endpoint 4).

**Responses:**

| Status                        | Body                                      | When                                                 |
|-------------------------------|-------------------------------------------|------------------------------------------------------|
| **200 OK**                    | `SearchResults` (JSON or XML)             | Search completed. May contain zero or more results.  |
| **400 Bad Request**           | `"Work package id should be an integer."` | The `workPackage` parameter was not a valid number. |
| **500 Internal Server Error** | Error object                              | An unexpected error occurred.                        |

---

### 9. Get Document by Reference

Retrieves a wiki page using its reference, and assigns it a short unique identifier if it doesn't have one yet.

```
GET /openproject/documents
```

**Parameters:**

| Parameter     | Location | Type      | Required | Default | Description                                                                                                           |
|---------------|----------|-----------|----------|---------|-----------------------------------------------------------------------------------------------------------------------|
| `docRef`      | query    | `String`  | ✅        | —       | The document reference of a page. If it doesn't specify a wiki, the main wiki is used.                                |
| `prettyNames` | query    | `Boolean` | ❌        | `false` | If `true`, user references are added to the response as human-readable display names instead of technical references. |
| `objects`     | query    | `Boolean` | ❌        | `false` | If `true`, the response includes all structured data objects attached to the page.                                    |
| `class`       | query    | `Boolean` | ❌        | `false` | If `true`, the response includes class definition metadata.                                                           |
| `attachments` | query    | `Boolean` | ❌        | `false` | If `true`, the response includes the list of files attached to the page.                                              |

**Responses:**

| Status                        | Body                                                                             | When                                                       |
|-------------------------------|----------------------------------------------------------------------------------|------------------------------------------------------------|
| **200 OK**                    | `Page` (JSON or XML) with `id`                                                   | The page was found. Its `id` field holds its short unique identifier. |
| **400 Bad Request**           | "Missing `docRef` query parameter pointing to the document with an unique id."   | The document reference query param is empty.               |
| **401 Unauthorized**          | —                                                                                | The current user doesn't have view right on the page.      |
| **404 Not Found**             | —                                                                                | The document reference does not point to an existing page. |
| **500 Internal Server Error** | `"Could not create a unique id for the page."` or `"Failed to retrieve the page."` | An unexpected error occurred.                              |

---

### 10. Get Document Ancestors by ID

Retrieves a wiki page and its ancestors, using the short unique identifier of the page.

```
GET /openproject/documents/{id}/ancestors
```

**Parameters:**

| Parameter     | Location | Type      | Required | Default | Description                                                                                                           |
|---------------|----------|-----------|----------|---------|-----------------------------------------------------------------------------------------------------------------------|
| `id`          | path     | `String`  | ✅        | —       | The short unique identifier of the page.                                                                              |
| `prettyNames` | query    | `Boolean` | ❌        | `false` | If `true`, user references are added to the response as human-readable display names instead of technical references. |
| `objects`     | query    | `Boolean` | ❌        | `false` | If `true`, the response includes all structured data objects attached to the page.                                    |
| `class`       | query    | `Boolean` | ❌        | `false` | If `true`, the response includes class definition metadata.                                                           |
| `attachments` | query    | `Boolean` | ❌        | `false` | If `true`, the response includes the list of files attached to the page.                                              |

**What happens:**

1. Looks up the page associated with the given short `id`.
2. If found, looks for its existing ancestors, i.e. the non-terminal pages (`WebHome`) of the spaces containing it.
3. Attaches unique ids to each page found.
4. Returns the list of these pages, from the top-level ancestor down to the page itself.

**Responses:**

| Status                        | Body                       | When                                                                                         |
|-------------------------------|----------------------------|----------------------------------------------------------------------------------------------|
| **200 OK**                    | `List<Page>` (JSON or XML) | The page and its ancestors were found, their unique ids were initialized and the list was returned successfully. |
| **400 Bad Request**           | `"Missing page id."`       | The `id` parameter was empty or not provided.                                                |
| **401 Unauthorized**          | —                          | The current user doesn't have view right on the page or on one of its ancestors.             |
| **404 Not Found**             | —                          | No page is associated with the given identifier, or the page (other than a space home page) was deleted. |
| **500 Internal Server Error** | —                          | An unexpected error occurred. I.e. unique id initialization failed; database issues.         |

---

### 11. Get the Link of a Page

Retrieves the link between a wiki page and OpenProject (created by endpoint 3 or 6), using the short unique identifier of the page.

```
GET /openproject/documents/{id}/links
```

**Parameters:**

| Parameter | Location | Type     | Required | Default | Description                              |
|-----------|----------|----------|----------|---------|------------------------------------------|
| `id`      | path     | `String` | ✅        | —       | The short unique identifier of the page. |

**Responses:**

| Status                        | Body                                         | When                                                                                                                                                                              |
|-------------------------------|----------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **200 OK**                    | `Object` (JSON or XML)                       | The `ProjectManagement.Code.RelationClass` object of the page whose `client` is `openproject`, with its properties (same structure as an entry of the Objects Structure above). |
| **401 Unauthorized**          | —                                            | The current user doesn't have view right on the page.                                                                                                                             |
| **404 Not Found**             | —                                            | No page is associated with the given identifier, the page was deleted, or the page isn't linked to OpenProject.                                                                   |
| **500 Internal Server Error** | `"Failed to retrieve the link of the page."` | An unexpected error occurred.                                                                                                                                                     |

---

### 12. Get the Mentions of a Page by ID

Retrieves the work package mentions of a wiki page, using the short unique identifier of the page.

```
GET /openproject/documents/{id}/mentions
```

**Parameters:**

| Parameter     | Location | Type      | Required | Default | Description                                                                                    |
|---------------|----------|-----------|----------|---------|------------------------------------------------------------------------------------------------|
| `id`          | path     | `String`  | ✅        | —       | The short unique identifier of the page.                                                       |
| `instance`    | query    | `String`  | ❌        | `""`    | Currently ignored: the mentions made for all the OpenProject connections are returned.         |
| `number`      | query    | `Integer` | ❌        | `25`    | Maximum number of mentions to return.                                                          |
| `start`       | query    | `Integer` | ❌        | `0`     | Offset for pagination (0-based).                                                               |
| `prettyNames` | query    | `Boolean` | ❌        | `false` | If `true`, the last author of the page is also returned as a readable name (`pageAuthorName`). |

**Responses:**

| Status                        | Body                                              | When                                                                      |
|-------------------------------|---------------------------------------------------|---------------------------------------------------------------------------|
| **200 OK**                    | `Objects` (JSON or XML)                           | The mentions of the page (possibly none).                                 |
| **401 Unauthorized**          | —                                                 | The current user doesn't have view right on the page.                     |
| **404 Not Found**             | —                                                 | No page is associated with the given identifier, or the page was deleted. |
| **500 Internal Server Error** | `"Failed to resolve the page id."` or `"Failed to retrieve the mentions of the page."` | An unexpected error occurred.                                             |

---

### 13. Get the Mentions of a Page by Reference

Retrieves the work package mentions of a wiki page, using the reference of the page. Works exactly like endpoint 12, but identifies the page by its reference.

```
GET /openproject/pages/{docRef}/mentions
```

**Parameters:**

| Parameter     | Location | Type      | Required | Default | Description                                                                                                                       |
|---------------|----------|-----------|----------|---------|-----------------------------------------------------------------------------------------------------------------------------------|
| `docRef`      | path     | `String`  | ✅        | —       | The (URL-encoded) reference of the page, e.g. `Projects.MyProject.WebHome`. If it doesn't specify a wiki, the current wiki is used. |
| `instance`    | query    | `String`  | ❌        | `""`    | Currently ignored: the mentions made for all the OpenProject connections are returned.                                            |
| `number`      | query    | `Integer` | ❌        | `25`    | Maximum number of mentions to return.                                                                                             |
| `start`       | query    | `Integer` | ❌        | `0`     | Offset for pagination (0-based).                                                                                                  |
| `prettyNames` | query    | `Boolean` | ❌        | `false` | If `true`, the last author of the page is also returned as a readable name (`pageAuthorName`).                                    |

**Responses:**

| Status                        | Body                                              | When                                                  |
|-------------------------------|---------------------------------------------------|-------------------------------------------------------|
| **200 OK**                    | `Objects` (JSON or XML)                           | The mentions of the page (possibly none).             |
| **401 Unauthorized**          | —                                                 | The current user doesn't have view right on the page. |
| **404 Not Found**             | —                                                 | The page doesn't exist.                               |
| **500 Internal Server Error** | `"Failed to retrieve the mentions of the page."` | An unexpected error occurred.                         |

---

## Internal Endpoints

These endpoints are used by the administration UI of the OpenProject integration (the OpenProject connections administration section) and aren't meant to be called by OpenProject.

---

### Create or Update an OpenProject Connection

Saves the configuration of a connection to an OpenProject server on the given page: the connection itself and the OpenID Connect client configuration used to authenticate the wiki users on OpenProject. The instance id of the OpenProject server is retrieved from the server and stored with the connection (an empty one is stored if the server can't be reached).

```
POST /wikis/{wikiName}/spaces/{spaceName}/pages/{pageName}/openproject/configurations
PUT  /wikis/{wikiName}/spaces/{spaceName}/pages/{pageName}/openproject/configurations
```

`POST` is used to create a connection and `PUT` to update one, but both save the connection on the page, whether it exists or not; only the success status differs.

**Parameters:**

| Parameter   | Location | Type                           | Required | Default | Description                                                                                                                       |
|-------------|----------|--------------------------------|----------|---------|-----------------------------------------------------------------------------------------------------------------------------------|
| `wikiName`  | path     | `String`                       | ✅        | —       | The wiki of the connection page.                                                                                                  |
| `spaceName` | path     | `String`                       | ✅        | —       | The space of the connection page, in the REST format (e.g. `OpenProject/spaces/Code/spaces/OpenProjectConfigurations`).           |
| `pageName`  | path     | `String`                       | ✅        | —       | The name of the connection page.                                                                                                  |
| *(body)*    | body     | `OpenProjectConnection` (JSON) | ✅        | —       | The connection configuration (see below).                                                                                         |

The connection page must be in the `OpenProject.Code.OpenProjectConfigurations` space of the wiki. The request body:

```json
{
  "connectionName": "My OpenProject",
  "serverURL": "https://openproject.example.com",
  "clientId": "XYZ",
  "clientSecret": "secret",
  "showPublicData": true
}
```

| Field            | Type      | Required | Description                                                                                                                         |
|------------------|-----------|----------|-------------------------------------------------------------------------------------------------------------------------------------|
| `connectionName` | `String`  | ✅        | The name of the connection. It must not be used by another connection of the wiki.                                                  |
| `serverURL`      | `String`  | ✅        | The URL of the OpenProject server. It must be an absolute HTTP or HTTPS URL.                                                        |
| `clientId`       | `String`  | ✅        | The client ID of the OAuth application created in OpenProject.                                                                      |
| `clientSecret`   | `String`  | ❌        | The client secret of the OAuth application. If empty, the secret already stored on the page is kept (the request fails if the page doesn't store one). |
| `showPublicData` | `Boolean` | ❌        | Whether the users who can't call OpenProject with their own account (guests, or users who haven't connected their OpenProject account) see the data that OpenProject shows to anonymous users. Defaults to `false`. |

The endpoint only validates the server URL and the client secret; the administration UI also requires the connection name and the client ID. The `instanceId` field, if sent, is ignored.

**Required rights:** admin right on the wiki of the connection page, and edit right on the connection page.

**Responses:**

| Status                        | Body          | When                                                                                                                                                                                                                                               |
|-------------------------------|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **201 Created**               | —             | `POST`: the connection was saved.                                                                                                                                                                                                                  |
| **200 OK**                    | —             | `PUT`: the connection was saved.                                                                                                                                                                                                                   |
| **400 Bad Request**           | Error message | The page isn't in the `OpenProject.Code.OpenProjectConfigurations` space, the server URL isn't valid, the client secret is missing, or the page path is malformed.                                                                                 |
| **403 Forbidden**             | Error message | The current user doesn't have admin right on the wiki or edit right on the page.                                                                                                                                                                   |
| **409 Conflict**              | Error message | Another connection of the wiki already uses this name, or the page exists and was last modified by a user without admin right on the wiki (the connection has to be deleted and created again).                                                  |
| **500 Internal Server Error** | Error message | Failed to save the connection.                                                                                                                                                                                                                     |

---

## Quick Reference

| #  | Method | Path                                    | Description                                          |
|----|--------|-----------------------------------------|------------------------------------------------------|
| 1  | `GET`  | `/openproject/documents/{id}`           | Get a page by its short ID                           |
| 2  | `PUT`  | `/openproject/documents`                | Create/update a page and get a short ID              |
| 3  | `PUT`  | `/openproject/documents/{id}/links`     | Link a page to an OpenProject project/work package   |
| 4  | `GET`  | `/openproject/links/projects/{id}`      | Find pages linked to an OpenProject project          |
| 5  | `GET`  | `/openproject/links/workPackages/{id}`  | Find pages linked to an OpenProject work package     |
| 6  | `POST` | `/openproject/spaces`                   | Create a full OpenProject space from template        |
| 7  | `GET`  | `/openproject/metadata`                 | Retrieve the XWiki instance id                       |
| 8  | `GET`  | `/openproject/mentions`                 | Find pages that contain a mention to a work package  |
| 9  | `GET`  | `/openproject/documents`                | Retrieve the information and unique id for a page    |
| 10 | `GET`  | `/openproject/documents/{id}/ancestors` | Get a page and its ancestors by the page's short ID  |
| 11 | `GET`  | `/openproject/documents/{id}/links`     | Get the OpenProject link of a page                   |
| 12 | `GET`  | `/openproject/documents/{id}/mentions`  | Get the work package mentions of a page by short ID  |
| 13 | `GET`  | `/openproject/pages/{docRef}/mentions`  | Get the work package mentions of a page by reference |

Internal endpoints:

| Method        | Path                                                                            | Description                               |
|---------------|---------------------------------------------------------------------------------|-------------------------------------------|
| `POST`, `PUT` | `/wikis/{wikiName}/spaces/{spaceName}/pages/{pageName}/openproject/configurations` | Create or update an OpenProject connection |

---
