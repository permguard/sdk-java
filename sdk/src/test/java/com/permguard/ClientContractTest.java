// Copyright (c) 2022 Nitro Agility S.r.l.
// SPDX-License-Identifier: Apache-2.0

package com.permguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.permguard.internal.grpc.v1.EvaluateRequest;
import com.permguard.internal.grpc.v1.EvaluateResponse;
import com.permguard.internal.grpc.v1.PolicyDecisionPointGrpc;
import com.sun.net.httpserver.HttpServer;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ClientContractTest {
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

    /** BUG-0273: a member the SDK does not know, at every level, is ignored over HTTP. */
    @Test
    void anAnswerWithMembersTheSdkDoesNotKnowIsRead() throws Exception {
        var seen = new AtomicReference<String>();
        var server = answering("""
                {"decision":true,"request_id":"r1","signed":"x",
                 "context":{"id":"d1","policies":["p"],"evidence":{"record":"e"}},
                 "evaluations":[{"decision":false,"request_id":"one","extra":1,
                   "context":{"id":"d2","evidence":{}}}]}
                """, seen);
        try (var client = new Client("http://127.0.0.1:" + server.getAddress().getPort())) {
            var request = Pdp.EvaluateRequest.builder("acme", "main").build();
            var response = client.evaluate(request);
            assertTrue(response.decision());
            assertEquals("d1", response.context().id());
            assertEquals("one", client.evaluateMany(request).evaluations().get(0).requestId());
        } finally {
            server.stop(0);
        }
        var configuration = answering("""
                {"interface":"permguard.api.pdp.native.v1","pdp":"p","version":"1","state":"ready",
                 "limits":{"body":1},"response_signing":{},"endpoints":{"evaluation":"e",
                 "evaluations":"es","more":"x"},"capabilities":[],
                 "store_scope":{"in":"payload","zone":"z","ledger":"l","profile":"p","extra":true}}
                """, seen);
        try (var client = new Client("http://127.0.0.1:" + configuration.getAddress().getPort())) {
            assertEquals("permguard.api.pdp.native.v1", client.getConfiguration().interfaceName());
        } finally {
            configuration.stop(0);
        }
        var garbage = answering("not json", seen);
        try (var client = new Client("http://127.0.0.1:" + garbage.getAddress().getPort())) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> client.evaluate(Pdp.EvaluateRequest.builder("acme", "main").build()));
            assertEquals("decode Permguard HTTP response", failure.getMessage());
        } finally {
            garbage.stop(0);
        }
    }

    /** BUG-0308: a null in the context is sent over HTTP, as gRPC sends it. */
    @Test
    void aNullInTheContextIsSent() throws Exception {
        var seen = new AtomicReference<String>();
        var server = answering("{\"decision\":true}", seen);
        try (var client = new Client("http://127.0.0.1:" + server.getAddress().getPort())) {
            var context = new HashMap<String, Object>();
            context.put("mfa", null);
            context.put("n", 1);
            client.evaluate(Pdp.EvaluateRequest.builder("acme", "main").context(context).build());
            assertTrue(seen.get().contains("\"mfa\":null"), seen.get());
            assertTrue(!seen.get().contains("\"profile\""), "an absent property is left out: " + seen.get());
        } finally {
            server.stop(0);
        }
    }

    /** BUG-0316: an endpoint with no scheme and a header the transports cannot carry alike are refused. */
    @Test
    void anEndpointWithoutSchemeAndAnUncarriableHeaderAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Client("//127.0.0.1:52444"));
        assertThrows(IllegalArgumentException.class, () -> Client.Options.builder().header("content-type", "text/plain"));
        assertThrows(IllegalArgumentException.class, () -> Client.Options.builder().header("x-trace-bin", "v"));
        assertThrows(IllegalArgumentException.class, () -> Client.Options.builder().header("grpc-timeout", "1S"));
        assertThrows(IllegalArgumentException.class, () -> Client.Options.builder().header("bad name", "v"));
        Client.Options.builder().header("X-Tenant", "acme").build();
    }

    /** A gRPC PDP that never answers. */
    private static final class SilentPdp extends PolicyDecisionPointGrpc.PolicyDecisionPointImplBase {
        @Override
        public void evaluate(EvaluateRequest request, StreamObserver<EvaluateResponse> observer) {}
    }

    /** BUG-0284: a timeout and an unreachable data plane are one refusal on HTTP and gRPC. */
    @Test
    void clientFailuresAreTheSameRefusalOnBothTransports() throws Exception {
        var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/", exchange -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        http.start();
        Server grpc = ServerBuilder.forPort(0).addService(new SilentPdp()).build().start();
        int closed;
        try (var socket = new ServerSocket(0)) {
            closed = socket.getLocalPort();
        }
        var options = Client.Options.builder().timeout(Duration.ofMillis(300)).build();
        try {
            for (String endpoint : new String[] {
                    "http://127.0.0.1:" + http.getAddress().getPort(),
                    "grpc://127.0.0.1:" + grpc.getPort()}) {
                try (var client = new Client(endpoint, options)) {
                    var refusal = assertThrows(Refusal.class,
                            () -> client.evaluate(Pdp.EvaluateRequest.builder("acme", "main").build()));
                    assertEquals(Refusal.UNAVAILABLE, refusal.errorClass(), endpoint);
                    assertEquals(Refusal.TIMEOUT, refusal.code(), endpoint);
                }
            }
            for (String endpoint : new String[] {"http://127.0.0.1:" + closed, "grpc://127.0.0.1:" + closed}) {
                try (var client = new Client(endpoint, Client.Options.builder().timeout(Duration.ofSeconds(3)).build())) {
                    var refusal = assertThrows(Refusal.class,
                            () -> client.evaluate(Pdp.EvaluateRequest.builder("acme", "main").build()));
                    assertEquals(Refusal.UNAVAILABLE, refusal.errorClass(), endpoint);
                    assertEquals(Refusal.UNREACHABLE, refusal.code(), endpoint);
                }
            }
        } finally {
            http.stop(0);
            grpc.shutdownNow();
        }
    }
}
