# Level 0 — first setup and Spring Boot concepts

Read this, then code. It covers the first slice only: schema, wiring, and Catalog (hotels and room types). Booking, inventory holds and payments come after that and use the same ideas.

The HTTP contract and the hold rules live in [`../levels.md`](../levels.md). The tables live in [`../architecture/data-model.md`](../architecture/data-model.md). This page is the Spring Boot map.

## Order of work

1. Write `V1__init.sql` and open a PR. Stop for review before Java.
2. Catalog: `POST/GET/PUT/DELETE` for hotels and room types.
3. Inventory rows for nights (seeded or inserted by a host flow).
4. Booking hold and release.
5. Payment gateway call and webhook.

Do not skip step 1. The exit-gate fixtures insert into the real table names, so the migration must match [`data-model.md`](../architecture/data-model.md) and the checklist in [`levels.md`](../levels.md).

## Stack this repo uses

| Piece | Choice | Why it matters |
| --- | --- | --- |
| Spring Boot | 3.5.x, Java 21 | Parent BOM pins versions in `pom.xml` |
| HTTP | `spring-boot-starter-web` | Controllers and JSON |
| Database access | `spring-boot-starter-jdbc` | **JDBC, not JPA.** No `@Entity`, no Hibernate |
| Migrations | Flyway | Files under `src/main/resources/db/migration/` |
| Validation | `spring-boot-starter-validation` | `@Valid` on request bodies |
| Time | Injected `Clock` bean | Already in `ClockConfig` |

Anti-pattern: adding `spring-boot-starter-data-jpa` for level 0. The gates and the hold `UPDATE … WHERE reserved < total` are written for plain SQL. Stay on JDBC.

## Layers

```text
HTTP request
    → @RestController   (parse JSON, status codes, nothing else)
    → @Service          (rules, transactions, Clock)
    → repository / DAO  (NamedParameterJdbcTemplate + SQL)
    → PostgreSQL
```

Keep SQL out of controllers. Keep HTTP status codes out of repositories. A service that needs several SQL statements wraps them in one `@Transactional` method.

Suggested packages (folders already exist as empty shells):

- `com.staybook.catalog` — hotels and room types
- `com.staybook.inventory` — night counters
- `com.staybook.booking` — hold and release
- `com.staybook.payment` — gateway and webhook
- `com.staybook.identity` — users (later if Catalog only needs `ownerId`)

## Core annotations and concepts

### Application entry

- **`@SpringBootApplication`** — already on `StaybookApplication`. Starts component scan from that package downward.
- **`@Configuration` + `@Bean`** — how `ClockConfig` registers the app `Clock`. Prefer constructor injection of `Clock` into services over calling `Clock.systemUTC()` yourself.

### HTTP

- **`@RestController`** — class that returns JSON bodies (combines `@Controller` + `@ResponseBody`).
- **`@RequestMapping("/api/…")`** — base path on the class.
- **`@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping`** — one method per verb.
- **`@PathVariable`** — `{id}` from the URL.
- **`@RequestBody`** — JSON body into a Java type.
- **`ResponseEntity`** — set status (`201`, `204`, `404`, `409`, `422`) and body together.

Catalog example shapes from the contract:

- `POST /api/hotels` → `201` + body with `id`
- `DELETE /api/hotels/{id}` → `204` or `404`
- Missing row → `404`, not an empty `200`

### Validation

- **`@Valid`** on a `@RequestBody` parameter — runs Jakarta Bean Validation before the method body.
- **`@NotNull` / `@NotBlank` / `@Positive` / `@Min` / `@Size`** on record fields of the request DTO.
- Prefer **Java `record`** types for request and response DTOs. They are immutable and map cleanly to JSON.

Invalid bodies should become `400`. You will wire an `@ControllerAdvice` later; for the first slice, Spring Boot’s default error body is enough to learn with.

### Services and wiring

- **`@Service`** — business logic bean.
- **Constructor injection** (no field `@Autowired`). One constructor; Spring injects the dependencies. Mark the class `final` fields.
- **`@Transactional`** on service methods that must commit or roll back as one unit (for Catalog create that inserts one row, optional; for Booking hold that updates many nights then inserts a booking, **required**).

Read the Spring transaction boundary carefully: the gateway must **not** be called inside a `@Transactional` method. Hold inventory, commit, then call the gateway. That rule matters from level 0 onward; the level 3 gate exists to catch the opposite.

### JDBC (not JPA)

- Inject **`NamedParameterJdbcTemplate`** (Boot auto-configures it from the datasource).
- Write SQL yourself. Map rows with `RowMapper` or `DataClassRowMapper` for simple records.
- Use `:named` parameters, never string concatenation.
- After `INSERT … RETURNING id`, read the generated `bigint` id back. Catalog creates need that id in the `201` body.

Useful methods to know: `query`, `queryForObject`, `update`, `update` with `GeneratedKeyHolder` or `RETURNING` via `queryForObject`.

### Flyway

- File name: `V1__init.sql` (two underscores). Next change is `V2__…`, never edit `V1` after it has run on `main`.
- Path: `src/main/resources/db/migration/`.
- Boot runs migrations on startup when `spring.flyway.enabled` is true (already set in `application.yml`).

### Configuration

- **`application.yml`** — datasource URL, Flyway, `staybook.payment.*`.
- **Profiles** — `dev` / `test` get a default DB password and webhook secret; other profiles require env vars. Local Compose matches the `dev` defaults.
- **`@ConfigurationProperties`** (optional for the first slice) — typed binding for `staybook.payment`. Fine to read with `@Value` at first; move to a properties record when Payment starts.

### Time and money (do these from day one)

- Inject **`Clock`**. Derive `Instant` / `LocalDate` with `clock.instant()` and `LocalDate.now(clock)`. Never `LocalDate.now()` or `Instant.now()` for business rules.
- Money fields are **`long` / `bigint` paise**, names end in `_paise` / `Paise`. Never `double` or `BigDecimal` for staybook amounts in level 0.

## First Java slice: Catalog

After V1 is merged, implement only:

| Verb | Path | Module |
| --- | --- | --- |
| POST/GET/PUT/DELETE | `/api/hotels` | catalog |
| POST/GET/PUT/DELETE | `/api/room-types` | catalog |

Suggested types per resource:

1. Request record (validated).
2. Response record (includes `id`).
3. `@RestController`.
4. `@Service` with `@Transactional` on write methods if you touch more than one statement.
5. Package-private repository class using `NamedParameterJdbcTemplate`.

Foreign keys: `hotel.owner_id` → `users`, `room_type.hotel_id` → `hotel`. For local manual tests you will need at least one `users` row (SQL insert is fine until Identity exists).

## How to run locally while learning

```bash
docker compose up -d --wait
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway applies `V1` on startup. Hit Catalog with curl or httpie against `http://localhost:8080`.

Quality gate before you open a PR:

```bash
./mvnw verify
```

Exit-gate tags stay off until you opt in; see `CONTRIBUTING.md`.

## What to skip for now

- Spring Security / JWT — Identity can stay minimal until Search and Booking need a real principal.
- JPA, Spring Data repositories, QueryDSL.
- Calling the payment gateway from Catalog.
- Editing a migration that already landed on `main`.

## Checklist before you say Catalog is done

- [ ] V1 merged; tables match `data-model.md`.
- [ ] Hotels and room types meet the JSON shapes in `levels.md`.
- [ ] Missing ids return `404`; creates return `201` with `id`.
- [ ] Deletes of a room type do not cascade-delete bookings (schema rule; enforced in V1).
- [ ] No `Instant.now()` / `LocalDate.now()` in services.
- [ ] `./mvnw verify` green on required checks.

When Catalog is up, ask for the next guide: Inventory seed + Booking hold (transactions, idempotency, `422` vs `409`).
