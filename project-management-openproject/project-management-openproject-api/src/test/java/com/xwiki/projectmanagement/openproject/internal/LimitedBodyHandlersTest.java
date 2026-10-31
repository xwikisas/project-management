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
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sun.net.httpserver.HttpServer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link LimitedBodyHandlers}, against a local HTTP server.
 *
 * @version $Id$
 */
class LimitedBodyHandlersTest
{
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final CountDownLatch releaseSlowBody = new CountDownLatch(1);

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException
    {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/latin1", exchange -> {
            byte[] body = "café".getBytes(StandardCharsets.ISO_8859_1);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=\"ISO-8859-1\"");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        this.server.createContext("/bytes", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                for (int i = 0; i < 10; i++) {
                    output.write(new byte[100]);
                }
            } catch (IOException e) {
                // The client may close the connection once the limit is exceeded.
            }
        });
        this.server.createContext("/slow", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write('{');
                output.flush();
                this.releaseSlowBody.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                output.write('}');
            } catch (IOException e) {
                // The client closes the connection once the deadline is exceeded.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        this.server.start();
    }

    @AfterEach
    void tearDown()
    {
        this.releaseSlowBody.countDown();
        this.server.stop(0);
    }

    @Test
    void ofStringDecodesWithTheCharsetOfTheResponse() throws Exception
    {
        String body = this.httpClient.send(request("/latin1"), LimitedBodyHandlers.ofString(1000, TIMEOUT)).body();

        assertEquals("café", body);
    }

    @Test
    void ofByteArrayReadsABodyOfTheMaximumSize() throws Exception
    {
        byte[] body = this.httpClient.send(request("/bytes"), LimitedBodyHandlers.ofByteArray(1000, TIMEOUT)).body();

        assertArrayEquals(new byte[1000], body);
    }

    @Test
    void ofByteArrayFailsWhenTheBodyIsTooLarge()
    {
        IOException exception = assertThrows(IOException.class,
            () -> this.httpClient.send(request("/bytes"), LimitedBodyHandlers.ofByteArray(999, TIMEOUT)));

        assertThat(exception.getMessage(), containsString("larger than the maximum allowed size of [999] bytes"));
    }

    @Test
    void ofStringFailsWhenTheBodyIsTooSlow()
    {
        HttpTimeoutException exception = assertThrows(HttpTimeoutException.class,
            () -> this.httpClient.send(request("/slow"),
                LimitedBodyHandlers.ofString(1000, Duration.ofMillis(500))));

        assertEquals("The response of the OpenProject API was not fully received in time", exception.getMessage());
    }

    @Test
    void ofStringFailsWhenTheDeadlineIsExceededBeforeTheResponse()
    {
        assertThrows(HttpTimeoutException.class,
            () -> this.httpClient.send(request("/latin1"), LimitedBodyHandlers.ofString(1000, Duration.ZERO)));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "application/json, UTF-8",
        "application/json;charset=utf-16, UTF-16",
        "text/plain; CHARSET=iso-8859-1, ISO-8859-1",
        "application/json; charset=unknown-charset, UTF-8",
        "application/json; charset=@invalid, UTF-8",
        "'', UTF-8"
    })
    void getCharset(String contentType, String expectedCharset)
    {
        Map<String, List<String>> values = contentType.isEmpty() ? Map.of() : Map.of("Content-Type",
            List.of(contentType));

        assertEquals(expectedCharset,
            LimitedBodyHandlers.getCharset(HttpHeaders.of(values, (name, value) -> true)).name());
    }

    private HttpRequest request(String path)
    {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + this.server.getAddress().getPort() + path))
            .build();
    }
}
