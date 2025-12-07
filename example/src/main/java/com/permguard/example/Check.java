/**
 *   Copyright 2024 Nitro Agility S.r.l.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
  */

package com.permguard.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.permguard.pep.builder.*;
import com.permguard.pep.client.AZClient;
import com.permguard.pep.config.AZConfig;
import com.permguard.pep.model.request.*;
import com.permguard.pep.model.response.AZResponse;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class for testing authorization requests, equivalent to check.py in Python.
 */
public class Check {

    private static final String JSON_FILE_PATH = "requests/ok_onlyone1.json";
    public static final long ZONE_ID = 634601921829L;
    public static final String POLICY_STORE_ID = "417b278c0d024cf789e3d3c2bc9854c6";

    public static final String PRINCIPAL_ID = "spiffe://edge.example.com/workload/64ad91fec7b0403eaf5d37e56c14ba42";
    public static final String PRINCIPAL_TYPE = "workload";
    public static final String PRINCIPAL_SOURCE = "spire";

    public static final String SUBJECT_ID = "role/branch-owner";
    public static final String SUBJECT_TYPE = "attribute";

    public static final String RESOURCE_ID = "fb008a600df04b21841c4fb5ad27ddf7";
    public static final String RESOURCE_TYPE = "PharmaAuthZFlow::Platform::Branch";

    public static final String ACTION_NAME = "PharmaAuthZFlow::Platform::Action::assign-role";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {


        AZConfig config = new AZConfig("localhost", 9094, true);
        AZClient client = new AZClient(config);

        System.out.println("\n🔹 Running checkJsonRequest()");
        checkJsonRequest(client);

        System.out.println("\n🔹 Running checkAtomicRequest()");
        checkAtomicRequest(client);

        System.out.println("\n🔹 Running checkMultipleEvaluationsRequest()");
        checkMultipleEvaluationsRequest(client);

        client.shutdown();
    }

    /**
     * Loads a JSON authorization request and validates it.
     * Equivalent to check_json_request() in Python.
     *
     * @param client The AZClient instance to send the request.
     */
    public static void checkJsonRequest(AZClient client) {
        try {
            // Load JSON as InputStream from resources folder and print its content
            InputStream inputStream = Check.class.getClassLoader().getResourceAsStream(JSON_FILE_PATH);
            AZRequest request = objectMapper.readValue(inputStream, AZRequest.class);
            inputStream.close();

            long requestStartTime = System.currentTimeMillis();
            AZResponse response = client.check(request);
            long requestEndTime = System.currentTimeMillis();

            System.out.println("Request execution time: " + (requestEndTime - requestStartTime) + " ms");
            printAuthorizationResult(response);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("❌ Error loading JSON request: " + e.getMessage());
        }
    }

    public static void checkAtomicRequest(AZClient client) {
        try {
            long zoneId = ZONE_ID;
            String policyStoreId = POLICY_STORE_ID;
            String requestId = "atomic-request-001";

            Principal principal = new PrincipalBuilder(PRINCIPAL_ID)
                    .withType(PRINCIPAL_TYPE)
                    .withSource(PRINCIPAL_SOURCE)
                    .build();

            // Build the atomic AZRequest using the exact JSON parameters
            AZRequest request = new AZAtomicRequestBuilder(
                    zoneId,
                    policyStoreId,
                    SUBJECT_ID,
                    RESOURCE_TYPE,
                    ACTION_NAME
            )
                    .withRequestId(requestId)
                    .withPrincipal(principal)
                    .withSubjectType(SUBJECT_TYPE)
                    .withResourceId(RESOURCE_ID)
                    .build();

            // Perform atomic authorization check
            long requestStartTime = System.currentTimeMillis();
            AZResponse response = client.check(request);
            long requestEndTime = System.currentTimeMillis();
            System.out.println("Request execution time: " + (requestEndTime - requestStartTime) + " ms");
            printAuthorizationResult(response);
        } catch (Exception e) {
            System.err.println("❌ Error executing atomic request: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public static void checkMultipleEvaluationsRequest(AZClient client) {
        try {
            long zoneId = ZONE_ID;
            String policyStoreId = POLICY_STORE_ID;
            String requestId = "batch-eval-001";

            // Create Principal
            Principal principal = new PrincipalBuilder(PRINCIPAL_ID)
                    .withType(PRINCIPAL_TYPE)
                    .withSource(PRINCIPAL_SOURCE)
                    .build();

            // Create Subject
            Subject subject = new SubjectBuilder(SUBJECT_ID)
                    .withType(SUBJECT_TYPE)
                    .build();

            // Create Resource
            Resource resource = new ResourceBuilder(RESOURCE_TYPE)
                    .withId(RESOURCE_ID)
                    .build();

            // Create Actions
            Action actionAssignRole = new ActionBuilder(ACTION_NAME)
                    .build();

            Action actionView = new ActionBuilder("PharmaAuthZFlow::Platform::Action::view")
                    .build();

            // Create Evaluations
            Evaluation evaluationAssignRole = new EvaluationBuilder(subject, resource, actionAssignRole)
                    .withRequestId("eval-assign-role")
                    .build();

            Evaluation evaluationView = new EvaluationBuilder(subject, resource, actionView)
                    .withRequestId("eval-view")
                    .build();

            // Build the AZRequest with multiple evaluations
            AZRequest request = new AZRequestBuilder(zoneId, policyStoreId)
                    .withRequestId(requestId)
                    .withPrincipal(principal)
                    .withEvaluation(evaluationAssignRole)
                    .withEvaluation(evaluationView)
                    .build();

            // Perform authorization check with multiple evaluations
            long requestStartTime = System.currentTimeMillis();
            AZResponse response = client.check(request);
            long requestEndTime = System.currentTimeMillis();
            System.out.println("Request execution time: " + (requestEndTime - requestStartTime) + " ms");
            printAuthorizationResult(response);
        } catch (Exception e) {
            System.err.println("❌ Error executing multiple evaluations request: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Prints the result of an authorization request.
     *
     * @param response The AZResponse received from the PDP.
     */
    public static void printAuthorizationResult(AZResponse response) {
        if (response == null) {
            System.out.println("❌ Authorization request failed.");
            return;
        }

        if (response.isDecision()) {
            System.out.println("✅ Authorization Permitted");
        } else {
            System.out.println("❌ Authorization Denied");
            if (response.getContext() != null) {
                if (response.getContext().getReasonAdmin() != null) {
                    System.out.println("-> Reason Admin: " + response.getContext().getReasonAdmin().getMessage());
                }
                if (response.getContext().getReasonUser() != null) {
                    System.out.println("-> Reason User: " + response.getContext().getReasonUser().getMessage());
                }
            }
            if (response.getEvaluations() != null) {
                for (var eval : response.getEvaluations()) {
                    if (eval.getContext() != null && eval.getContext().getReasonUser() != null) {
                        System.out.println("-> Evaluation RequestID " + eval.getRequestId()
                                + ": Reason User: " + eval.getContext().getReasonUser().getMessage());
                    } else if(eval.isDecision()){
                        System.out.println("-> Evaluation RequestID " + eval.getRequestId() + ": Single Authorization Permitted");
                    }
                }
            }
        }
    }
}
