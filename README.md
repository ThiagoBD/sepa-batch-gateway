# SEPA Batch Gateway

Receives ISO 20022 `pain.001.001.09` payment files, validates every instruction against the EPC SEPA rules and returns a `pain.002.001.10` status report. It validates and reports; it never executes payments. Quayside Pay and all data in this repository are fictional and synthetic.

> Status: work in progress (MVP, feature `001-payment-file-intake`). This README grows with each sprint.

## Build

Requirements: JDK 25 and Docker (the tests start PostgreSQL 18 with Testcontainers).

```bash
./mvnw -B verify
```

## Where to look

- `specs/001-payment-file-intake/`: spec, plan, data model, contracts and tasks
- `.specify/memory/constitution.md`: the project's non-negotiable principles
- `AGENTS.md`: commands, rules and the AI policy for coding agents
