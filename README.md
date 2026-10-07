<!--
Copyright (c) 2022 Nitro Agility S.r.l.
SPDX-License-Identifier: Apache-2.0
-->

# Permguard Java SDK

The official Java client for the stateless Permguard PDP interface
`permguard.api.pdp.native.v1`.

One public API supports both server bindings:

- `http://` and `https://` use JSON;
- `grpc://` and `grpcs://` use `permguard.data.v1.PolicyDecisionPoint`.

## Requirements

Java 17 or newer and Maven.

## Installation

```xml
<dependency>
    <groupId>com.permguard.pep</groupId>
    <artifactId>permguard</artifactId>
    <version>0.0.1</version>
</dependency>
```

## Evaluate one request

```java
import com.permguard.Client;
import com.permguard.Pdp;

try (var client = new Client("grpc://localhost:7443")) {
    // Use http://localhost:7443 for the HTTP/JSON binding.
    var request = Pdp.EvaluateRequest.builder("acme", "documents")
            .subject(new Pdp.Entity("user", "amy@example.com"))
            .resource(new Pdp.Entity("document", "quarterly-report"))
            .action(new Pdp.Action("read"))
            .build();

    var response = client.evaluate(request);
    System.out.println("permitted: " + response.decision());
}
```

A deny is a successful response with `decision() == false`. Validation,
authorization, availability, and server failures throw `Refusal`, preserving
their stable class and code.

## Partition inputs

```java
var request = Pdp.EvaluateRequest.builder("acme", "documents")
        .partitionInputs(Map.of(
                "authorization",
                new Pdp.PartitionInput(
                        "permguard.cedar.entities.v1",
                        List.of(Map.of(
                                "uid", Map.of("type", "Team", "id", "engineering"),
                                "attrs", Map.of("active", true),
                                "parents", List.of())))))
        .build();
```

The map key is the partition name declared by the selected profile. `type`
asserts the input contract; it does not select a policy runtime.

## Evaluate a batch

```java
var request = Pdp.EvaluateRequest.builder("acme", "documents")
        .subject(new Pdp.Entity("user", "amy@example.com"))
        .evaluations(List.of(
                Pdp.Evaluation.builder()
                        .resource(new Pdp.Entity("document", "one"))
                        .action(new Pdp.Action("read"))
                        .requestId("one")
                        .build(),
                Pdp.Evaluation.builder()
                        .resource(new Pdp.Entity("document", "two"))
                        .action(new Pdp.Action("read"))
                        .requestId("two")
                        .build()))
        .options(new Pdp.EvaluationOptions(Pdp.EvaluationsSemantic.EXECUTE_ALL))
        .build();

var response = client.evaluateMany(request);
```

An evaluation whose `partitionInputs` is `null` inherits request defaults. An
explicit empty map replaces the defaults with no inputs; the SDK preserves this
distinction on both transports.

## Discovery and transport options

```java
var options = Client.Options.builder()
        .timeout(Duration.ofSeconds(5))
        .header("authorization", "Bearer " + token)
        .build();

try (var client = new Client("https://pdp.example.com", options)) {
    var configuration = client.getConfiguration();
}
```

The same static headers are HTTP headers or gRPC metadata. `Options` also
accepts a custom `HttpClient`, HTTP `SSLContext`, or gRPC `ChannelCredentials`.
Every call has an overload accepting its own timeout.

## Compatibility

This major version implements `permguard.api.pdp.native.v1`. Compatibility is
tied to that versioned interface rather than to a server minor version.

## Development

```bash
mvn -f sdk/pom.xml test
mvn -f sdk/pom.xml package
./scripts/third-party-notices.sh --check
```

Maven generates protobuf and gRPC sources from the checked-in native v1
contract during every build.

## License

Apache License 2.0. See [LICENSE](LICENSE), [NOTICE.md](NOTICE.md), and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
