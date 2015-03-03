/*
 * Copyright 2015 Async-IO.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.vertx;

import org.atmosphere.cpr.AsyncIOWriter;
import org.atmosphere.cpr.AtmosphereInterceptorWriter;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceImpl;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.util.ByteArrayAsyncWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class VertxAsyncIOWriter extends AtmosphereInterceptorWriter {
    private static final Logger logger = LoggerFactory.getLogger(VertxAsyncIOWriter.class);
    private final AtomicInteger pendingWrite = new AtomicInteger();
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final ByteArrayAsyncWriter buffer = new ByteArrayAsyncWriter();
    private long lastWrite = 0;
    private final HttpServerResponse out;
    private boolean headerWritten = false;

    public VertxAsyncIOWriter(final HttpServerRequest request) {
        out = request.response();
    }

    public boolean isClosed() {
        return isClosed.get();
    }

    @Override
    public AsyncIOWriter writeError(AtmosphereResponse r, int errorCode, String message) throws IOException {
        // TODO: Set status
        logger.error("Error {}:{}", errorCode, message);
        out.write(message);
        return this;
    }

    @Override
    public AsyncIOWriter write(AtmosphereResponse r, String data) throws IOException {
        byte[] b = data.getBytes(r.getCharacterEncoding());
        write(r, b);
        return this;
    }

    @Override
    public AsyncIOWriter write(AtmosphereResponse r, byte[] data) throws IOException {
        write(r, data, 0, data.length);
        return this;
    }

    protected byte[] transform(AtmosphereResponse response, byte[] b, int offset, int length) throws IOException {
        AsyncIOWriter a = response.getAsyncIOWriter();
        try {
            response.asyncIOWriter(buffer);
            invokeInterceptor(response, b, offset, length);
            return buffer.stream().toByteArray();
        } finally {
            buffer.close(null);
            response.asyncIOWriter(a);
        }
    }

    @Override
    public AsyncIOWriter write(final AtmosphereResponse r, byte[] data, int offset, int length) throws IOException {
        logger.trace("Writing {} with transport {}", r.resource().uuid(), r.resource().transport());
        boolean transform = filters.size() > 0 && r.getStatus() < 400;
        if (transform) {
            data = transform(r, data, offset, length);
            offset = 0;
            length = data.length;
        }

        pendingWrite.incrementAndGet();
        if (!headerWritten) {
            out.setChunked(true);
            constructStatusAndHeaders(r, out);
            headerWritten = true;
        }

        String sdata = new String(data, offset, length, r.getCharacterEncoding());
        out.write(sdata);
        lastWrite = System.currentTimeMillis();

        AtmosphereResourceImpl impl = AtmosphereResourceImpl.class.cast(r.resource());
        if (sdata.trim().length() > 0 && impl.transport().equals(AtmosphereResource.TRANSPORT.LONG_POLLING)) {
            close(r);
        }
        return this;
    }

    public long lastTick() {
        return lastWrite == -1 ? System.currentTimeMillis() : lastWrite;
    }

    @Override
    public void close(AtmosphereResponse r) throws IOException {
        if (!isClosed.getAndSet(true)) {
            try {
                out.end();
            } catch (IllegalStateException ex) {
                logger.trace("", ex);
            }
        }
    }

    // Duplicate from AtmosphereResource.constructStatusAndHeaders
    private void constructStatusAndHeaders(AtmosphereResponse response, HttpServerResponse out) {
        Map<String, String> headers = response.headers();
        String contentType = response.getContentType();

        out.putHeader("Content-Type", contentType != null ? contentType : "text/plain");
        for (Map.Entry<String, String> s : headers.entrySet()) {
            out.putHeader(s.getKey(), s.getValue());
        }
    }
}
