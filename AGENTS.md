# AGENTS.md

Instructions for coding agents working in this repository. Humans: see `README.md`.

SEPA Batch Gateway receives ISO 20022 pain.001.001.09 payment files, validates every instruction against the EPC SEPA rules and returns a pain.002.001.10 status report. It validates and reports; it never executes payments. All data is synthetic.

## Commands

| Task | Command |
| --- | --- |
| Build and run all tests (Testcontainers, needs Docker) | `./mvnw -B verify` |
| Memory proof (10,000 instructions, 64 MB heap) | `./mvnw -B verify -Pmemory-proof` |
| Script tests | `python3 -m unittest discover -s tools` |
| Performance targets (SC-002, SC-003, SC-008 to SC-010) | `tools/measure.sh` (exits non-zero when a target fails) |
| Run the demo end to end | `make demo` (needs `SEPA_DEMO_API_KEY` and `.env`) |
| Start only the database | `docker compose -f docker-compose.yml up -d postgres` |
| Run the app against it | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` |
| Clean everything | `make clean` |

## Stack (pinned)

Java 25 · Spring Boot 4.1 · Spring Security 7 · PostgreSQL 18 · Flyway · Jakarta XML Binding 4 + StAX · springdoc-openapi 3.x · Maven (wrapper) · GNU Make · Testcontainers 2 · JUnit 5 · AssertJ · ArchUnit · JaCoCo · Docker Compose · GitHub Actions · Dependabot · GitHub Pages (API docs only) · Spec Kit. Scripts in `tools/`: Python 3 (standard library only) and Bash. Command-line tools used only in manual checks (`curl`, `openssl`, `xmllint`, `psql`) are not part of the stack.

Spring Boot 4 renamed things; code written for Boot 3 or Testcontainers 1.x does not compile here:

- Starters: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway` (plus `flyway-database-postgresql`); tests use `spring-boot-starter-webmvc-test`, `spring-boot-testcontainers`, `testcontainers-postgresql`.
- HTTP tests use `RestTestClient` with `@AutoConfigureRestTestClient`; databases come from `@ServiceConnection`.
- Spring Security 7 accepts only the lambda DSL.

This list matches the constitution. Adding a dependency or technology not listed here requires an ADR first.

## Where things live

- `specs/001-payment-file-intake/`: spec (user stories, RN-xx rules), plan, data model, contracts, tasks with the mode of each subtask
- `.specify/memory/constitution.md`: non-negotiable principles
- `src/main/java/com/quaysidepay/sepagateway/`: modules `ingestion`, `validation` and `reporting`, each with `domain`, `port`, `application` and `adapter.{web,persistence,xml}`, plus `shared`. Which class goes where: `docs/architecture/04-code-map.md` and the Structure Decision in `specs/001-payment-file-intake/plan.md`
- `src/main/resources/db/migration/`: Flyway migrations; `src/main/resources/xsd/`: official ISO 20022 XSDs
- `api/openapi.yaml`: API snapshot, checked by `OpenApiContractTest`
- `docs/adr/`, `docs/architecture/`, `docs/threat-model.md`, `docs/ai-usage.md`
- `fixtures/`: synthetic pain.001 files; `tools/`: generator and measurement scripts

## Rules

- Money is `BigDecimal` in Java and `numeric` in SQL; never `double` or `float`. Compare with `compareTo`. JSON carries money as strings.
- `domain` packages import nothing from Spring, JAXB or `iso20022`. Outside a module only its `port` and `domain` packages are visible. Allowed directions: `ingestion` → `validation`, `reporting` → `validation`, every module → `shared`; `shared` depends on no module; no cycles. Only `adapter.xml` uses the generated `iso20022` classes. ArchUnit fails the build otherwise.
- The business date comes from an injected `Clock` (zone `Europe/Dublin`); rules never read the system time.
- Never normalise payment data: an IBAN with lowercase letters is rejected (AC03), not upper-cased.
- Every error is RFC 9457 `ProblemDetail` with `correlationId`; no stack traces in responses.
- Never log person names, full IBANs, file content or API keys; mask IBANs with `IbanMasker`.
- XML factories come from `SecureXmlFactories` only (no DTD, no external entities).
- Every read and write filters by the authenticated `client_id`; another client's resource answers 404.
- Idempotency lives in database constraints. Handle a unique violation outside the failed transaction: PostgreSQL aborts the whole transaction on any error.
- `@Transactional` processing uses `rollbackFor = Exception.class` and is called from another bean.
- After `unmarshal(reader, ...)` the StAX cursor already sits after the element: never call `next()` right after it.
- Never edit an applied migration; add a new `V{n}__*.sql`.
- Test names for acceptance criteria follow `usXX_acY_*` and live in `*AcceptanceIT` classes.
- Commits follow Conventional Commits (`feat`, `fix`, `test`, `docs`, `build`, `ci`, `chore`, `refactor`).
- One task is one pull request: each subtask is one commit on the task's branch, and the PR merges when the task's Definition of Done holds.

## AI policy

Each subtask in `specs/*/tasks.md` has a mode. Read it before doing anything:

| Mode | The agent may | The agent must not |
| --- | --- | --- |
| `HAND` | Write the test first from the Given/When/Then; explain; review the human's code | Write or suggest the production code of the subtask |
| `AI+REVIEW` | Write the production code | Write the test of the same subtask (the human writes it) |
| `AI-TUTOR` | Explain, review, point out risks | Write the deliverable (acceptance tests, measurements, docs are written by the human) |

Whoever writes the code of a subtask does not write its test. Acceptance tests (`*AcceptanceIT`, `usXX_acY_*`) are black-box tests of the spec: the human writes them in `AI-TUTOR` subtasks, and the agent reviews them against the spec's Given/When/Then, not against the implementation. No LLM runs inside the service. Record every agent mistake caught by a test or review in `docs/ai-usage.md`.

## Spec Kit

Use `/speckit-specify`, `/speckit-clarify`, `/speckit-plan` and `/speckit-analyze` for specs and plans. `/speckit-analyze` is read-only; CRITICAL findings block closing a task.

`.specify/feature.json` is per checkout and gitignored. After cloning, create it with `{"feature_directory":"specs/001-payment-file-intake"}`; without it the `/speckit-*` commands cannot find feature 001 from `main` or from a task branch.

Do not run `/speckit-tasks` on feature 001 (its `tasks.md` follows the project format). Never run `/speckit-implement` on `HAND` subtasks; on `AI+REVIEW` subtasks, run it for one subtask at a time.

## PR checklist

- [ ] The test was written by whoever did not write the code (subtask mode respected)
- [ ] Money only as `BigDecimal` and `numeric`; no `double`
- [ ] No log with a full IBAN, name, file content or API key
- [ ] New errors in RFC 9457 and in `api/openapi.yaml`
- [ ] Schema change in a new migration, never an edited one
- [ ] Every line generated by an agent was read and can be explained
- [ ] Agent mistakes recorded in `docs/ai-usage.md`
- [ ] No study notes, scratch experiments or personal files in the diff
