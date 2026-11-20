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
        if (value.requestNonce() != null) builder.setRequestNonce(value.requestNonce());
        return builder.build();
    }

    Pdp.EvaluateResponse response(com.permguard.internal.grpc.v1.EvaluateResponse value) {
        return new Pdp.EvaluateResponse(
                value.getDecision(),
                emptyToNull(value.getRequestId()),
                value.hasContext() ? context(value.getContext()) : null,
                value.getEvaluationsList().stream().map(this::decision).toList(),
                value.hasAttestation()
                        ? new Pdp.Attestation(
                                value.getAttestation().getProtected(),
                                value.getAttestation().getPayload(),
                                value.getAttestation().getSignature())
                        : null);
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
        var signing = value.getResponseSigning();
        return new Pdp.Configuration(
                value.getInterface(),
                value.getPdp(),
                endpoints,
                value.getCapabilitiesList(),
                scope,
                value.getVersion(),
                value.getComponent(),
                value.getState(),
                value.getStage(),
                value.hasSunset() ? value.getSunset() : null,
                value.getLimitsMap(),
                new Pdp.ResponseSigning(
                        signing.getState(),
                        signing.getValiditySeconds() == 0 ? null : signing.getValiditySeconds(),
                        signing.getClockSkewSeconds() == 0 ? null : signing.getClockSkewSeconds()));
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
                value.getAbsentInputsList(),
                value.hasEvidence() ? evidence(value.getEvidence()) : null);
    }

    private Pdp.Evidence evidence(com.permguard.internal.grpc.v1.Evidence value) {
        return new Pdp.Evidence(
                value.getMode(),
                value.getStatus(),
                value.hasRecordId() ? value.getRecordId() : null,
                value.hasPosition()
                        ? new Pdp.EvidencePosition(
                                value.getPosition().getHostId(),
                                value.getPosition().getRun(),
                                value.getPosition().getSequence())
                        : null);
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
        // The server's rule, the same one the client checks before either transport (BUG-0307).
        Values.check(value);
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
