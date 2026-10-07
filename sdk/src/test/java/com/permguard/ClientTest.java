// Copyright (c) 2022 Nitro Agility S.r.l.
// SPDX-License-Identifier: Apache-2.0

package com.permguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.permguard.internal.grpc.v1.Decision;
import com.permguard.internal.grpc.v1.DecisionContext;
import com.permguard.internal.grpc.v1.EvaluateRequest;
import com.permguard.internal.grpc.v1.EvaluateResponse;
import com.permguard.internal.grpc.v1.GetConfigurationRequest;
import com.permguard.internal.grpc.v1.GetConfigurationResponse;
import com.permguard.internal.grpc.v1.PolicyDecisionPointGrpc;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ClientTest {
    @Test
    void httpImplementsNativeContractAndStructuredRefusals() throws Exception {
        var seenPath = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handleHttp(exchange, seenPath));
        server.start();

        try (var client = new Client("http://127.0.0.1:" + server.getAddress().getPort())) {
            var response = client.evaluate(Pdp.EvaluateRequest.builder("acme", "documents")
                    .requestId("r1")
                    .build());
            assertTrue(response.decision());
            assertEquals("r1", response.requestId());
            assertEquals(List.of("policy-1"), response.context().policies());
            assertEquals("/access/v1/evaluation", seenPath.get());

            var configuration = client.getConfiguration();
            assertEquals("permguard.api.pdp.native.v1", configuration.interfaceName());
            assertEquals("/.well-known/permguard-pdp-v1-configuration", seenPath.get());

            var refusal = assertThrows(
                    Refusal.class,
                    () -> client.evaluate(Pdp.EvaluateRequest.builder("acme", "bad").build()));
            assertEquals("validation", refusal.errorClass());
            assertEquals("ledger_invalid", refusal.code());
            assertEquals(400, refusal.httpStatus());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void grpcImplementsNativeContractBatchDiscoveryAndStructuredRefusals() throws Exception {
        Server server = ServerBuilder.forPort(0).addService(new TestPdp()).build().start();
        try (var client = new Client("grpc://127.0.0.1:" + server.getPort())) {
            var response = client.evaluate(Pdp.EvaluateRequest.builder("acme", "documents")
                    .requestId("r1")
                    .build());
            var batch = client.evaluateMany(Pdp.EvaluateRequest.builder("acme", "documents")
                    .evaluations(List.of(Pdp.Evaluation.builder().build(), Pdp.Evaluation.builder().build()))
                    .build());
            var configuration = client.getConfiguration();

            assertTrue(response.decision());
            assertEquals("r1", response.requestId());
            assertEquals(List.of("policy-1"), response.context().policies());
            assertFalse(batch.decision());
            assertEquals(2, batch.evaluations().size());
            assertEquals("permguard.api.pdp.native.v1", configuration.interfaceName());

            var refusal = assertThrows(
                    Refusal.class,
                    () -> client.evaluate(Pdp.EvaluateRequest.builder("acme", "bad").build()));
            assertEquals("validation", refusal.errorClass());
            assertEquals("ledger_invalid", refusal.code());
            assertEquals(Status.Code.INVALID_ARGUMENT, refusal.grpcCode());
        } finally {
            server.shutdownNow().awaitTermination();
        }
    }

    @Test
    void grpcMappingPreservesPresenceAndRejectsLossyIntegers() {
        var mapper = new PdpMapper(new ObjectMapper());
        var request = Pdp.EvaluateRequest.builder("acme", "documents")
                .evaluations(List.of(Pdp.Evaluation.builder()
                        .context(Map.of())
                        .partitionInputs(Map.of())
                        .build()))
                .build();
        var wire = mapper.request(request);
        assertTrue(wire.getEvaluations(0).hasContext());
        assertTrue(wire.getEvaluations(0).hasPartitionInputs());

        var unsafe = Pdp.EvaluateRequest.builder("acme", "documents")
                .context(Map.of("unsafe", 9_007_199_254_740_992L))
                .build();
        assertThrows(IllegalArgumentException.class, () -> mapper.request(unsafe));
    }

    private static void handleHttp(HttpExchange exchange, AtomicReference<String> seenPath)
            throws IOException {
        seenPath.set(exchange.getRequestURI().getPath());
        String response;
        int status = 200;
        if (exchange.getRequestMethod().equals("GET")) {
            response = """
                    {"interface":"permguard.api.pdp.native.v1","pdp":"http://test",
                     "endpoints":{"evaluation":"e","evaluations":"es"},"capabilities":[],
                     "store_scope":{"in":"payload","zone":"required","ledger":"required","profile":"optional"}}
                    """;
        } else {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (request.contains("\"ledger\":\"bad\"")) {
                status = 400;
                response = "{\"class\":\"validation\",\"code\":\"ledger_invalid\",\"message\":\"bad ledger\"}";
            } else {
                response = "{\"decision\":true,\"request_id\":\"r1\",\"context\":{\"policies\":[\"policy-1\"]}}";
            }
        }
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static final class TestPdp
            extends PolicyDecisionPointGrpc.PolicyDecisionPointImplBase {
        @Override
        public void evaluate(EvaluateRequest request, StreamObserver<EvaluateResponse> observer) {
            if (request.getLedger().equals("bad")) {
                var trailers = new Metadata();
                trailers.put(
                        Metadata.Key.of("permguard-error-class", Metadata.ASCII_STRING_MARSHALLER),
                        "validation");
                trailers.put(
                        Metadata.Key.of("permguard-error-code", Metadata.ASCII_STRING_MARSHALLER),
                        "ledger_invalid");
                observer.onError(Status.INVALID_ARGUMENT
                        .withDescription("bad ledger")
                        .asRuntimeException(trailers));
                return;
            }
            observer.onNext(EvaluateResponse.newBuilder()
                    .setDecision(true)
                    .setRequestId(request.getRequestId())
                    .setContext(DecisionContext.newBuilder().addPolicies("policy-1"))
                    .build());
            observer.onCompleted();
        }

        @Override
        public void evaluateMany(EvaluateRequest request, StreamObserver<EvaluateResponse> observer) {
            observer.onNext(EvaluateResponse.newBuilder()
                    .setDecision(false)
                    .addEvaluations(Decision.newBuilder().setDecision(true).setRequestId("one"))
                    .addEvaluations(Decision.newBuilder().setDecision(false).setRequestId("two"))
                    .build());
            observer.onCompleted();
        }

        @Override
        public void getConfiguration(
                GetConfigurationRequest request,
                StreamObserver<GetConfigurationResponse> observer) {
            observer.onNext(GetConfigurationResponse.newBuilder()
                    .setInterface("permguard.api.pdp.native.v1")
                    .setPdp("grpc://test")
                    .build());
            observer.onCompleted();
        }
    }
}
