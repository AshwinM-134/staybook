# staybook

A hotel booking system (think Booking.com or Airbnb), built by hand one level at a time: from a single Spring Boot and Postgres monolith at level 0, up through concurrency, payments, microservices, caching, search and load balancing.

The point is to learn system design by meeting each problem for real and fixing it, not by reading about it.

![Level 0 architecture](docs/architecture/level-0.svg)

The live design is on the [Hotel Booking System board](https://www.tldraw.com/f/qtsxGM0vwAG2muaGMI4pF) in tldraw. That board is the source of truth; the files in [`docs/architecture/`](docs/architecture/) are snapshots of it.

## Level 0: the monolith

One Spring Boot app, one Postgres database, and a mock payment gateway running outside the app.

**Modules**

| Read side | Write side |
| --- | --- |
| Search | Booking |
| Catalog | Inventory |
| Pricing | Payment |
| Reviews | Identity |

**Actors:** a Guest searches and books; a Host manages hotels in Catalog and sets pricing rules.

**The path to study:** Booking to Inventory, labelled *hold*. It is the only path that writes `inventory.reserved`, and every later level exists to keep that write correct under load.

### Entities

| Table | Columns |
| --- | --- |
| `users` | id, username (unique), email (unique), password_hash, created_at, last_logged_in |
| `hotel` | id, owner_id → users, name, city, address |
| `room_type` | id, hotel_id → hotel, name, capacity |
| `inventory` | room_type_id → room_type, date, total, reserved. Primary key (room_type_id, date), `reserved <= total` |
| `rate` | room_type_id → room_type, date, price |
| `booking` | id, user_id → users, room_type_id → room_type, check_in, check_out, guests, total_price, status, idempotency_key (unique), hold_expires_at, created_at |
| `payment` | id, booking_id → booking, amount, payment_type, status, provider_ref (unique), idempotency_key, created_at |

Booking status is one of `HELD`, `CONFIRMED_UNPAID`, `PAID`, `CANCELLED`, `EXPIRED`. One booking can have many payments, because a failed attempt can be retried.

Reviews and amenities (`review`, `amenity`, `hotel_amenity`) come in at level 1.

### Holding a room

Availability is a counter per room type per night. A hold for check-in 20 Dec and check-out 22 Dec runs one statement:

```sql
UPDATE inventory
SET reserved = reserved + 1
WHERE room_type_id = :rt
  AND date >= :check_in AND date < :check_out
  AND reserved < total;
```

If the number of rows updated equals the number of nights, insert the booking and commit. Otherwise roll back.

### Payments

The gateway (Razorpay or Stripe later, a mock for now) is an external system behind a `PaymentGateway` interface. Payment creates an order, the guest pays on the gateway's page, and the gateway calls our webhook. The webhook can arrive late, twice, or never, so it is matched by `provider_ref` and handled idempotently. The gateway is never called while a database transaction is open.

## How we work

- Ash writes all of the application code, including the schema in `V1__init.sql`.
- Each level starts with a problem statement, `TODO`s, and exit-gate tests that fail until the level is solved. When they all pass, the level is cleared.
- Exit-gate tests run against a real Postgres through Testcontainers, in GitHub Actions.
- Work goes in through pull requests, reviewed before merge.

## The climb

Levels 0 to 4 run locally on Docker Compose. The rough order:

1. **Level 0:** the monolith above, working end to end.
2. **Level 1:** reviews and amenities.
3. **Level 2:** concurrency. No overbooking under parallel load, idempotent booking, and a consistency check that recounts `reserved` from bookings.
4. **Level 3:** reliable payments with an outbox.
5. **Later:** caching, search with Elasticsearch, splitting into services, load balancing, and whatever problem shows up next.
