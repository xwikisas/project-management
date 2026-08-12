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
package com.xwiki.projectmanagement.presets.internal;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.presets.Preset;
import com.xwiki.projectmanagement.presets.PresetsManager;

@Component
@Singleton
public class DefaultPresetsManager implements PresetsManager
{
    @Inject
    private QueryManager queryManager;

    @Inject
    private DocumentReferenceResolver<String> resolver;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Override
    public List<Preset> getPresets(int offset, int limit)
    {
        try {
            List<String> presets =
                queryManager.createQuery(String.format("from doc.object('%s')", Preset.CLASS_NAME), Query.XWQL)
                    .setOffset(offset).setLimit(limit).execute();
            if (presets.isEmpty()) {
                return Collections.emptyList();
            }
            return presets.stream().map(this::getPresetObject).filter(Objects::nonNull).collect(Collectors.toList());
        } catch (QueryException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Preset> getClientPresets(String client, int offset, int limit)
    {
        try {
            List<String> presets =
                queryManager
                    .createQuery(
                        String.format("from doc.object('%s') as obj where obj.client = :client", Preset.CLASS_NAME),
                        Query.XWQL)
                    .setLimit(limit)
                    .setOffset(offset)
                    .bindValue("client", client)
                    .execute();
            if (presets.isEmpty()) {
                return Collections.emptyList();
            }
            return presets.stream().map(this::getPresetObject).filter(Objects::nonNull).collect(Collectors.toList());
        } catch (QueryException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Preset getPreset(String name)
    {
        try {
            List<String> presets =
                queryManager
                    .createQuery(
                        String.format("from doc.object('%s') as obj where obj.name = :presetname", Preset.CLASS_NAME),
                        Query.XWQL)
                    .setLimit(1)
                    .bindValue("presetname", name)
                    .execute();
            if (presets.isEmpty()) {
                return null;
            }
            return getPresetObject(presets.get(0));
        } catch (QueryException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getNextId()
    {
        try {
            List<Integer> result =
                queryManager.createQuery(String.format(
                    "select max(obj.id) from Document as doc doc.object('%s') as obj where obj.id is not null",
                    Preset.CLASS_NAME), Query.XWQL).execute();

            int number = 1;
            if (!result.isEmpty() && result.get(0) != null) {
                number = result.get(0);
            }
            return number;
        } catch (QueryException e) {
            throw new RuntimeException(e);
        }
    }

    private Preset getPresetObject(String serializedDocumentReference)
    {
        XWikiContext context = contextProvider.get();
        XWiki xWiki = context.getWiki();
        DocumentReference ref = resolver.resolve(serializedDocumentReference);
        try {
            XWikiDocument document = xWiki.getDocument(ref, context);
            BaseObject baseObject = document.getXObject(Preset.REFERENCE);
            if (baseObject == null) {
                return null;
            }
            return new Preset(baseObject);
        } catch (XWikiException e) {
            throw new RuntimeException(e);
        }
    }
}
