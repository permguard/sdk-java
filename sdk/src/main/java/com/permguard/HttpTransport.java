// Copyright (c) 2022 Nitro Agility S.r.l.
// SPDX-License-Identifier: Apache-2.0

package com.permguard;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

final class HttpTransport implements Client.Transport {
    private static final String EVALUATION_PATH = "/access/v1/evaluation";
    private static final String EVALUATIONS_PATH = "/access/v1/evaluations";
    private static final String CONFIGURATION_PATH = "/.well-known/permguard-pdp-v1-configuration";
    private static final int MAX_RESPONSE_BYTES = 16 << 20;

    private final URI base;
    private final HttpClient client;
    private final Map<String, String> headers;
    private final ObjectMapper json;

    HttpTransport(URI endpoint, Client.Options options) {
        validateEndpoint(endpoint, "HTTP");
        this.base = URI.create(endpoint.getScheme() + "://" + endpoint.getRawAuthority());
        this.headers = options.headers;
        this.json = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        if (options.httpClient != null && options.httpSslContext != null) {
            throw new IllegalArgumentException(
                    "httpSslContext cannot be combined with a supplied HttpClient");
        }
        if (options.httpClient != null) {
            this.client = options.httpClient;
        } else {
            var builder = HttpClient.newBuilder();
            if (options.httpSslContext != null) builder.sslContext(options.httpSslContext);
            this.client = builder.build();
        }
    }

    @Override
    public Pdp.EvaluateResponse evaluate(
            Pdp.EvaluateRequest request, boolean many, Duration timeout) {
        try {
            byte[] body = json.writeValueAsBytes(request);
            return call(
                    "POST",
                    many ? EVALUATIONS_PATH : EVALUATION_PATH,
                    body,
                    timeout,
                    Pdp.EvaluateResponse.class);
        } catch (IOException exception) {
            throw new IllegalStateException("encode Permguard evaluation request", exception);
        }
    }

    @Override
    public Pdp.Configuration getConfiguration(Duration timeout) {
        return call("GET", CONFIGURATION_PATH, null, timeout, Pdp.Configuration.class);
    }

    @Override
    public void close() {
        // java.net.http.HttpClient owns no closeable resource before Java 21.
    }

    private <T> T call(
            String method, String path, byte[] body, Duration timeout, Class<T> responseType) {
        var builder = HttpRequest.newBuilder(base.resolve(path))
                .timeout(timeout)
                .header("Accept", "application/json");
        headers.forEach(builder::header);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        }

        try {
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] payload;
            try (InputStream stream = response.body()) {
                payload = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
            }
            if (payload.length > MAX_RESPONSE_BYTES) {
                throw new IllegalStateException(
                        "Permguard HTTP response exceeds " + MAX_RESPONSE_BYTES + " bytes");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw refusal(response.statusCode(), payload);
            }
            return json.readValue(payload, responseType);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("call Permguard HTTP endpoint", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("call Permguard HTTP endpoint", exception);
        }
    }

    private Refusal refusal(int status, byte[] payload) {
        Map<String, Object> error;
        try {
            error = json.readValue(payload, new TypeReference<>() {});
        } catch (IOException exception) {
            error = Map.of();
        }
        return new Refusal(
                text(error.get("class"), httpClass(status)),
                text(error.get("code"), "http_status"),
                text(error.get("message"), "HTTP status " + status),
                status,
                null,
                null);
    }

    private static String text(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static String httpClass(int status) {
        if (status == 400 || status == 422) return "validation";
        if (status == 401 || status == 403) return "authorization";
        if (status == 404) return "not_found";
        if (status == 503 || status == 504) return "unavailable";
        return "internal";
    }

    static void validateEndpoint(URI endpoint, String transport) {
        String path = endpoint.getPath();
        if ((path != null && !path.isEmpty() && !path.equals("/"))
                || endpoint.getQuery() != null
                || endpoint.getFragment() != null
                || endpoint.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    transport + " Permguard endpoint must not contain credentials, a path, query, or fragment");
        }
    }
}
