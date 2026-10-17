# Level 0 architecture

StayBook level 0 is a single Spring Boot monolith backed by one PostgreSQL database, with an external mock payment gateway. The diagram is in [`level-0.svg`](level-0.svg).

## Requirements

- **Hotel administration.** A host can add a property, its rooms, pricing and so on.
- **User.** A guest can create an account and search for hotels and rooms to book, filtering by price, rating, amenities, rooms and number of guests.
- **Dynamic pricing.** Not specified yet.
- **Payment.** Can be a mock. Once the booking details are confirmed, the guest can pay at booking or the payment is tracked at check-in. It integrates with a mock payment gateway.

## Actors

- **Guest** searches, books, and pays or confirms.
- **Host** manages hotels and rooms and sets pricing rules.

## Modules

| Module | Owns |
| --- | --- |
| Identity | users, sign-up, login |
| Catalog | hotels and room types |
| Search | finding room types, reads Catalog and availability from Inventory |
| Pricing | per-night rates and pricing rules |
| Reviews | reviews (level 1) |
| Inventory | rooms per room type per night (total set from Catalog), holds and releases |
| Booking | the booking lifecycle and idempotency |
| Payment | payments, gateway calls and webhooks |

## Flows

- **Search:** Guest to Search. Search reads hotels and room types from Catalog and availability from Inventory.
- **Book:** Guest to Booking. Booking holds rooms in Inventory.
- **Pay:** Booking to Payment to pay or confirm. Payment creates an order on the gateway, and the gateway calls back with a webhook.
- **Manage:** Host to Catalog, which sets each room type's inventory total, and Host to Pricing for pricing rules.
- Every module reads and writes the one PostgreSQL database. The gateway is never called inside a database transaction.

## Payment gateway

External to StayBook. Locally it is a WireMock container in Docker Compose, behind the `PaymentGateway` interface.

## Data model

Tables, the rules the schema enforces, booking states, and money and time conventions live in [`data-model.md`](data-model.md).
