## Project Management

Provides the tools necessary for integrating project management software into XWiki.

* Project Lead: [Teo Caras](https://github.com/trrenty)
* [Documentation](https://store.xwiki.com/xwiki/bin/view/Extension/ProjectManagement)
* Communication: [Forum and mailing list](http://dev.xwiki.org/xwiki/bin/view/Community/MailingLists), [chat](http://dev.xwiki.org/xwiki/bin/view/Community/IRC)
* [Development Practices](http://dev.xwiki.org)
* License: LGPL 2.1+
* Minimal XWiki version supported: XWiki 14.10
* Translations: N/A
* Sonar Dashboard: N/A
* Continuous Integration Status: [![Build Status](http://ci.xwikisas.com/view/All/job/xwikisas/job/project-management/job/master/badge/icon)](https://ci.xwikisas.com/view/All/job/xwikisas/job/project-management/job/main/)

# Release

```
mvn release:prepare -Pintegration-tests -DskipTests -Darguments="-N"
mvn release:perform -Pintegration-tests -DskipTests -Darguments="-DskipTests"
```
