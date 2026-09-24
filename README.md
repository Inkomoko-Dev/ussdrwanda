# Inkomoko USSD Client Self-Service Platform (*311#)

Spring Boot 4 / Java 21 / PostgreSQL. The service implements the menu from the FDI Technical Proposal (§3.3). It connects to the FDI USSD gateway through the JSON contract in *FDI USSD Gateway Integration*. The code structure follows the `docs/sample-ussd` classes: a single `UssdController`, a `UssdSession` state row, and per-service menu classes under `utils/menus`.

## Run locally

```bash
createdb -U postgres inkomoko_db          # user/password: postgres/postgres (application.properties)
./mvnw spring-boot:run                    # Flyway creates the schema on first start
```

* USSD simulator: http://localhost:8080/simulator.html
* Swagger UI: http://localhost:8080/swagger-ui.html

By default Fineract, the payment gateway and SMS run in **mock** mode (`inkomoko.*.mode=mock`), so the whole flow works without external systems:

| Test phone     | Mock client                                               |
|----------------|-----------------------------------------------------------|
| 250788000001   | 1 active loan (+1 settled), first dial goes through PIN setup |
| 250788000002   | 2 repayable loans (one in arrears) + 1 under review       |
| 250722000003   | Airtel number, 1 approved loan pending disbursement       |
| 250788000004   | inactive client                                           |
| any other      | not registered                                            |

A mock payment confirms automatically after 5 s. Amounts ending in **13** (e.g. 1013) fail, so you can test the failure path. SMS messages are written to the log.

## USSD flow

```
*311# → Screen 0 consent (1 Continue / 2 Exit)
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

## Before go-live (open items)

1. **Fineract**: set `inkomoko.fineract.mode=live`, plus `base-url`, credentials and `payment-type-id`. Confirm against the sandbox that client search by mobile number (`/search?resource=clients`) and the loan status mapping (`ELoanStatus.fromFineract`) match Inkomoko's setup.
2. **Payment gateway / SMS**: the paths and fields in `EfashePaymentGatewayService`, `EfasheSmsService` and `PaymentCallbackRequest` are provisional. Align them with the Efashe merchant API.
3. **Content**: the helpline (`inkomoko.support.helpline`) and the FAQ office addresses (seeded by `V2__seed_faq.sql`) are placeholders. The Kinyarwanda and French wording needs native-speaker review.
4. **Secrets**: move the DB password, API keys and callback token out of `application.properties` (use env vars or a vault).
5. The Redis starter is on the classpath but unused. Its health check is disabled.
