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

import io.netty.handler.codec.http.HttpHeaders;

import org.atmosphere.cpr.AtmosphereRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.http.HttpServerRequest;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class AtmosphereUtils {

    private static Logger logger = LoggerFactory.getLogger(AtmosphereUtils.class);

    public final static AtmosphereRequest request(final HttpServerRequest request) throws Throwable {
        final String base = getBaseUri(request);
        final URI requestUri = new URI(base.substring(0, base.length() - 1) + request.uri());
        String ct = "text/plain";
        if (request.headers().get("Content-Type") != null) {
            ct = request.headers().get("Content-Type");
        }
        String method = request.method().name();


        URI uri = null;
        try {
            uri = URI.create(request.uri());
        } catch (IllegalArgumentException e) {
            logger.trace("", e);
        }
        String queryString = uri.getQuery();
        Map<String, String[]> qs = new HashMap<String, String[]>();
        if (queryString != null) {
            parseQueryString(qs, queryString);
        }

        String u = requestUri.toURL().toString();
        int last = u.indexOf("?") == -1 ? u.length() : u.indexOf("?");
        String url = u.substring(0, last);
        int l = requestUri.getAuthority().length() + requestUri.getScheme().length() + 3;

        final Map<String, Object> attributes = new HashMap<String, Object>();

        final StringBuilder b = new StringBuilder();

        int port = uri == null ? 0 : uri.getPort();
        String uriString = uri.getPath();
        String host = uri.getHost();
        AtmosphereRequest.Builder requestBuilder = new AtmosphereRequest.Builder();
        final AtmosphereRequest r = requestBuilder.requestURI(url.substring(l))
                .requestURL(u)
                .pathInfo(url.substring(l))
                .headers(getHeaders(request))
                .method(method)
                .requestURL(request.uri())
                .contentType(ct)
                .destroyable(false)
                .attributes(attributes)
                .servletPath("")
                .remotePort(port)
                .remoteAddr(uriString)
                .remoteHost(host)
//                .localPort(((InetSocketAddress) ctx.getChannel().getLocalAddress()).getPort())
//                .localAddr(((InetSocketAddress) ctx.getChannel().getLocalAddress()).getAddress().getHostAddress())
//                .localName(((InetSocketAddress) ctx.getChannel().getLocalAddress()).getHostName())
                .body(b.toString())
                .queryStrings(qs)
                .build();
        return r;
    }

    public static void parseQueryString(Map<String, String[]> qs, String queryString) {
        if (queryString != null) {
            String[] s = queryString.split("&");
            for (String a : s) {
                String[] q = a.split("=");
                String[] z = new String[]{q.length > 1 ? q[1] : ""};
                qs.put(q[0], z);
            }
        }
    }

    public static String getBaseUri(final HttpServerRequest request) {
        return "http://" + request.headers().get(HttpHeaders.Names.HOST) + "/";

    }

    public static Map<String, String> getHeaders(final HttpServerRequest request) {
        final Map<String, String> headers = new HashMap<String, String>();

        for (Entry<String, String> e : request.headers()) {
            headers.put(e.getKey().toLowerCase(), e.getValue());
        }

        return headers;
    }
}
