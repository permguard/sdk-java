// Copyright (c) 2022 Nitro Agility S.r.l.
// SPDX-License-Identifier: Apache-2.0

package com.permguard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import com.permguard.internal.grpc.v1.GetConfigurationResponse;
import java.lang.reflect.Array;
import java.util.Map;

final class PdpMapper {
    private static final long MAX_EXACT_PROTO_INTEGER = (1L << 53) - 1;
    private final ObjectMapper json;

    PdpMapper(ObjectMapper json) {
        this.json = json;
    }

    com.permguard.internal.grpc.v1.EvaluateRequest request(Pdp.EvaluateRequest value) {
        var builder = com.permguard.internal.grpc.v1.EvaluateRequest.newBuilder()
                .setZone(value.zone())
                .setLedger(value.ledger());
        if (value.profile() != null) builder.setProfile(value.profile());
        if (value.subject() != null) builder.setSubject(entity(value.subject()));
        if (value.resource() != null) builder.setResource(entity(value.resource()));
        if (value.action() != null) builder.setAction(action(value.action()));
        if (value.context() != null) builder.setContext(struct(value.context()));
        if (value.principal() != null) builder.setPrincipal(entity(value.principal()));
        if (value.partitionInputs() != null) {
            value.partitionInputs().forEach((name, input) -> builder.putPartitionInputs(name, input(input)));
        }
        value.evaluations().forEach(item -> builder.addEvaluations(evaluation(item)));
        if (value.options() != null && value.options().evaluationsSemantic() != null) {
            builder.setEvaluationsSemantic(semantic(value.options().evaluationsSemantic()));
        }
        if (value.requestId() != null) builder.setRequestId(value.requestId());
        return builder.build();
    }

    Pdp.EvaluateResponse response(com.permguard.internal.grpc.v1.EvaluateResponse value) {
        return new Pdp.EvaluateResponse(
                value.getDecision(),
                emptyToNull(value.getRequestId()),
                value.hasContext() ? context(value.getContext()) : null,
                value.getEvaluationsList().stream().map(this::decision).toList());
    }

    Pdp.Configuration configuration(GetConfigurationResponse value) {
        var endpoints = value.hasEndpoints()
                ? new Pdp.Endpoints(value.getEndpoints().getEvaluation(), value.getEndpoints().getEvaluations())
                : new Pdp.Endpoints("", "");
        var scope = value.hasStoreScope()
                ? new Pdp.StoreScope(
                        value.getStoreScope().getIn(),
                        value.getStoreScope().getZone(),
                        value.getStoreScope().getLedger(),
                        value.getStoreScope().getProfile())
                : new Pdp.StoreScope("", "", "", "");
        return new Pdp.Configuration(
                value.getInterface(), value.getPdp(), endpoints, value.getCapabilitiesList(), scope);
    }

    private com.permguard.internal.grpc.v1.Entity entity(Pdp.Entity value) {
        var builder = com.permguard.internal.grpc.v1.Entity.newBuilder()
                .setType(value.type())
                .setId(value.id());
        if (value.properties() != null) builder.setProperties(struct(value.properties()));
        return builder.build();
    }

    private com.permguard.internal.grpc.v1.Action action(Pdp.Action value) {
        var builder = com.permguard.internal.grpc.v1.Action.newBuilder().setName(value.name());
        if (value.properties() != null) builder.setProperties(struct(value.properties()));
        return builder.build();
    }

    private com.permguard.internal.grpc.v1.PartitionInput input(Pdp.PartitionInput value) {
        var builder = com.permguard.internal.grpc.v1.PartitionInput.newBuilder().setType(value.type());
        if (value.data() != null) builder.setData(protoValue(value.data()));
        return builder.build();
    }

    private com.permguard.internal.grpc.v1.Evaluation evaluation(Pdp.Evaluation value) {
        var builder = com.permguard.internal.grpc.v1.Evaluation.newBuilder();
        if (value.subject() != null) builder.setSubject(entity(value.subject()));
        if (value.resource() != null) builder.setResource(entity(value.resource()));
        if (value.action() != null) builder.setAction(action(value.action()));
        if (value.context() != null) builder.setContext(struct(value.context()));
        if (value.requestId() != null) builder.setRequestId(value.requestId());
        if (value.partitionInputs() != null) {
            var inputs = com.permguard.internal.grpc.v1.PartitionInputs.newBuilder();
            value.partitionInputs().forEach((name, input) -> inputs.putInputs(name, input(input)));
            builder.setPartitionInputs(inputs);
        }
        return builder.build();
    }

    private com.permguard.internal.grpc.v1.EvaluationsSemantic semantic(Pdp.EvaluationsSemantic value) {
        return switch (value) {
            case EXECUTE_ALL -> com.permguard.internal.grpc.v1.EvaluationsSemantic.EVALUATIONS_SEMANTIC_EXECUTE_ALL;
            case DENY_ON_FIRST_DENY -> com.permguard.internal.grpc.v1.EvaluationsSemantic.EVALUATIONS_SEMANTIC_DENY_ON_FIRST_DENY;
            case PERMIT_ON_FIRST_PERMIT -> com.permguard.internal.grpc.v1.EvaluationsSemantic.EVALUATIONS_SEMANTIC_PERMIT_ON_FIRST_PERMIT;
        };
    }

    private Pdp.Decision decision(com.permguard.internal.grpc.v1.Decision value) {
        return new Pdp.Decision(
                value.getDecision(),
                emptyToNull(value.getRequestId()),
                value.hasContext() ? context(value.getContext()) : null);
    }

    private Pdp.DecisionContext context(com.permguard.internal.grpc.v1.DecisionContext value) {
        return new Pdp.DecisionContext(
                emptyToNull(value.getId()),
                value.hasReasonAdmin() ? reason(value.getReasonAdmin()) : null,
                value.hasReasonUser() ? reason(value.getReasonUser()) : null,
                value.getPoliciesList(),
                value.getAbsentInputsList());
    }

    private Pdp.Reason reason(com.permguard.internal.grpc.v1.Reason value) {
        return new Pdp.Reason(value.getCode(), value.getMessage());
    }

    private Struct struct(Map<String, Object> value) {
        validateNumbers(value);
        var builder = Struct.newBuilder();
        try {
            JsonFormat.parser().merge(json.writeValueAsString(value), builder);
        } catch (JsonProcessingException | com.google.protobuf.InvalidProtocolBufferException exception) {
            throw new IllegalArgumentException("encode protobuf Struct", exception);
        }
        return builder.build();
    }

    private Value protoValue(Object value) {
        validateNumbers(value);
        var builder = Value.newBuilder();
        try {
            JsonFormat.parser().merge(json.writeValueAsString(value), builder);
        } catch (JsonProcessingException | com.google.protobuf.InvalidProtocolBufferException exception) {
            throw new IllegalArgumentException("encode protobuf Value", exception);
        }
        return builder.build();
    }

    private void validateNumbers(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) return;
        if (value instanceof Long number
                && (number > MAX_EXACT_PROTO_INTEGER || number < -MAX_EXACT_PROTO_INTEGER)) {
            throw new IllegalArgumentException(
                    "integer " + number + " is not exactly representable by protobuf Value");
        }
        if (value instanceof Float number && !Float.isFinite(number)) {
            throw new IllegalArgumentException("JSON numbers must be finite");
        }
        if (value instanceof Double number && !Double.isFinite(number)) {
            throw new IllegalArgumentException("JSON numbers must be finite");
        }
        if (value instanceof Map<?, ?> map) map.values().forEach(this::validateNumbers);
        if (value instanceof Iterable<?> values) values.forEach(this::validateNumbers);
        if (value != null && value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) {
                validateNumbers(Array.get(value, index));
            }
        }
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
