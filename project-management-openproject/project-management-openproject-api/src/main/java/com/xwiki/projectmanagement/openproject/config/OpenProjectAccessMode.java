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
package com.xwiki.projectmanagement.openproject.config;

/**
 * Who can see the data of an OpenProject connection, and how.
 *
 * @version $Id$
 * @since 1.4.0
 */
public enum OpenProjectAccessMode
{
    /**
     * The users see the OpenProject data only after connecting their OpenProject account. Guests and the users who
     * haven't connected their account see a message asking them to connect it.
     */
    USER_ACCOUNTS(false, true),

    /**
     * The users who haven't connected their OpenProject account, and guests, see the data that OpenProject shows to
     * anonymous users. The users can connect their OpenProject account to see the data that it can access.
     */
    PUBLIC_DATA_AND_USER_ACCOUNTS(true, true),

    /**
     * Everybody sees the data that OpenProject shows to anonymous users, and nobody connects an OpenProject account:
     * no OAuth application is needed.
     */
    PUBLIC_DATA(true, false);

    private final boolean publicData;

    private final boolean userAccounts;

    OpenProjectAccessMode(boolean publicData, boolean userAccounts)
    {
        this.publicData = publicData;
        this.userAccounts = userAccounts;
    }

    /**
     * @return {@code true} if the users who can't call OpenProject with their own account see its public data
     */
    public boolean showsPublicData()
    {
        return this.publicData;
    }

    /**
     * @return {@code true} if the users can connect their OpenProject account, which requires an OAuth application
     *     registered in OpenProject (client id and client secret)
     */
    public boolean allowsUserAccounts()
    {
        return this.userAccounts;
    }

    /**
     * @param value the name of an access mode, as stored in the connection configuration
     * @return the access mode with this name, or {@link #USER_ACCOUNTS} if the value is empty or unknown
     */
    public static OpenProjectAccessMode fromString(String value)
    {
        for (OpenProjectAccessMode mode : values()) {
            if (mode.name().equals(value)) {
                return mode;
            }
        }
        return USER_ACCOUNTS;
    }
}
