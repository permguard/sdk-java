package com.permguard.pep.client;

import com.permguard.pep.internal.proto.AuthorizationCheck;
import com.permguard.pep.model.request.*;
import com.permguard.pep.model.response.AZResponse;
import com.permguard.pep.model.response.ContextResponse;
import com.permguard.pep.model.response.EvaluationResponse;
import com.permguard.pep.model.response.ReasonResponse;
import com.permguard.pep.utils.GrpcStructMapper;

class Mapper {

    /**
     * Converts an AZRequest into a gRPC-compatible AuthorizationCheckRequest.
     *
     * @param request The AZRequest.
     * @return A gRPC-compatible AuthorizationCheckRequest.
     */
    AuthorizationCheck.AuthorizationCheckRequest mapAuthorizationCheckRequest(AZRequest request) {
        AuthorizationCheck.AuthorizationCheckRequest.Builder requestBuilder = AuthorizationCheck.AuthorizationCheckRequest.newBuilder()
                .setRequestID(request.getRequestId() != null ? request.getRequestId() : "")
                .setAuthorizationModel(mapAuthorizationModel(request.getAuthorizationModel()));

        if (request.getSubject() != null) {
            requestBuilder.setSubject(mapSubject(request.getSubject()));
        }
        if (request.getResource() != null) {
            requestBuilder.setResource(mapResource(request.getResource()));
        }
        if (request.getAction() != null) {
            requestBuilder.setAction(mapAction(request.getAction()));
        }
        if (request.getContext() != null) {
            requestBuilder.setContext(GrpcStructMapper.toGrpcStruct(request.getContext())); // Use Struct directly
        }
        if (request.getEvaluations() != null) {
            for (Evaluation eval : request.getEvaluations()) {
                requestBuilder.addEvaluations(mapEvaluation(eval));
            }
        }

        return requestBuilder.build();
    }

    /**
     * Converts an AuthorizationCheckResponse into an AZResponse.
     *
     * @param response The gRPC response.
     * @return An AZResponse instance.
     */
    AZResponse mapAuthResponsePayload(AuthorizationCheck.AuthorizationCheckResponse response) {
        return new AZResponse(
                response.getDecision(),
                response.hasRequestID() ? response.getRequestID() : "",
                response.hasContext() ? mapContextResponse(response.getContext()) : null,
                response.getEvaluationsList().stream().map(this::mapEvaluationResponse).toList()
        );
    }

    /** MAPPING HELPERS **/

    private AuthorizationCheck.AuthorizationModelRequest mapAuthorizationModel(AZModel model) {
        return AuthorizationCheck.AuthorizationModelRequest.newBuilder()
                .setZoneID(model.getZoneId())
                .setPolicyStore(mapPolicyStore(model.getPolicyStore()))
                .setPrincipal(mapPrincipal(model.getPrincipal()))
                .setEntities(model.getEntities() != null ? mapEntities(model.getEntities()) :
                        AuthorizationCheck.Entities.newBuilder().build()
                )

                .build();
    }

    private AuthorizationCheck.PolicyStore mapPolicyStore(PolicyStore store) {
        return AuthorizationCheck.PolicyStore.newBuilder()
                .setKind(store.getKind())
                .setID(store.getId())
                .build();
    }

    private AuthorizationCheck.Principal mapPrincipal(Principal principal) {
        return AuthorizationCheck.Principal.newBuilder()
                .setType(principal.getType())
                .setID(principal.getId())
                .setSource(principal.getSource())
                .build();
    }

    private AuthorizationCheck.Entities mapEntities(Entities entities) {
        return AuthorizationCheck.Entities.newBuilder()
                .setSchema(entities.getSchema())
                .build();
    }

    private AuthorizationCheck.Subject mapSubject(Subject subject) {
        return AuthorizationCheck.Subject.newBuilder()
                .setType(subject.getType())
                .setID(subject.getId())
                .setSource(subject.getSource() != null ? subject.getSource() : "")
                .setProperties(subject.getProperties() != null ? GrpcStructMapper.toGrpcStruct(subject.getProperties()) : com.google.protobuf.Struct.newBuilder().build())
                .build();
    }

    private AuthorizationCheck.Resource mapResource(Resource resource) {
        return AuthorizationCheck.Resource.newBuilder()
                .setType(resource.getType())
                .setID(resource.getId())
                .setProperties(resource.getProperties() != null ? GrpcStructMapper.toGrpcStruct(resource.getProperties()) : com.google.protobuf.Struct.newBuilder().build())
                .build();
    }

    private AuthorizationCheck.Action mapAction(Action action) {
        return AuthorizationCheck.Action.newBuilder()
                .setName(action.getName())
                .setProperties(action.getProperties() != null ? GrpcStructMapper.toGrpcStruct(action.getProperties()) : com.google.protobuf.Struct.newBuilder().build())
                .build();
    }

    private AuthorizationCheck.EvaluationRequest mapEvaluation(Evaluation evaluation) {
        AuthorizationCheck.EvaluationRequest.Builder builder = AuthorizationCheck.EvaluationRequest.newBuilder()
                .setRequestID(evaluation.getRequestId() != null ? evaluation.getRequestId() : "")
                .setSubject(mapSubject(evaluation.getSubject()))
                .setResource(mapResource(evaluation.getResource()))
                .setAction(mapAction(evaluation.getAction()));

        if (evaluation.getContext() != null) {
            builder.setContext(GrpcStructMapper.toGrpcStruct(evaluation.getContext()));
        }

        return builder.build();
    }

    private EvaluationResponse mapEvaluationResponse(AuthorizationCheck.EvaluationResponse response) {
        return new EvaluationResponse(
                response.getDecision(),
                response.hasRequestID() ? response.getRequestID() : "",
                response.hasContext() ? mapContextResponse(response.getContext()) : null
        );
    }



    private ContextResponse mapContextResponse(AuthorizationCheck.ContextResponse grpcContext) {
        return new ContextResponse(
                grpcContext.getID(),
                grpcContext.hasReasonAdmin() ? mapReasonResponse(grpcContext.getReasonAdmin()) : null,
                grpcContext.hasReasonUser() ? mapReasonResponse(grpcContext.getReasonUser()) : null
        );
    }


    private ReasonResponse mapReasonResponse(AuthorizationCheck.ReasonResponse grpcReason) {
        return new ReasonResponse(
                grpcReason.getCode(),
                grpcReason.getMessage()
        );
    }


}
