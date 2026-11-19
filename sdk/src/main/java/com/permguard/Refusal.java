// Copyright (c) 2022 Nitro Agility S.r.l.
// SPDX-License-Identifier: Apache-2.0

package com.permguard;

import io.grpc.Status;

/** A structured PDP failure. A deny is a successful response, not a refusal. */
public final class Refusal extends RuntimeException {
    /**
     * The class of a call that failed in the client, the same on HTTP and gRPC and in every
     * Permguard SDK: {@link #TIMEOUT} when the call's timeout passed, {@link #UNREACHABLE} when
     * the data plane could not be reached. A caller's own interruption or cancellation is not a
     * refusal.
     */
    public static final String UNAVAILABLE = "unavailable";
    /** The code of a call whose timeout passed in the client. */
    public static final String TIMEOUT = "timeout";
    /** The code of a call whose data plane could not be reached. */
    public static final String UNREACHABLE = "unreachable";

    private final String errorClass;
    private final String code;
    private final Integer httpStatus;
    private final Status.Code grpcCode;

    Refusal(
            String errorClass,
            String code,
            String message,
            Integer httpStatus,
            Status.Code grpcCode,
            Throwable cause) {
        super(code == null || code.isEmpty() ? message : code + ": " + message, cause);
        this.errorClass = errorClass;
        this.code = code;
        this.httpStatus = httpStatus;
        this.grpcCode = grpcCode;
    }

    static Refusal timeout(Throwable cause) {
        Status.Code grpc = cause instanceof io.grpc.StatusRuntimeException held ? held.getStatus().getCode() : null;
        return new Refusal(UNAVAILABLE, TIMEOUT, "the data plane did not answer within the call's timeout",
                null, grpc, cause);
    }

    static Refusal unreachable(Throwable cause) {
        Status.Code grpc = cause instanceof io.grpc.StatusRuntimeException held ? held.getStatus().getCode() : null;
        return new Refusal(UNAVAILABLE, UNREACHABLE, "the data plane could not be reached: " + cause.getMessage(),
                null, grpc, cause);
    }

    public String errorClass() { return errorClass; }
    public String code() { return code; }
    public Integer httpStatus() { return httpStatus; }
    public Status.Code grpcCode() { return grpcCode; }
}
