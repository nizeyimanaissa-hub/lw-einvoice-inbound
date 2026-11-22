# Setup: rebuild the P2 trial environment

Goal: rebuild the BTP trial, Integration Suite trial and local tools in about an hour (bible §15, rule 1).
Nothing secret goes in this file. Secrets live in the git-ignored `.env` (service key only) and in the password manager.

Last verified: 2026-11-22 (Phase 1 complete).

## Overview

| # | Step | Where | Result |
| --- | --- | --- | --- |
| 1 | Create the BTP trial | BTP cockpit | Subaccount, CF org and space `dev` |
| 2 | Subscribe to Integration Suite | BTP cockpit | Cloud Integration provisioned |
| 3 | Assign Cloud Integration roles | BTP cockpit | You can design and monitor iFlows |
| 4 | Sender credentials | BTP cockpit | Process Integration Runtime service key in `.env` |
| 5 | HANA Cloud trial instance | BTP cockpit | Database for the stand-in |
| 6 | Check JMS | Cloud Integration | Result recorded in ADR 0005 |
| 7 | Install local tools | Terminal | Node, CAP, CF, MTA, Java, KoSIT validator |
| 8 | Smoke test | Terminal + Cloud Integration | Token call to an HTTPS iFlow returns 200 |

## Deviations from the guide

| Guide | What we did | Why / consequence |
| --- | --- | --- |
| Subaccount display name `leinewerk-dev` (bible §16) | Kept the default name `trial` | Trial subaccount was created with the default name. Cosmetic; no setting depends on it. Rename in the cockpit under *Subaccount → Overview → Edit* if you want §16 compliance. In a new trial, name it `leinewerk-dev` from the start. |
| Roles to IAS groups (bible §12) | Roles assigned to the user directly | The trial has no group setup; note this in the README. |

## Step 1: Create the BTP trial

1. Open <https://www.sap.com/products/technology-platform/trial.html> and start the trial (onboarding: <https://developers.sap.com/tutorials/cp-trial-quick-onboarding.html>).
2. Choose a region that offers Integration Suite. This trial uses **US East (us10, Cloud Foundry)**.
3. Subaccount name: `leinewerk-dev` (see deviations). Cloud Foundry space: `dev`.
4. Calendar reminders: **day 25** (extend the trial before suspension at day 30) and day 85 (trial deleted at day 90; export everything to git first).

## Step 2: Subscribe to Integration Suite

1. Follow <https://developers.sap.com/tutorials/cp-starter-isuite-onboard-subscribe.html>.
2. *Instances and Subscriptions → Create* → *Integration Suite*, plan `trial`.
3. Assign yourself the role collection `Integration_Provisioner`.
4. Open the Integration Suite app → *Add Capabilities* → *Build Integration Scenarios* (Cloud Integration). API Management is not needed for P2.
5. Wait until the status shows active.

## Step 3: Assign Cloud Integration roles

In the subaccount, *Security → Users → your user → Assign Role Collection*:

- `PI_Administrator`
- `PI_Business_Expert`
- `PI_Integration_Developer`

Log out and back in so the roles take effect.

## Step 4: Sender credentials (service key)

1. *Instances and Subscriptions → Create*: service *Process Integration Runtime*, plan `integration-flow`.
2. Roles: `ESBMessaging.send`. Grant type: *Client Credentials*.
3. Create a **service key** for the instance and copy the JSON.
4. Save it as `.env` in the repo root (git-ignored). Keep it as the service-key JSON only; `clientid`, `clientsecret`, `tokenurl` and `url` are the fields used.
5. Never commit it (principle 9). Store a copy in the password manager.

Tool note: the CAP CLI (`cds`) parses a `.env` file in the current directory and fails on this JSON. Run `cds` only inside the CAP repo `lw-s4-standin`, never in this repo.

## Step 5: HANA Cloud trial instance (for the stand-in)

1. In the cockpit, open *SAP HANA Cloud* (trial) and create an instance, plan `hana-free`, allow access from all IP addresses (trial only).
2. Store the admin password in the password manager, not in `.env`.
3. The trial instance **stops every night**. Starting it is step 1 of every test routine.
4. For local development the stand-in uses SQLite; HANA is needed only for the deployed app.

## Step 6: Check JMS

1. Cloud Integration → *Monitor → Manage Stores → Message Queues*.
2. **Result: this tenant offers JMS queues.** Phase 6 therefore uses the JMS retry pattern (queue `LW_EI_INBOUND`, retries with exponential back-off, dead-letter handling). The Data Store fallback is not needed. Recorded in ADR 0005.

## Step 7: Install local tools

macOS (arm64) as used here; adapt the package manager on other systems.

| Tool | Version used | Install |
| --- | --- | --- |
| Node.js | 24.x (LTS) | <https://nodejs.org> or `brew install node` |
| `@sap/cds-dk` | 10.2.1 | `npm i -g @sap/cds-dk` |
| CF CLI | 8.19.0 | `brew install cloudfoundry/tap/cf-cli` |
| CF MultiApps plugin | 3.12.0 | `cf install-plugin multiapps` |
| `mbt` | 1.2.51 | `npm i -g mbt` |
| Java | OpenJDK 21 | `brew install openjdk@21` |
| Git | 2.x | preinstalled |
| REST client | Postman (Bruno also fine) | already installed |
| KoSIT validator | 1.5.0 | see below |
| XRechnung configuration | 3.0.2, release 2024-06-20 | see below |

**Java on the PATH.** Homebrew does not link `openjdk@21`. Add to `~/.zshrc`:

```bash
export PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH
```

**KoSIT validator and configuration** (downloads from GitHub releases; unpack each in its own empty directory):

```bash
mkdir -p ~/tools/kosit/validator ~/tools/kosit/config && cd ~/tools/kosit
curl -L -o validator/v.zip https://github.com/itplr-kosit/validator/releases/download/v1.5.0/validator-1.5.0-distribution.zip
curl -L -o config/c.zip https://github.com/itplr-kosit/validator-configuration-xrechnung/releases/download/release-2024-06-20/validator-configuration-xrechnung_3.0.2_2024-06-20.zip
(cd validator && unzip -q v.zip) && (cd config && unzip -q c.zip)
```

Newer 3.0.2 bundles exist (2026-01-31, 2026-08-31). The guide's results for TC01/02/05/06 were verified with 2024-06-20, so we pin it; evaluate newer bundles separately.

**Verify** (use absolute paths for the invoices):

```bash
cd ~/tools/kosit/config
java -jar ../validator/validationtool-1.5.0-standalone.jar -s scenarios.xml -r "$PWD" -o <outdir> <absolute paths to invoices>
```

Expected: TC01 and TC02 ACCEPTABLE, TC05 REJECT (`BR-DE-15`), TC06 REJECT (`BR-CO-16`).

**Cloud Foundry login** (needed from Phase 2 on):

```bash
cf login -a https://api.cf.us10-001.hana.ondemand.com --sso
cf target -o <org> -s dev
```

`cf target` must show the org and space of *this* trial. After creating a new trial, log in again; a stale session points at the old org.

Not installed yet (needed later): Saxon-HE (Phase 8 XSLT tests), Groovy/Spock via Gradle or Maven (Phase 8), Mustang CLI (TC03 PDF).

## Step 8: Smoke test

1. In Cloud Integration create a package and an iFlow with an **HTTPS sender**: address `/hello`, *Authorization* **User Role**, role `ESBMessaging.send`, **CSRF Protected off**. Behind it a Content Modifier with body `Hello from Leinewerk!`.
2. Save and deploy. Wait for status *Started* (*Monitor → Manage Integration Content*).
3. Get a token (client credentials) and call the iFlow:

```bash
# values from .env: tokenurl, clientid, clientsecret, url
TOKEN=$(curl -s -u "$CLIENTID:$CLIENTSECRET" -d grant_type=client_credentials "$TOKENURL" | jq -r .access_token)
curl -i -H "Authorization: Bearer $TOKEN" "$URL/http/hello"
curl -i -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: text/plain" -d Hello "$URL/http/hello"
```

4. Expect `200` and `Hello from Leinewerk!`. Check the completed message in *Monitor → Monitor Message Processing*.
5. **Undeploy** the iFlow (*Monitor → Manage Integration Content → Undeploy*).

Result 2026-11-22: GET and POST returned 200; flow undeployed afterwards.

Troubleshooting:

| Symptom | Cause | Fix |
| --- | --- | --- |
| 404 | iFlow not deployed, wrong path, or wrong tenant host | Check *Manage Integration Content* and the `url` in `.env` |
| 403 | Sender refuses the token | Authorization must be *User Role* with `ESBMessaging.send`; CSRF off; redeploy |
| 500 on POST only | Request sent with the wrong `Content-Type` (clients such as Python `urllib` default to `application/x-www-form-urlencoded`) | Always send an explicit `Content-Type` (`text/plain`, `application/xml`, `application/pdf`) |
| Token error | Service key from a different tenant | Recreate the key in the active subaccount and replace `.env` |

## Rebuild checklist

- [ ] Trial created, subaccount and space `dev`, reminders set
- [ ] Integration Suite subscribed, Cloud Integration active
- [ ] Roles assigned, re-login done
- [ ] Process Integration Runtime service key saved in `.env`
- [ ] HANA Cloud instance created (stops nightly)
- [ ] JMS result checked and recorded in ADR 0005
- [ ] Tools installed, Java on PATH, KoSIT results match the expected table
- [ ] `cf target` shows the current org and space
- [ ] Hello iFlow returns 200 and is undeployed
