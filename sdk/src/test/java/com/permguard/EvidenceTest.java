// Copyright (c) 2022 Nitro Agility S.r.l.
// SPDX-License-Identifier: Apache-2.0

package com.permguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.permguard.internal.grpc.v1.Attestation;
import com.permguard.internal.grpc.v1.DecisionContext;
import com.permguard.internal.grpc.v1.EvaluateResponse;
import com.permguard.internal.grpc.v1.Evidence;
import com.permguard.internal.grpc.v1.EvidencePosition;
import com.permguard.internal.grpc.v1.GetConfigurationResponse;
import com.permguard.internal.grpc.v1.ResponseSigning;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EvidenceTest {
    private static HttpServer answering(String body, AtomicReference<String> seen) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            seen.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static final Pdp.Evidence EVIDENCE = new Pdp.Evidence(
            "required", "recorded", "rec-1", new Pdp.EvidencePosition("h", "r", "7"));
    private static final Pdp.Attestation ATTESTATION = new Pdp.Attestation("p", "b", "s");

    /**
     * BUG-0274: a request asks for a signed response with its nonce, and the answer's attestation
     * and decision evidence reach the caller over both transports.
     */
    @Test
    void theNonceTheAttestationAndTheEvidenceTravelOnBothTransports() throws Exception {
        var mapper = new PdpMapper(new ObjectMapper());
        var request = Pdp.EvaluateRequest.builder("z", "l").requestNonce("bm9uY2Utb2Ytc2l4dGVlbg").build();
        assertEquals("bm9uY2Utb2Ytc2l4dGVlbg", mapper.request(request).getRequestNonce());

        var fromGrpc = mapper.response(EvaluateResponse.newBuilder()
                .setDecision(true)
                .setContext(DecisionContext.newBuilder()
                        .setId("d-1")
                        .setEvidence(Evidence.newBuilder()
                                .setMode("required")
                                .setStatus("recorded")
                                .setRecordId("rec-1")
                                .setPosition(EvidencePosition.newBuilder()
                                        .setHostId("h").setRun("r").setSequence("7"))))
                .setAttestation(Attestation.newBuilder().setProtected("p").setPayload("b").setSignature("s"))
                .build());
        assertEquals(EVIDENCE, fromGrpc.context().evidence());
        assertEquals(ATTESTATION, fromGrpc.attestation());

        var seen = new AtomicReference<String>();
        var server = answering("""
                {"decision":true,"context":{"id":"d-1","evidence":{"mode":"required","status":"recorded",
                 "record_id":"rec-1","position":{"host_id":"h","run":"r","sequence":"7"}}},
                 "attestation":{"protected":"p","payload":"b","signature":"s"}}
                """, seen);
        try (var client = new Client("http://127.0.0.1:" + server.getAddress().getPort())) {
            var fromHttp = client.evaluate(request);
            assertTrue(seen.get().contains("\"request_nonce\":\"bm9uY2Utb2Ytc2l4dGVlbg\""), seen.get());
            assertEquals(EVIDENCE, fromHttp.context().evidence());
            assertEquals(ATTESTATION, fromHttp.attestation());
            client.evaluate(Pdp.EvaluateRequest.builder("z", "l").build());
            assertTrue(!seen.get().contains("request_nonce"), seen.get());
        } finally {
            server.stop(0);
        }
    }

    /**
     * BUG-0274: the discovery document's version, lifecycle, limits and response signing reach the
     * caller over both transports.
     */
    @Test
    void theDiscoveryDocumentIsReadWholeOnBothTransports() throws Exception {
        var fromGrpc = new PdpMapper(new ObjectMapper()).configuration(GetConfigurationResponse.newBuilder()
                .setInterface("permguard.api.pdp.native.v1")
                .setVersion("0.1.33")
                .setComponent("data-plane")
                .setState("serving")
                .setStage("v1")
                .putLimits("max_evaluations", 64)
                .setResponseSigning(ResponseSigning.newBuilder()
                        .setState("optional").setValiditySeconds(300).setClockSkewSeconds(30))
                .build());
        var seen = new AtomicReference<String>();
        var server = answering("""
                {"interface":"permguard.api.pdp.native.v1","version":"0.1.33","component":"data-plane",
                 "state":"serving","stage":"v1","limits":{"max_evaluations":64},
                 "response_signing":{"state":"optional","validity_seconds":300,"clock_skew_seconds":30}}
                """, seen);
        try (var client = new Client("http://127.0.0.1:" + server.getAddress().getPort())) {
            var fromHttp = client.getConfiguration();
            for (var held : new Pdp.Configuration[] {fromGrpc, fromHttp}) {
                assertEquals("0.1.33", held.version());
                assertEquals("data-plane", held.component());
                assertEquals("serving", held.state());
                assertEquals("v1", held.stage());
                assertNull(held.sunset());
                assertEquals(Map.of("max_evaluations", 64L), held.limits());
                assertEquals(new Pdp.ResponseSigning("optional", 300, 30), held.responseSigning());
            }
        } finally {
            server.stop(0);
        }
    }
}
