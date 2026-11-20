# ADR 0005: Cloud Integration addresses LW_S4 by parameter

- Status: Accepted
- Date: 2026-11-20
- Project: P2 Inbound e-invoicing
- Bible section changed: §15 (S/4HANA stand-in, destination LW_S4) and §16 (destination naming)

## Context

Cloud Integration adapters do not use the BTP destination service; they take an address plus a security-material alias. Bible §15 and §16 name the connection `LW_S4`.

## Decision

The iFlow externalises `LW_S4_Address` and the credential alias `LW_S4`. The BTP destination `LW_S4` exists for CAP apps. Moving to real S/4HANA changes these two values (and the destination) only.

## Consequences

- Two places hold the same target; keep them in sync (documented in `docs/setup.md`).
- JMS availability on the trial tenant: yes
