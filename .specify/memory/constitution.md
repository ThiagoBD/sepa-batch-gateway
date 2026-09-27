<!--
Sync Impact Report
- Version change: 1.1.0 → 1.2.0
- Modified principles: none (II, III and VIII keep their text; a new Development Workflow rule says when they must hold before the first release)
- Modified sections: Additional Constraints (the stack list adds Maven, GNU Make, Dependabot, GitHub Pages and Spec Kit, which the plan already used, and says that command-line tools used only in manual checks are not part of the stack); Development Workflow (adds pre-release increments and one pull request per task)
- Reason: /speckit-analyze on 2026-09-26 reported D1 (the merges of T-01 to T-03 break II, III and VIII until T-02 and T-04 land) and D2 (tools in use were missing from the stack list)
- Templates: plan.md (Constitution Check, Complexity Tracking) and data-model.md for 001-payment-file-intake, and AGENTS.md, updated in the same change
- Follow-up TODOs: none
-->

# SEPA Batch Gateway Constitution

## Core Principles

### I. Money Is Exact

Amounts MUST be `BigDecimal` in Java and `numeric` in PostgreSQL. `double` and `float` are forbidden in the domain, and an ArchUnit rule enforces it. JSON MUST carry money as decimal strings. Amounts are compared with `compareTo`, never `equals`.

### II. Never Pay Twice

Idempotency MUST live in database constraints (UNIQUE and partial unique indexes), not only in application code. Every upload path MUST have a concurrency test that proves a duplicate cannot be created.

### III. All or Nothing per File

A file MUST be stored with its final status in one transaction, or not at all. An unexpected error MUST roll the whole file back and MUST never turn into "accepted".

### IV. Deterministic Validation

The same file on the same business date (Europe/Dublin) MUST always produce the same result and the same reason codes. The business date comes from an injected `Clock`, never from the system time read inside a rule. Each rejected item carries exactly one ISO 20022 reason, chosen by a documented precedence. Validation uses no LLM and no heuristics.

### V. The Standard Is the Contract

Input MUST validate against the `pain.001.001.09` XSD before any business rule runs. Every `pain.002` the service produces MUST validate against `pain.002.001.10` in tests.

### VI. Every API Has OpenAPI

`api/openapi.yaml` is versioned, and a test MUST fail when the code drifts from it. Errors MUST follow RFC 9457 (`application/problem+json`).

### VII. Every Behavior Change Has an Integration Test

A feature merges only with a Testcontainers test against real PostgreSQL. Every acceptance criterion MUST map to a test named `usXX_acY_*`. Build and configuration changes are proven by the CI pipeline, not by tests of their own.

### VIII. Secure by Default

No DTDs and no external entities in XML parsing. Size limits apply before parsing. Secrets come only from the environment. Every read and write is scoped by client, and another client's resource answers 404.

### IX. Personal Data Stays Out of Logs

Logs MUST NOT contain person names, full IBANs, file content or API keys. IBANs in logs are always masked.

### X. The Domain Has No Framework

Validation rules are plain Java with no Spring or JAXB imports. Modules talk only through ports, and ArchUnit enforces the boundaries.

### XI. LLMs Stay Off the Money Path

No LLM runs in this service. Coding agents follow `AGENTS.md`, and whoever writes the code of a subtask MUST NOT write its test.

### XII. Decisions Are Written Down

Any choice with a real alternative gets an ADR in `docs/adr/` before the PR merges.

## Additional Constraints

- Stack: Java 25, Spring Boot 4.1, Spring Security 7, PostgreSQL 18, Flyway, Jakarta XML Binding 4 with StAX, springdoc-openapi 3.x, Maven (through the wrapper), GNU Make, Docker Compose, GitHub Actions, Dependabot and GitHub Pages (static API documentation only). Tests: JUnit 5, AssertJ, Testcontainers 2, ArchUnit and JaCoCo. Scripts in `tools/`: Python 3 (standard library only) and Bash. Workflow: Spec Kit. Command-line tools used only in manual checks (`curl`, `openssl`, `xmllint`, `psql`) are not part of the stack. A technology outside these lists needs an ADR before it is used.
- Test and demo data are 100% synthetic. No real names, IBANs or payroll data, ever.
- The MVP runs locally with Docker Compose and GitHub Actions, at no cloud cost.
- Applied Flyway migrations are never edited; every schema change is a new migration.

## Development Workflow

- Spec-driven: constitution → spec → plan → tasks, under `specs/NNN-feature/`. `/speckit-analyze` runs before a task is closed, and its CRITICAL findings block the merge.
- Every subtask has an AI mode, listed in `tasks.md`: `HAND` (a human writes the production code), `AI+REVIEW` (an agent writes the code and a human reviews every line and writes the test) or `AI-TUTOR` (a human writes it and the agent explains and reviews).
- One task is one pull request: its subtasks are commits on the task's branch, and the PR merges when the task's Definition of Done holds.
- Pre-release increments: before the first release tag (`v0.1.0`), a task may merge a capability that a later task completes, even if principles II, III or VIII do not hold for it yet, when all of the following are true: the gap, the principle it affects and the task that closes it are listed in the feature plan's Complexity Tracking; the service runs only on developer machines and in CI; all data is synthetic. Every release tag MUST satisfy every principle.
- Commits follow Conventional Commits. Each PR passes the checklist in `AGENTS.md`.
- The repository contains product work only. Study spikes, scratch experiments and personal notes stay outside it.

## Governance

This constitution supersedes other practices in this repository. An amendment is a PR that updates this file, explains the reason in the PR description and, when it changes an architectural decision, adds or revises an ADR. Versions follow semantic versioning: MAJOR when a principle is removed or redefined, MINOR when a principle or section is added, PATCH for wording. Compliance is checked in every PR review and by `/speckit-analyze`.

**Version**: 1.2.0 | **Ratified**: 2026-09-26 | **Last Amended**: 2026-09-26
