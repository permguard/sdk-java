// Copyright (c) 2022 Nitro Agility S.r.l.
// SPDX-License-Identifier: Apache-2.0

package com.permguard;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

/**
 * The values of a request, checked with the server's rule before either transport sends them
 * (BUG-0306, BUG-0307): an integer past ±2^53 is {@code validation}/{@code value_unrepresentable};
 * a number that is not JSON (NaN, an infinity) is {@code validation}/{@code payload_malformed}.
 */
final class Values {
    /** The largest integer both transports carry exactly, the server's bound: 2^53. */
    static final long EXACT = 1L << 53;
    private static final BigInteger EXACT_BIG = BigInteger.valueOf(EXACT);
    private static final ObjectMapper TREE = new ObjectMapper();

    private Values() {}

    /** Checks every value of {@code request}, as Jackson would send it. */
    static void check(Pdp.EvaluateRequest request) {
        check(TREE.convertValue(request, Object.class));
    }

    static void check(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Byte || value instanceof Short || value instanceof Integer) {
            return;
        }
        if (value instanceof Long number) {
            if (number > EXACT || number < -EXACT) throw unrepresentable(number);
            return;
        }
        if (value instanceof BigInteger number) {
            if (number.abs().compareTo(EXACT_BIG) > 0) throw unrepresentable(number);
            return;
        }
        if (value instanceof BigDecimal number) {
            if (number.stripTrailingZeros().scale() <= 0
                    && number.toBigInteger().abs().compareTo(EXACT_BIG) > 0) {
                throw unrepresentable(number);
            }
            return;
        }
        if (value instanceof Double number && !Double.isFinite(number)
                || value instanceof Float floating && !Float.isFinite(floating)) {
            throw new Refusal("validation", "payload_malformed", value + " is not a JSON number", null, null, null);
        }
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(Values::check);
            return;
        }
        if (value instanceof Iterable<?> values) {
            values.forEach(Values::check);
            return;
        }
        if (value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) check(Array.get(value, index));
        }
    }

    private static Refusal unrepresentable(Object number) {
        return new Refusal("validation", "value_unrepresentable",
                number + " is beyond the largest integer both transports carry exactly (2^53): send it as a string",
                null, null, null);
    }
}
