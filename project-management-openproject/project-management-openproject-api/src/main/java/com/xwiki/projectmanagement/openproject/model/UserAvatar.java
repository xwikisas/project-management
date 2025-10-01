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

package com.xwiki.projectmanagement.openproject.model;

import javax.ws.rs.core.StreamingOutput;

/**
 * Models the response of a UserAvatar coming from Open Project.
 *
 * @version $Id$
 * @since 1.0-rc-5
 */
public class UserAvatar
{
    private StreamingOutput streamingOutput;

    private String contentType;

    /**
     * @param streamingOutput the streaming output where he file can be read.
     * @param contentType the type of file that is being read through the stream.
     */
    public UserAvatar(StreamingOutput streamingOutput, String contentType)
    {
        this.streamingOutput = streamingOutput;
        this.contentType = contentType;
    }

    /**
     * @return the content type of the stream.
     */
    public String getContentType()
    {
        return contentType;
    }

    /**
     * @param contentType see {@link #getContentType()}.
     */
    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    /**
     * @return the stream where the avatar can be read.
     */
    public StreamingOutput getStreamingOutput()
    {
        return streamingOutput;
    }

    /**
     * @param streamingOutput see {@link #getStreamingOutput()}.
     */
    public void setStreamingOutput(StreamingOutput streamingOutput)
    {
        this.streamingOutput = streamingOutput;
    }
}
