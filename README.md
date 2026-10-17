# staybook

Staybook is a hotel booking system, like Booking.com or Airbnb. Each level is built by hand. Level 0 is one Spring Boot app and one Postgres database. Later levels add concurrency, payments, microservices, caching, search, and load balancing.

The point is to learn system design by meeting each problem for real and fixing it, not by reading about it.

![Level 0 architecture](docs/architecture/level-0.svg)

[`docs/architecture/`](docs/architecture/) holds snapshots of the design board.

[`docs/levels.md`](docs/levels.md) is the level guide. It has the level 0
design, the HTTP contract the exit gates call, and the levels in order.

[`CONTRIBUTING.md`](CONTRIBUTING.md) has the commit, pull request, and
CI rules.

## Running locally

Start Postgres and the mock gateway. `--wait` returns when both
healthchecks pass:

```bash
docker compose up -d --wait
```

Run the app against that Postgres with the `dev` profile. The `dev` and
`test` profiles use the database password `staybook` unless
`DB_PASSWORD` is set, and `test-webhook-secret` unless
`STAYBOOK_PAYMENT_WEBHOOK_SECRET` is set. Any other profile has no
fallback for either and does not start without both:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

`src/main/resources/db/migration` holds only a `.gitkeep`. Ash's real
schema is the first migration, `V1__init.sql`, and Flyway applies it on
startup. Until then Flyway logs `No migrations found` and the app still
starts.

Default tests, linters, and formatter check:

```bash
./mvnw verify
```

The smoke test boots Spring against Postgres via Testcontainers. Tags
`level0`, `level2`, and `level3` are excluded, so this stays green.

Run one level on its own. Level 0 fails until the
[HTTP contract](docs/levels.md#http-contract) is implemented:

```bash
./mvnw verify -Dgroups=level0 -DexcludedGroups=
```

`./mvnw verify` runs PMD, Checkstyle (Google style), SpotBugs, and
Spotless. Format Java with:

```bash
./mvnw spotless:apply
```

## License

MIT. See [`LICENSE`](LICENSE).
