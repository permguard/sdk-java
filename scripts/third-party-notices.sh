#!/usr/bin/env bash
# Copyright 2024 Nitro Agility S.r.l.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0

# Writes THIRD_PARTY_NOTICES.md from what the build actually resolves, and checks it is current.
#
# # Why this reads the resolved dependencies rather than the manifest
#
# The manifest says what this project asks for; the resolved set says what a build receives — the
# transitive closure, at the exact versions. A notices file written from the manifest names a
# fraction of what ships, which is worse than no file: it looks like disclosure and is not.
#
# # Why the output is sorted
#
# The file is the input to a CI check that fails when it drifts. That check is only meaningful if a
# regeneration with unchanged dependencies produces byte-identical output, so entries are sorted
# and nothing timestamped or machine-specific is written into it.
#
# # What the plugin is asked for
#
# `add-third-party` rather than `download-licenses`: the first writes one text file naming every
# artifact with its licence, which is what this file needs, while the second downloads licence
# documents into the build directory and answers a different question.

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

readonly OUTPUT="THIRD_PARTY_NOTICES.md"

usage() {
    cat >&2 <<'USAGE'
usage: third-party-notices.sh [--check]

  (no argument)  regenerate THIRD_PARTY_NOTICES.md in place
  --check        fail if the file is not what a regeneration would write
USAGE
}

checking="false"
case "${1-}" in
    "") ;;
    --check) checking="true" ;;
    -h | --help)
        usage
        exit 0
        ;;
    *)
        usage
        exit 2
        ;;
esac

require() {
    if ! command -v "$1" >/dev/null 2>&1; then
        printf 'error: %s is required to write the third-party notices.\n' "$1" >&2
        printf 'install it with: %s\n' "$2" >&2
        exit 1
    fi
}

require mvn "https://maven.apache.org/install.html"

# `add-third-party` writes a THIRD-PARTY.txt whose lines look like:
#
#     (Apache License 2.0) Guava (com.google.guava:guava:31.1-jre - https://github.com/google/guava)
#
# The plugin is invoked by coordinates rather than declared in the POM, so the build file stays
# about the build. `-Dlicense.excludedScopes` drops what never ships.
readonly REPORT="sdk/target/generated-sources/license/THIRD-PARTY.txt"

mvn -q -B -f sdk/pom.xml \
    org.codehaus.mojo:license-maven-plugin:2.4.0:add-third-party \
    -Dlicense.excludedScopes=test,provided \
    -Dlicense.outputDirectory="target/generated-sources/license" \
    -Dlicense.thirdPartyFilename="THIRD-PARTY.txt" >/dev/null

if [ ! -f "${REPORT}" ]; then
    printf 'error: the plugin wrote no report at %s\n' "${REPORT}" >&2
    exit 1
fi

collected="$(
    sed -n 's/^[[:space:]]*(\(.*\))[[:space:]]\(.*\)[[:space:]](\([^:]*\):\([^:]*\):\([^ ]*\)[[:space:]]-[[:space:]]\(.*\))$/\3:\4\t\5\t\1\t\6/p' \
        "${REPORT}"
)"

# One line per dependency, tab separated: name, version, licence, source. Sorted here so the
# collection step above never has to care about order.
rows="$(printf '%s' "${collected}" | LC_ALL=C sort -u)"
count="$(printf '%s' "${rows}" | grep -c . || true)"

table="$(
    printf '%s\n' "${rows}" | awk -F'\t' 'NF >= 3 {
        name = $1; version = $2; licence = $3; source = $4;
        if (licence == "" || licence == "null" || licence == "UNKNOWN") licence = "not declared";
        if (source == "" || source == "null") source = "—";
        printf "| `%s` | %s | %s | %s |\n", name, version, licence, source;
    }'
)"

undeclared="$(
    printf '%s\n' "${rows}" | awk -F'\t' '
        $3 == "" || $3 == "null" || $3 == "UNKNOWN" {
            printf "- `%s` %s — %s\n", $1, $2, ($4 == "" || $4 == "null" ? "no source declared either" : $4);
        }'
)"

if [ -z "${undeclared}" ]; then
    undeclared_section="Every package above declares a licence."
else
    undeclared_section="$(
        cat <<UNDECLARED
The packages below declare no licence in their metadata. That is usually an upstream omission
rather than an absence of licence: check the licence file in each source tree before a distribution
that relies on this list.

${undeclared}
UNDECLARED
    )"
fi

rendered="$(
    cat <<HEADER
# Third-Party Notices

The Permguard Java SDK is distributed under the Apache License, Version 2.0. It depends on the
third-party artifacts listed below, each under its own licence.

This file is generated from the resolved dependencies and is checked in CI. Do not edit it by hand:
run \`task notices\` instead.

Development and test dependencies are excluded where the ecosystem distinguishes them: a notice
covers what is distributed, and a test harness is not.

## Packages

${count} packages.

| Package | Version | Licence | Source |
| ------- | ------- | ------- | ------ |
${table}

## Packages without a declared licence

${undeclared_section}

## Full licence texts

The full text of the Apache License 2.0 is in [LICENSE](LICENSE). The texts of the other licences
named above are published by their respective projects at the sources listed.

For licence questions, contact <opensource@permguard.com>.
HEADER
)"

if [ "${checking}" = "true" ]; then
    if [ ! -f "${OUTPUT}" ]; then
        printf 'error: %s does not exist. Run `task notices` and commit it.\n' "${OUTPUT}" >&2
        exit 1
    fi
    if ! printf '%s\n' "${rendered}" | diff -u "${OUTPUT}" - >/dev/null; then
        printf 'error: %s is out of date with the resolved dependencies.\n\n' "${OUTPUT}" >&2
        printf '%s\n' "${rendered}" | diff -u "${OUTPUT}" - >&2 || true
        printf '\nRun `task notices` and commit the result.\n' >&2
        exit 1
    fi

    printf 'ok: %s matches the resolved dependencies (%s packages)\n' "${OUTPUT}" "${count}"
    exit 0
fi

printf '%s\n' "${rendered}" >"${OUTPUT}"
printf 'wrote %s (%s packages)\n' "${OUTPUT}" "${count}"
