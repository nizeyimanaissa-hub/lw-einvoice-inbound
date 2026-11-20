# ADR 0003: Official KoSIT validation

- Status: Accepted
- Date: 2026-11-20
- Project: P2 Inbound e-invoicing
- Bible section changed: 

## Context

Hand-written checks would drift from the legal rules and miss business rules such as BR-DE-15 or BR-CO-16.

## Decision

Validate inside the iFlow with the official KoSIT XRechnung configuration 3.0.2: XSD plus Schematron compiled to XSLT. Fatal SVRL assertions reject the file. Principle 2: use the released standard artefacts, do not re-implement them.

## Consequences

- Rule IDs in rejection responses come straight from the standard.
- Validation artefacts must be updated when KoSIT publishes new releases (4.0 expected spring 2027).
- XSLT validation steps cost processing time per message; cheap checks (format, size, profile) run first.
