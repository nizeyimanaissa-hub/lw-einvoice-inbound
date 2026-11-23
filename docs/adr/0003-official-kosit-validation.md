# ADR 0003: Official KoSIT validation

- Status: Accepted
- Date: 2026-11-20
- Project: P2 Inbound e-invoicing
- Bible section changed: §8 (principle 2, keep the core clean)

## Context

Hand-written checks would drift from the legal rules and miss business rules such as BR-DE-15 or BR-CO-16.

## Decision

Validate inside the iFlow with the official KoSIT XRechnung configuration 3.0.2: XSD plus Schematron compiled to XSLT. Fatal SVRL assertions reject the file. Principle 2: use the released standard artefacts, do not re-implement them.

## Consequences

- Rule IDs in rejection responses come straight from the standard.
- Validation artefacts must be updated when KoSIT publishes new releases (4.0 expected spring 2027).
- XSLT validation steps cost processing time per message; cheap checks (format, size, profile) run first.
- Configuration release 2024-06-20 (XRechnung 3.0.2) is fetched by `scripts/fetch-tools.sh`; `scripts/build-cpi-resources.py` builds the upload bundle for the iFlow.
- Use the XSLTs that the validator's own `scenarios.xml` names (`resources/ubl|cii/.../xsl/`, `resources/xrechnung/3.0.2/xsl/`), not the top-level `EN16931-*-validation.xslt` files, which differ.
- The EN 16931 and the XRechnung Schematron are two separate checks on the *original* invoice, and each catches rules the other does not (verified: BR-CO-16 only in EN 16931, BR-DE-15 only in XRechnung). Chained XSLT steps would feed the first report into the second, so the flow stashes each SVRL in a property (`ei.svrlEn`, `ei.svrlXr`) and restores the invoice before the next step; `EvaluateSvrl.groovy` reads both.
- Business errors are exceptions whose message starts with the `EI_*` code. Cloud Integration cannot share a custom exception class between script steps, and `ClassifyError.groovy` parses the prefix.
