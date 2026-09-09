# FX Trade Lifecycle

[![CI](https://github.com/kelvychuks/fx-trade-lifecycle/actions/workflows/ci.yml/badge.svg)](https://github.com/kelvychuks/fx-trade-lifecycle/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)

A back-office system for a foreign exchange desk: capture spot and forward
trades, move them through a controlled lifecycle, keep positions, and mark the
book to market at the end of each day.

**Live API:** <https://fx-trade-lifecycle.onrender.com> · **Swagger UI:** <https://fx-trade-lifecycle.onrender.com/swagger-ui/index.html> · **Blotter:** _coming soon_

> Hosted on a free tier that sleeps when idle — the first request can take
> 30–50 seconds while the instance wakes.

---

## Why this exists

Most portfolio backends are a CRUD API with a different noun in front of it.
This one is built around rules that are genuinely hard to get right and that
only exist in a particular corner of finance: when money actually moves, what a
forward is worth, who is allowed to approve what, and how to prove afterwards
what happened.

I work on [Calypso](https://www.nasdaq.com/solutions/calypso) implementations
for banks — configuration, support, and custom Java development against a
treasury platform. This is a small, self-contained take on the part of that
world I find most interesting, built from scratch so the reasoning is visible.

## Sign in

| Login | Password | Role | May |
|---|---|---|---|
| `trader@fxdesk.dev` | `Trader1234!` | `TRADER` | book, amend, cancel |
| `mo@fxdesk.dev` | `Middle1234!` | `MIDDLE_OFFICE` | validate, confirm, settle, load market data |
| `viewer@fxdesk.dev` | `Viewer1234!` | `VIEWER` | read everything, change nothing |

An empty database seeds itself with two weeks of market data and a book of
trades spread across every lifecycle state, so the blotter is never empty.

---

## The lifecycle

```
                     ┌──────────── amend ────────────┐
                     ▼                               │
   book ───▶  CAPTURED  ──validate──▶  VALIDATED  ──confirm──▶  CONFIRMED  ──settle──▶  SETTLED
                  │                        │                        │
                  └──────── cancel ────────┴──────── cancel ────────┘
                                           │
                                           ▼
                                       CANCELLED
```

Every one of those arrows is checked in one place — `TradeStatus.canTransitionTo`
— and every transition writes an append-only `trade_event` row saying who did
it and when. Two rules people usually get wrong, both enforced here:

- **Amending a validated trade sends it back to CAPTURED.** The economics
  changed, so the checks must run again. A trade must never carry a validation
  that was performed against different terms.
- **A confirmed trade can still be cancelled, right up until it settles.** A
  settled one cannot — the cash has moved, and the correction for that is a new
  offsetting trade, not an edit.

## What it actually does

**Value dates.** The day the money moves is derived from a settlement calendar,
not from "today plus two". Spot is the trade date plus the pair's lag, counting
only days on which both currencies are open — and the result must also be a US
business day, because the dollar leg of the settlement chain has to clear, even
for a pair with no dollar in it. Forwards run from spot and are adjusted
**Modified Following**: roll forward to the next open day unless that crosses
into the next month, in which case roll back, so a three-month trade never
quietly becomes a four-month one. And the **end-of-month rule**: if spot is the
last business day of its month, every month-based forward lands on the last
business day of its month. Spot 26 February plus one month is 31 March.

**Forward pricing.** Covered interest parity, not a forecast:

```
F = S x (1 + r_quote x t) / (1 + r_base x t)      t = days / 360
```

The currency with the higher interest rate trades at a forward discount. A
dealer sees the result as points: "EURUSD 3M at 36.6".

**Controls that reject trades.** An inactive counterparty, a notional over that
counterparty's limit, a value date that is not a settlement day, and a manually
dealt rate more than 5% from the market are all refusals, not warnings — an
off-market rate is how a loss gets buried inside a trade.

**Four-eyes.** The person who books a trade cannot confirm it, checked in the
service rather than assumed of the UI. Roles alone mostly prevent this; the
check catches the case roles miss, where someone books a trade and moves to
middle office before it is confirmed.

**Idempotent booking.** A booking request may carry an `externalRef`. Replaying
it — flaky network, impatient click — returns the original trade instead of
booking a second one.

**Positions.** Every trade moves two currencies in opposite directions, so
positions are built by walking both legs of every live trade. What matters is
the net per currency across all pairs: a long EURUSD and a long USDJPY partly
offset in dollars.

**End-of-day mark-to-market.** For each live trade, what would it cost to close
out today? That means comparing against the market rate *for the trade's own
value date*, not today's spot, and discounting the result back:

```
MTM = direction x notional x (market rate - dealt rate)
PV  = MTM x discount factor to the value date
```

An unrealised gain landing in six months is not worth its face value now, and a
P&L that says otherwise overstates the desk.

## Architecture

```
        React blotter ────────────────┐
                                      │ JSON + JWT
                                      ▼
┌─────────────────────────────────────────────────────────────────┐
│ Spring Boot                                                     │
│                                                                 │
│  trading    ─ TradeService ─── the one gate for every state     │
│               │                change; writes trade_event       │
│               ├──▶ pricing    ─ ValueDateCalculator             │
│               │                 ForwardPricer                   │
│               │                 (plain classes, no framework)   │
│               ├──▶ marketdata ─ spot + deposit rates            │
│               └──▶ reference  ─ pairs, counterparties, holidays │
│                                                                 │
│  positions  ─ pure aggregation over live trades                 │
│  valuation  ─ end-of-day run (scheduled + on demand)            │
│  security   ─ JWT, roles, @PreAuthorize on the rule it guards   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    PostgreSQL (Flyway)
```

The domain logic that is worth testing — value dates and pricing — is in plain
Java classes with no Spring or JPA in them, so every market convention is a unit
test that runs in milliseconds against a hand-built calendar. Services are the
thin layer that feeds them from the database.

## Glossary

| Term | Meaning |
|---|---|
| **Base / quote** | EURUSD is quoted as dollars per euro. EUR is the base, USD the quote. "Buy EURUSD" always means buy the base. |
| **Spot date** | When a spot trade settles: two business days out for most pairs. |
| **Value date** | The day cash actually moves. |
| **Tenor** | How far out a forward runs, measured from spot: `SP`, `W1`, `M1`, `M3`, `M6`, `Y1`. |
| **Forward points** | The difference between the forward and spot rates, in pips. |
| **Pip** | The smallest quoted increment: the fourth decimal for most pairs, the second for yen pairs. |
| **Notional** | The base-currency amount of the trade. |
| **Counter amount** | The other side: notional × rate, in the quote currency. |
| **Broken date** | A value date that is not a standard tenor. Legitimate, and still has to be a settlement day. |
| **MTM** | Mark to market: what the trade is worth now versus what it was dealt at. |
| **Four-eyes** | Two different people must be involved: one books, another confirms. |

## Design decisions

**Money is `BigDecimal` with an explicit scale and rounding mode, everywhere.**
Rates carry 8 decimals in the database, amounts carry the quote currency's minor
units (2 for dollars, 0 for yen). `double` cannot represent `0.10`, and an FX
desk that is a hundredth of a cent out per trade has a reconciliation problem by
Friday.

**One gate for state changes.** Nothing sets `status` directly; everything goes
through `TradeService.transition`, which checks the state machine and writes the
audit event. Making an illegal transition would take deliberate effort.

**The audit trail is append-only and sequenced per trade.** Nothing updates or
deletes a `trade_event`. Gaps in the sequence would mean something was removed,
which is exactly what an auditor looks for.

**Authorisation sits next to the rule.** `@PreAuthorize` on the service method,
not a URL pattern in a config class three packages away — so the rule and its
guard get read, and changed, together.

**Optimistic locking on trades.** Two people acting on the same trade at once is
not hypothetical on a desk. The second one gets a 409 telling them to reload,
rather than silently overwriting the first.

**Blotter filtering uses Specifications.** Every filter is optional; composing
predicates beats one JPQL query with six `(:param is null or ...)` branches the
database has to plan around.

**Seed data goes through the real service.** The demo book is booked, validated
and confirmed through `TradeService`, obeying every rule the API obeys — so
seeding doubles as a smoke test of the whole booking path on each fresh deploy.

## What is deliberately simplified

Being explicit about this matters more than pretending otherwise:

- **One rate per currency, not a curve.** Real pricing uses a term structure of
  interest rates, not a single deposit rate per currency.
- **ACT/360 for every currency.** GBP and several others are conventionally
  ACT/365, and beyond a year rates compound.
- **Mid rates only.** A real desk prices off bid or offer depending on which way
  the position would be closed, and carries a spread.
- **No netting or settlement instructions.** Real settlement nets payments per
  counterparty per currency per day and routes them through SSIs.
- **Valuations are not converted to a single reporting currency.** Adding
  dollars to yen is how a P&L report starts lying, so the summary keeps them
  apart.
- **The scheduled valuation assumes one instance.** With replicas it needs a
  lock (ShedLock, or a row in the database).

## Running it

**With Docker:**

```bash
docker compose up --build
```

API on <http://localhost:8080>, Swagger UI on
<http://localhost:8080/swagger-ui/index.html>, database migrated and seeded.

**Without Docker** — a JDK 17 and any reachable PostgreSQL, including a free
hosted one:

```bash
export DATABASE_URL=jdbc:postgresql://<host>/<db>?sslmode=require
export DATABASE_USERNAME=<user>
export DATABASE_PASSWORD=<password>
./mvnw spring-boot:run
```

**Tests:**

```bash
./mvnw verify
```

Unit tests — the value-date rules, the pricing maths, the state machine,
position aggregation — run anywhere. The integration tests start their own
PostgreSQL through Testcontainers and skip themselves when no Docker daemon is
present, so this passes on a JDK-only machine and runs in full in CI.

## API tour

| Method | Path | Role | Purpose |
|---|---|---|---|
| POST | `/api/auth/login` | public | Get a bearer token |
| GET | `/api/reference/currency-pairs` | any | Tradeable pairs |
| GET | `/api/reference/counterparties` | any | Counterparties and limits |
| GET | `/api/reference/holidays` | any | Settlement calendar |
| GET | `/api/pricing/quote` | any | Price a pair for a tenor, showing the workings |
| GET | `/api/pricing/value-date` | any | Where spot and a tenor land |
| GET | `/api/market-data/rates` | any | A day's snapshot |
| POST | `/api/market-data/rates` | MO | Load a day's snapshot |
| GET | `/api/trades` | any | Blotter, every filter optional |
| POST | `/api/trades` | TRADER | Book |
| GET | `/api/trades/{ref}/events` | any | Audit trail |
| POST | `/api/trades/{ref}/validate` | MO | Run the desk's checks |
| POST | `/api/trades/{ref}/confirm` | MO | Confirm (four-eyes) |
| POST | `/api/trades/{ref}/settle` | MO | Settle on or after value date |
| POST | `/api/trades/{ref}/amend` | TRADER | Amend and re-validate |
| POST | `/api/trades/{ref}/cancel` | TRADER/MO | Cancel with a reason |
| GET | `/api/positions` | any | Net position per currency |
| GET | `/api/positions/counterparties` | any | Exposure per counterparty |
| POST | `/api/valuations/run` | MO | Run end-of-day |
| GET | `/api/valuations/summary` | any | Unrealised P&L by currency and book |

### A worked example

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"trader@fxdesk.dev","password":"Trader1234!"}' | jq -r .token)

# What would a three-month EURUSD forward price at, and when does it settle?
curl -s "localhost:8080/api/pricing/quote?pair=EURUSD&tenor=M3&notional=1000000" \
  -H "Authorization: Bearer $TOKEN" | jq

# Book it
curl -s -X POST localhost:8080/api/trades \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"pair":"EURUSD","direction":"BUY","notional":1000000,
       "counterparty":"MERIDIAN","tenor":"M3","externalRef":"demo-1"}' | jq
```

## Tech

Java 17 · Spring Boot 3.5 · Spring Security (JWT, method security) ·
Spring Data JPA · PostgreSQL 16 · Flyway · springdoc-openapi · Lombok ·
JUnit 5 · AssertJ · Testcontainers · Docker · GitHub Actions
