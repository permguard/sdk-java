// Copyright (c) 2022 Nitro Agility S.r.l.
// SPDX-License-Identifier: Apache-2.0

package com.permguard.example;

import com.permguard.Client;
import com.permguard.Pdp;
import com.permguard.Refusal;

public final class Check {
    private Check() {}

    public static void main(String[] args) {
        String endpoint = System.getenv().getOrDefault(
                "PERMGUARD_PDP_URL", "grpc://localhost:7443");
        try (var client = new Client(endpoint)) {
            var request = Pdp.EvaluateRequest.builder("acme", "documents")
                    .subject(new Pdp.Entity("user", "amy@example.com"))
                    .resource(new Pdp.Entity("document", "quarterly-report"))
                    .action(new Pdp.Action("read"))
                    .requestId("example-1")
                    .build();
            var response = client.evaluate(request);
            System.out.println("permitted: " + response.decision());
        } catch (Refusal refusal) {
            System.err.printf(
                    "PDP refused the request: class=%s code=%s message=%s%n",
                    refusal.errorClass(), refusal.code(), refusal.getMessage());
            System.exit(1);
        }
    }
}
