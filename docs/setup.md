# Setup: rebuild the P2 trial environment

Goal: rebuild the BTP trial, Integration Suite trial and local tools in about an hour (bible §15, rule 1).
Nothing secret goes in this file. Secrets live in the git-ignored `.env` (service key only) and in the password manager.

Last verified: 2026-11-23 (Phase 1 complete; Step 9 = Phase 4, TC01, TC02, TC05 and TC06 verified).

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
| 9 | Build the Phase 4 iFlows (receive, detect, validate) | Terminal + Cloud Integration | TC01/TC02 return 200, TC05/TC06 return 422 |

## Deviations from the guide

| Guide | What we did | Why / consequence |
| --- | --- | --- |
| Subaccount display name `leinewerk-dev` (bible §16) | Kept the default name `trial` | Trial subaccount was created with the default name. Cosmetic; no setting depends on it. Rename in the cockpit under *Subaccount → Overview → Edit* if you want §16 compliance. In a new trial, name it `leinewerk-dev` from the start. |
| Roles to IAS groups (bible §12) | Roles assigned to the user directly | The trial has no group setup; note this in the README. |

## Step 1: Create the BTP trial

1. Open <https://www.sap.com/products/technology-platform/trial.html> and start the trial (onboarding: <https://developers.sap.com/tutorials/cp-trial-quick-onboarding.html>).
2. Choose a region that offers Integration Suite. This trial uses **US East (us10, Cloud Foundry; API endpoint `api.cf.us10-003`, check yours in the cockpit)**.
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
cf login -a https://api.cf.us10-003.hana.ondemand.com --sso
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

## Step 9: Phase 4 iFlows (receive, detect the format, validate)

Needs Steps 1 to 8. The package `LW_EInvoicing_Inbound` is exported as `iflow/LW_EInvoicing_Inbound.zip` (4 artifacts: the core iFlow, the HTTPS entry iFlow, the value mapping `LW_EI_ValueMappings` and the test iFlow `LW_Test_S4_Read`). Import it (*Design → Import*) to skip the clicking, then redo the test in 9.4. The core iFlow in the export already contains the 6 scripts (identical to `src/groovy/`), the 18 XSDs, the 4 XSLTs, the 3 JARs and `LW_MaxPayloadMB`=10; no credentials are inside. The rest of this step is the manual rebuild.

### 9.1 Prepare the files locally

```bash
./scripts/fetch-tools.sh                      # Saxon, Groovy, PDFBox, KoSIT config into .tools/
python3 scripts/build-cpi-resources.py        # flattened XSDs and XSLTs into build/cpi-resources/
./test/unit/run.sh                            # 4 Schematron checks + 29 Groovy tests, all green
```

`build/cpi-resources/` holds `xsd-ubl/` (14 files), `xsd-cii/` (4 files) and `xslt/` (4 files). The scripts to paste are in `src/groovy/`.

### 9.2 Core iFlow `LW_Supplier_to_S4_SupplierInvoice`

1. *Design → your package → Add → Integration Flow*. Sender adapter **ProcessDirect**, address `/LW/EI/Core`.
2. *Resources → Add → Archive*: upload `.tools/jars/pdfbox-2.0.30.jar`, `fontbox-2.0.30.jar` and `commons-logging-1.2.jar` (PDFBox 2.x needs commons-logging; the exported iFlow includes all three).
3. *Resources → Add → Schema → XSD*: upload **all 14** files from `build/cpi-resources/xsd-ubl/` and all 4 from `xsd-cii/`. Imports resolve by relative name, so a missing file fails with `Cannot find resource: /xsd/<name>`.
4. *Resources → Add → XSLT Mapping*: upload the 4 files from `build/cpi-resources/xslt/` (they are stored under `src/main/resources/mapping/` in the iFlow).
5. Main flow, in this order. Every `${...}` value needs **Type = Expression** (a Constant sends the literal text).

   | # | Step | Settings |
   | --- | --- | --- |
   | 1 | Content Modifier "Init" | Properties `ei.receivedAt` = `${date:now:yyyy-MM-dd'T'HH:mm:ss}`, `ei.channel` = `${header.EI_Channel}`, `ei.fileName` = `${header.EI_FileName}`, `ei.maxMB` = `{{LW_MaxPayloadMB}}`; header `SAP_MessageType` = `EInvoice` |
   | 2 | Groovy | `CheckSize.groovy` |
   | 3 | Data Store *Write* | `LW_EI_Originals`, Visibility **Global**, Entry ID `${header.SAP_MessageProcessingLogID}`, retention 30 days |
   | 4 | Groovy | `ExtractZugferd.groovy` |
   | 5 | Groovy | `DetectFormat.groovy` |
   | 6 | Groovy | `ProfileAllowList.groovy` |
   | 7 | Content Modifier "Stash XML" | Property `ei.xml` = `${in.body}` |
   | 8 | Router | Route 1 **Non-XML** condition `${property.ei.syntax} = 'UBL'` calls `Validate_UBL`; default route calls `Validate_CII`; both end in a **Join** |
   | 9 | Groovy | `EvaluateSvrl.groovy` |
   | 10 | Content Modifier (interim reply) | Body (Expression) `{"result":"VALID","invoiceNumber":"${property.ei.invoiceNumber}","syntax":"${property.ei.syntax}","mode":"${property.ei.mode}"}`; header `Content-Type` = `application/json` |

6. Local processes: *Process → Local Integration Process*, dropped **outside** the main pool and named `Validate_UBL` and `Validate_CII`. In the main flow, *Call → Local Call → Local Integration Process* is the Process Call; its dropdown lists the local processes by name.

   `Validate_UBL`: XML Validator `/xsd/UBL-Invoice-2.1.xsd` → XSLT Mapping `EN16931-UBL-validation.xsl` → Content Modifier "Stash EN" (property `ei.svrlEn` = `${in.body}`, body = `${property.ei.xml}`, both Expression) → Router (Non-XML `${property.ei.mode} = 'XRECHNUNG'` to the next two steps, default route straight to the Join) → XSLT Mapping `XRechnung-UBL-validation.xsl` → Content Modifier "Stash XR" (property `ei.svrlXr` = `${in.body}`, body = `${property.ei.xml}`) → Join → End.

   `Validate_CII`: the same with `/xsd/CrossIndustryInvoice_100pD16B.xsd`, `EN16931-CII-validation.xsl`, `XRechnung-CII-validation.xsl`.
7. **Exception Subprocess**: Error Start → Groovy `ClassifyError.groovy` → **End Message**. This returns the 422, 202 or 500 JSON to the caller.
8. *Configure*: externalise `LW_MaxPayloadMB` = `10`. Save and deploy.

### 9.3 Entry iFlow `LW_SupplierHTTPS_to_CPI_EInvoice`

1. Sender **HTTPS**, address `/lw/einvoice/inbound`, authorization *User Role* `ESBMessaging.send`, **CSRF Protection off**.
2. Content Modifier: headers `EI_Channel` = `HTTPS`, `EI_FileName` = `upload`.
3. *Request Reply* with a **ProcessDirect** receiver, address `/LW/EI/Core`.
4. Save and deploy.

### 9.4 Test

POST the file as the raw body (Postman: Body → binary) with `Authorization: Bearer <token>` and an explicit `Content-Type` (`application/xml`, or `application/pdf` for ZUGFeRD) to `<url>/http/lw/einvoice/inbound`. The token comes from the same service key as in Step 8.

| File | Result |
| --- | --- |
| `TC01_xrechnung_ubl_po.xml` | 200, `"syntax":"UBL"`, `"mode":"XRECHNUNG"` |
| `TC02_xrechnung_cii_po.xml` | 200, `"syntax":"CII"` |
| `TC05_missing_buyer_reference.xml` | 422 `EI_RULES`, `BR-DE-15` |
| `TC06_wrong_payable_amount.xml` | 422 `EI_RULES`, `BR-CO-16` |
| `<Order xmlns="urn:oasis:names:specification:ubl:schema:xsd:Order-2"/>` | 422 `EI_UNKNOWN_FORMAT` |

Also check *Monitor Message Processing*: searching for the invoice number (`SAP_ApplicationID`) finds the message.

Result 2026-11-23: TC01, TC02, TC05 and TC06 as expected. Not yet checked: `EI_SCHEMA`, PDF cases, credit note.

### 9.5 Export

After changes, download the package from *Design* and replace `iflow/LW_EInvoicing_Inbound.zip`. Set the log level back to **Info** (Trace stores payloads).

### 9.6 Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Cannot find resource: /xsd/UBL-CommonAggregateComponents-2.1.xsd` | Only the main XSD was uploaded | Upload all 14 files from `xsd-ubl/` (and the 4 from `xsd-cii/`) |
| `Invalid xpath: ${exchangeProperty.ei.mode} = ...`, `expected "<name>", found "{"` | Router **Condition Type** is XML | Set Non-XML on every route condition |
| `Unexpected character '$' (code 36) in prolog` in an XSLT step | Content Modifier body is a Constant, so the XSLT got the text `${property.ei.xml}` | Body Type = Expression |
| TC05 or TC06 return 200 | SVRL empty: wrong or missing XSLT, property stored as Constant, or the Join not wired to `EvaluateSvrl` | Trace: *Message Content* after the XSLT step must contain `svrl:failed-assert`; check `ei.svrlEn` and `ei.svrlXr` |
| 403 | CSRF protection on, or role missing | CSRF off in the HTTPS sender; key needs `ESBMessaging.send` |

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
- [ ] Phase 4: both iFlows deployed, TC01/TC02 return 200, TC05/TC06 return 422, log level back to Info, package exported to `iflow/`
