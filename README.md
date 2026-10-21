# Inkomoko USSD Client Self-Service Platform (*662*105# pilot, *311# later)

Spring Boot 4 / Java 21 / PostgreSQL. The service implements the menu from the FDI Technical Proposal (§3.3). It connects to the FDI USSD gateway through the JSON contract in *FDI USSD Gateway Integration*. The code structure follows the `docs/sample-ussd` classes: a single `UssdController`, a `UssdSession` state row, and per-service menu classes under `utils/menus`.

## Admin portal (`/portal`)

This is a React app in `frontend/`, served by Spring Boot at **`/portal/`**. The Docker build compiles it and packs it into the JAR, so it deploys together with the backend. The portal carries the Inkomoko logo (`frontend/public/logo.png`) on white surfaces. The primary colour is Inkomoko navy `#00205F`, with the logo's coral `#EA4E35` and sky `#5DC5E9` used only as accents, plus white surfaces and charcoal text. Headings use Montserrat and body text uses Inter; both are bundled, so no external font requests are made.

* **Seeded administrator.** On the first start of a new database, a Super Administrator is created: username `admin`, e-mail `admin@yopmail.com`, password `Admin@2026!`. Override these with `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`, using the `_STAGING` suffix in GitLab for staging. The password must be changed at first sign-in. Staff sign in with their username **or** e-mail.
* **Country setup.** At first sign-in, the Super Administrator chooses the country this installation serves (Rwanda, Kenya, Ethiopia, South Sudan or Chad templates). They confirm its languages and their order, currency, repayment limits, helpline and mobile networks. The settings can be changed later under *Countries*.
* **Sign-in.** Sessions use JWTs. Failed attempts lock the account. A password change is forced after first sign-in, after a reset and when the password expires. Tokens issued before a password change stop working.
* **Password policy** (Settings): minimum length; upper, lower, digit and special characters; expiry; history; lockout threshold and duration; session length.
* **Privileges and roles.** Every action is a privilege (`EPrivilege`, 20 of them). Roles are built from privileges in *Roles & privileges* and assigned to users. The seeded roles are Super Administrator (system role, all privileges), Operations Officer, Finance Checker, Support Agent and Viewer.
* **Customers.**
  * Search the USSD customers.
  * **Onboard** a client: look them up in core banking by phone, see their loans, confirm their ID was verified, and send an SMS invitation.
  * Reset or unlock a PIN, and disable or enable access.
  * **Registration is staff-only.** A client can use USSD only after staff onboard them; there is no self-registration on first dial. After onboarding, the client creates their own PIN on their first dial. Unknown or un-onboarded numbers are told to visit an Inkomoko office.
* **Other pages:**
  * dashboard (sessions, completion and abandonment rates, response time, PIN failures, repayments by status)
  * repayments with reconciliation proposals (maker)
  * approvals (checker)
  * help and FAQ content
  * demo core banking (pilot only)
  * users
  * countries
  * audit log

**Local development:**

```bash
cd frontend && npm install
npm run dev            # http://localhost:5173/portal/ (proxies /api to :9094)
npm run build          # outputs to src/main/resources/static/portal (gitignored) for ./mvnw spring-boot:run
```

## Run with Docker Compose

The app runs in Docker. PostgreSQL does not: use the Postgres already installed on your machine. The image builds the admin portal into the same process, so you do not run `npm` first. The first build compiles the portal and the Java app and takes several minutes. Later starts reuse that image.

Core banking stays in **demo** mode, and payments and SMS stay in **mock** mode, so the simulator and portal work without Fineract or a payment gateway.

1. Create the database (once), if it does not exist yet:

```bash
createdb -U postgres inkomoko_db
```

2. From `mobilebanking/`, copy the env file and set the Postgres user and password you actually use:

```bash
cp .env.example .env
```

`.env` uses `localhost:5432`. Compose runs the app on the host network, the same way staging does, so that address is the Postgres installed on the machine. The same file works for `./mvnw spring-boot:run`. Postgres can keep its default local-only setup.

On Docker Desktop for Mac or Windows, turn on host networking once: **Settings → Resources → Network → Enable host networking**, then Apply & restart. Docker Engine on Linux already uses the host network.

3. Start the app:

```bash
docker compose up --build
```

Flyway creates the schema on the first start. Sign in to the portal as `admin` / `admin@yopmail.com` with password `Admin@2026!` (you must change it at first sign-in).

* USSD simulator: http://localhost:9094/simulator.html
* Admin portal: http://localhost:9094/portal/
* Swagger UI: http://localhost:9094/swagger-ui.html

Stop with `docker compose down`. That does not delete the database.

## Run locally without Docker

```bash
createdb -U postgres inkomoko_db          # user/password: postgres/postgres (application.properties)
./mvnw spring-boot:run                    # Flyway creates the schema on first start
```

* USSD simulator: http://localhost:9094/simulator.html
* Admin portal: http://localhost:9094/portal/ (run `npm run build` in `frontend/` first)
* Swagger UI: http://localhost:9094/swagger-ui.html

By default core banking runs in **demo** mode (`inkomoko.fineract.mode=demo`), and the payment gateway and SMS run in **mock** mode. The whole flow works without external systems:

| Test phone     | Demo client (seeded by `V3__demo_core_banking.sql`)        |
|----------------|-----------------------------------------------------------|
| 250788000001   | 1 active loan (+1 settled); the first dial goes through PIN setup |
| 250788000002   | 2 repayable loans (one in arrears) + 1 under review       |
| 250722000003   | Airtel number, 1 approved loan pending disbursement       |
| 250788000004   | inactive client                                           |
| `DEMO_ALLOWED_MSISDNS` | registered at startup, each with an active loan and a loan in arrears |
| any other      | not registered: the session ends right after the language screen |

A mock payment confirms automatically after 5 s. Amounts ending in **13** (e.g. 1013) fail, so you can test the failure path. SMS messages are written to the log. In demo mode, repayments reduce the demo loan balances exactly as a Fineract posting would.

## Demo data API (pilot only)

These endpoints are only available while `inkomoko.fineract.mode` is not `live`.

```bash
# get a token: POST /api/auth/login {"username":"...","password":"..."} -> data.token
H='-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"'
# allow a phone (adds an active loan + a loan in arrears unless "sampleLoans": false)
curl -X POST $BASE/api/admin/demo/clients $H -d '{"msisdn":"250788683008","displayName":"Stanley M"}'
# custom loan
curl -X POST $BASE/api/admin/demo/clients/250788683008/loans $H \
  -d '{"productName":"Agri Loan","status":"ACTIVE","principalOutstanding":500000,"interestOutstanding":25000,"totalOverdue":0,"installment":45000,"nextDueDate":"2026-10-15"}'
# change balances / status (e.g. push into arrears)
curl -X PUT $BASE/api/admin/demo/loans/7 $H -d '{"status":"IN_ARREARS","totalOverdue":45000}'
# repayment history line for the mini statement (applyToBalance=true also reduces the balance)
curl -X POST $BASE/api/admin/demo/loans/7/repayments $H -d '{"amount":45000,"date":"2026-09-15"}'
```

| Method | Path | Body |
|---|---|---|
| GET | `/api/admin/demo/clients` | lists the allowed numbers with their loans |
| POST | `/api/admin/demo/clients` | `msisdn`, `displayName?`, `active?`, `sampleLoans?` (default true) |
| GET / PUT / DELETE | `/api/admin/demo/clients/{msisdn}` | PUT: `displayName?`, `active?`. DELETE also removes the loans and history |
| POST | `/api/admin/demo/clients/{msisdn}/sample-loans` | adds the standard sample portfolio |
| POST | `/api/admin/demo/clients/{msisdn}/loans` | `accountNo?`, `productName`, `status`, `principalOutstanding`, `interestOutstanding`, `totalOverdue`, `installment`, `nextDueDate` |
| PUT / DELETE | `/api/admin/demo/loans/{loanId}` | PUT accepts the same fields; null fields are left unchanged |
| GET / POST | `/api/admin/demo/loans/{loanId}/repayments` | `amount`, `date?`, `reference?`, `applyToBalance?` (default false) |

The loan `status` can be `UNDER_REVIEW`, `APPROVED`, `ACTIVE`, `IN_ARREARS`, `SETTLED`, `REJECTED`, `WITHDRAWN` or `CLOSED`. To let a tester start over with a new PIN, use `POST /api/admin/pins/{msisdn}/reset`.

## Demo -> production switch (*662*105# -> *311#)

The demo only replaces **one component**, the core-banking adapter behind the `FineractService` interface. Everything else runs the same code in both modes, so none of the pilot work is thrown away:

| Kept as-is at the switch | Swapped at the switch |
|---|---|
| USSD engine, screens and translations, input parsing | `DemoFineractService` -> `FineractRestService` (`FINERACT_MODE=live`) |
| consent, language, PIN setup/lockout, sessions | mock payment gateway -> Efashe gateway (`PAYMENT_MODE=live`) |
| repayment ledger, idempotency, reconciliation (maker/checker) | mock SMS -> Efashe SMS (`SMS_MODE=live`) |
| audit log, FAQ admin, dashboard, CI/CD, DB schema | `USSD_SERVICE_CODE` `662*105` -> `311` |

**Environments.** Staging (`master`) stays the permanent demo/UAT environment: `*662*105#`, demo mode, its own database. Production (`main`) is live only, with `*311#` and Fineract. The `deploy-prod` job refuses any `*_MODE` other than `live` unless `ALLOW_DEMO_IN_PRODUCTION=true`, and the demo endpoints don't exist there. Demo data therefore never reaches the production database.

**Switch checklist:**
1. **Fineract sandbox.** Point staging at the Fineract sandbox (`FINERACT_MODE_STAGING=live` plus the credentials) and run the same UAT scripts used for the demo. Confirm that client search by phone, the loan status mapping, next due date / installment and the repayment posting (`paymentTypeId`, `externalId`) match what the demo showed. Anything that differs is fixed in `FineractRestService` or `ELoanStatus.fromFineract`; the menus don't change.
2. **Payments and SMS.** Switch `PAYMENT_MODE_STAGING` and `SMS_MODE_STAGING` to `live` against Efashe test credentials and test a real MoMo repayment end to end, including the callback URL.
3. **Production variables.** Set them in GitLab: DB, Fineract, payment, SMS, `PAYMENT_CALLBACK_TOKEN`, `SUPPORT_HELPLINE`. `USSD_SERVICE_CODE` defaults to `311`.
4. **Go-live.** Merge to `main`, then ask FDI to route `*311#` to the production URL. Pilot users set their PIN again on first `*311#` dial. PINs are stored per environment, and a PIN is also reset automatically if the client ID behind a number changes.
5. **Optional rollback.** Keep `*662*105#` pointed at staging during hypercare, so the demo stays available for training.
6. **After go-live.** The `demo_*` tables stay empty and unused in production. They can be dropped later with a new migration once the pilot is formally closed.

## Gateway request format

The FDI gateway POSTs the full dial string on every step. Each answer is appended with `*`, so the last segment is the latest answer:

```json
{ "sessionId": "1744384620034994445", "gwRef": "1744384620049945|250788499473|20250411", "gwTstamp": "",
  "networkCode": "RW|MTN", "serviceCode": "RW|MTN|662|105", "userInput": "*662*105*1*1#",
  "serviceId": "...", "msisdn": "250785642275", "tenantId": "..." }
```

| userInput | Answers | Latest |
|---|---|---|
| `*662*105#` | none, new session | shows the language screen |
| `*662*105*1#` | `1` | `1` (Kinyarwanda, so the T&C screen is shown in Kinyarwanda) |
| `*662*105*1*1#` | `1, 1` | `1` (T&C accepted, then PIN) |

**Service code:** `inkomoko.ussd.service-code` (env `USSD_SERVICE_CODE`, `USSD_SERVICE_CODE_STAGING` on staging) is `662*105` for the pilot. Change it to `311` once Inkomoko's shortcode is configured; no code change is needed. The gateway's `serviceCode` (`RW|MTN|662|105` → `662*105`) must match it, or the request is rejected with HTTP 400.

**Retries:** a repeated identical `userInput` in the same session is a gateway retry. The previous screen is replayed instead of being processed twice.

## USSD flow

```
*662*105# → Screen 1 language (1 Kinyarwanda / 2 English / 3 Francais)
      → Screen 2 Terms & Conditions / privacy consent in the chosen language (1 Accept / 2 Exit)
      → MSISDN matched to Fineract client (not registered / inactive → end)
      → Screen 2a PIN setup + confirm (first time)  |  Screen 2b PIN entry (3 attempts → locked)
      → MAIN MENU
          1 Check Balance   principal, total, overdue, next due date, installment (live from Fineract)
          2 Loan Status     Under Review / Approved-Pending Disbursement / Active / In Arrears / Settled
          3 Mini Statement  last 5 repayments + "1" = full statement by SMS
          4 Repay Loan      amount (min RWF 500, ≤ outstanding) → confirm → MoMo push → SMS receipt
          5 Help / FAQs     content from faq_item table (editable via admin API)
          0 Exit
Inside a service: 0 = back, 00 = main menu.
```

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/ussd` | FDI gateway entry point (`sessionId, gwRef, msisdn, networkCode, serviceCode, userInput…` → `text, continueSession, appRef, appTstamp`) |
| POST | `/api/payments/callback` | Payment gateway result (header `X-Callback-Token`) |
| GET | `/api/admin/reports/summary?from=&to=` | Dashboard: sessions, completion/abandonment %, avg response ms, login failures, locks, repayments by status |
| GET | `/api/admin/repayments?status=` | Repayment monitoring |
| POST | `/api/admin/repayments/{ref}/reconcile` | **Maker** proposes REPOST / MARK_POSTED / MARK_FAILED / MARK_REVERSED |
| POST | `/api/admin/reconciliation-actions/{id}/approve` `/reject` | **Checker**, who must be a different user, approves or rejects the proposal |
| GET/POST | `/api/admin/pins/{msisdn}` `/reset` `/unlock` | Support desk PIN operations (after identity verification) |
| GET/POST/PUT/DELETE | `/api/admin/faqs` | Help/FAQ content |
| GET | `/api/admin/messages?country=` | USSD/SMS texts: default, override and effective text per language (`country` = code, `*` = all countries, omitted = active country) |
| POST | `/api/admin/messages/{key}/check` | Validates a draft text (placeholders, GSM-7, length) and returns a preview |
| PUT/DELETE | `/api/admin/messages/{key}/{language}?country=` | Saves an override / reverts to the default |
| GET | `/api/admin/messages/{key}/history` | Who changed a text, when, from what to what |
| GET/POST/PUT/DELETE | `/api/admin/languages` `/{code}` | USSD languages with translation progress and the countries offering them |
| GET | `/api/admin/audit-logs?actor=` | Audit trail |

Every `/api/admin/**` endpoint needs a portal login (`Authorization: Bearer <token>` from `POST /api/auth/login`) and the privilege for that action. Actions are audited under the signed-in username; the maker and checker of a reconciliation must be different users.

## USSD & SMS texts (internationalisation)

All customer-facing wording lives in `src/main/resources/i18n/ussd_<lang>.properties` (`rw`, `en`, `fr`), not in Java code:

* Each key is a whole screen or sentence with named placeholders, for example `pin.wrong=Wrong PIN. {remaining} attempt(s) left.`. Apostrophes need no escaping.
* `MessageService` resolves texts in this order: portal override for the active country → portal override for all countries (`*`) → bundle for the language → English bundle.
* `CommonHeadings` and the menus only compose screens (line breaks, "0. Back").
* The consent version stored with every consent record is `<lang>-<hash of the notice text>`, so any wording change is traceable automatically.
* Staff with `MESSAGE_MANAGE` edit texts under **Content → USSD & SMS texts** in the portal. Edits are live on the node that saved them at once, and on other nodes within `inkomoko.messages.refresh-ms`. Every change is kept in `ussd_message_history`, and "Revert" always restores the shipped default.

`UssdMessageBundleTest` runs in the build and the portal applies the same rules (`MessageRules`) on save:

* Every language file has the same keys and the same `{placeholders}` as English.
* Characters are GSM-7 only. An accent outside GSM-7 switches the screen to UCS-2 and cuts it to about 70 characters.
* Every USSD screen, including composed ones (error + re-prompt, statement rows, loan lists), fits in 182 characters with the longest realistic values (`MessageRules.SAMPLE_VALUES`).

### Adding a language (no release needed)

Kinyarwanda (`rw`), English (`en`) and French (`fr`) ship as bundles. Any other language is data (table `ussd_language`):

1. **Administration → Languages → Add language.** Enter the ISO code (e.g. `sw`), the name customers see on the language screen (e.g. `Kiswahili`), and the character set:
   * GSM-7: Latin letters without most accents, 182 characters per screen.
   * UCS-2: any script (Arabic, Amharic), about 80 characters per screen.
2. **Translate every text.** You land on **Content → USSD & SMS texts** in translation mode:
   * English is shown as the reference, with "Not translated only" and "Next untranslated text".
   * Translations are saved for all countries (`ussd_message`, `country_code='*'`) and checked with the same rules as the bundles, using that language's character set.
3. **Offer it in a country (Administration → Countries).** A country can only add a language once every text is translated (`LanguageService.requireReady`), so customers never see a half-translated menu.

Other rules:

* Built-in languages can be edited but not deleted.
* A language offered by a country can be neither deactivated nor deleted.
* Language management needs `LANGUAGE_MANAGE`.
* A UCS-2 language also needs the FDI gateway and MNOs to deliver UCS-2 USSD. Test on real handsets before going live.

## Repayment & reconciliation

`INITIATED → PENDING` (MoMo push sent) `→ PAID` (gateway confirmed) `→ POSTED` (Fineract repayment, SMS receipt).
`FAILED` = declined or timed out (the client was not charged). `POSTING_FAILED` is retried every minute, up to 5 attempts. After that, or when a payment stays unconfirmed for 60 min, or the paid amount doesn't match, the repayment moves to `NEEDS_REVIEW` for maker/checker reconciliation.

Duplicate protection works in three layers:

* A row lock is taken on every state change.
* Callbacks are idempotent.
* Only one pending push is allowed per phone at a time.

The repayment reference is also sent to Fineract as the transaction `externalId`. The service checks for an existing transaction with that ID before posting.

## Security

* PINs are stored as bcrypt hashes only. During first-time setup, only the hash of the first entry is kept between screens.
* `userInput` is never logged.
* The DB-level trigger makes `audit_log` append-only.
* If a number is reassigned to a different Fineract client, the old PIN is invalidated.
* Admin API: JWT sign-in (HS256, `JWT_SECRET`), one privilege per action, and a configurable password policy (see *Admin portal*).

## CI/CD (GitLab)

This pipeline follows the loan-service pipeline. It uses the same shell-executor runner tags and Docker BuildKit registry cache, and deploys with docker compose (`network_mode: host`).

| Branch | Jobs (runner tag) | Target |
|---|---|---|
| `master` | `build` -> `deploy` (`staging`) | host PostgreSQL `localhost:5432/inkomoko_db`, created if missing |
| `main` | `build-prod` -> `deploy-prod` (`production`) | `DB_HOST` (default `10.0.0.4`) `/inkomoko_db`, which must already exist |

* **Image:** `registry.gitlab.com/efashe2/efashe-wallet/inkomoko-ussd:<branch-slug>`. The pipeline also pushes `:latest` and a tag for the short commit SHA.
* **Container:** `inkomoko-ussd-service`, listening on port **8093** (`APP_PORT` in `.gitlab-ci.yml`; loan-service uses 8089). The health check is `/actuator/health`.
* **Staging variables:** use the `_STAGING` suffix, e.g. `DB_PASSWORD_STAGING` or `FINERACT_MODE_STAGING`. Integrations default to `mock`. When a mode is set to `live`, its credentials are required.
* **Production variables:**
  * Always required: `DB_USERNAME`, `DB_PASSWORD`, `PAYMENT_CALLBACK_TOKEN`, `JWT_SECRET` (at least 32 characters; staging uses `JWT_SECRET_STAGING`).
  * Integrations default to `live`, which also requires `FINERACT_BASE_URL/USERNAME/PASSWORD`, `PAYMENT_BASE_URL/API_KEY/CALLBACK_URL` and `SMS_BASE_URL/API_KEY`.
  * Optional: `DB_HOST`, `DB_PORT`, `DB_NAME`, `FINERACT_TENANT_ID`, `FINERACT_PAYMENT_TYPE_ID`, `SUPPORT_HELPLINE`, `*_MODE`.
* **Logs:** written to `/opt/tomcat/logs/inkomoko-ussd.log` on the host.

## Before go-live (open items)

1. **Fineract**: see the switch checklist above. Set `inkomoko.fineract.mode=live`, plus `base-url`, credentials and `payment-type-id`. Confirm against the sandbox that client search by mobile number (`/search?resource=clients`) and the loan status mapping (`ELoanStatus.fromFineract`) match Inkomoko's setup.
2. **Payment gateway / SMS**: the paths and fields in `EfashePaymentGatewayService`, `EfasheSmsService` and `PaymentCallbackRequest` are provisional. Align them with the Efashe merchant API.
3. **Content**: the helpline (`inkomoko.support.helpline`) and the FAQ office addresses (seeded by `V2__seed_faq.sql`) are placeholders. The Kinyarwanda and French wording needs native-speaker review.
4. **Secrets**: move the DB password, API keys and callback token out of `application.properties` (use env vars or a vault).
5. The Redis starter is on the classpath but unused. Its health check is disabled.
