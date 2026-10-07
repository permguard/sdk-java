// Copyright (c) 2022 Nitro Agility S.r.l.
// SPDX-License-Identifier: Apache-2.0

package com.permguard;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Value types for {@code permguard.api.pdp.native.v1}. */
public final class Pdp {
    private Pdp() {}

    public record Entity(String type, String id, Map<String, Object> properties) {
        public Entity {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(id, "id");
            properties = immutable(properties);
        }

        public Entity(String type, String id) {
            this(type, id, null);
        }
    }

    public record Action(String name, Map<String, Object> properties) {
        public Action {
            Objects.requireNonNull(name, "name");
            properties = immutable(properties);
        }

        public Action(String name) {
            this(name, null);
        }
    }

    public record PartitionInput(String type, Object data) {
        public PartitionInput {
            Objects.requireNonNull(type, "type");
        }
    }

    public record Evaluation(
            Entity subject,
            Entity resource,
            Action action,
            Map<String, Object> context,
            Map<String, PartitionInput> partitionInputs,
            String requestId) {
        public Evaluation {
            context = immutable(context);
            partitionInputs = immutable(partitionInputs);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private Entity subject;
            private Entity resource;
            private Action action;
            private Map<String, Object> context;
            private Map<String, PartitionInput> partitionInputs;
            private String requestId;

            public Builder subject(Entity value) { subject = value; return this; }
            public Builder resource(Entity value) { resource = value; return this; }
            public Builder action(Action value) { action = value; return this; }
            public Builder context(Map<String, Object> value) { context = value; return this; }
            public Builder partitionInputs(Map<String, PartitionInput> value) {
                partitionInputs = value;
                return this;
            }
            public Builder requestId(String value) { requestId = value; return this; }

            public Evaluation build() {
                return new Evaluation(subject, resource, action, context, partitionInputs, requestId);
            }
        }
    }

    public enum EvaluationsSemantic {
        EXECUTE_ALL("execute_all"),
        DENY_ON_FIRST_DENY("deny_on_first_deny"),
        PERMIT_ON_FIRST_PERMIT("permit_on_first_permit");

        private final String wireValue;

        EvaluationsSemantic(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }
    }

    public record EvaluationOptions(EvaluationsSemantic evaluationsSemantic) {}

    public record EvaluateRequest(
            String zone,
            String ledger,
            String profile,
            Entity subject,
            Entity resource,
            Action action,
            Map<String, Object> context,
            Entity principal,
            Map<String, PartitionInput> partitionInputs,
            List<Evaluation> evaluations,
            EvaluationOptions options,
            String requestId) {
        public EvaluateRequest {
            Objects.requireNonNull(zone, "zone");
            Objects.requireNonNull(ledger, "ledger");
            context = immutable(context);
            partitionInputs = immutable(partitionInputs);
            evaluations = evaluations == null ? List.of() : List.copyOf(evaluations);
        }

        public static Builder builder(String zone, String ledger) {
            return new Builder(zone, ledger);
        }

        public static final class Builder {
            private final String zone;
            private final String ledger;
            private String profile;
            private Entity subject;
            private Entity resource;
            private Action action;
            private Map<String, Object> context;
            private Entity principal;
            private Map<String, PartitionInput> partitionInputs;
            private List<Evaluation> evaluations = List.of();
            private EvaluationOptions options;
            private String requestId;

            private Builder(String zone, String ledger) {
                this.zone = Objects.requireNonNull(zone, "zone");
                this.ledger = Objects.requireNonNull(ledger, "ledger");
            }

            public Builder profile(String value) { profile = value; return this; }
            public Builder subject(Entity value) { subject = value; return this; }
            public Builder resource(Entity value) { resource = value; return this; }
            public Builder action(Action value) { action = value; return this; }
            public Builder context(Map<String, Object> value) { context = value; return this; }
            public Builder principal(Entity value) { principal = value; return this; }
            public Builder partitionInputs(Map<String, PartitionInput> value) {
                partitionInputs = value;
                return this;
            }
            public Builder evaluations(List<Evaluation> value) { evaluations = value; return this; }
            public Builder options(EvaluationOptions value) { options = value; return this; }
            public Builder requestId(String value) { requestId = value; return this; }

            public EvaluateRequest build() {
                return new EvaluateRequest(
                        zone,
                        ledger,
                        profile,
                        subject,
                        resource,
                        action,
                        context,
                        principal,
                        partitionInputs,
                        evaluations,
                        options,
                        requestId);
            }
        }
    }

    public record Reason(String code, String message) {}

    public record DecisionContext(
            String id,
            Reason reasonAdmin,
            Reason reasonUser,
            List<String> policies,
            List<String> absentInputs) {
        public DecisionContext {
            policies = policies == null ? List.of() : List.copyOf(policies);
            absentInputs = absentInputs == null ? List.of() : List.copyOf(absentInputs);
        }
    }

    public record Decision(boolean decision, String requestId, DecisionContext context) {}

    public record EvaluateResponse(
            boolean decision,
            String requestId,
            DecisionContext context,
            List<Decision> evaluations) {
        public EvaluateResponse {
            evaluations = evaluations == null ? List.of() : List.copyOf(evaluations);
        }
    }

    public record Endpoints(String evaluation, String evaluations) {}
    public record StoreScope(String in, String zone, String ledger, String profile) {}

    public record Configuration(
            @JsonProperty("interface") String interfaceName,
            String pdp,
            Endpoints endpoints,
            List<String> capabilities,
            StoreScope storeScope) {
        public Configuration {
            capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        }
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> value) {
        return value == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
