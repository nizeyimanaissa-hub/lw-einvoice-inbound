#!/usr/bin/env bash
# Local unit tests: Schematron against the sample invoices, the Groovy scripts, then the Phase 5 mappings.
set -euo pipefail
cd "$(dirname "$0")/../.."
./scripts/fetch-tools.sh >/dev/null
./test/unit/validate-kosit.sh
CP="$(ls .tools/jars/*.jar | tr "\n" ":")test/unit/groovy/stub"
java -cp "$CP" groovy.ui.GroovyMain test/unit/groovy/ScriptsTest.groovy
java -cp "$CP" groovy.ui.GroovyMain test/unit/groovy/MappingTest.groovy
