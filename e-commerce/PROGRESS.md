# Progress

Mark a step done only after its acceptance checks pass **and** the review is done.

Status: `[ ]` not started · `[~]` in progress · `[x]` reviewed and done

## Phase 0 — Toolchain
- [x] JDK 21 installed (`/opt/homebrew/opt/openjdk@21`)
- [x] jenv installed and activated in `~/.zshrc`
- [x] Project pinned to 21.0.8 via `.java-version`

## Phase 1 — HTTP From Scratch
- [x] 1.1 Accept a connection
- [x] 1.2 Parse the request line
- [ ] 1.3 Parse headers and body
- [ ] 1.4 Write a valid response
- [ ] 1.5 Routing, 404, and 405
- [ ] 1.6 Concurrency

## Phase 2 — REST API
- [ ] 2.1 Generate and understand the project
- [ ] 2.2 First controller
- [ ] 2.3 Domain model, in memory
- [ ] 2.4 DTOs and the mapping boundary
- [ ] 2.5 Validation
- [ ] 2.6 Error handling
- [ ] 2.7 REST semantics

## Phase 3 — Persistence
- [ ] 3.1 First entity with JPA and H2
- [ ] 3.2 Flyway migrations
- [ ] 3.3 Relations and the N+1 problem
- [ ] 3.4 Order aggregate
- [ ] 3.5 Transactions
- [ ] 3.6 The stock race condition
- [ ] 3.7 Postgres via Docker
- [ ] 3.8 Money, properly

## Phase 4 — Architecture and Tests
- [ ] 4.1 Layering
- [ ] 4.2 Unit tests for services
- [ ] 4.3 Controller tests
- [ ] 4.4 Repository tests
- [ ] 4.5 Integration tests with Testcontainers
- [ ] 4.6 Test the race condition

## Phase 5 — Auth
- [ ] 5.1 Spring Security basics
- [ ] 5.2 Users and password storage
- [ ] 5.3 JWT
- [ ] 5.4 Roles
- [ ] 5.5 Ownership
- [ ] 5.6 Security review

## Phase 6 — Scale and Operability
- [ ] 6.1 Pagination
- [ ] 6.2 Filtering and search
- [ ] 6.3 Caching
- [ ] 6.4 Fix N+1 properly
- [ ] 6.5 Async work
- [ ] 6.6 Observability
- [ ] 6.7 Rate limiting

## Phase 7 — Deployment
- [ ] 7.1 Configuration and profiles
- [ ] 7.2 Secrets
- [ ] 7.3 Docker image
- [ ] 7.4 Compose
- [ ] 7.5 Production readiness
- [ ] 7.6 CI
- [ ] 7.7 Final review

---

## Notes log

Record surprises, mistakes, and things worth remembering. Future-you reads this.

| Date | Step | Note |
|------|------|------|
| | | |
