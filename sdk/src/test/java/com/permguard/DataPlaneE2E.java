// Copyright (c) 2022 Nitro Agility S.r.l.
// SPDX-License-Identifier: Apache-2.0

package com.permguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Against a real data plane: {@code scripts/e2e-server.sh} starts it from the Permguard release
 * PERMGUARD_CONTRACT pins, serving a ledger {@code e2e/policies} that permits {@code User:alice} to
 * {@code read} (BUG-0077). Its name keeps it out of {@code mvn test}; run it with
 * {@code mvn test -Dtest=DataPlaneE2E}.
 */
class DataPlaneE2E {
    private static Pdp.EvaluateRequest.Builder ask(String id) {
        return Pdp.EvaluateRequest.builder("e2e", "policies")
                .subject(new Pdp.Entity("User", id))
                .resource(new Pdp.Entity("Document", "d"))
                .action(new Pdp.Action("read"));
    }

    /**
     * Over both transports: a permit with its decision evidence, a deny, a batch, a refusal, a
     * request for a signed response, and the discovery document (BUG-0077, BUG-0274).
     */
    @Test
    void againstARealDataPlane() throws Exception {
        for (var variable : List.of("E2E_HTTP", "E2E_GRPC")) {
            var endpoint = System.getenv(variable);
            assertNotNull(endpoint, "E2E_HTTP and E2E_GRPC name the data plane scripts/e2e-server.sh started");
            overOneTransport(endpoint);
        }
    }

    private static void overOneTransport(String endpoint) throws Exception {
        try (var client = new Client(endpoint)) {
            var permit = client.evaluate(ask("alice").build());
            assertTrue(permit.decision(), "alice reads");
            assertNotNull(permit.context().evidence(), "the decision's evidence");
            assertFalse(permit.context().evidence().status().isEmpty(), "the decision's evidence");
            assertFalse(client.evaluate(ask("bob").build()).decision(), "bob reads");

            var many = client.evaluateMany(ask("alice")
                    .evaluations(List.of(
                            Pdp.Evaluation.builder().subject(new Pdp.Entity("User", "alice")).build(),
                            Pdp.Evaluation.builder().subject(new Pdp.Entity("User", "bob")).build()))
                    .build());
            assertEquals(List.of(true, false),
                    many.evaluations().stream().map(Pdp.Decision::decision).toList(), "the batch");

            var missing = ask("alice").build();
            var unknown = new Pdp.EvaluateRequest("e2e", "nope", null, missing.subject(), missing.resource(),
                    missing.action(), null, null, null, null, null, null);
            assertEquals("ledger_not_served",
                    assertThrows(Refusal.class, () -> client.evaluate(unknown)).code());

            // The nonce reaches the plane: a signed response is addressed to an authenticated
            // caller, and this one presents no token, so the plane refuses rather than answer
            // unsigned. The attestation's mapping is the unit tests'.
            var signed = ask("alice").requestNonce("ZTJlLW5vbmNlLTE2LWJ5dGVz").build();
            assertEquals("unauthenticated", assertThrows(Refusal.class, () -> client.evaluate(signed)).code());

            var configuration = client.getConfiguration();
            assertFalse(configuration.version().isEmpty(), "the version");
            assertFalse(configuration.stage().isEmpty(), "the stage");
            assertTrue(configuration.limits().getOrDefault("max_evaluations", 0L) > 0, "the limits");
            assertFalse(configuration.responseSigning().state().isEmpty(), "the response signing");
        }
    }
}
