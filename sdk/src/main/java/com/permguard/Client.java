// Copyright (c) 2022 Nitro Agility S.r.l.
// SPDX-License-Identifier: Apache-2.0

package com.permguard;

import io.grpc.ChannelCredentials;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.SSLContext;

/** A reusable synchronous client for the Permguard native PDP v1 interface. */
public final class Client implements AutoCloseable {
    private final Transport transport;
    private final Duration timeout;

    public Client(String endpoint) {
        this(endpoint, Options.builder().build());
    }

    public Client(String endpoint, Options options) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(options, "options");
        URI uri = URI.create(endpoint);
        // An endpoint names its scheme: `//host:port` has none, and is refused here, not with a
        // NullPointerException (BUG-0316).
        if (uri.getScheme() == null) {
            throw new IllegalArgumentException(
                    "Permguard endpoint requires a scheme: http://, https://, grpc:// or grpcs://");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Permguard endpoint requires a host");
        }
        this.timeout = positive(options.timeout, "timeout");
        this.transport = switch (uri.getScheme().toLowerCase()) {
            case "http", "https" -> new HttpTransport(uri, options);
            case "grpc", "grpcs" -> new GrpcTransport(uri, options);
            default -> throw new IllegalArgumentException(
                    "unsupported Permguard endpoint scheme " + uri.getScheme());
        };
    }

    public Pdp.EvaluateResponse evaluate(Pdp.EvaluateRequest request) {
        return evaluate(request, timeout);
    }

    public Pdp.EvaluateResponse evaluate(Pdp.EvaluateRequest request, Duration callTimeout) {
        Values.check(Objects.requireNonNull(request, "request"));
        return transport.evaluate(request, false, positive(callTimeout, "timeout"));
    }

    public Pdp.EvaluateResponse evaluateMany(Pdp.EvaluateRequest request) {
        return evaluateMany(request, timeout);
    }

    public Pdp.EvaluateResponse evaluateMany(Pdp.EvaluateRequest request, Duration callTimeout) {
        Values.check(Objects.requireNonNull(request, "request"));
        return transport.evaluate(request, true, positive(callTimeout, "timeout"));
    }

    public Pdp.Configuration getConfiguration() {
        return getConfiguration(timeout);
    }

    public Pdp.Configuration getConfiguration(Duration callTimeout) {
        return transport.getConfiguration(positive(callTimeout, "timeout"));
    }

    @Override
    public void close() {
        transport.close();
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }

    public static final class Options {
        final Duration timeout;
        final Map<String, String> headers;
        final HttpClient httpClient;
        final SSLContext httpSslContext;
        final ChannelCredentials grpcCredentials;

        private Options(Builder builder) {
            this.timeout = builder.timeout;
            this.headers = Map.copyOf(builder.headers);
            this.httpClient = builder.httpClient;
            this.httpSslContext = builder.httpSslContext;
            this.grpcCredentials = builder.grpcCredentials;
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private Duration timeout = Duration.ofSeconds(5);
            private final Map<String, String> headers = new LinkedHashMap<>();
            private HttpClient httpClient;
            private SSLContext httpSslContext;
            private ChannelCredentials grpcCredentials;

            public Builder timeout(Duration value) { timeout = positive(value, "timeout"); return this; }
            /**
             * A static header, also sent as gRPC metadata. A name the transports cannot carry alike
             * is refused here, the same for both (BUG-0316): one the SDK sets itself
             * ({@code content-type}, {@code accept}), one a transport owns ({@code host},
             * {@code content-length}, {@code te}, {@code grpc-*}), a binary gRPC one
             * ({@code *-bin}), or one that is not an HTTP token.
             */
            public Builder header(String name, String value) {
                Objects.requireNonNull(name, "name");
                Objects.requireNonNull(value, "value");
                String lower = name.toLowerCase(java.util.Locale.ROOT);
                if (!lower.matches("[a-z0-9!#$%&'*+.^_`|~-]+")) {
                    throw new IllegalArgumentException("header name `" + name + "` is not an HTTP token");
                }
                if (java.util.Set.of("content-type", "accept", "host", "content-length", "te",
                                "connection", "transfer-encoding").contains(lower)
                        || lower.startsWith("grpc-")
                        || lower.endsWith("-bin")) {
                    throw new IllegalArgumentException(
                            "header `" + name + "` is set by the SDK or its transport, or is binary gRPC metadata");
                }
                if (!value.chars().allMatch(c -> c >= 0x20 && c < 0x7f)) {
                    throw new IllegalArgumentException("header `" + name + "` has a value that is not printable ASCII");
                }
                headers.put(lower, value);
                return this;
            }
            public Builder httpClient(HttpClient value) { httpClient = Objects.requireNonNull(value); return this; }
            public Builder httpSslContext(SSLContext value) {
                httpSslContext = Objects.requireNonNull(value);
                return this;
            }
            public Builder grpcCredentials(ChannelCredentials value) {
                grpcCredentials = Objects.requireNonNull(value);
                return this;
            }
            public Options build() { return new Options(this); }
        }
    }

    interface Transport {
        Pdp.EvaluateResponse evaluate(Pdp.EvaluateRequest request, boolean many, Duration timeout);
        Pdp.Configuration getConfiguration(Duration timeout);
        void close();
    }
}
