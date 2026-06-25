# XWiki Endpoints Documentation

## Overview

**Base URL:** `http(s)://<your-xwiki-host>/xwiki/rest`

**Authentication:** All requests are executed with the permissions of the authenticated user. You can authenticate
using **Basic Auth** or an **OIDC Bearer Token**
(`Authorization: Bearer <token>`). The endpoints only return data and perform actions that the logged-in user has
rights to access. It works also for guests.

Note: Bearer Token authentication is only available if you configured the Token authenticator in your
wiki: https://extensions.xwiki.org/xwiki/bin/view/Extension/OpenID%20Connect/Token%20Authenticator/

**Content type:** All endpoints accept and return **JSON** (`application/json`) or **XML** (`application/xml`).

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
`objectSummaries`. Each entry in `objectSummaries` represents one XWiki object attached to the page:

```json
{
  "objects": {
    "links": [],
    "objectSummaries": [
      {
        "links": [
          {
            "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects/OpenProject.Code.WorkPackageLink/0",
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
        "className": "OpenProject.Code.WorkPackageLink",
        "number": 0,
        "headline": "",
        "properties": [
          {
            "links": [
              {
                "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects/OpenProject.Code.WorkPackageLink/0/properties/project",
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
                "value": "OpenProject Project ID"
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
                "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects/OpenProject.Code.WorkPackageLink/0/properties/workPackage",
                "rel": "self",
                "type": null,
                "hrefLang": null
              }
            ],
            "attributes": [
              {
                "links": [],
                "name": "name",
                "value": "workPackage"
              },
              {
                "links": [],
                "name": "prettyName",
                "value": "OpenProject Work Package ID"
              },
              {
                "links": [],
                "name": "size",
                "value": "20"
              }
            ],
            "value": "125",
            "name": "workPackage",
            "type": "String"
          },
          {
            "links": [
              {
                "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects/OpenProject.Code.WorkPackageLink/0/properties/instance",
                "rel": "self",
                "type": null,
                "hrefLang": null
              }
            ],
            "attributes": [
              {
                "links": [],
                "name": "name",
                "value": "instance"
              },
              {
                "links": [],
                "name": "prettyName",
                "value": "Open Project Instance Name"
              },
              {
                "links": [],
                "name": "size",
                "value": "40"
              }
            ],
            "value": "",
            "name": "instance",
            "type": "String"
          },
          {
            "links": [
              {
                "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects/OpenProject.Code.WorkPackageLink/0/properties/primary",
                "rel": "self",
                "type": null,
                "hrefLang": null
              }
            ],
            "attributes": [
              {
                "links": [],
                "name": "name",
                "value": "primary"
              },
              {
                "links": [],
                "name": "prettyName",
                "value": "Is Primary Link?"
              },
              {
                "links": [],
                "name": "displayFormType",
                "value": "select"
              }
            ],
            "value": "",
            "name": "primary",
            "type": "Boolean"
          }
        ]
      }
    ]
  }
}
```

### `Page` (Request Body — for PUT)

When creating or updating a page, you send a `Page` object in the request body. Only a subset of fields is needed:

```json
{
  "title": "My New Page",
  "content": "This is the page content."
}
```

### `WorkPackageLink` (Request Body — for POST link)

```json
{
  "project": "42",
  "workPackage": "123",
  "instance": "my-openproject"
}
```

| Field         | Type     | Required | Description                                 |
|---------------|----------|----------|---------------------------------------------|
| `project`     | `String` | ❌        | The OpenProject project ID to link to.      |
| `workPackage` | `String` | ❌        | The OpenProject work package ID to link to. |
| `instance`    | `String` | ❌        | The id of the OpenProject instance.         |

### `SearchResults` (Response)

Returned by the link search endpoints (endpoints 4 and 5). The response is a standard XWiki `SearchResults` object.
Each search result entry represents a page that matches the query, and includes an `object` field with the full
`WorkPackageLink` object data.

```json
{
  "links": [],
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
      "className": "OpenProject.Code.WorkPackageLink",
      "objectNumber": 0,
      "score": 0.0,
      "object": {
        "links": [
          {
            "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects/OpenProject.Code.WorkPackageLink/0",
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
        "className": "OpenProject.Code.WorkPackageLink",
        "number": 0,
        "headline": "",
        "properties": [
          {
            "links": [
              {
                "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects/OpenProject.Code.WorkPackageLink/0/properties/project",
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
                "value": "OpenProject Project ID"
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
                "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects/OpenProject.Code.WorkPackageLink/0/properties/workPackage",
                "rel": "self",
                "type": null,
                "hrefLang": null
              }
            ],
            "attributes": [
              {
                "links": [],
                "name": "name",
                "value": "workPackage"
              },
              {
                "links": [],
                "name": "prettyName",
                "value": "OpenProject Work Package ID"
              },
              {
                "links": [],
                "name": "size",
                "value": "20"
              }
            ],
            "value": "125",
            "name": "workPackage",
            "type": "String"
          },
          {
            "links": [
              {
                "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects/OpenProject.Code.WorkPackageLink/0/properties/instance",
                "rel": "self",
                "type": null,
                "hrefLang": null
              }
            ],
            "attributes": [
              {
                "links": [],
                "name": "name",
                "value": "instance"
              },
              {
                "links": [],
                "name": "prettyName",
                "value": "Open Project Instance Name"
              },
              {
                "links": [],
                "name": "size",
                "value": "40"
              }
            ],
            "value": "",
            "name": "instance",
            "type": "String"
          },
          {
            "links": [
              {
                "href": "http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Projects/spaces/MyProject/pages/WebHome/objects/OpenProject.Code.WorkPackageLink/0/properties/primary",
                "rel": "self",
                "type": null,
                "hrefLang": null
              }
            ],
            "attributes": [
              {
                "links": [],
                "name": "name",
                "value": "primary"
              },
              {
                "links": [],
                "name": "prettyName",
                "value": "Is Primary Link?"
              },
              {
                "links": [],
                "name": "displayFormType",
                "value": "select"
              }
            ],
            "value": "",
            "name": "primary",
            "type": "Boolean"
          }
        ]
      },
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
  ]
}
```

Each search result entry contains:

| Field          | Type      | Description                                                                                |
|----------------|-----------|--------------------------------------------------------------------------------------------|
| `type`         | `String`  | Always `"page"`.                                                                           |
| `id`           | `String`  | The full qualified identifier of the page.                                                 |
| `pageFullName` | `String`  | The full name of the page (e.g. `Projects.MyProject.WebHome`).                             |
| `title`        | `String`  | The title of the page.                                                                     |
| `wiki`         | `String`  | The wiki the page belongs to.                                                              |
| `space`        | `String`  | The space the page is in.                                                                  |
| `pageName`     | `String`  | The technical name of the page.                                                            |
| `modified`     | `Long`    | Last modification timestamp (Unix milliseconds).                                           |
| `author`       | `String`  | The reference of the last author.                                                          |
| `version`      | `String`  | The page version.                                                                          |
| `className`    | `String`  | The class name of the matched object (`OpenProject.Code.WorkPackageLink`).                 |
| `objectNumber` | `Integer` | The index of the matched object.                                                           |
| `object`       | `Object`  | The full object data with all properties (same structure as in the Objects section above). |
| `hierarchy`    | `Object`  | The breadcrumb hierarchy of the page.                                                      |

---

## Endpoints

---

### 1. Get Document by ID

Retrieves a wiki page using its short unique identifier.

```
GET /xwiki/rest/openproject/documents/{id}
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

| Status                        | Body                 | When                                          |
|-------------------------------|----------------------|-----------------------------------------------|
| **200 OK**                    | `Page` (JSON or XML) | The page was found and returned successfully. |
| **400 Bad Request**           | `"Missing page id."` | The `id` parameter was empty or not provided. |
| **404 Not Found**             | —                    | No page exists with the given identifier.     |
| **500 Internal Server Error** | —                    | An unexpected error occurred.                 |

---

### 2. Create or Update Document

Creates a new wiki page or updates an existing one, and assigns it a short unique identifier. This identifier can
then be used in all other endpoints to reference the page.

```
PUT /openproject/documents
```

**Parameters:**

| Parameter       | Location | Type          | Required | Default | Description                                                                                              |
|-----------------|----------|---------------|----------|---------|----------------------------------------------------------------------------------------------------------|
| `docRef`        | query    | `String`      | ✅        | —       | The full page reference where the page should be created or updated (e.g. `Projects.MyProject.WebHome`). |
| `minorRevision` | query    | `Boolean`     | ❌        | —       | If `true`, the edit is saved as a minor version (does not appear prominently in page history).           |
| `create`        | query    | `Boolean`     | ❌        | false   | If `true`, the request will fail if the document already exists.                                         |
| *(body)*        | body     | `Page` (JSON) | ✅        | —       | The page data (title, content, etc.). See the `Page` request model above.                                |

**What happens:**

1. If the page at `docRef` does not exist, it is **created** with the provided content.
2. If it already exists, it is **updated** with the provided content.
3. A short unique identifier is generated and permanently associated with this page.
4. The response contains the full page data, with the `id` field set to the new short identifier.

**Responses:**

| Status                        | Body                    | When                                                           |
|-------------------------------|-------------------------|----------------------------------------------------------------|
| **201 Created**               | `Page` (JSON) with `id` | A new page was created.                                        |
| **202 Accepted**              | `Page` (JSON) with `id` | An existing page was updated.                                  |
| **400 Bad Request**           | Error message           | The `docRef` query parameter was missing.                      |
| **401 Unauthorized**          | —                       | The authenticated user does not have permission to edit.       |
| **409 Unauthorized**          | —                       | If the `create` param is used and the document already exists. |
| **500 Internal Server Error** | Error message           | Failed to generate the short identifier or save the page.      |

---

### 3. Create Link between Page and OpenProject Entity

Attaches a project and/or work package to a wiki page. Multiple links can be made to a single page.

```
POST /openproject/documents/{id}/links
```

**Parameters:**

| Parameter       | Location | Type                     | Required | Default | Description                                                               |
|-----------------|----------|--------------------------|----------|---------|---------------------------------------------------------------------------|
| `id`            | path     | `String`                 | ✅        | —       | The short unique identifier of the page (obtained from endpoints 1 or 2). |
| `minorRevision` | query    | `Boolean`                | ❌        | —       | If `true`, adding the link is saved as a minor version change.            |
| *(body)*        | body     | `WorkPackageLink` (JSON) | ✅        | —       | The link data. See the `WorkPackageLink` model above.                     |

**What happens:**

1. Looks up the page associated with the given `id`.
2. A link object is created on the page with the provided project ID, work package ID, instance name, and primary flag.
3. The link can later be searched for using the link search endpoints (endpoints 4 and 5).

**Responses:**

| Status                        | Body                    | When                                                                                                     |
|-------------------------------|-------------------------|----------------------------------------------------------------------------------------------------------|
| **201 Created**               | —                       | The link was created. The `Location` header contains the URL of the new link object.                     |
| **400 Bad Request**           | `"Missing link entity"` | The request body was empty/null OR if the instance id does not correspond to any configured OP instances |
| **401 Unauthorized**          | Error message           | The bearer token is missing, invalid, or doesn't match any configured OpenProject instance.              |
| **404 Not Found**             | —                       | No page exists with the given `id`.                                                                      |
| **500 Internal Server Error** | Error message           | An unexpected error occurred.                                                                            |

---

### 4. Search Pages Linked to an OpenProject Project

Finds all wiki pages that have been linked to a specific OpenProject project.

```
GET /openproject/links/projects/{id}
```

**Parameters:**

| Parameter     | Location | Type      | Required | Default   | Description                                                           |
|---------------|----------|-----------|----------|-----------|-----------------------------------------------------------------------|
| `id`          | path     | `String`  | ✅        | —         | The OpenProject **project ID** (must be a number).                    |
| `instance`    | query    | `String`  | ❌        | —         | Matches the links based on the instance that was used to create them. |
| `number`      | query    | `Integer` | ❌        | `0` (all) | Maximum number of results to return. Use `0` for no limit.            |
| `start`       | query    | `Integer` | ❌        | `0`       | Offset for pagination (0-based).                                      |
| `orderField`  | query    | `String`  | ❌        | `""`      | Name of the field to sort results by.                                 |
| `order`       | query    | `String`  | ❌        | `asc`     | Sort direction: `asc` (ascending) or `desc` (descending).             |
| `prettyNames` | query    | `Boolean` | ❌        | `false`   | If `true`, user references are displayed as readable names.           |

**What happens:**

1. Searches all wiki pages that have a link object with a `project` value matching the given `id`.
2. If `instance` is not empty, results are further filtered to only include links belonging to the caller's
   OpenProject instance.
3. Only pages the current user has permission to view are returned.

**Responses:**

| Status                        | Body                                 | When                                                          |
|-------------------------------|--------------------------------------|---------------------------------------------------------------|
| **200 OK**                    | `SearchResults` (JSON or XML)        | Search completed. May contain zero or more results.           |
| **400 Bad Request**           | `"Project id should be an integer."` | The `id` was not a valid number.                              |
| **500 Internal Server Error** | Error object                         | An unexpected error occurred (e.g. token resolution failure). |

---

### 5. Search Pages Linked to an OpenProject Work Package

Finds all wiki pages that have been linked to a specific OpenProject work package. Works exactly like endpoint 4,
but matches on the **work package ID** instead of the project ID.

```
GET /openproject/links/workPackages/{id}
```

**Parameters:**

| Parameter     | Location | Type      | Required | Default | Description                                                           |
|---------------|----------|-----------|----------|---------|-----------------------------------------------------------------------|
| `id`          | path     | `String`  | ✅        | —       | The OpenProject **work package ID** (must be a number).               |
| `instance`    | query    | `String`  | ❌        | —       | Matches the links based on the instance that was used to create them. |
| `number`      | query    | `Integer` | ❌        | `0`     | Max results.                                                          |
| `start`       | query    | `Integer` | ❌        | `0`     | Pagination offset.                                                    |
| `orderField`  | query    | `String`  | ❌        | `""`    | Sort field.                                                           |
| `order`       | query    | `String`  | ❌        | `asc`   | Sort direction.                                                       |
| `prettyNames` | query    | `Boolean` | ❌        | `false` | Human-readable names.                                                 |

**What happens:**

1. Searches all wiki pages that have a link object with a `workPackage` value matching the given `id`.
2. If `instance` is not empty, only links belonging to the caller's OpenProject instance are returned.
3. Only pages the current user can view are returned.

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

| Parameter     | Location | Type      | Required | Default              | Description                                                                                                             |
|---------------|----------|-----------|----------|----------------------|-------------------------------------------------------------------------------------------------------------------------|
| `docRef`      | query    | `String`  | ✅        | —                    | The full page reference where the space should be created (e.g. `Projects.MyProject.WebHome`).                          |
| `withId`      | query    | `Boolean` | ❌        | `false`              | If `true`, each created page will also get a short unique identifier assigned (so they can be used with endpoints 1–3). |
| `instance`    | query    | `String`  | ❌        | —                    | The OpenProject instance id. If set, the created space will contain a link object that will point to the OP instance.   |
| `project`     | query    | `Int`     | ❌        | —                    | The id of a OP project. If set, the space will contain a link to the project.                                           |
| `workPackage` | query    | `Int`     | ❌        | —                    | The if of a WorkPackage. If set, the space will contain a link to the work package.                                     |
| `title`       | query    | `String`  | ❌        | `Open Project Space` | The title of the newly created space.                                                                                   |

**Responses:**

| Status                        | Body                                                                        | When                                                                                                                  |
|-------------------------------|-----------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| **200 OK**                    | `List<Page>` (JSON array)                                                   | All pages were created successfully. If `withId=true`, each page's `id` field contains its assigned short identifier. |
| **400 Bad Request**           | Error message                                                               | The `docRef` query parameter was missing.                                                                             |
| **401 Unauthorized**          | —                                                                           | The authenticated user does not have edit permissions.                                                                |
| **403 Forbidden**             | `"Document already exists."`                                                | A page already exists at the target location.                                                                         |
| **406 Not Acceptable**        | `"The requested wiki does not have the OpenProject application installed."` | The OpenProject template is not available in the target wiki.                                                         |
| **500 Internal Server Error** | Error details                                                               | Something went wrong during page creation.                                                                            |

---

### 7. Get XWiki Instance ID

Retrieve the xwiki instance id.

```
POST /openproject/metadata
```

**Parameters:**

No params

**Responses:**

| Status                        | Body                      | When                             |
|-------------------------------|---------------------------|----------------------------------|
| **200 OK**                    | `InstanceId` (JSON array) | Request was successful           |
| **500 Internal Server Error** | Error details             | The instance id was not present. |

---

### 8. Search the Pages that contain a mention to a given Work Package

Finds all wiki pages that contain in their content a mention to an OpenProject work package.

```
GET /openproject/mentions
```

**Parameters:**

| Parameter     | Location | Type      | Required | Default   | Description                                                           |
|---------------|----------|-----------|----------|-----------|-----------------------------------------------------------------------|
| `workPackage` | query    | `Integer` | ❌        | —         | A work package id.                                                    |
| `instance`    | query    | `String`  | ❌        | —         | Matches the links based on the instance that was used to create them. |
| `number`      | query    | `Integer` | ❌        | `0` (all) | Maximum number of results to return. Use `0` for no limit.            |
| `start`       | query    | `Integer` | ❌        | `0`       | Offset for pagination (0-based).                                      |
| `orderField`  | query    | `String`  | ❌        | `""`      | Name of the field to sort results by.                                 |
| `order`       | query    | `String`  | ❌        | `asc`     | Sort direction: `asc` (ascending) or `desc` (descending).             |
| `prettyNames` | query    | `Boolean` | ❌        | `false`   | If `true`, user references are displayed as readable names.           |

**Responses:**

| Status                        | Body                                      | When                                                          |
|-------------------------------|-------------------------------------------|---------------------------------------------------------------|
| **200 OK**                    | `SearchResults` (JSON or XML)             | Search completed. May contain zero or more results.           |
| **400 Bad Request**           | `"Work package id should be an integer."` | The `id` was not a valid number.                              |
| **500 Internal Server Error** | Error object                              | An unexpected error occurred (e.g. token resolution failure). |

---

### 8. Retrieve the page info and unique id based of a document reference.

```
GET /openproject/documents
```

**Parameters:**

| Parameter     | Location | Type      | Required | Default | Description                                                                                                           |
|---------------|----------|-----------|----------|---------|-----------------------------------------------------------------------------------------------------------------------|
| `docRef`      | query    | `String`  | ✅        | —       | The document reference of a page.                                                                                     |
| `prettyNames` | query    | `Boolean` | ❌        | `false` | If `true`, user references are added to the response as human-readable display names instead of technical references. |
| `objects`     | query    | `Boolean` | ❌        | `false` | If `true`, the response includes all structured data objects attached to the page.                                    |
| `class`       | query    | `Boolean` | ❌        | `false` | If `true`, the response includes class definition metadata.                                                           |
| `attachments` | query    | `Boolean` | ❌        | `false` | If `true`, the response includes the list of files attached to the page.                                              |

**Responses:**

| Status                        | Body                                                                           | When                                                          |
|-------------------------------|--------------------------------------------------------------------------------|---------------------------------------------------------------|
| **200 OK**                    | `SearchResults` (JSON or XML)                                                  | Search completed. May contain zero or more results.           |
| **400 Bad Request**           | "Missing `docRef` query parameter pointing to the document with an unique id." | The document reference query param is empty                   |
| **404 Not Found**             |                                                                                | The document reference does not point to an existing page.    |
| **500 Internal Server Error** | Error object                                                                   | An unexpected error occurred (e.g. token resolution failure). |

---

## Quick Reference

| # | Method | Path                                   | Description                                          |
|---|--------|----------------------------------------|------------------------------------------------------|
| 1 | `GET`  | `/openproject/documents/{id}`          | Get a page by its short ID                           |
| 2 | `PUT`  | `/openproject/documents`               | Create/update a page and get a short ID              |
| 3 | `POST` | `/openproject/documents/{id}/links`    | Link a page to an OpenProject project/work package   |
| 4 | `GET`  | `/openproject/links/projects/{id}`     | Find pages linked to an OpenProject project          |
| 5 | `GET`  | `/openproject/links/workPackages/{id}` | Find pages linked to an OpenProject work package     |
| 6 | `POST` | `/openproject/spaces`                  | Create a full OpenProject space from template        |
| 7 | `GET`  | `/openproject/metadata`                | Retrieves the XWiki instance id.                     |
| 8 | `GET`  | `/openproject/mentions`                | Find pages that contain a mention to a work package. |
| 9 | `GET`  | `/openproject/documents`               | Retrieve the information and unique id for a page.   |

---

