# ADR 0002: Canonical invoice model

- Status: Accepted
- Date: 2026-11-20
- Project: P2 Inbound e-invoicing
- Bible section changed: §10 (integration map, interface "Supplier e-invoices")

## Context

Suppliers send XRechnung UBL, XRechnung CII or ZUGFeRD (with embedded CII). Mapping each syntax straight to the API would duplicate the API mapping. XRechnung 4.0 is a pre-release; 3.0 stays valid until at least 31 July 2027.

## Decision

Map UBL and CII first to one internal Leinewerk invoice XML (canonical), then map the canonical invoice to the API request. One API mapping, two small input mappings. Format detection is built so a 4.0 branch can be added later.

## Consequences

- XRechnung 4.0 becomes a third input mapping (`*-to-canonical.xsl`); the API mapping is untouched.
- Each XSLT is unit-tested in CI with Saxon-HE.
- One extra transformation step per message.
