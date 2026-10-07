// Copyright (c) 2022 Nitro Agility S.r.l.
// SPDX-License-Identifier: Apache-2.0

package com.permguard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.permguard.internal.grpc.v1.GetConfigurationRequest;
import com.permguard.internal.grpc.v1.PolicyDecisionPointGrpc;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;
import io.grpc.stub.MetadataUtils;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class GrpcTransport implements Client.Transport {
    private static final Metadata.Key<String> ERROR_CLASS =
            Metadata.Key.of("permguard-error-class", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> ERROR_CODE =
            Metadata.Key.of("permguard-error-code", Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel channel;
    private final PolicyDecisionPointGrpc.PolicyDecisionPointBlockingStub stub;
    private final PdpMapper mapper;

    GrpcTransport(URI endpoint, Client.Options options) {
        HttpTransport.validateEndpoint(endpoint, "gRPC");
        boolean secure = endpoint.getScheme().equalsIgnoreCase("grpcs");
        if (!secure && options.grpcCredentials != null) {
            throw new IllegalArgumentException("gRPC credentials require a grpcs:// endpoint");
        }
        var credentials = options.grpcCredentials != null
                ? options.grpcCredentials
                : secure ? TlsChannelCredentials.create() : InsecureChannelCredentials.create();
        this.channel = Grpc.newChannelBuilderForAddress(
                        endpoint.getHost(), effectivePort(endpoint, secure), credentials)
                .build();
        var metadata = new Metadata();
        options.headers.forEach((name, value) -> metadata.put(
                Metadata.Key.of(name.toLowerCase(Locale.ROOT), Metadata.ASCII_STRING_MARSHALLER), value));
        this.stub = PolicyDecisionPointGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
        this.mapper = new PdpMapper(new ObjectMapper());
    }

    @Override
    public Pdp.EvaluateResponse evaluate(
            Pdp.EvaluateRequest request, boolean many, Duration timeout) {
        try {
            var timed = stub.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS);
            var response = many
                    ? timed.evaluateMany(mapper.request(request))
                    : timed.evaluate(mapper.request(request));
            return mapper.response(response);
        } catch (StatusRuntimeException exception) {
            throw refusal(exception);
        }
    }

    @Override
    public Pdp.Configuration getConfiguration(Duration timeout) {
        try {
            var response = stub.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .getConfiguration(GetConfigurationRequest.getDefaultInstance());
            return mapper.configuration(response);
        } catch (StatusRuntimeException exception) {
            throw refusal(exception);
        }
    }

    @Override
    public void close() {
        channel.shutdownNow();
    }

    private Refusal refusal(StatusRuntimeException exception) {
        Metadata metadata = exception.getTrailers();
        Status.Code status = exception.getStatus().getCode();
        String errorClass = metadata == null ? null : metadata.get(ERROR_CLASS);
        String code = metadata == null ? null : metadata.get(ERROR_CODE);
        if (errorClass == null) errorClass = grpcClass(status);
        if (code == null) code = status.name().toLowerCase(Locale.ROOT);
        String message = exception.getStatus().getDescription();
        if (message == null || message.isBlank()) message = exception.getMessage();
        return new Refusal(errorClass, code, message, null, status, exception);
    }

    private static int effectivePort(URI endpoint, boolean secure) {
        return endpoint.getPort() >= 0 ? endpoint.getPort() : secure ? 443 : 80;
    }

    private static String grpcClass(Status.Code status) {
        return switch (status) {
            case INVALID_ARGUMENT, OUT_OF_RANGE -> "validation";
            case UNAUTHENTICATED, PERMISSION_DENIED -> "authorization";
            case NOT_FOUND -> "not_found";
            case UNAVAILABLE, DEADLINE_EXCEEDED -> "unavailable";
            default -> "internal";
        };
    }
}
