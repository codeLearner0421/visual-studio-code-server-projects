# E-Commerce Backend — Learning Plan

## How this works

1. Pick the next step. Read its **Goal**, **Concepts**, and **Acceptance checks**.
2. **You write the code.** No code comes from me unless you ask.
3. Run the acceptance checks yourself first.
4. Tell me the step is done. I review against the checks and your global review
   rules: side-by-side comparison, the *why*, pros and cons, pitfalls.
5. Ask anything at any point. Each step lists questions worth asking — they are
   prompts, not a quiz.

Steps are sized ~30–60 minutes. If one takes 3 hours, stop and ask — the step
was probably hiding a concept that deserves its own explanation.

**Ground rule:** do not skip Phase 1. It is the only phase with no framework,
and it is the reason the rest will make sense.

---

## Phase 1 — HTTP From Scratch

**Directory:** `phase-01-http-from-scratch/`
**No dependencies. No Spring. Plain `java`.**

**Why this phase exists:** Spring Boot turns an HTTP request into a method
parameter and hides roughly 2,000 lines of machinery doing it. If you never see
that machinery, `@RestController` is magic, and magic is impossible to debug.
After this phase you will know exactly what Spring is doing for you.

### Step 1.1 — Accept a connection

**Goal:** A process that listens on a TCP port and prints whatever a client sends.

**Concepts:** `ServerSocket`, `accept()` blocking, TCP vs HTTP, ports, what
"listening" actually means.

**Acceptance checks:**
- Program starts, prints something like `listening on 8080`, does not exit.
- `curl -v http://localhost:8080/hello` causes the raw request text to print to
  your console.
- The printed text starts with `GET /hello HTTP/1.1` and includes `Host:` and
  `User-Agent:` headers you never wrote.
- `curl` hangs waiting (you send no response yet). That hang is correct here.

**Ask me about:** Why does `accept()` block? What is the difference between a
port being closed, filtered, and listening? Why does `curl` hang instead of
erroring?

### Step 1.2 — Parse the request line

**Goal:** Extract method, path, and HTTP version from the first line.

**Concepts:** Request line grammar, `BufferedReader` over the socket stream,
CRLF line endings.

**Acceptance checks:**
- `GET /products HTTP/1.1` prints `method=GET path=/products version=HTTP/1.1`.
- `curl -X POST localhost:8080/products` prints `method=POST`.
- `curl "localhost:8080/products?page=2&size=10"` — decide and state what your
  `path` holds. Query string separated or not? Either is fine; know which.
- Sending garbage (`printf 'nonsense\r\n\r\n' | nc localhost 8080`) does not
  crash the server process.

**Ask me about:** Why is the line terminator `\r\n` and not `\n`? What breaks if
you split on whitespace naively? What is the max request line length and why do
servers cap it?

### Step 1.3 — Parse headers and body

**Goal:** Read all headers into a map, stop at the blank line, then read the body
if there is one.

**Concepts:** Header block termination, `Content-Length`, case-insensitive header
names, why you cannot just "read until end of stream".

**Acceptance checks:**
- All headers print as key/value pairs.
- Header lookup is case-insensitive: `Content-Type` and `content-type` both hit.
- `curl -X POST localhost:8080/products -H 'Content-Type: application/json' -d '{"name":"Mouse"}'`
  prints the body exactly, with no missing or extra characters.
- A `GET` with no body does **not** hang waiting for one.

**Ask me about:** Why does reading until EOF deadlock on a keep-alive
connection? What is `Transfer-Encoding: chunked` and why does it exist? What
happens if `Content-Length` lies?

### Step 1.4 — Write a valid response

**Goal:** Send a well-formed HTTP response that `curl` accepts and terminates on.

**Concepts:** Status line, response headers, the blank line, `Content-Length`,
`Content-Type`, flushing and closing.

**Acceptance checks:**
- `curl -v localhost:8080/health` returns `HTTP/1.1 200 OK` and exits
  immediately — no hang.
- Response has correct `Content-Length` matching the body byte count (not
  character count — try a body with a non-ASCII character such as `café`).
- `curl -s localhost:8080/products | jq .` parses as valid JSON.
- Browser at `http://localhost:8080/health` renders without error.

**Ask me about:** Why does an off-by-one `Content-Length` hang the client? Bytes
vs characters in UTF-8 — where does this bite in real APIs? Why do responses
need `Content-Type` at all?

### Step 1.5 — Routing, 404, and 405

**Goal:** Dispatch on method + path. Return correct status codes for misses.

**Concepts:** Routing tables, status code semantics, the `Allow` header.

**Acceptance checks:**
- `GET /products` → `200` with a hardcoded JSON product list.
- `GET /products/1` → `200` single product; `GET /products/999` → `404`.
- `GET /nonsense` → `404`.
- `DELETE /products` → `405` **and** includes an `Allow: GET, POST` header.
- Every response body is JSON, including the errors.

**Ask me about:** When is `404` wrong and `400` right? Why does `405` require
`Allow`? How does Spring's `RequestMappingHandlerMapping` do this same job?

### Step 1.6 — Concurrency

**Goal:** Serve multiple clients at once instead of one at a time.

**Concepts:** Thread-per-connection, thread pools, `ExecutorService`, blocking IO
cost, virtual threads (Java 21).

**Acceptance checks:**
- Add an artificial `/slow` endpoint that sleeps 3 seconds.
- **Before** the change: two parallel `curl` calls to `/slow` take ~6s total.
  Prove it with `time`.
- **After** the change: the same two take ~3s. Prove it.
- Server survives 100 concurrent requests without dying
  (`seq 100 | xargs -P 50 -I{} curl -s localhost:8080/products > /dev/null`).
- You can state your thread pool size and justify the number.

**Ask me about:** Why not one thread per connection forever? What is the C10K
problem? How do Java 21 virtual threads change this answer? What does Tomcat
default to, and why?

### Phase 1 exit criteria

You can explain, without notes, what happens between `curl` being typed and
bytes appearing on screen — every step, including the parts you did not write.

---

## Phase 2 — REST API with Spring Boot

**Directory:** `shop-api/` (created this phase)

**Why now:** You have earned the abstraction. Every annotation in this phase
replaces code you wrote by hand in Phase 1.

### Step 2.1 — Generate and understand the project

**Goal:** A running Spring Boot app you can explain line by line.

**Concepts:** Spring Initializr, Maven wrapper, `pom.xml`, starter dependencies,
dependency management vs dependencies, the fat jar.

**Acceptance checks:**
- `./mvnw spring-boot:run` starts and logs a Tomcat port.
- You can explain what each dependency in `pom.xml` pulls in.
- You can explain what `spring-boot-starter-parent` gives you.
- `./mvnw clean package` produces a jar; `java -jar target/*.jar` runs it.
- You can point at the exact log line where Tomcat starts and connect it to
  Step 1.1.

**Ask me about:** What is auto-configuration and how does it decide? Why a
wrapper instead of installed Maven? What is actually inside the fat jar?

### Step 2.2 — First controller, compared to Phase 1

**Goal:** Reimplement Step 1.5's routing with Spring.

**Concepts:** `@RestController`, `@GetMapping`, `@PathVariable`,
`@RequestParam`, automatic JSON serialization.

**Acceptance checks:**
- `GET /products` and `GET /products/{id}` behave identically to Phase 1.
- You wrote **zero** JSON serialization code.
- You can name which Phase 1 step each annotation replaced.

**Ask me about:** Where did Jackson come from? What does `@RestController` add
over `@Controller`? How does Spring pick the return content type?

### Step 2.3 — Domain model, in memory

**Goal:** `Product` and `Category` with an in-memory store. No database yet.

**Concepts:** Records vs classes, immutability, package-by-feature vs
package-by-layer, `BigDecimal` for money.

**Acceptance checks:**
- Price is `BigDecimal`. Never `double`. Never `float`.
- `0.1 + 0.2` in `double` — run it, see the answer, understand why money is
  banned from floating point.
- Full CRUD works against the in-memory store.
- Your package layout is a deliberate choice you can defend.

**Ask me about:** Why is `BigDecimal` correct and `double` catastrophic for
money? What scale and rounding mode should prices use? Records vs classes for
domain objects?

### Step 2.4 — DTOs and the mapping boundary

**Goal:** Stop exposing domain objects directly over HTTP.

**Concepts:** Request vs response DTOs, over-posting, coupling your API to your
schema, mapping strategies.

**Acceptance checks:**
- Create and update take request DTOs, not domain objects.
- Responses are response DTOs.
- A client cannot set `id` or `createdAt` by including them in a POST body —
  prove it with `curl`.
- You can articulate the cost of this layer, not just the benefit.

**Ask me about:** When are DTOs overkill? What is a mass-assignment
vulnerability? MapStruct vs manual mapping vs constructors?

### Step 2.5 — Validation

**Goal:** Reject bad input at the edge with useful messages.

**Concepts:** `jakarta.validation`, `@Valid`, constraint annotations, custom
validators, field vs cross-field validation.

**Acceptance checks:**
- Empty product name → `400`, not `500`.
- Negative price → `400`.
- Price with 3 decimal places → `400` (decide your rule, enforce it).
- Response names the offending **field**, not just "invalid request".
- Multiple bad fields report **all** errors, not just the first.

**Ask me about:** Where should validation live — DTO, service, or database?
What can annotations not express? Why is a `500` for bad input a bug?

### Step 2.6 — Error handling

**Goal:** One consistent error shape across the whole API.

**Concepts:** `@RestControllerAdvice`, `ProblemDetail` (RFC 7807), exception
translation, not leaking internals.

**Acceptance checks:**
- Every error response has the same JSON shape.
- Stack traces never reach the client — check a deliberately thrown exception.
- Unknown product → `404` with a useful message.
- Validation failure → `400` with field details.
- Unhandled exception → `500`, generic message to client, **full detail in the
  server log**.

**Ask me about:** How much detail is safe in an error message? What is RFC 7807?
Why is a leaked stack trace a security issue and not just untidy?

### Step 2.7 — REST semantics

**Goal:** Get the HTTP contract right, not just "it returns JSON".

**Concepts:** Status code correctness, `Location` header, PUT vs PATCH,
idempotency, safe methods.

**Acceptance checks:**
- `POST /products` → `201` with a `Location` header pointing at the new resource.
- `PUT` twice with the same body → same end state (idempotent). Prove it.
- `DELETE` twice → decide `204` then `404`, or `204` twice. Justify your choice.
- `GET` never mutates anything.
- You can explain `PUT` vs `PATCH` on your own endpoints.

**Ask me about:** Is `200` with an error body ever acceptable? What does
idempotency mean for retries and network failures? When does `202` apply?

---

## Phase 3 — Persistence

**Where most backend bugs actually live.**

### Step 3.1 — First entity with JPA and H2

**Goal:** Products survive a restart.

**Concepts:** JPA vs Hibernate vs Spring Data, `@Entity`, `@Id`, generation
strategies, `JpaRepository`, the persistence context.

**Acceptance checks:**
- Create a product, restart the app, `GET` still returns it.
- You can read the generated SQL in the logs (`show-sql` on).
- You can explain the difference between JPA, Hibernate, and Spring Data JPA.

**Ask me about:** Why is `GenerationType.AUTO` risky on Postgres? What is the
persistence context and why does it surprise people? Repository vs DAO?

### Step 3.2 — Flyway migrations

**Goal:** Schema managed by versioned SQL, not by Hibernate guessing.

**Concepts:** `ddl-auto` and why it is dangerous, migration versioning,
immutability of applied migrations, `flyway_schema_history`.

**Acceptance checks:**
- `spring.jpa.hibernate.ddl-auto=validate` — app refuses to start on mismatch.
- Schema is created entirely by `V1__*.sql`.
- Adding a column means a new `V2__*.sql`, never editing `V1`.
- You can explain what happens if a teammate edits an applied migration.

**Ask me about:** Why is `ddl-auto=update` a production incident waiting to
happen? How do you write a migration that is safe to run against a live table?
Flyway vs Liquibase?

### Step 3.3 — Relations and the N+1 problem

**Goal:** `Category 1—N Product`, and see N+1 with your own eyes.

**Concepts:** `@ManyToOne`, `@OneToMany`, `mappedBy`, owning side, `FetchType`
LAZY vs EAGER, lazy initialization exceptions.

**Acceptance checks:**
- Seed 20 products across 3 categories.
- `GET /products` returning category names fires **21 queries**. Count them in
  the log. Do not fix it yet — see it first.
- You can explain which side owns the foreign key and why it matters.
- You have hit `LazyInitializationException` at least once deliberately.

**Ask me about:** Why is `EAGER` the wrong default fix? What is an open session
in view and why is it controversial? How does the owning side decide the SQL?

### Step 3.4 — Order aggregate

**Goal:** `Order` and `OrderItem` modelled as a unit.

**Concepts:** Aggregates, `cascade`, `orphanRemoval`, composition vs
association, denormalising price at purchase time.

**Acceptance checks:**
- Saving an `Order` saves its items in one service call.
- Removing an item from the order's collection deletes the row.
- `OrderItem` stores the price **at time of purchase**, not a live link to the
  product price. You can explain why that is not redundant data.
- Deleting a product does not corrupt historical orders.

**Ask me about:** When is denormalisation correct rather than lazy? What is an
aggregate root? Which cascade types are safe?

### Step 3.5 — Transactions

**Goal:** Multi-step operations are all-or-nothing.

**Concepts:** `@Transactional`, propagation, rollback rules, checked vs
unchecked exceptions, self-invocation proxy trap.

**Acceptance checks:**
- Checkout decrements stock **and** creates the order, or does neither.
- Force a failure mid-checkout — prove no partial state persisted.
- You have hit the self-invocation trap (calling a `@Transactional` method from
  the same class and watching it do nothing) and can explain the proxy cause.
- You know which exceptions roll back by default. It is not the intuitive one.

**Ask me about:** Why does a checked exception not roll back by default? What do
the propagation modes actually do? Why does `@Transactional` sometimes silently
do nothing?

### Step 3.6 — The stock race condition

**Goal:** Two buyers, one unit in stock. Only one order succeeds.

**Concepts:** Lost updates, isolation levels, optimistic locking (`@Version`),
pessimistic locking (`SELECT ... FOR UPDATE`), retry strategy.

**Acceptance checks:**
- **First, reproduce the bug.** Two concurrent checkouts on stock=1 both
  succeed, stock goes to -1. Get this failure on purpose.
- Fix it. Rerun the concurrent test. One succeeds, one fails cleanly with a
  sensible status code.
- Stock never goes negative under 50 concurrent attempts.
- You can explain your choice of optimistic vs pessimistic for this case.

**Ask me about:** Optimistic vs pessimistic — how do you actually choose? What
do the four isolation levels prevent? How should the client handle a lock
failure? This is the most interview-relevant step in the whole plan.

### Step 3.7 — Postgres via Docker

**Goal:** Real database, not H2.

**Concepts:** Docker basics, `docker compose`, connection strings, connection
pooling (HikariCP), dialect differences.

**Acceptance checks:**
- `docker compose up -d` gives you Postgres.
- App connects, Flyway migrations run against it.
- Something that worked on H2 behaves differently on Postgres — find at least
  one difference and record it.
- You can explain what HikariCP is doing and why pool size is not "as big as
  possible".

**Ask me about:** Why is H2-in-tests / Postgres-in-prod a trap? How do you size
a connection pool? What breaks when the pool is exhausted?

### Step 3.8 — Money, properly

**Goal:** Prices that survive accountants.

**Concepts:** `NUMERIC(19,4)`, scale and precision, rounding modes, currency,
total calculation.

**Acceptance checks:**
- Column type is `NUMERIC`/`DECIMAL`, never `float`/`double`/`real`.
- Order total of 3 × `19.99` is exactly `59.97`.
- Rounding mode is explicit in code, not defaulted.
- You can explain why storing amounts in minor units (cents as `BIGINT`) is the
  other valid answer, and when you would pick it.

**Ask me about:** `BigDecimal.equals` vs `compareTo` — the bug that catches
everyone. How do real systems store multi-currency amounts?

---

## Phase 4 — Architecture and Tests

### Step 4.1 — Layering

**Goal:** Controllers get thin, services own the rules.

**Concepts:** Controller/service/repository responsibilities, dependency
direction, constructor injection, where business logic must not live.

**Acceptance checks:**
- No controller touches a repository directly.
- No business rule lives in a controller.
- All injection is constructor-based, no `@Autowired` on fields.
- You can state, per layer, what belongs there and what does not.

**Ask me about:** Why is field injection discouraged? Is a service layer always
worth it? Package-by-layer vs package-by-feature at scale?

### Step 4.2 — Unit tests for services

**Goal:** Test business rules with no Spring context.

**Concepts:** JUnit 5, Mockito, test naming, arrange/act/assert, what is worth
mocking.

**Acceptance checks:**
- Checkout rules covered: happy path, insufficient stock, unknown product,
  empty cart.
- Tests run in milliseconds — no Spring context loaded.
- Test names describe behaviour, not method names.
- You can explain why you mocked each thing you mocked.

**Ask me about:** What should never be mocked? Are these tests worth maintaining
or are they testing the mocks? How much coverage is honest?

### Step 4.3 — Controller tests

**Goal:** Test the HTTP contract without a database.

**Concepts:** `@WebMvcTest`, `MockMvc`, slice tests, `@MockBean`.

**Acceptance checks:**
- Status codes, JSON shape, and validation errors all asserted.
- Runs without a database.
- A deliberate change to a response field breaks a test.

**Ask me about:** What does a slice test actually load? When is `MockMvc` lying
to you compared to a real server?

### Step 4.4 — Repository tests

**Goal:** Verify your queries and mappings.

**Concepts:** `@DataJpaTest`, test transactions and rollback, custom queries.

**Acceptance checks:**
- Custom queries covered.
- Each test starts from a known clean state.
- You can explain why these tests roll back automatically.

### Step 4.5 — Integration tests with Testcontainers

**Goal:** Test against real Postgres, automatically.

**Concepts:** `@SpringBootTest`, Testcontainers, container lifecycle, test
speed trade-offs.

**Acceptance checks:**
- A full checkout flow runs end to end against a containerised Postgres.
- Tests pass on a machine with no local Postgres installed.
- Migrations run as part of the test.
- You can state your unit/integration ratio and defend it.

**Ask me about:** Where is the honest line between unit and integration tests?
What does the test pyramid get wrong in practice?

### Step 4.6 — Test the race condition

**Goal:** Automate the Step 3.6 proof.

**Acceptance checks:**
- A test spawns N concurrent checkouts against stock=1.
- Exactly one succeeds. Asserted, not eyeballed.
- The test fails if you revert your locking fix. **Verify this by reverting.**

---

## Phase 5 — Authentication and Authorization

> Security steps get reviewed more strictly than the rest. Getting auth "working"
> and getting auth *right* are different milestones, and the gap is where real
> breaches live.

### Step 5.1 — Spring Security basics

**Goal:** Understand the filter chain before configuring it.

**Concepts:** `SecurityFilterChain`, filter ordering, authentication vs
authorization, the default login you did not ask for.

**Acceptance checks:**
- You can list the filters in the chain and say what each does.
- Public endpoints (product browsing) and protected ones are explicitly separated.
- You can explain where in the chain your own filter would go.

### Step 5.2 — Users and password storage

**Goal:** Registration that stores credentials safely.

**Concepts:** BCrypt, salting, work factor, `UserDetailsService`, timing attacks.

**Acceptance checks:**
- Passwords stored BCrypt-hashed. Verify directly in the database.
- The same password registered twice produces **different** hashes. Confirm this
  and understand why.
- Password never appears in any log, response, or `toString()`.
- Login failure message does not reveal whether the email exists.

**Ask me about:** Why BCrypt rather than SHA-256? What work factor, and what does
it cost? Why does user enumeration matter?

### Step 5.3 — JWT

**Goal:** Stateless authenticated requests.

**Concepts:** JWT structure, signing vs encryption, expiry, refresh tokens,
where to store the token, revocation.

**Acceptance checks:**
- Login returns a token; protected endpoints accept it.
- Expired token → `401`. Test with a short expiry.
- Tampered payload → `401`. Edit one character and confirm.
- Signing secret is **not** in the repository.
- You can decode your own token at jwt.io and explain every claim.

**Ask me about:** Why can anyone read a JWT payload? How do you revoke a
stateless token — and what does that cost? Sessions vs JWT: which was actually
right for this app?

### Step 5.4 — Roles

**Goal:** Customers and admins have different power.

**Concepts:** Authorities vs roles, `@PreAuthorize`, method vs URL security.

**Acceptance checks:**
- Only `ADMIN` can create or delete products → `403` for customers.
- Any authenticated user can place an order.
- `403` (authenticated, not allowed) vs `401` (not authenticated) used correctly.

### Step 5.5 — Ownership

**Goal:** Customer A cannot read Customer B's orders.

**Concepts:** Horizontal privilege escalation / IDOR, ownership checks, where
the check belongs.

**Acceptance checks:**
- `GET /orders/{id}` for someone else's order → `404` or `403`. Decide which,
  and justify it — the choice leaks information either way.
- Attempt this deliberately with two real accounts and confirm it fails.
- The check is enforced in the service layer, not only in the controller.

**Ask me about:** What is IDOR and why is it consistently in the OWASP Top 10?
`403` vs `404` for someone else's resource — what does each leak?

### Step 5.6 — Security review

**Goal:** Find what you got wrong.

**Acceptance checks:**
- Walk the OWASP Top 10 against your own app and write findings down.
- No secrets in git history — verify, do not assume.
- Error responses leak nothing about internals.
- Dependencies scanned for known CVEs.

---

## Phase 6 — Scale and Operability

### Step 6.1 — Pagination

**Concepts:** `Pageable`, offset vs keyset pagination, total count cost, stable
sort order.

**Acceptance checks:**
- `GET /products?page=0&size=20&sort=name,asc` works.
- Response includes total elements and total pages.
- Page size is capped server-side — `size=1000000` does not fetch everything.
- You can explain why deep offset pagination degrades, and what keyset fixes.

### Step 6.2 — Filtering and search

**Concepts:** Query parameters, `Specification`, dynamic queries, SQL injection
(and why JPQL parameters save you).

**Acceptance checks:**
- Filter by category, price range, and name substring, in any combination.
- No string-concatenated SQL anywhere.
- You attempted an injection payload against your own filter and it failed safely.

### Step 6.3 — Caching

**Concepts:** `@Cacheable`, cache keys, eviction, TTL, staleness, Caffeine vs Redis.

**Acceptance checks:**
- Product catalogue reads hit cache — prove it with query logs.
- Updating a product evicts the right entry. Prove staleness is gone.
- You can state your TTL and defend it.
- You can name what must **never** be cached in this app.

**Ask me about:** Cache invalidation strategies and why the joke about it is
true. When does caching actively hurt?

### Step 6.4 — Fix N+1 properly

**Concepts:** `JOIN FETCH`, `@EntityGraph`, `@BatchSize`, DTO projections,
cartesian product with multiple collections.

**Acceptance checks:**
- The Step 3.3 endpoint now fires a bounded number of queries. Count them.
- You tried `JOIN FETCH` on two collections and saw the cartesian explosion.
- You can explain which fix suits which shape of query.

### Step 6.5 — Async work

**Concepts:** `@Async`, thread pools, fire-and-forget risk, outbox pattern,
message queues.

**Acceptance checks:**
- Order confirmation does not block the checkout response.
- A failure in the async task does not silently vanish — prove it is logged.
- You can explain why `@Async` alone is not durable, and what the outbox pattern
  fixes.

### Step 6.6 — Observability

**Concepts:** Actuator, health checks, structured logging, correlation IDs,
metrics, log levels.

**Acceptance checks:**
- `/actuator/health` reports database status.
- Logs are structured (JSON) and include a correlation ID per request.
- One request is traceable across all its log lines by that ID.
- Custom metric exists: orders placed, checkout latency, or similar.
- Sensitive actuator endpoints are not publicly exposed.

**Ask me about:** What do you actually want in production logs at 3am? Which
metrics matter and which are noise?

### Step 6.7 — Rate limiting

**Concepts:** Token bucket, per-user vs per-IP, `429`, `Retry-After`.

**Acceptance checks:**
- Login endpoint rate-limited per IP.
- Exceeding it → `429` with `Retry-After`.
- Legitimate traffic is unaffected — verify you did not break normal use.

---

## Phase 7 — Deployment

### Step 7.1 — Configuration and profiles

**Concepts:** `application.yml`, profiles, property precedence, 12-factor config.

**Acceptance checks:**
- `dev` and `prod` profiles differ meaningfully.
- No environment-specific value is hardcoded in Java.
- You can override any property with an environment variable.

### Step 7.2 — Secrets

**Concepts:** Secret handling, env vars, `.gitignore`, what git history remembers.

**Acceptance checks:**
- No credential in any committed file.
- `git log -p | grep -i -E 'password|secret|key'` finds nothing meaningful.
- App fails fast and loudly if a required secret is missing.
- You can explain why `.gitignore` does not help once a secret is committed.

### Step 7.3 — Docker image

**Concepts:** Layered jars, multi-stage builds, base image choice, non-root user,
image size.

**Acceptance checks:**
- `docker build` produces a working image.
- Multi-stage build — the JDK is not in the final image.
- Container runs as a non-root user. Verify with `docker exec ... whoami`.
- You can state your image size and what dominates it.

### Step 7.4 — Compose

**Acceptance checks:**
- `docker compose up` gives a working app plus database from a clean machine.
- App waits for the database to be genuinely ready, not just started.
- Data survives `docker compose restart`.

### Step 7.5 — Production readiness

**Concepts:** Graceful shutdown, health probes, resource limits, JVM tuning.

**Acceptance checks:**
- In-flight requests complete during shutdown rather than being cut off.
- Liveness and readiness are **separate** checks — you can explain the difference.
- Container has memory limits and the JVM respects them.

### Step 7.6 — CI

**Acceptance checks:**
- GitHub Actions runs build and tests on push.
- A failing test fails the pipeline. Verify by breaking one on purpose.
- Build is reproducible from a clean checkout.

### Step 7.7 — Final review

Full review together: architecture, security, performance, tests, operability.
Then the honest question — what would you build differently starting over?

---

## Questions worth asking at any time

- "Why is this the idiomatic way in Spring?"
- "How would this break at 100× the traffic?"
- "What is the frontend consequence of this API decision?"
- "What would a reviewer flag here?"
- "Show me how you would have written this." *(after I have reviewed yours)*
- "Is this over-engineered for the actual requirement?"

## Progress

Tracked in [PROGRESS.md](PROGRESS.md).
