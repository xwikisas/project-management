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
package com.xwiki.projectmanagement.relations.internal;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.relations.RelationsManager;
import com.xwiki.projectmanagement.relations.store.ProjectManagementRelation;

/**
 * Default implementation of {@link RelationsManager}.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Component
@Singleton
public class DefaultRelationsManager implements RelationsManager
{
    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private Logger logger;

    @Inject
    private QueryManager queryManager;

    @Inject
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> serializer;

    @Override
    public ProjectManagementRelation getClientRelation(
        DocumentReference documentReference, String client,
        Boolean ancestorLookup)
    {
        try {
            XWikiContext context = contextProvider.get();
            XWikiDocument document = context.getWiki().getDocument(documentReference, context);

            BaseObject relation =
                document.getXObject(ProjectManagementRelation.CLASS_REFERENCE, ProjectManagementRelation.FIELD_CLIENT,
                    client, false);
            if (relation == null && ancestorLookup) {

                XWikiDocument ancestorDoc = getAncestorWithRelation(document, context, " and obj.client = :client ",
                    query -> query.bindValue("client", client));
                if (ancestorDoc == null) {
                    return null;
                }
                relation = ancestorDoc.getXObject(ProjectManagementRelation.CLASS_REFERENCE);
            }

            if (relation == null) {
                return null;
            }
            return new ProjectManagementRelation(relation);
        } catch (XWikiException | QueryException e) {
            logger.error("Failed to retrieve the relation object for the document [{}].", documentReference, e);
            return null;
        }
    }

    @Override
    public List<ProjectManagementRelation> getRelations(DocumentReference documentReference, Boolean ancestorLookup)
    {
        try {
            XWikiContext context = contextProvider.get();
            XWikiDocument document = context.getWiki().getDocument(documentReference, context);

            List<BaseObject> relations =
                document.getXObjects(ProjectManagementRelation.CLASS_REFERENCE);
            if ((relations == null || relations.isEmpty()) && ancestorLookup) {
                XWikiDocument xWikiDocument = getAncestorWithRelation(document, context, "", null);
                if (xWikiDocument == null) {
                    return null;
                }
                relations = xWikiDocument.getXObjects(ProjectManagementRelation.CLASS_REFERENCE);
            }

            if (relations == null || relations.isEmpty()) {
                return Collections.emptyList();
            }
            return relations.stream().map(ProjectManagementRelation::new).collect(Collectors.toList());
        } catch (XWikiException | QueryException e) {
            logger.error("Failed to retrieve the relation objects for the document [{}].", documentReference, e);
            return null;
        }
    }

    private XWikiDocument getAncestorWithRelation(XWikiDocument document, XWikiContext context, String extraWhere,
        Consumer<Query> queryConsumer)
        throws QueryException, XWikiException
    {
        List<String> parents = document.getDocumentReference().getParent().getReversedReferenceChain().stream()
            .filter(e -> !(e instanceof WikiReference)).map(serializer::serialize).collect(Collectors.toList());
        // Get the closest ancestor that has a relation obj.
        Query query = queryManager.createQuery(
            String.format(
                "from doc.object('ProjectManagement.Code.RelationClass') as obj "
                    + "where doc.space in (:ancestorList) %s"
                    + "order by length(doc.space) desc", extraWhere),
            Query.XWQL).bindValue("ancestorList", parents).setLimit(1);
        if (queryConsumer != null) {
            queryConsumer.accept(query);
        }
        List<String> result = query.execute();

        if (result.isEmpty()) {
            return null;
        }

        DocumentReference parentDocRef = documentReferenceResolver.resolve(result.get(0));

        return context.getWiki().getDocument(parentDocRef, context);
    }
}
