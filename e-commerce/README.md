# E-Commerce Backend — Learning Track

One application, grown across 7 phases, from a hand-written HTTP server to a
deployed Spring Boot service.

**Stack:** Java 21, Spring Boot 3.x, Maven (via wrapper), Postgres, Docker.

## Why e-commerce

The domain forces the hard problems instead of letting you avoid them:

- **Stock decrement is a race condition.** Two buyers, one unit left. This is
  the cleanest real-world reason to learn transactions, isolation levels and
  optimistic locking — concepts that stay abstract in a CRUD app.
- **An order is a state machine.** `PENDING → PAID → SHIPPED → DELIVERED`, plus
  cancellation and refund paths. Teaches why business state belongs in a service
  layer, not in a controller.
- **Money is not a `double`.** Forces `BigDecimal`, rounding rules, and currency
  handling early.
- **Reads and writes have different shapes.** Product catalogue is read-heavy
  (caching, pagination, search); checkout is write-heavy and correctness-
  critical. Same app, two profiles — a realistic reason to care about both.

## Documents

- **[PLAN.md](PLAN.md)** — the working document. Every step, its concepts, and
  its acceptance checks. Start here.
- **[PROGRESS.md](PROGRESS.md)** — checklist and notes log.

## Phases

| Phase | Directory | Topic | Core lessons |
|-------|-----------|-------|--------------|
| 0 | — | Toolchain | JDK 21, jenv, Maven wrapper, `pom.xml` anatomy |
| 1 | `phase-01-http-from-scratch/` | HTTP by hand | Sockets, request parsing, response framing, thread-per-connection |
| 2 | `shop-api/` | REST API | Controllers, DTOs, validation, status codes, error handling |
| 3 | `shop-api/` | Persistence | JPA, H2 → Postgres, Flyway, transactions, N+1 |
| 4 | `shop-api/` | Architecture & tests | Layering, JUnit 5, MockMvc, Testcontainers |
| 5 | `shop-api/` | Auth | Spring Security, JWT, roles, password hashing |
| 6 | `shop-api/` | Scale | Caching, pagination, async, queues, Actuator |
| 7 | `shop-api/` | Deploy | Fat jar, Docker, profiles, config & secrets, CI |

## Domain model (target shape)

Built incrementally — do not create all of this in phase 2.

```
Customer 1──N Order 1──N OrderItem N──1 Product N──1 Category
                 │
                 └──1 Payment
```

## Rule for this track

Phase 1 uses **no framework at all**. Spring Boot hides the entire request
lifecycle behind annotations. Writing the socket loop once means every later
abstraction is a shortcut you recognise, rather than magic you trust.

## Naming note

The directory is `e-commerce` (hyphen, filesystem convention). The Java package
is `ecommerce` — package names cannot contain hyphens. This mismatch is normal
and appears in most real Maven projects.
