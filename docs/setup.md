# Setup: rebuild the P2 trial environment

Goal: rebuild the BTP trial and Integration Suite trial in about an hour (bible §15, rule 1).
Write every click here as you do Phase 1. Never put secrets in this file.

- [ ] 1. Create the BTP trial (subaccount `leinewerk-dev`, CF space `dev`); calendar reminder at day 25
- [ ] 2. Subscribe to Integration Suite, add Cloud Integration capability
- [ ] 3. Assign `PI_Administrator`, `PI_Business_Expert`, `PI_Integration_Developer`
- [ ] 4. Process Integration Runtime instance (`integration-flow`, `ESBMessaging.send`) and service key (local `.env` only)
- [ ] 5. HANA Cloud trial instance `hana-free` (stops nightly; start it first in every test routine)
- [ ] 6. Check JMS availability; record result in ADR 0005
- [x] 7. Install local tools (see below)
- [ ] 8. Smoke test with a Hello World HTTPS iFlow, then undeploy

## Step 7: local tools (done 2026-11-21, macOS arm64)

| Tool | Version | Install |
| --- | --- | --- |
| Node.js | 24.x | already present |
| `@sap/cds-dk` | 10.2.1 | `npm i -g @sap/cds-dk` |
| CF CLI + MultiApps plugin | 8.19.0 / 3.12.0 | `brew install cloudfoundry/tap/cf-cli`, `cf install-plugin multiapps` |
| `mbt` | 1.2.51 | `npm i -g mbt` |
| Java | OpenJDK 21 | `brew install openjdk@21`; add `/opt/homebrew/opt/openjdk@21/bin` to `PATH` |
| REST client | Postman | already present (Bruno optional) |
| KoSIT validator | 1.5.0 | `validator-1.5.0-distribution.zip` from the GitHub release, unpacked to `~/tools/kosit/validator` |
| XRechnung configuration | 3.0.2, release 2024-06-20 | `validator-configuration-xrechnung_3.0.2_2024-06-20.zip`, unpacked to `~/tools/kosit/config` |

Newer 3.0.2 bundles exist (2026-01-31, 2026-08-31). The guide's TC01/02/05/06 results were verified with 2024-06-20, so CI pins that one; evaluate a newer bundle separately.

Verified: TC01 and TC02 ACCEPTABLE, TC05 rejected with BR-DE-15, TC06 rejected with BR-CO-16.

```bash
cd ~/tools/kosit/config
java -jar ../validator/validationtool-1.5.0-standalone.jar -s scenarios.xml -r "$PWD" -o <outdir> <absolute paths to invoices>
```
