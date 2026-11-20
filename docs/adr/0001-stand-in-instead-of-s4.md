# ADR 0001: Post into the S/4HANA stand-in instead of S/4HANA

- Status: Accepted
- Date: 2026-11-20
- Project: P2 Inbound e-invoicing
- Bible section changed: 

## Context

Leinewerk has no S/4HANA licence (bible §15). P2 must still post supplier invoices through the real API contract, and write access is not possible on the Business Accelerator Hub sandbox.

## Decision

Post into `lw-s4-standin`, a CAP app that serves `API_SUPPLIERINVOICE_PROCESS_SRV` with the same service path, entity names and field names as S/4HANA, seeded with Leinewerk golden data. Switching to a real S/4HANA system means changing the `LW_S4` connection only.

## Consequences

- Rules in the stand-in are simplified (R1–R8); behaviour against real S/4HANA must be re-verified in the 30-day sprint.
- The EDMX is the contract; where the guide and the EDMX differ, the EDMX wins.
- Every README states that the stand-in is not S/4HANA (bible §15 rule 4).
