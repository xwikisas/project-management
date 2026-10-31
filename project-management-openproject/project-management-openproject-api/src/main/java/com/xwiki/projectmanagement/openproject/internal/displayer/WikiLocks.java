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
package com.xwiki.projectmanagement.openproject.internal.displayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One lock per wiki, to run a task on a wiki one at a time.
 *
 * @version $Id$
 */
class WikiLocks
{
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Acquire the lock of a wiki, waiting for it to be released if needed.
     *
     * @param wikiId the identifier of the wiki
     */
    void lock(String wikiId)
    {
        getLock(wikiId).lock();
    }

    /**
     * Acquire the lock of a wiki only if it isn't held by another thread.
     *
     * @param wikiId the identifier of the wiki
     * @return whether the lock was acquired
     */
    boolean tryLock(String wikiId)
    {
        return getLock(wikiId).tryLock();
    }

    /**
     * Release the lock of a wiki, held by the current thread.
     *
     * @param wikiId the identifier of the wiki
     */
    void unlock(String wikiId)
    {
        getLock(wikiId).unlock();
    }

    private ReentrantLock getLock(String wikiId)
    {
        return this.locks.computeIfAbsent(wikiId, wiki -> new ReentrantLock());
    }
}
