# GiftConnect – Loved Ones Gifting Platform

A web-based gifting platform built as a college capstone project. Customers browse gifts, add
them to a cart, schedule delivery for a special day, place orders, pay through a simulated
gateway, and track fulfillment; sellers manage products and administrators oversee users and
platform activity.

## Signature Feature — Scheduled Gift Delivery

Checkout can **schedule a gift for a future special day**. A scheduled gift must be ordered at
least **seven calendar days** in advance, and the customer picks a preferred delivery time slot
(morning / afternoon / evening). The rule is enforced **server-side** (`DeliveryPolicy`) in the
delivery location's local zone (**Asia/Kolkata**) and is never trusted to the browser date
picker. The requested date, slot, recipient, and an optional gift message are stored on the order
and shown on the confirmation and order-detail views.

Both the date and the slot are required together — omitting either one, sending an unknown slot,
or picking a date inside the seven-day window is rejected with `400` **before** any product row
is locked or any row is written.

Supported slots (stable `code` stored on the order → label shown in the picker):

| Code | Label |
|---|---|
| `09:00-12:00` | 9:00 AM – 12:00 PM (MORNING) |
| `12:00-15:00` | 12:00 PM – 3:00 PM (AFTERNOON) |
| `15:00-18:00` | 3:00 PM – 6:00 PM (EVENING) |

**Actual delivery date behavior:** `actualDeliveryDate` is *never* copied from the requested
date. It is stamped from the server clock **only when an operator moves the order to
`DELIVERED`** via `PUT /api/orders/{id}/status`, so a scheduled date that has simply passed is
not mistaken for a completed delivery. Orders placed without a schedule keep every scheduling
field and `actualDeliveryDate` empty until they are actually delivered.

## Tech Stack

- Backend: **Java 24**, **Spring Boot 3.5.16**, Spring Data JPA, Bean Validation
- Build tool: Maven
- Database: MySQL 8 (`loved_ones_gifting`)
- Auth: session-cookie authentication (servlet session) with BCrypt password hashing
  (`spring-security-crypto`); no Spring Security filter chain
- Email: Spring Mail for the registration OTP (credentials from environment only)
- Payments: **simulated** gateway (no real money moves)
- Frontend: HTML, CSS, vanilla JavaScript (no framework)
- API style: REST (JSON)

## Prerequisites

- Java 24 installed (`java -version`)
- Maven installed (`mvn -version`)
- MySQL Server running locally with the `loved_ones_gifting` database

## Setup

### 1. Provide the MySQL password (never hardcode it)

The datasource password is read from the `DB_PASSWORD` environment variable. You can either
export it, or create a git-ignored `.env` file in the project root (loaded automatically via
`spring.config.import`):

**Windows (PowerShell):**
```powershell
$env:DB_PASSWORD="your_mysql_password"
```

**macOS / Linux (bash/zsh):**
```bash
export DB_PASSWORD=your_mysql_password
```

`.env` (optional, git-ignored):
```properties
DB_PASSWORD=your_mysql_password
# MAIL_HOST=smtp.gmail.com
# MAIL_USERNAME=you@gmail.com
# MAIL_PASSWORD=your_16_char_app_password
# MAIL_FROM=no-reply@giftconnect.local
```

### 2. Create the database and apply the schema (manual)

Create the database with the MySQL client of your choice (CLI or GUI):

```sql
CREATE DATABASE IF NOT EXISTS loved_ones_gifting;
```

**Every script under `database/` must be run manually** — nothing in the project executes them
at startup, and there is no migration tool (Flyway/Liquibase) in this build. Run them **in this
order**; each is additive and non-destructive:

```
database/schema.sql
database/user_management_schema.sql
database/category_product_schema.sql
database/product_ownership_schema.sql
database/order_payment_schema.sql
database/order_idempotency_schema.sql
database/activity_log_schema.sql
database/delivery_schedule_schema.sql   -- scheduled-delivery columns on orders
```

`spring.jpa.hibernate.ddl-auto=update` only **adds** missing tables/columns; it never drops or
recreates existing data.

## Building and Running

Build the jar (omit `-DskipTests` to include the test suite):

```bash
mvn package -DskipTests
```

Run from sources:

```bash
mvn spring-boot:run
```

or run the packaged jar:

```bash
java -jar target/giftconnect-0.0.1-SNAPSHOT.jar
```

**Application port:** the configured port is **`server.port=8080`** (verified in
`src/main/resources/application.properties`), so the landing page is
**http://localhost:8080**. If `8080` is already held by another process on your machine, start
the app on a different port instead of editing the file:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

## Authentication Flow

Registration is **email-verified**:

1. `POST /api/auth/register` — creates a pending account and sends a 6-digit OTP to the email.
2. `POST /api/auth/verify-otp` — verifies the OTP and activates the account.
3. `POST /api/auth/login` — starts a server-side session (cookie `JSESSIONID`).
4. `GET /api/auth/me` — returns the logged-in user (role, email, etc.).

Protected endpoints require that session cookie. The role used for authorization is always
resolved **server-side** from the session and re-validated against the database on each request —
never taken from a request parameter, body, header, or cookie.

### Register

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test User",
    "email": "test@example.com",
    "password": "password123",
    "phone": "9876543210",
    "address": "Trichy"
  }'
```

Then verify the emailed code:

```bash
curl -X POST http://localhost:8080/api/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{ "email": "test@example.com", "otp": "123456" }'
```

### Log in (and reuse the session cookie)

```bash
curl -c cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{ "email": "test@example.com", "password": "password123" }'
```

## API Overview

Public (no session) reads:

| Method | Path | Notes |
|---|---|---|
| GET | `/api/products`, `/api/products/{id}` | Catalog (ACTIVE products only) |
| GET | `/api/products/search?name=` | Name search |
| GET | `/api/products/category/{categoryId}` | By category |
| GET | `/api/categories`, `/api/categories/{id}` | Categories |

Authenticated:

| Method | Path | Notes |
|---|---|---|
| POST | `/api/orders` | Place an order (owner = session user) |
| GET | `/api/orders/{id}` | Owner or ADMIN only |
| GET | `/api/orders/user/{userId}` | A user's orders |
| PUT | `/api/orders/{id}/status` | **ADMIN only**, forward-only transitions |
| PUT | `/api/orders/{id}/cancel` | Owner or ADMIN; restocks items |
| GET | `/api/delivery/options` | Scheduling metadata (earliest date, slots, timezone) |
| POST | `/api/payments` | Create a payment for an order |
| POST | `/api/payments/{id}/process` | Simulated gateway result |
| GET | `/api/users/me`, PUT `/api/users/me` | Current profile |
| POST | `/api/auth/change-password`, `/api/auth/logout` | Account actions |

ADMIN-only:

| Method | Path | Notes |
|---|---|---|
| GET | `/api/orders` | Platform-wide order list |
| GET | `/api/admin/overview`, `/api/admin/activity` | Dashboard + audit log |
| GET | `/api/admin/users`, `/api/admin/users/{id}` | User administration |
| PUT | `/api/admin/users/{id}/status`, `/api/admin/users/{id}/role` | Activate/deactivate, change role |

Product/category mutations (POST/PUT/DELETE) require SELLER or ADMIN.

## Rate Limiting (credential endpoints)

Login, registration, OTP verification and OTP resend are throttled **per client IP** by an
in-memory fixed-window limiter (`RateLimitService`):

| Endpoint | Once the window budget is exhausted |
|---|---|
| `POST /api/auth/login` | `429` + `{"error":"Too many requests. Please wait a moment and try again."}` |
| `POST /api/auth/register` | `429` (same body) |
| `POST /api/auth/verify-otp` | `429` (same body) |
| `POST /api/auth/resend-otp` | `429` (same body) |

Defaults, all overridable in `application.properties`:

```properties
app.rate-limit.enabled=true
app.rate-limit.max-requests=20      # requests allowed per window
app.rate-limit.window-seconds=60    # window length in seconds
app.rate-limit.max-keys=10000       # hard cap on tracked client keys (bounds memory)
```

A throttled request is rejected **before** any password comparison, user lookup, or OTP work
happens.

**Limitations of this implementation:** the counters live in the application's memory, so they
**reset every time the application restarts**, and they are **not shared across multiple
application instances** — behind a load balancer the effective allowance is roughly
`max-requests × instance count`, and each instance keeps its own window. Making it distributed
would require a shared store (e.g. Redis) or a rate-limiting gateway, which this project does
not use. The client key is the first `X-Forwarded-For` entry when that header is present,
which assumes a trusted proxy sets it; without one a client could rotate the header to gain a
fresh budget.

## CSRF Protection

`CsrfProtectionFilter` runs before the authentication filter on **unsafe methods** (POST, PUT,
PATCH, DELETE) and compares the `Origin` header (falling back to `Referer`) with the origin the
request was actually addressed to (`Host`, honoring `X-Forwarded-Proto`):

| Situation | Result |
|---|---|
| `Origin`/`Referer` matches this server's origin, or an entry in `app.csrf.allowed-origins` | allowed through |
| `Origin`/`Referer` present but different (cross-site), or the opaque `null` origin | **`403`** `{"error":"Cross-site request blocked"}` |
| Safe method (GET/HEAD/OPTIONS/TRACE) | never blocked |
| **Neither `Origin` nor `Referer` present** | **allowed through** |

```properties
app.csrf.enabled=true
app.csrf.allowed-origins=            # optional comma-separated extra trusted origins
```

**Exact limitation:** requests that carry **no `Origin` and no `Referer` are not blocked** —
this filter deliberately permits them so curl, Postman, and server-to-server callers keep
working without a token handshake. It is safe to do so for browser-based CSRF because browsers
always send `Origin` on cross-site unsafe requests, but it means the filter is a **second
layer**; the **primary control is the `SameSite=Lax` session cookie** configured in
`application.properties`. This is not a token-based (synchronizer/Double-Submit) scheme.

## Testing

```bash
mvn test
```

When dependencies are already in the local repository you can stay offline with `mvn -o test`.

The suite runs entirely with mocked repositories / `@WebMvcTest` slices — no database, SMTP, or
network is touched.

## Known Limitations

- **HTTP only for local use.** No HTTPS/`Secure` cookie flag is configured; this is fine for
  local development over `http://localhost` and is **not** a production-ready session-cookie
  posture.
- **Manual, additive database schema.** `spring.jpa.hibernate.ddl-auto=update` only *adds*
  missing tables/columns; there are no automated migrations (Flyway/Liquibase), no rollback
  scripts, and the SQL files under `database/` must be run by hand and kept in sync with the
  entities.
- **Rate limiting is local and volatile:** single instance, in-memory, reset on restart (see
  Rate Limiting above) — not distributed rate limiting.
- **CSRF is an Origin/Referer check**, not a token scheme, and allows requests that send
  neither header (see CSRF Protection above).
- **SQL logging is on:** `spring.jpa.show-sql=true` prints every statement to the console —
  useful while developing, noisy and undesirable in production.
- **Payments are simulated.** No real gateway, webhook, or refund flow exists; no money moves.
- **No deployment/operations tooling:** no Dockerfile, CI pipeline, health-check endpoint,
  backup/restore procedure, or dependency-vulnerability scan is included. The project is a
  campus demo — **do not treat it as deployment-ready**.

## Common Errors & Fixes

| Error | Likely Cause | Fix |
|---|---|---|
| `Communications link failure` | MySQL not running / wrong host or port | Start MySQL; confirm `localhost:3306` |
| `Access denied for user 'root'@'localhost'` | Wrong password or `DB_PASSWORD` not set | Set `DB_PASSWORD` in the same terminal you run the app from |
| `Unknown database 'loved_ones_gifting'` | Database not created | `CREATE DATABASE loved_ones_gifting;` then run the schema scripts |
| `Table '...' doesn't exist` | Schema scripts not applied | Apply the `database/*.sql` scripts in the order above |
| Port `8080` already in use | Another process using it | Stop it, or set `server.port=8081` in `application.properties` |
| `429 Too many requests` | Credential endpoint throttled by the in-memory limiter | Wait out the window (default 60 s), or raise `app.rate-limit.*` |
| `403 Cross-site request blocked` | Unsafe request carried a foreign `Origin`/`Referer` | Send it from this server's origin, or list it in `app.csrf.allowed-origins` |
| `Method not allowed` / `Unsupported content type` | Wrong HTTP verb or `Content-Type` for the endpoint | Use the verb/content type listed in the API overview |
