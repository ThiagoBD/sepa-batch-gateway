# tasks.md — 001 Payment file intake (MVP)

Spec: `spec.md` · Plan: `plan.md` · Constitution: `.specify/memory/constitution.md`.
One task = one Linear issue (POR-xx, sprint milestones) and one pull request; its subtasks are sub-issues, and each subtask is one commit on the task's branch (constitution 1.2.0). Every code subtask ships with an automated test; config and release subtasks are verified by the check listed and by CI. A fixture file is created by the subtask that first uses it, together with that subtask's test or demo.
Sprints are one week (2026-09-26 → 2026-11-20). Estimates are hours of focused work.
This file uses the project's own format (task `T-XX` = one issue, subtask `T-XX.n`), not the `/speckit-tasks` default; `/speckit-analyze` still checks it against the spec and the plan.

Modes (AI policy, see `AGENTS.md`): `[HAND]` I write all production code; the agent writes the test first from the Given/When/Then and I review it. `[AI+REVIEW]` the agent writes the code; I review every line and write the test (config: I review and run the verification). `[AI-TUTOR]` I write it (acceptance tests, measurements, docs); the agent explains and reviews.

Definition of Done
- Code subtask: compiles, its new test passes, CI green, Conventional Commit, no secrets.
- Config or release subtask: the verification listed passes, CI green, Conventional Commit, no secrets.
- Task (issue): every acceptance criterion passes as an automated test named `usXX_acY_*`, the demo runs, spec and README updated, `/speckit-analyze` reports no CRITICAL finding, PR reviewed with the checklist in `AGENTS.md`.
- Sprint: demo recorded, sprint ADRs written, measured numbers logged, retro in `docs/retros/`.

## Sprint plan

### Sprint 1 (2026-09-26 → 2026-10-02) — Walking skeleton: upload persists to PostgreSQL with JSON logs, size limit and green CI.
T-01.1, T-01.2, T-01.3, T-01.4, T-01.5

### Sprint 2 (2026-10-03 → 2026-10-09) — Finish the skeleton (read + RFC 9457) and protect the API with API keys and per-client isolation.
T-01.6, T-01.7, T-02.1, T-02.2, T-02.3

### Sprint 3 (2026-10-10 → 2026-10-16) — Make uploads idempotent and build hardened XML validation.
T-02.4, T-02.5, T-03.1, T-03.2

### Sprint 4 (2026-10-17 → 2026-10-23) — Reject invalid files with FF01, read the pain.001 header and start streaming parsing.
T-03.3, T-03.4, T-03.5, T-03.6, T-04.1

### Sprint 5 (2026-10-24 → 2026-10-30) — Stream-process instructions in one transaction per file and start the SEPA rules.
T-04.2, T-04.3, T-04.4, T-04.5, T-04.6, T-05.1

### Sprint 6 (2026-10-31 → 2026-11-06) — Complete the SEPA rules with partial acceptance and build the pain.002.
T-05.2, T-05.3, T-05.4, T-05.5, T-06.1

### Sprint 7 (2026-11-07 → 2026-11-13) — Serve the pain.002 and the rejection query, and reject reused MsgIds.
T-06.2, T-06.3, T-06.4, T-06.5, T-07.1

### Sprint 8 (2026-11-14 → 2026-11-20) — Resolve the MsgId race and prove the 4,000-line payroll scenario with measured numbers.
T-07.2, T-07.3, T-08.1, T-08.2, T-08.3

## Issues

### T-01 — Walking skeleton: upload a payment file and read it back (13.5 h)
Linear: POR-6 · Sprints: 1, 2 · Covers: US-01 (AC1, AC3), US-06 (AC1, partial); RN-03 (size limit) · Depends on: —

- [x] T-01.1 Scaffold the repository, the Spring Boot 4.1 project and Spec Kit — S1, 2 h [AI+REVIEW] — config, verify: `./mvnw -B verify` builds and passes; `/speckit-analyze` reports no CRITICAL finding; constitution, spec, plan and tasks of feature 001 are committed. — commit: `chore: scaffold project with Spring Boot 4.1 and Spec Kit`
- [x] T-01.2 Run PostgreSQL in Compose and prepare Testcontainers test support — S1, 1 h [HAND] — config, verify: `docker compose -f docker-compose.yml up -d postgres`, `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`, `curl localhost:8080/actuator/health` → `UP` (the aggregate status includes the database connection); `.env.example` is versioned without values. — commit: `build: add PostgreSQL to Docker Compose and Testcontainers support`
- [ ] T-01.3 Create the payment_file table and POST /v1/payment-files computing SHA-256 while streaming — S1, 3 h [HAND] — test: `ReceivePaymentFileIT.uploadReturns201AndPersistsSha256` (also proves Flyway applied V1); `pom.xml` declares `maven-failsafe-plugin`, so `./mvnw -B verify` runs the `*IT` classes — commit: `feat(ingestion): accept payment file upload and persist sha256`
- [ ] T-01.4 Configure JSON logs, correlation id and the upload size limit — S1, 1.5 h [HAND] — test: `CorrelationIdFilterTest.replacesInvalidHeaderWithGeneratedId`, `UploadLimitIT.fileAbove20MbReturns413Problem`; the 413 `file-too-large` comes from a first `GlobalProblemHandler` that T-01.6 extends — commit: `feat(observability): add JSON logs, correlation id and upload limit`
- [ ] T-01.5 Set up CI and the Docker image for the first time — S1, 2.5 h [HAND] — config, verify: green Actions run (`./mvnw -B verify` with the T-01.3 and T-01.4 ITs, plus `docker build`); `docker compose up` reports the `app` service healthy; also delivers the `Makefile` (`demo`, `test`, `clean`) and `.github/dependabot.yml`. — commit: `ci: add GitHub Actions pipeline and Docker image`
- [ ] T-01.6 Create GET /v1/payment-files/{id}, RFC 9457 errors and the OpenAPI snapshot — S2, 2 h [HAND] — test: `GetPaymentFileIT.unknownIdReturns404ProblemJson`, `.nonUuidIdReturns400`, `UploadRequestIT.missingOrEmptyFilePartReturns400`, `.nonMultipartRequestReturns415`, `OpenApiContractTest.committedSpecMatchesGenerated` — commit: `feat(ingestion): get payment file by id with RFC 9457 errors and OpenAPI snapshot`
- [ ] T-01.7 Write the walking-skeleton acceptance test, update spec and README, and write ADRs 0001, 0002 and 0006 — S2, 1.5 h [AI-TUTOR] — test: `WalkingSkeletonAcceptanceIT.us01_ac1_uploadReturns201WithSha256`, `.us01_ac3_fileOver20MbReturns413`; also creates `docs/architecture/` (`README.md` and `01-system-context.md` to `05-upload-and-processing.md`, the last one up to status RECEIVED; see plan.md) and starts `docs/ai-usage.md` — commit: `test(acceptance): walking skeleton end to end`

### T-02 — Authenticated, client-scoped and content-idempotent upload (11.5 h)
Linear: POR-7 · Sprints: 2, 3 · Covers: US-01 (AC2), US-02 (AC1, AC2), US-07 (AC1, AC2); RN-01, RN-14 · Depends on: T-01

- [ ] T-02.1 Create the api_client table, repository and local demo-key seeding — S2, 2 h [AI+REVIEW] — test: `ApiClientRepositoryIT.findsOnlyActiveClientByKeyHash`; the local demo key comes only from `SEPA_DEMO_API_KEY` and is stored only as its hash — commit: `feat(security): add api_client table and local demo seeding`
- [ ] T-02.2 Authenticate requests with API keys in Spring Security — S2, 3 h [HAND] — test: `ApiKeyAuthenticationIT.missingUnknownOrInactiveKeyReturns401`, `.validKeyReturns201`; every earlier IT, including `WalkingSkeletonAcceptanceIT`, sends a test client's key because `AbstractPostgresIT` sets `X-API-Key` as the default header of its `RestTestClient`, so the acceptance tests do not change — commit: `feat(security): authenticate clients with hashed API keys`
- [ ] T-02.3 Scope payment files by client — S2, 2 h [HAND] — test: `PaymentFileOwnershipIT.otherClientGets404AndAccessDeniedIsLogged` — commit: `feat(security): scope payment files by client`
- [ ] T-02.4 Make uploads idempotent by content hash, including under concurrency — S3, 3 h [HAND] — test: `IdempotentUploadIT.sameBytesTwiceReturnsSameIdAnd200`, `ConcurrentUploadIT.tenParallelIdenticalUploadsCreateOneRow` — commit: `feat(ingestion): make uploads idempotent by content hash`
- [ ] T-02.5 Write the acceptance tests and the idempotency and authentication ADRs — S3, 1.5 h [AI-TUTOR] — test: `AuthenticationAcceptanceIT.us01_ac2_missingOrInvalidKeyReturns401` (+2), `IdempotentUploadAcceptanceIT.us02_ac1_sameBytesReplayReturns200SameId` (+1) — commit: `test(acceptance): authenticated idempotent upload`

### T-03 — Reject invalid or malicious files and read the pain.001 group header (12.5 h)
Linear: POR-8 · Sprints: 3, 4 · Covers: US-01 (AC4), US-04 (AC1, AC2, AC3); RN-03 · Depends on: T-02

- [ ] T-03.1 Add the official XSDs and generate the JAXB model — S3, 2 h [AI+REVIEW] — config, verify: `./mvnw generate-sources` produces `GroupHeader85` and `CreditTransferTransaction34` under `target/generated-sources`; `fixtures/pain001-10tx.xml` is created here and `xmllint --noout --schema src/main/resources/xsd/pain.001.001.09.xsd fixtures/pain001-10tx.xml` validates. The T-03.2 and T-03.5 tests exercise the XSDs and the generated model. — commit: `build(xml): generate JAXB model from ISO 20022 XSDs`
- [ ] T-03.2 Implement hardened, streaming XSD validation — S3, 3 h [HAND] — test: `XsdValidatorTest.rejectsDoctypeAndNeverReadsLocalFile`, `.rejectsBillionLaughsUnder2Seconds`, `.acceptsValidFixture`, `.reportsLineAndColumnOfFirstViolation`; the XXE and billion-laughs inputs live in `fixtures/xxe.xml` and `fixtures/billion-laughs.xml` — commit: `feat(validation): add hardened streaming XSD validation`
- [ ] T-03.3 Reject invalid files with FF01 and verify the optional checksum — S4, 2 h [HAND] — test: `MalformedFileIT.schemaInvalidFileIsRejectedWithFf01`, `.otherMessageVersionIsRejectedWithFf01`, `.violationDetailNeverEchoesValue`, `ChecksumIT.mismatchReturns422AndPersistsNothing`, `.upperCaseHexMatches`; `rejectionDetail` added to the response and to `api/openapi.yaml` — commit: `feat(ingestion): reject schema-invalid files with FF01 and verify checksum`
- [ ] T-03.4 Create the pure domain model and ArchUnit rules — S4, 1.5 h [HAND] — test: `DomainArchitectureTest.domainHasNoFrameworkOrFloatingPoint`, `.modulesTalkOnlyThroughPorts` (outside a module only its `port` and `domain` packages are visible; no cycles); JaCoCo check of at least 90% line coverage in `validation.domain`; `docs/architecture/03-components.md` revised with the rules now enforced — commit: `feat(validation): add pure domain model and architecture rules`
- [ ] T-03.5 Read GrpHdr with StAX + JAXB and persist the header — S4, 2 h [HAND] — test: `Pain001HeaderReaderTest.readsMsgIdNbOfTxsAndCtrlSum` (NbOfTxs up to 15 digits, stored as `bigint`) — commit: `feat(validation): read pain.001 group header with StAX and JAXB`
- [ ] T-03.6 Write the acceptance test, threat model, and ADRs 0004 (XML parsing), 0005 (synchronous processing, one transaction per file) and 0008 (invalid file) — S4, 2 h [AI-TUTOR] — test: `StructuralValidationAcceptanceIT.us04_ac2_xxeIsRejectedWithoutLeak` (+3) — commit: `docs(security): add threat model and ADRs for XML parsing, processing and invalid files`

### T-04 — Stream-process blocks and instructions with control totals (11.5 h)
Linear: POR-9 · Sprints: 4, 5 · Covers: US-03 (AC4); RN-04, RN-12 (partial) · Depends on: T-03

- [ ] T-04.1 Stream each PmtInf and emit the payment block — S4, 2.5 h [HAND] — test: `Pain001StreamReaderTest.emitsBlocksInDocumentOrderWithHeaderFields` — commit: `feat(validation): stream payment information blocks`
- [ ] T-04.2 Stream each CdtTrfTxInf on its own and map it to the domain — S5, 2.5 h [HAND] — test: `Pain001StreamReaderTest.mapsAmountsAsExactBigDecimalAndKeepsSequence`, `.readsAdjacentTransactionsWithoutWhitespace` — commit: `feat(validation): stream credit transfer transactions`
- [ ] T-04.3 Batch-insert payment blocks and instructions — S5, 2.5 h [AI+REVIEW] — test: `InstructionWriterIT.persists4000InstructionsInBatchesOf500` — commit: `feat(persistence): batch-insert payment blocks and instructions`
- [ ] T-04.4 Enforce control totals in one transaction per file — S5, 2 h [HAND] — test: `ControlTotalsTest` (parameterized, including a 15-digit NbOfTxs → AM18), `ProcessingRollbackIT.checkedFailureMidFileLeavesNoRows`; demo fixture `fixtures/ctrlsum-mismatch.xml` — commit: `feat(validation): enforce control totals in one transaction per file`
- [ ] T-04.5 Prove streaming with a memory-bounded test — S5, 1 h [AI+REVIEW] — test: `StreamingMemoryTest.parses10kInstructionsWith64MbHeap`; the agent writes the `memory-proof` Maven profile and the file generator, the human writes the test — commit: `test(validation): prove streaming parse under a 64 MB heap`
- [ ] T-04.6 Write the acceptance test and revise ADR-0005 (rollback proof) and ADR-0006 (batch measurement from T-04.3) — S5, 1 h [AI-TUTOR] — test: `ParseAndPersistAcceptanceIT.us03_ac4_ctrlSumMismatchRejectsFileWithAm16`, `ParseAndPersistIT.tenValidInstructionsAreAccepted`; completes `docs/architecture/05-upload-and-processing.md` — commit: `test(acceptance): parse and persist pain.001 end to end`

### T-05 — Apply SEPA rules per instruction and accept files partially (11 h)
Linear: POR-10 · Sprints: 5, 6 · Covers: US-03 (AC2, AC3); RN-05 to RN-12 · Depends on: T-04

- [ ] T-05.1 Implement the Iban value object with per-country length, mod-97 and masking — S5, 2 h [HAND] — test: `IbanTest` parameterized (IE29AIBK93115212345678 ok; ...679 AC03; 21 chars AC03; country XX AC03; BR9700360305000010009795493P1, valid but outside SEPA, AC03; IE29aibk93115212345678 AC03), `IbanMaskerTest` — commit: `feat(validation): validate IBAN length per country and mod-97`
- [ ] T-05.2 Implement the BIC, amount and creditor-name rules — S6, 2 h [HAND] — test: `BicRuleTest` (AIBKXK2D RC01), `AmountRuleTest` (parameterized: 1500.00, 1500.001, 1500.000, 0.00, 1000000000.00, 100.00 USD, `EqvtAmt` AM12), `CreditorNameRuleTest` — commit: `feat(validation): add BIC country, SEPA amount and creditor name rules`
- [ ] T-05.3 Implement the TARGET calendar and the execution-date rule — S6, 2 h [HAND] — test: `TargetCalendarTest` (closed on weekdays: 2026-04-03, 2026-04-06, 2026-05-01, 2026-12-25, 2027-01-01, 2027-03-26, 2027-03-29, 2028-04-14, 2028-12-26; weekend: 2026-12-26; open: 2026-12-28, 2027-12-24), `ExecutionDateRuleTest` (fixed `Clock`; `DtTm` DT01) — commit: `feat(validation): add TARGET calendar and execution date rule`
- [ ] T-05.4 Compose rules with fixed precedence and aggregate statuses — S6, 3 h [HAND] — test: `BlockValidatorTest.appliesDt01BeforeAc02AndTotalsRemarkTheBlock`, `.debtorAccountWithoutIbanIsAc02`, `InstructionValidatorTest.firstFailingRuleWinsInDeclaredOrder`, `StatusAggregatorTest`, `MixedValidityFileIT.mixedFileIsPartiallyAccepted` (with `fixtures/mixed-validity.xml`) — commit: `feat(validation): compose SEPA rules and aggregate statuses`
- [ ] T-05.5 Write the acceptance test and document the rules — S6, 2 h [AI-TUTOR] — test: `BusinessValidationAcceptanceIT.us03_ac3_invalidBlockRejectsOnlyThatBlock` (+1) — commit: `test(acceptance): partial acceptance with ISO reason codes`

### T-06 — Serve the pain.002 status report and the rejection query (10.5 h)
Linear: POR-11 · Sprints: 6, 7 · Covers: US-05 (AC1, AC2, AC3), US-06 (AC1, AC2), US-07 (AC1 for report and instructions); RN-13, RN-14 · Depends on: T-05

- [ ] T-06.1 Build the pain.002.001.10 report from the file result — S6, 3 h [AI+REVIEW] — test: `Pain002BuilderTest.partialFileProducesXsdValidReportWithOnlyRejectedTx`, `.rejectedBlockCarriesItsReasonWithoutTransactions` — commit: `feat(reporting): build pain.002.001.10 status report`
- [ ] T-06.2 Serve GET /status-report with rejections streamed from the database — S7, 2.5 h [AI+REVIEW] — test: `StatusReportIT.returnsXsdValidPain002ForPartialFile`, `.otherClientGets404` — commit: `feat(reporting): serve pain.002 status report`
- [ ] T-06.3 Create the paginated instruction query — S7, 2 h [AI+REVIEW] — test: `ListInstructionsIT.filtersRejectedAndPaginatesBySequence`, `.sizeAbove200Returns400` — commit: `feat(reporting): list instructions by status with pagination`
- [ ] T-06.4 Generate group-level rejection reports — S7, 1.5 h [HAND] — test: `GroupRejectionReportTest.ff01ReportHasRjctAndNoTransactions`, `.totalsMismatchReportCountsReadInstructionsAsRejected` — commit: `feat(reporting): report group-level rejections`
- [ ] T-06.5 Write the acceptance test and revise the status-report ADR — S7, 1.5 h [AI-TUTOR] — test: `StatusReportAcceptanceIT.us05_ac1_partialFileReportListsOnlyRejected` (+5) — commit: `test(acceptance): status report and instruction query`

### T-07 — Reject duplicate MsgId with DU01, including under concurrency (6 h)
Linear: POR-12 · Sprints: 7, 8 · Covers: US-02 (AC3, AC4), US-05 (AC3 for DU01); RN-02 · Depends on: T-06 (group report), T-03 (GrpHdr)

- [ ] T-07.1 Reject an already-used MsgId with DU01 — S7, 2 h [HAND] — test: `DuplicateMessageIdIT.differentContentSameMsgIdIsRejectedDu01`, `.reuseAfterRejectedFileIsAllowed`; demo fixture `fixtures/payroll-same-msgid.xml` — commit: `feat(ingestion): reject duplicate MsgId per client with DU01`
- [ ] T-07.2 Resolve the MsgId race with the unique index and one retry — S8, 2.5 h [HAND] — test: `ConcurrentDuplicateMessageIdIT.exactlyOneOfTwoParallelFilesIsAccepted` — commit: `feat(ingestion): resolve concurrent MsgId race with unique index and retry`
- [ ] T-07.3 Write the acceptance test and revise the idempotency ADR — S8, 1.5 h [AI-TUTOR] — test: `DuplicateSubmissionAcceptanceIT.us02_ac4_concurrentSameMsgIdOnlyOneAccepted` (+2); adds `docs/architecture/06-concurrent-msgid.md` — commit: `test(acceptance): duplicate submission end to end`

### T-08 — 4,000-payment payroll scenario, measurements and v0.1.0 release (7 h)
Linear: POR-13 · Sprint: 8 · Covers: US-03 (AC1), US-07 (AC3); every RN end to end; SC-002, SC-003 and SC-006 to SC-011 · Depends on: T-07

- [ ] T-08.1 Create the pain.001 generator and the payroll fixture — S8, 2 h [AI+REVIEW] — test: `tools/test_generate_pain001.py` (unittest, run in CI), `GeneratedFixtureIT.payrollFixtureIsXsdValid` — commit: `test(tools): add deterministic pain.001 generator and payroll fixture`
- [ ] T-08.2 Automate the payroll scenario and check the performance targets — S8, 3.5 h [AI-TUTOR] — test: `PayrollScenarioAcceptanceIT.us03_ac1_payroll4000With12InvalidIbans`, `.us07_ac3_logsNeverContainFullIban` (the four scenarios of US-07.AC3); `tools/measure.sh` exits 0 (SC-002, SC-003, SC-008 to SC-010) — commit: `test(acceptance): payroll scenario and measured results`
- [ ] T-08.3 Publish release v0.1.0 with the final README, CHANGELOG.md, OpenAPI on GitHub Pages and a recorded demo — S8, 1.5 h [AI-TUTOR] — release, verify: release checklist; `make clean && time make demo` on a fresh clone in under 5 min (SC-007); green CI on the `v0.1.0` tag within 8 min (SC-011); `api/openapi.yaml` served on GitHub Pages. — commit: `docs: release v0.1.0 MVP`
