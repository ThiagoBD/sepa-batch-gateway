# Research: Payment File Intake (MVP)

Each decision below becomes an ADR in `docs/adr/` (MADR format), written in the task that first depends on it, so it is merged before or with the code that uses it (constitution XII). This file keeps the short version and the alternatives that were rejected.

| ADR | Written in | Revised in |
| --- | --- | --- |
| 0001 Record decisions, 0002 Modular monolith, 0006 Spring JDBC | T-01.7 | 0006 in T-04.6 (batch measurement) |
| 0003 File idempotency, 0007 API key | T-02.5 | 0003 in T-07.3 (MsgId race) |
| 0004 StAX + JAXB, 0008 Invalid file answers 201 REJECTED | T-03.6 | 0008 in T-06.5 (report content) |
| 0005 One transaction per file | T-04.6 | — |

## Decisions

### D1. Record architecture decisions (ADR-0001)

- **Decision**: MADR files in `docs/adr/`, one per decision with a real alternative.
- **Rationale**: A solo project still has to show its reasoning to whoever reads the repository.
- **Alternatives considered**: no records; a wiki outside the repository.

### D2. Modular monolith with a hexagonal core (ADR-0002)

- **Decision**: One deployable with `ingestion`, `validation` and `reporting` modules; rules in plain Java; boundaries enforced by ArchUnit.
- **Rationale**: Three responsibilities, one developer and one database do not justify network boundaries. Rules stay testable without Spring, and adapters (storage, queue) can be swapped in later versions.
- **Alternatives considered**: plain layers (rules would leak into services); three microservices (network and deployment cost with no benefit at this size).

### D3. File idempotency by content hash and MsgId (ADR-0003)

- **Decision**: SHA-256 of the bytes per client, with a UNIQUE constraint (replay answers 200), plus MsgId unique among a client's non-rejected files through a partial unique index (DU01).
- **Rationale**: A resend after a timeout carries the same bytes; a regenerated file keeps its MsgId. Constraints hold under concurrency where application checks do not.
- **Alternatives considered**: `Idempotency-Key` header (ERPs send files, not keys); filename (changes on every export).
- **Known limit**: a regenerated file with a new MsgId passes; instruction-level deduplication is left for a future ADR.

### D4. StAX cursor with JAXB fragments, XSD in a separate pass (ADR-0004)

- **Decision**: Validate the whole file against the XSD in streaming first; then walk it with StAX and unmarshal only `GrpHdr`, each block header and each `CdtTrfTxInf`.
- **Rationale**: Constant memory for 20 MB today and 100,000 lines later, with typed classes generated from the official XSD.
- **Alternatives considered**: DOM or JAXB on the whole document (memory grows with the file); SAX (no typed classes, more hand-written state).
- **Cost**: the file is read twice; measured in T-08.

### D5. Synchronous processing, one transaction per file (ADR-0005)

- **Decision**: The upload request processes the file and commits once: either the whole file with its final status, or nothing.
- **Rationale**: No broker in the MVP, files up to 20 MB, and a resend after any failure must be safe.
- **Alternatives considered**: commits per chunk (partial files on failure); `@Async` in memory (lost on restart); Spring Batch (planned for V2 with restart, skip and 202 Accepted).
- **Cost**: one connection and row lock held for seconds per file.

### D6. Spring JDBC instead of JPA (ADR-0006)

- **Decision**: `JdbcClient` and `NamedParameterJdbcTemplate.batchUpdate`, SQL written by hand, Flyway for the schema.
- **Rationale**: Bulk inserts with explicit SQL and batch control; no flush or lazy-loading surprises.
- **Alternatives considered**: Spring Data JPA. It is covered in other portfolio projects.

### D7. API key now, OAuth2 later (ADR-0007)

- **Decision**: One random 256-bit key per client, stored as a SHA-256 hash, revocable with an `active` flag; stateless security, CSRF off (no cookies).
- **Rationale**: Security from the first sprint without running an identity provider. A fast hash is enough for a random key; a password would need bcrypt or Argon2.
- **Alternatives considered**: no authentication until later; OAuth2 client credentials now (planned for V2).

### D8. Status report and invalid-file semantics (ADR-0008)

- **Decision**: An invalid file answers 201 with status REJECTED and a pain.002; the report lists only rejected transactions plus `NbOfTxsPerSts`.
- **Rationale**: The file was received, and the client needs a machine-readable report either way. Listing only rejections keeps a 4,000-line report small.
- **Alternatives considered**: 422 with no resource; a report listing every transaction.

## Pinned versions and known pitfalls

| Topic | Choice | Why it matters |
| --- | --- | --- |
| Web starter | `spring-boot-starter-webmvc` | Boot 4 renamed the starters; Boot 3 tutorials do not compile |
| Flyway | `spring-boot-starter-flyway` + `flyway-database-postgresql` | Without the starter, migrations silently do not run |
| Testcontainers | 2.x, `testcontainers-postgresql`, `@ServiceConnection` | Artifact names changed from 1.x |
| HTTP tests | `RestTestClient` with `@AutoConfigureRestTestClient` | Spring Framework 7 test client |
| PostgreSQL image | `postgres:18`, volume at `/var/lib/postgresql` | The data path changed in 18 |
| XML binding | `org.jvnet.jaxb:jaxb-maven-plugin` 4.0.8, one execution and package per message | Avoids name clashes between pain.001 and pain.002 |
| StAX + JAXB | Never call `next()` after `unmarshal(reader, ...)` | The cursor already sits after the element; `next()` skips adjacent transactions |
| Transactions | `rollbackFor = Exception.class`; no self-invocation | Checked exceptions (`IOException`, `JAXBException`) commit by default |
| PostgreSQL errors | Handle unique violations outside the failed transaction | Any error aborts the whole PostgreSQL transaction |
| Streaming reads | `fetchSize` inside a read-only transaction | Without both, the driver loads every row at once |
| OpenAPI | springdoc-openapi 3.x | The line that supports Spring Boot 4 |
| Scripts | Python 3 standard library (`unittest`, no pip packages) for `tools/`, run in CI | Nothing to install; the generator stays reproducible with `--seed` |
| IBAN case | No normalisation: lowercase after the country code passes the XSD pattern `[A-Z]{2}[0-9]{2}[a-zA-Z0-9]{1,30}` and is rejected with AC03 | The gateway reports bad data; it never rewrites payment data |
