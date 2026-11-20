# Setup: rebuild the P2 trial environment

Goal: rebuild the BTP trial and Integration Suite trial in about an hour (bible §15, rule 1).
Write every click here as you do Phase 1. Never put secrets in this file.

- [ ] 1. Create the BTP trial (subaccount `leinewerk-dev`, CF space `dev`); calendar reminder at day 25
- [ ] 2. Subscribe to Integration Suite, add Cloud Integration capability
- [ ] 3. Assign `PI_Administrator`, `PI_Business_Expert`, `PI_Integration_Developer`
- [ ] 4. Process Integration Runtime instance (`integration-flow`, `ESBMessaging.send`) and service key (local `.env` only)
- [ ] 5. HANA Cloud trial instance `hana-free` (stops nightly; start it first in every test routine)
- [ ] 6. Check JMS availability; record result in ADR 0005
- [ ] 7. Install local tools (Node LTS, `@sap/cds-dk`, CF CLI + MultiApps, `mbt`, Java 17+, KoSIT validator 1.5.0 + XRechnung configuration 3.0.2)
- [ ] 8. Smoke test with a Hello World HTTPS iFlow, then undeploy
