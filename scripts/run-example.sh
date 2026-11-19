#!/usr/bin/env bash
# Copyright (c) 2022 Nitro Agility S.r.l.
# SPDX-License-Identifier: Apache-2.0
#
# Builds the SDK of this repository and runs the example against a data plane:
#
#   scripts/run-example.sh [endpoint]      default grpc://localhost:7443
#
# The SDK is installed under its placeholder version, 0.0.0-SNAPSHOT, which Maven Central never
# holds, so the example always runs on the code next to it.
set -euo pipefail

cd "$(dirname "$0")/.."
export PERMGUARD_PDP_URL="${1:-${PERMGUARD_PDP_URL:-grpc://localhost:7443}}"
mvn -q -f sdk/pom.xml install -DskipTests
mvn -q -f example/src/pom.xml compile exec:java
