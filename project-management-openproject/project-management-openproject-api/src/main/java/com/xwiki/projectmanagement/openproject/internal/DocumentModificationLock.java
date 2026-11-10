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
package com.xwiki.projectmanagement.openproject.internal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Serializes the modifications that the extension makes to a document (the macros inserted in a page, the mention
 * objects updated after each save of a page), so that one doesn't overwrite another with an older version of the
 * document. Each modification has to {@link #lock(DocumentReference) lock} the document, then
 * {@link #loadLatestDocument(DocumentReference) load} it, modify and save it, and finally
 * {@link #unlock(DocumentReference) unlock} it.
 *
 * @version $Id$
 * @since 1.3.1
 */
@Component(roles = DocumentModificationLock.class)
@Singleton
public class DocumentModificationLock
{
    /**
     * A lock which counts the threads using it, so that it is removed from the map only when no thread holds or waits
     * for it.
     */
    private static final class CountedLock
    {
        private final ReentrantLock lock = new ReentrantLock();

        private int users;
    }

    private final ConcurrentMap<DocumentReference, CountedLock> locks = new ConcurrentHashMap<>();

    @Inject
    private Provider<XWikiContext> contextProvider;

    /**
     * Waits until no other thread modifies the document, and prevents the other threads from modifying it until
     * {@link #unlock(DocumentReference)} is called. The locale of the reference is ignored: the translations of a
     * document share its lock, since the callers don't always pass a reference with the same locale (e.g. the
     * document events give a reference with the {@link java.util.Locale#ROOT root} locale).
     *
     * @param documentReference the document to modify
     */
    public void lock(DocumentReference documentReference)
    {
        // The user count is updated atomically with the map entry, so a lock can't be removed between the moment a
        // thread gets it and the moment it locks it.
        CountedLock countedLock = this.locks.compute(documentReference.withoutLocale(), (reference, existing) -> {
            CountedLock result = existing != null ? existing : new CountedLock();
            result.users++;
            return result;
        });
        countedLock.lock.lock();
    }

    /**
     * Lets the other threads modify the document. Must be called by the thread which called
     * {@link #lock(DocumentReference)}, once for each call.
     *
     * @param documentReference the modified document
     */
    public void unlock(DocumentReference documentReference)
    {
        this.locks.computeIfPresent(documentReference.withoutLocale(), (reference, existing) -> {
            existing.lock.unlock();
            existing.users--;
            return existing.users > 0 ? existing : null;
        });
    }

    /**
     * Loads the document from the database rather than from the document cache: right after a save, the cache can
     * still hold the previous version, put back by a thread that loaded it before the save was committed. The
     * returned document is not shared, so it can be modified.
     *
     * @param documentReference the document to load
     * @return the latest saved version of the document, or a new document if it doesn't exist
     * @throws XWikiException if the document fails to be loaded
     */
    public XWikiDocument loadLatestDocument(DocumentReference documentReference) throws XWikiException
    {
        XWikiContext context = this.contextProvider.get();
        String currentWiki = context.getWikiId();
        try {
            context.setWikiId(documentReference.getWikiReference().getName());
            return context.getWiki().getNotCacheStore().loadXWikiDoc(new XWikiDocument(documentReference), context);
        } finally {
            context.setWikiId(currentWiki);
        }
    }
}
