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

import java.io.IOException;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;

/**
 * Body handlers that read at most a given number of bytes of a response body and that fail when the whole response,
 * body included, isn't received before a deadline. {@link java.net.http.HttpRequest#timeout(Duration)} only bounds the
 * time until the response headers are received, so without them a huge or slow body can exhaust the memory or block
 * the calling thread.
 *
 * @version $Id$
 * @since 1.4.0
 */
final class LimitedBodyHandlers
{
    private static final String CHARSET_PARAMETER = "charset=";

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private LimitedBodyHandlers()
    {
    }

    /**
     * @param maxBytes the maximum number of bytes of the body
     * @param timeout the maximum duration of the request, counted from the creation of the handler (i.e. just before
     *     sending the request), so that it covers both the wait for the response headers and the read of the body
     * @return a handler reading the body as a string, decoded with the charset of the {@code Content-Type} header
     *     (UTF-8 by default), and failing with an {@link IOException} when the body is larger than {@code maxBytes} or
     *     with an {@link HttpTimeoutException} when the deadline is exceeded
     */
    static HttpResponse.BodyHandler<String> ofString(int maxBytes, Duration timeout)
    {
        long deadline = System.nanoTime() + timeout.toNanos();
        return responseInfo -> {
            Charset charset = getCharset(responseInfo.headers());
            return HttpResponse.BodySubscribers.mapping(
                new LimitedBodySubscriber(maxBytes, deadline, getContentLength(responseInfo.headers())),
                bytes -> new String(bytes, charset));
        };
    }

    /**
     * @param maxBytes the maximum number of bytes of the body
     * @param timeout the maximum duration of the request, see {@link #ofString(int, Duration)}
     * @return a handler reading the body as a byte array, failing with an {@link IOException} when the body is larger
     *     than {@code maxBytes} or with an {@link HttpTimeoutException} when the deadline is exceeded
     */
    static HttpResponse.BodyHandler<byte[]> ofByteArray(int maxBytes, Duration timeout)
    {
        long deadline = System.nanoTime() + timeout.toNanos();
        return responseInfo ->
            new LimitedBodySubscriber(maxBytes, deadline, getContentLength(responseInfo.headers()));
    }

    private static OptionalLong getContentLength(HttpHeaders headers)
    {
        try {
            return headers.firstValueAsLong("Content-Length");
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    static Charset getCharset(HttpHeaders headers)
    {
        String contentType = headers.firstValue("Content-Type").orElse("");
        for (String parameter : StringUtils.split(contentType, ';')) {
            String trimmed = parameter.trim();
            if (StringUtils.startsWithIgnoreCase(trimmed, CHARSET_PARAMETER)) {
                String name = StringUtils.strip(trimmed.substring(CHARSET_PARAMETER.length()).trim(), "\"");
                try {
                    return Charset.forName(name);
                } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * Thrown when the body of a response is larger than the maximum allowed size.
     *
     * @version $Id$
     */
    static final class ResponseTooLargeException extends IOException
    {
        private static final long serialVersionUID = 1L;

        ResponseTooLargeException(long maxBytes)
        {
            super(String.format("The response body is larger than the maximum allowed size of [%s] bytes", maxBytes));
        }
    }

    /**
     * Accumulates the body until it exceeds the maximum size or the deadline, in which case the subscription is
     * cancelled, which closes the connection.
     */
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]>
    {
        private final int maxBytes;

        private final CompletableFuture<byte[]> result = new CompletableFuture<>();

        private final CompletionStage<byte[]> body;

        private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();

        private byte[] buffer;

        private int received;

        LimitedBodySubscriber(int maxBytes, long deadline, OptionalLong contentLength)
        {
            this.maxBytes = maxBytes;
            if (contentLength.isPresent() && contentLength.getAsLong() > maxBytes) {
                // Fail without reading the body. The subscription is cancelled as soon as it's received.
                this.buffer = new byte[0];
                this.result.completeExceptionally(new ResponseTooLargeException(maxBytes));
            } else {
                // The size of the body is known in advance most of the time, so that it's read without copies.
                this.buffer = new byte[(int) contentLength.orElse(Math.min(DEFAULT_BUFFER_SIZE, maxBytes))];
            }
            long remaining = deadline - System.nanoTime();
            long timeoutMillis = TimeUnit.NANOSECONDS.toMillis(remaining);
            // The scheduled timeout is cancelled as soon as the result is completed.
            this.result.orTimeout(Math.max(timeoutMillis, 0), TimeUnit.MILLISECONDS);
            this.result.whenComplete((bytes, error) -> {
                if (error != null) {
                    cancelSubscription();
                }
            });
            this.body = this.result.handle((bytes, error) -> {
                if (error instanceof TimeoutException) {
                    throw new CompletionException(new HttpTimeoutException(
                        "The response of the OpenProject API was not fully received in time"));
                } else if (error != null) {
                    throw (error instanceof CompletionException) ? (CompletionException) error
                        : new CompletionException(error);
                }
                return bytes;
            });
        }

        @Override
        public CompletionStage<byte[]> getBody()
        {
            return this.body;
        }

        @Override
        public void onSubscribe(Flow.Subscription newSubscription)
        {
            if (!this.subscription.compareAndSet(null, newSubscription)) {
                newSubscription.cancel();
            } else if (this.result.isDone()) {
                // The deadline was already exceeded or the body is too large.
                cancelSubscription();
            } else {
                newSubscription.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(List<ByteBuffer> buffers)
        {
            if (this.result.isDone()) {
                return;
            }
            for (ByteBuffer item : buffers) {
                int length = item.remaining();
                if (length > this.maxBytes - this.received) {
                    this.buffer = new byte[0];
                    this.result.completeExceptionally(new ResponseTooLargeException(this.maxBytes));
                    return;
                }
                if (this.received + length > this.buffer.length) {
                    long newSize = Math.max((long) this.buffer.length * 2, (long) this.received + length);
                    this.buffer = Arrays.copyOf(this.buffer, (int) Math.min(newSize, this.maxBytes));
                }
                item.get(this.buffer, this.received, length);
                this.received += length;
            }
        }

        @Override
        public void onError(Throwable throwable)
        {
            this.result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete()
        {
            if (this.result.isDone()) {
                return;
            }
            byte[] bytes =
                (this.received == this.buffer.length) ? this.buffer : Arrays.copyOf(this.buffer, this.received);
            this.buffer = null;
            this.result.complete(bytes);
        }

        private void cancelSubscription()
        {
            Flow.Subscription current = this.subscription.get();
            if (current != null) {
                current.cancel();
            }
        }
    }
}
