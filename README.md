# Inkomoko USSD platform

Customer self-service over USSD (`*662*105#` pilot, `*311#` later) plus a staff admin portal.

**Stack:** Spring Boot 4 · Java 21 · PostgreSQL · React/Vite portal  
**Traffic:** Comviva (MTN/Airtel) and FDI/Efashe gateways  
**Data:** Inkomoko Omnichannel (core banking + MoMo). PINs stay in this app’s database.

---

## Quick start (local)

**You need:** Java 21, Maven wrapper (included), PostgreSQL 14+, Docker (optional), Node 20+ (only if you work on the portal UI).

```bash
# 1. Database (once)
createdb -U postgres inkomoko_db

# 2. Secrets
cd mobilebanking
cp .env.example .env
# Ask the team for OMNICHANNEL_SECRET and paste it into .env — never commit .env

# 3a. Run with Docker (builds portal + JAR; first build is slow)
docker compose up --build
# On Docker Desktop Mac/Windows: enable host networking once
# (Settings → Resources → Network → Enable host networking)

# 3b. Or run with Maven (faster day-to-day once the portal is built)
cd frontend && npm install && npm run build && cd ..
./mvnw spring-boot:run
```

Then open:

| What | URL |
|---|---|
| Admin portal | http://localhost:9094/portal/ |
| USSD simulator | http://localhost:9094/simulator.html |
| Swagger | http://localhost:9094/swagger-ui.html |
| Health | http://localhost:9094/actuator/health |

**First sign-in:** `admin` or `admin@yopmail.com` / `Admin@2026!` — you must change the password.  
On a brand-new DB the Super Admin also picks the country (Rwanda, …) before the rest of the portal unlocks.

Stop Docker with `docker compose down` (the database on your machine is kept).

### Portal UI in watch mode

```bash
# Terminal 1 — API on :9094
./mvnw spring-boot:run

# Terminal 2 — Vite on :5173 (proxies /api to :9094)
cd frontend && npm install && npm run dev
# → http://localhost:5173/portal/
```

---

## What this system does

1. Customer dials the shortcode → telecom gateway POSTs to this app.
2. App checks Omnichannel (`registered: true`?) → language → terms → PIN → main menu.
3. Loan balance, status, mini statement and repayments are fetched/initiated via Omnichannel.
4. Staff use `/portal` for customers, sessions, repayments, USSD wording, users and audit.

There is **no demo/mock core-banking data**. The app starts only when `FINERACT_MODE` and `PAYMENT_MODE` are `omnichannel` (default) or `live`.

---

## Repository map

```
mobilebanking/
├── src/main/java/.../mobilebanking/
│   ├── controllers/          USSD + payment callback (public)
│   │   ├── admin/            Staff APIs (/api/admin/**)
│   │   └── portal/           Login / me (/api/auth/**)
│   ├── services/
│   │   ├── UssdFlowService   Shared menu engine (Comviva + FDI)
│   │   ├── PinService        PIN hash, lockout (local DB)
│   │   ├── RepaymentService  MoMo lifecycle + posting
│   │   ├── SessionAnalyticsService
│   │   ├── integration/      Omnichannel / Fineract / Efashe / SMS
│   │   └── admin/            Portal domain services
│   ├── utils/menus/          One class per USSD service (loan, repay, …)
│   ├── utils/i18n/           Message bundles + GSM-7 rules
│   ├── models/               JPA entities + DTOs
│   ├── repositories/         Spring Data
│   ├── security/             JWT + privileges
│   └── config/               Properties, modes, SecurityConfig
├── src/main/resources/
│   ├── application.properties
│   ├── db/migration/         Flyway (V1…Vn) — schema is validate-only at runtime
│   ├── i18n/ussd_*.properties  Default USSD/SMS texts (en/rw/fr)
│   └── static/               simulator.html; portal built into static/portal
├── frontend/                 React admin portal (Vite)
├── docker-compose.yml        Local
├── docker-compose.staging.yml / docker-compose.prod.yml
└── .gitlab-ci.yml            Build + deploy
```

**Where to look first**

| Goal | Start here |
|---|---|
| Change a USSD menu step | `services/UssdFlowService` + `utils/menus/*` |
| Change screen wording | `i18n/ussd_en.properties` (and rw/fr), or portal **USSD & SMS texts** |
| Omnichannel API calls | `services/integration/OmnichannelClient` + `OmnichannelFineractService` / `OmnichannelPaymentGatewayService` |
| Comviva vs FDI HTTP shape | `controllers/UssdController` |
| Portal page | `frontend/src/pages/` + matching `controllers/admin/*` |
| Schema change | New Flyway file under `db/migration/` (never edit applied migrations) |

---

## USSD entry points

| Path | Who calls it | In | Out |
|---|---|---|---|
| `POST /api/ussd` | **Comviva** (MTN & Airtel) | `{ sessionId, input, newRequest, msisdn }` | Plain text + header `Freeflow: FC` (continue) / `FB` (end) |
| `POST /api/ussd/fdi` | **FDI / Efashe** | FDI JSON (`sessionId`, `gwRef`, `userInput`, `serviceCode`, …) | JSON `{ text, continueSession, … }` |

Both paths share `UssdFlowService`. Set the shortcode with `USSD_SERVICE_CODE` (`662*105` pilot, `311` for `*311#`). Comviva’s first dial sends `input: "311"` (the shortcode itself); later steps send only the latest choice. FDI sends a cumulative dial string (`*662*105*1*1#`).

**Typical customer journey**

```
Dial → language → terms → (PIN setup | PIN entry) → main menu
  1 Loan / balance   2 Repay   3 Account   4 Settings   5 FAQs   0 Exit
Inside a service: 0 = back, 00 = main menu
```

Use the **simulator** locally, or curl Comviva-style:

```bash
curl -s localhost:9094/api/ussd -H 'Content-Type: application/json' \
  -d '{"sessionId":"dev-1","input":"662*105","newRequest":"1","msisdn":"250785642275"}' \
  -D -
# Expect Freeflow: FC|FB and a plain-text screen
# Use "311" when USSD_SERVICE_CODE=311
```

---

## Core banking & payments

| Mode | Clients / loans | MoMo repayments |
|---|---|---|
| `omnichannel` (default) | Omnichannel REST + HMAC | Omnichannel `/ussd/payment/initiate` |
| `live` | Direct Fineract | Efashe merchant API |

Local `.env` needs at least:

```env
OMNICHANNEL_BASE_URL=http://dev.inkomoko.com:1134/api/v1
OMNICHANNEL_API_KEY=fdi
OMNICHANNEL_SECRET=<from the team>
```

CBS calls are logged as `CBS request ->` / `CBS response <-`. A number can use USSD when validate returns `registered: true`; then they accept terms and set a PIN here. Reset a test PIN with the portal or `POST /api/admin/pins/{msisdn}/reset` (auth + privilege required).

`FINERACT_*` and `PAYMENT_*` (Efashe) are only needed when the matching mode is `live`.

---

## Admin portal (what staff see)

Served at `/portal/`. Seeded roles: Super Administrator (all privileges), Operations Officer, Finance Checker, Support Agent, Viewer.

Useful areas for developers:

- **Dashboard / USSD sessions** — volume, outcomes, visits per phone (billing)
- **Customers** — search, optional SMS invite, PIN reset/unlock
- **Repayments / Approvals** — maker–checker reconciliation (USSD repay itself is optional; toggle under **Countries**)
- **USSD & SMS texts / Languages / Countries** — live wording, shortcode, and **Enable Mobile Money repayment on USSD**
- **Users / Roles / Audit**

Privileges live in `enums/EPrivilege`. Super Admin gets every privilege automatically.

---

## Configuration cheat sheet

| Variable | Purpose | Local default |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL | `localhost:5432/inkomoko_db`, `postgres`/`postgres` |
| `SERVER_PORT` | HTTP port | `9094` locally · `8093` on staging/prod |
| `USSD_SERVICE_CODE` | Shortcode digits (`662*105` or `311`) | `662*105` |
| `OMNICHANNEL_*` | Core banking + MoMo | staging URL + key `fdi` |
| `SMS_MODE` | `mock` (log only) or `live` | `mock` in local compose |
| `JWT_SECRET` | Portal tokens (≥ 32 chars in prod) | dev default in properties |
| `BOOTSTRAP_ADMIN_*` | First Super Admin | `admin@yopmail.com` / `Admin@2026!` |

Full list: `src/main/resources/application.properties` and `.env.example`. Staging/prod compose files map GitLab CI variables (`*_STAGING` on staging).

---

## Deploy

| Environment | How | Port |
|---|---|---|
| **Staging** | GitLab CI on `master` (`build` → `deploy`) | **8093** |
| **Production** | On the server: `git pull` (or cron) then Compose **build** — no Efashe registry / CI image | **8093** |

Container name: `inkomoko-ussd-service` · health: `/actuator/health` · logs: `/opt/tomcat/logs/inkomoko-ussd.log`

### Production (build JAR on the server)

No GitLab registry login. The Dockerfile compiles the portal + Spring Boot JAR during `docker compose … --build`.

```bash
cd /path/to/mobilebanking   # e.g. ~/ussdrwanda after git pull
# Ensure .env has JWT_SECRET (≥32), DB_USERNAME, DB_PASSWORD, OMNICHANNEL_* …

docker compose --env-file .env -f docker-compose.prod.yml up --build -d

docker compose --env-file .env -f docker-compose.prod.yml ps
docker compose --env-file .env -f docker-compose.prod.yml logs -f --tail=100
curl -s http://127.0.0.1:8093/actuator/health
```

Cron-friendly one-liner after `git pull`:

```bash
docker compose --env-file .env -f docker-compose.prod.yml up --build -d
```

Image is tagged locally as `inkomoko-ussd:prod` (not pushed anywhere).

**Production `.env` must include:** `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (≥ 32 chars), `OMNICHANNEL_BASE_URL`, `OMNICHANNEL_API_KEY`, `OMNICHANNEL_SECRET`. Defaults: `USSD_SERVICE_CODE=311`, `DB_HOST=10.0.0.4`.

### Staging (CI/CD)

Push to `master`. Runner builds/pushes the image and runs `docker-compose.staging.yml`.  
Needs `OMNICHANNEL_SECRET_STAGING` (and `SMS_API_KEY_STAGING` if SMS should be live).

---

## Day-to-day developer tasks

**Change USSD copy**  
Edit `i18n/ussd_en.properties` (keep keys/`{placeholders}` in sync in `rw`/`fr`), or override in the portal. `UssdMessageBundleTest` enforces GSM-7 and 182-character screens.

**Add a menu option**  
Extend `UssdFlowService.handleMainMenu` and add/adjust a class under `utils/menus/`.

**Hit Omnichannel from code**  
Use `OmnichannelClient.get/post` — signing matches the Postman script (compact JSON → SHA-256 → HMAC). Do not hand-roll signatures.

**Schema change**  
Add `V18__….sql` under `db/migration/`. Flyway runs on startup; `ddl-auto=validate`.

**Run tests**

```bash
./mvnw test
# Focused:
./mvnw test -Dtest=UssdInputParserTest,OmnichannelClientTest
```

**Reset a stuck test customer**  
Portal → Customers → unlock / reset PIN (after identity check in real ops).

---

## Security notes (short)

- PINs are bcrypt only; raw USSD `input` / `userInput` is never logged.
- Portal APIs need `Authorization: Bearer <jwt>` and the right privilege; actions are audited.
- Reconciliation approve/reject must be a different user from the proposer.
- Staging/prod sit behind the telecom VPN for Comviva; no app-level IP allowlist on `/api/ussd`.

---

## Troubleshooting

| Symptom | Check |
|---|---|
| App won’t start: mode not supported | `FINERACT_MODE` / `PAYMENT_MODE` must be `omnichannel` or `live` |
| `Omnichannel is not configured` | `.env` has `OMNICHANNEL_SECRET`; restart after editing |
| Comviva `400` “input must start with shortcode” | `USSD_SERVICE_CODE` must match first-dial `input` (`311` vs `662*105`) |
| Portal blank / old UI after code change | Rebuild frontend (`npm run build`) or use `npm run dev` |
| Docker can’t reach Postgres (Mac) | Enable host networking in Docker Desktop |
| Flyway / validate errors | Don’t edit old migrations; add a new `V*` file |
| Customer “not registered” | Omnichannel validate for that MSISDN; confirm staging secret and CBS data |

---

## Useful links inside the repo

- Sample Comviva-style controller (reference): `../docs/sample-ussd/`
- Local env template: `.env.example`
- Staging / prod Compose: `docker-compose.staging.yml`, `docker-compose.prod.yml`
- CI variables and jobs: `.gitlab-ci.yml`
