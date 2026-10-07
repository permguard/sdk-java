// Copyright (c) 2022 Nitro Agility S.r.l.
// SPDX-License-Identifier: Apache-2.0

package com.permguard;

import io.grpc.Status;

/** A structured PDP failure. A deny is a successful response, not a refusal. */
public final class Refusal extends RuntimeException {
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

    public String errorClass() { return errorClass; }
    public String code() { return code; }
    public Integer httpStatus() { return httpStatus; }
    public Status.Code grpcCode() { return grpcCode; }
}
