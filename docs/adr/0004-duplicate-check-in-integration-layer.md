# ADR 0004: Duplicate check in the integration layer

- Status: Accepted
- Date: 2026-11-20
- Project: P2 Inbound e-invoicing
- Bible section changed: 

## Context

SAP's standard duplicate check keys on company code, supplier, reference, document date and currency, and is not applied to invoices posted through automated interfaces in standard (SAP KBA 3784142).

## Decision

Detect duplicates in three layers: (1) business key SHA-256 of upper(seller VAT ID) | trim(invoice number) | issue date, so the same invoice as ZUGFeRD and XRechnung matches; (2) idempotent process call in Cloud Integration that records the key only after success; (3) rule R4 in the stand-in using the S/4HANA key fields.

## Consequences

- A failed posting does not record the key, so retries still post (TC12).
- A re-issued invoice with the same number and a new amount reaches Aylin Demir, which is intended.
- Duplicates posted by hand before the e-invoice arrived are caught by layer 3 only (TC16).
