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

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Provider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.store.XWikiStoreInterface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentModificationLock}.
 *
 * @version $Id$
 */
@ComponentTest
class DocumentModificationLockTest
{
    private static final DocumentReference DOCUMENT = new DocumentReference("subwiki", "Space", "Page");

    private static final DocumentReference OTHER_DOCUMENT = new DocumentReference("subwiki", "Space", "Other");

    // The time given to a thread to acquire a lock that is expected to be held by another thread.
    private static final long BLOCKED_TIMEOUT_MILLIS = 200;

    private static final long TIMEOUT_SECONDS = 10;

    @InjectMockComponents
    private DocumentModificationLock documentModificationLock;

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    @Mock
    private XWikiContext context;

    @Mock
    private XWiki xwiki;

    @Mock
    private XWikiStoreInterface store;

    private ExecutorService otherThread;

    @BeforeEach
    void setUp()
    {
        this.otherThread = Executors.newSingleThreadExecutor();
        when(this.contextProvider.get()).thenReturn(this.context);
        when(this.context.getWiki()).thenReturn(this.xwiki);
        when(this.xwiki.getNotCacheStore()).thenReturn(this.store);
    }

    @AfterEach
    void tearDown()
    {
        this.otherThread.shutdownNow();
    }

    @Test
    void lockWaitsForTheModificationOfTheSameDocument() throws Exception
    {
        this.documentModificationLock.lock(DOCUMENT);

        Future<?> otherModification = lockAndUnlockInOtherThread(DOCUMENT);
        assertThrows(TimeoutException.class,
            () -> otherModification.get(BLOCKED_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

        this.documentModificationLock.unlock(DOCUMENT);
        otherModification.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void lockDoesNotWaitForTheModificationOfAnotherDocument() throws Exception
    {
        this.documentModificationLock.lock(DOCUMENT);
        try {
            lockAndUnlockInOtherThread(OTHER_DOCUMENT).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            this.documentModificationLock.unlock(DOCUMENT);
        }
    }

    @Test
    void lockIgnoresTheLocale() throws Exception
    {
        this.documentModificationLock.lock(new DocumentReference(DOCUMENT, Locale.ROOT));

        Future<?> otherModification = lockAndUnlockInOtherThread(DOCUMENT);
        assertThrows(TimeoutException.class,
            () -> otherModification.get(BLOCKED_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

        this.documentModificationLock.unlock(new DocumentReference(DOCUMENT, Locale.FRENCH));
        otherModification.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void lockIsReentrant() throws Exception
    {
        this.documentModificationLock.lock(DOCUMENT);
        this.documentModificationLock.lock(DOCUMENT);
        this.documentModificationLock.unlock(DOCUMENT);

        // Still held once.
        Future<?> otherModification = lockAndUnlockInOtherThread(DOCUMENT);
        assertThrows(TimeoutException.class,
            () -> otherModification.get(BLOCKED_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

        this.documentModificationLock.unlock(DOCUMENT);
        otherModification.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // The lock can be acquired again once released by all the threads.
        this.documentModificationLock.lock(DOCUMENT);
        this.documentModificationLock.unlock(DOCUMENT);
    }

    @Test
    void loadLatestDocumentBypassesTheDocumentCache() throws Exception
    {
        XWikiDocument loadedDocument = new XWikiDocument(DOCUMENT);
        when(this.context.getWikiId()).thenReturn("currentwiki");
        doAnswer(invocation -> {
            // The document is loaded from its own wiki.
            verify(this.context).setWikiId("subwiki");
            assertEquals(DOCUMENT, invocation.<XWikiDocument>getArgument(0).getDocumentReference());
            return loadedDocument;
        }).when(this.store).loadXWikiDoc(any(XWikiDocument.class), same(this.context));

        assertSame(loadedDocument, this.documentModificationLock.loadLatestDocument(DOCUMENT));

        verify(this.context).setWikiId("currentwiki");
    }

    private Future<?> lockAndUnlockInOtherThread(DocumentReference documentReference)
    {
        return this.otherThread.submit(() -> {
            this.documentModificationLock.lock(documentReference);
            this.documentModificationLock.unlock(documentReference);
        });
    }
}
