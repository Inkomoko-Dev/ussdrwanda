# Inkomoko USSD Client Self-Service Platform (*662*105# pilot, *311# later)

Spring Boot 4 / Java 21 / PostgreSQL. The service implements the menu from the FDI Technical Proposal (§3.3). It connects to the FDI USSD gateway through the JSON contract in *FDI USSD Gateway Integration*. The code structure follows the `docs/sample-ussd` classes: a single `UssdController`, a `UssdSession` state row, and per-service menu classes under `utils/menus`.

## Run locally

```bash
createdb -U postgres inkomoko_db          # user/password: postgres/postgres (application.properties)
./mvnw spring-boot:run                    # Flyway creates the schema on first start
```

* USSD simulator: http://localhost:8080/simulator.html
* Swagger UI: http://localhost:8080/swagger-ui.html

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

These endpoints are only available while `inkomoko.fineract.mode` is not `live`. Like the other admin endpoints, they need the `X-API-KEY` and `X-Admin-User` headers.

```bash
H='-H "X-API-KEY: $KEY" -H "X-Admin-User: stanley" -H "Content-Type: application/json"'
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
3. **Production variables.** Set them in GitLab: DB, Fineract, payment, SMS, `ADMIN_API_KEYS`, `PAYMENT_CALLBACK_TOKEN`, `SUPPORT_HELPLINE`. `USSD_SERVICE_CODE` defaults to `311`.
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
| `*662*105#` | none, new session | shows the consent screen |
| `*662*105*1#` | `1` | `1` |
| `*662*105*1*1#` | `1, 1` | `1` |

**Service code:** `inkomoko.ussd.service-code` (env `USSD_SERVICE_CODE`, `USSD_SERVICE_CODE_STAGING` on staging) is `662*105` for the pilot. Change it to `311` once Inkomoko's shortcode is configured; no code change is needed. The gateway's `serviceCode` (`RW|MTN|662|105` → `662*105`) must match it, or the request is rejected with HTTP 400.

**Retries:** a repeated identical `userInput` in the same session is a gateway retry. The previous screen is replayed instead of being processed twice.

## USSD flow

```
*662*105# → Screen 0 consent (1 Continue / 2 Exit)
      → Screen 1 language (1 Kinyarwanda / 2 English / 3 Francais)
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
| GET | `/api/admin/audit-logs?actor=` | Audit trail |

Admin calls need the headers `X-API-KEY` (listed in `inkomoko.admin.api-keys`) and `X-Admin-User` (operator name, used for maker/checker and audit).

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
* Admin API: API key plus operator header. **Replace with SSO/RBAC before production.**

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
  * Always required: `DB_USERNAME`, `DB_PASSWORD`, `ADMIN_API_KEYS`, `PAYMENT_CALLBACK_TOKEN`.
  * Integrations default to `live`, which also requires `FINERACT_BASE_URL/USERNAME/PASSWORD`, `PAYMENT_BASE_URL/API_KEY/CALLBACK_URL` and `SMS_BASE_URL/API_KEY`.
  * Optional: `DB_HOST`, `DB_PORT`, `DB_NAME`, `FINERACT_TENANT_ID`, `FINERACT_PAYMENT_TYPE_ID`, `SUPPORT_HELPLINE`, `*_MODE`.
* **Logs:** written to `/opt/tomcat/logs/inkomoko-ussd.log` on the host.

## Before go-live (open items)

1. **Fineract**: see the switch checklist above. Set `inkomoko.fineract.mode=live`, plus `base-url`, credentials and `payment-type-id`. Confirm against the sandbox that client search by mobile number (`/search?resource=clients`) and the loan status mapping (`ELoanStatus.fromFineract`) match Inkomoko's setup.
2. **Payment gateway / SMS**: the paths and fields in `EfashePaymentGatewayService`, `EfasheSmsService` and `PaymentCallbackRequest` are provisional. Align them with the Efashe merchant API.
3. **Content**: the helpline (`inkomoko.support.helpline`) and the FAQ office addresses (seeded by `V2__seed_faq.sql`) are placeholders. The Kinyarwanda and French wording needs native-speaker review.
4. **Secrets**: move the DB password, API keys and callback token out of `application.properties` (use env vars or a vault).
5. The Redis starter is on the classpath but unused. Its health check is disabled.
