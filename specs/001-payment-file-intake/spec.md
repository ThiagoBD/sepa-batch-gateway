# Feature Specification: Payment File Intake (MVP)

**Feature Branch**: `001-payment-file-intake`

**Created**: 2026-09-26

**Status**: Approved

**Input**: User description: "A corporate client's ERP uploads a SEPA credit transfer file (ISO 20022 pain.001.001.09). The gateway rejects invalid or malicious files, validates every instruction against the EPC SEPA rules, accepts the valid ones, rejects the others with one ISO 20022 reason code each, never processes the same file twice, and returns a pain.002.001.10 status report."

**Context**: Quayside Pay is a fictional payment institution in Dublin. Its corporate clients send payroll and supplier batches before the SEPA cut-off. Today one bad line blocks a whole payroll, and a timeout followed by a resend can pay people twice. This feature is the MVP of the gateway: it validates and reports, it does not execute payments.

## User Scenarios & Testing *(mandatory)*

Story IDs (US-xx) and acceptance criterion IDs (US-xx.ACy) are stable: acceptance tests carry them in their names (`usXX_acY_*`).

### US-01 - Upload a payment file (Priority: P1)

As a corporate ERP system, I want to upload a pain.001 file through an authenticated REST API, so that my payments are validated before the cut-off.

**Why this priority**: Nothing else works without a stored file and its content hash, which is also the basis of idempotency.

**Independent Test**: Upload a file with `curl` and read it back by id; the stored SHA-256 matches `sha256sum` of the file.

**Acceptance Scenarios**:

1. **US-01.AC1** — **Given** a valid API key and a well-formed pain.001.001.09 file, **When** I POST it to `/v1/payment-files`, **Then** I receive 201 Created with `Location`, the file id and the SHA-256 of the content.
2. **US-01.AC2** — **Given** no API key, or an unknown or inactive key, **When** I POST a file, **Then** I receive 401 `application/problem+json` and nothing is stored.
3. **US-01.AC3** — **Given** a file larger than 20 MB, **When** I POST it, **Then** I receive 413 `file-too-large` and nothing is stored.
4. **US-01.AC4** — **Given** I send the optional `sha256` field and it does not match the content, **When** I POST, **Then** I receive 422 `checksum-mismatch` and nothing is stored.

---

### US-02 - Resubmit safely (Priority: P1)

As a corporate ERP system, I want to resend a file after a timeout without any risk of paying twice, so that network failures never create duplicate payments.

**Why this priority**: Paying a payroll twice is the most expensive failure this gateway can cause.

**Independent Test**: Upload the same bytes twice under different names, then two different files with the same MsgId, and check that only one file is processed each time.

**Acceptance Scenarios**:

1. **US-02.AC1** — **Given** I already uploaded file F, **When** I upload the same bytes again under any filename, **Then** I receive 200 with F's id and `Idempotent-Replayed: true`, and no new instructions are created.
2. **US-02.AC2** — **Given** 10 identical uploads sent in parallel, **When** all complete, **Then** exactly one file exists and all 10 responses carry the same id.
3. **US-02.AC3** — **Given** an accepted or partially accepted file with MsgId M, **When** I upload different content with the same MsgId M, **Then** the new file is REJECTED with group reason DU01 and no instructions are stored.
4. **US-02.AC4** — **Given** two different files with MsgId M uploaded at the same time, **When** both finish, **Then** exactly one is not REJECTED and the other is REJECTED with DU01.

---

### US-03 - Validate each instruction (Priority: P1)

As a payments operations analyst, I want each invalid instruction rejected with an ISO 20022 reason code while valid ones are accepted, so that one bad line never blocks a whole payroll.

**Why this priority**: Partial acceptance with a clear reason per line is the value the gateway sells.

**Independent Test**: Upload a file that mixes valid and invalid instructions and check the status and reason code of each stored instruction.

**Acceptance Scenarios**:

1. **US-03.AC1** — **Given** a 4,000-instruction payroll with 12 creditor IBANs whose check digits are wrong, **When** it is processed, **Then** the file is PARTIALLY_ACCEPTED with 3,988 accepted and 12 rejected, all with AC03.
2. **US-03.AC2** — **Given** an instruction that breaks several rules, **When** it is validated, **Then** exactly one reason is recorded: the first one in the RN-11 precedence.
3. **US-03.AC3** — **Given** a payment block whose requested execution date is a TARGET closing day or in the past (or whose debtor IBAN is invalid), **When** it is processed, **Then** all its instructions are rejected with DT01 (or AC02) and other blocks are unaffected.
4. **US-03.AC4** — **Given** a group header whose CtrlSum differs from the sum of instructed amounts, **When** the file is processed, **Then** the whole file is REJECTED with AM16.

---

### US-04 - Reject invalid or malicious files (Priority: P1)

As the gateway owner, I want files that are not valid pain.001.001.09, or that attempt XML attacks, rejected as a whole, so that nothing ambiguous or dangerous reaches the payment flow.

**Why this priority**: A parser that resolves external entities or expands entities without limit is a security incident, not a bug.

**Independent Test**: Upload a schema-invalid file, an XXE file and a "billion laughs" file; each ends REJECTED with FF01 and the service stays healthy.

**Acceptance Scenarios**:

1. **US-04.AC1** — **Given** a file that violates the XSD, **When** it is uploaded, **Then** I receive 201 with status REJECTED, reason FF01 and the first violation with line and column.
2. **US-04.AC2** — **Given** a file with a DOCTYPE declaring an external entity, **When** it is uploaded, **Then** it is REJECTED with FF01 and no local file content appears in any response or log.
3. **US-04.AC3** — **Given** a "billion laughs" file, **When** it is uploaded, **Then** it is rejected in under 2 seconds and `/actuator/health` stays UP.

---

### US-07 - Keep client data isolated (Priority: P1)

As a corporate client, I want my API key to reach only my own files, so that my employees' payroll data stays private.

**Why this priority**: Payroll files hold personal data under GDPR; a cross-client leak is unacceptable at any stage.

**Independent Test**: With two clients, check that one never reads the other's file, report or instructions, and that identical uploads stay separate.

**Acceptance Scenarios**:

1. **US-07.AC1** — **Given** client B's key, **When** B requests a file owned by client A (summary, report or instructions), **Then** B receives 404 and an `access.denied` event is logged without payload data.
2. **US-07.AC2** — **Given** clients A and B upload identical bytes, **When** both uploads finish, **Then** each has its own file id.
3. **US-07.AC3** — **Given** any file is processed, **When** logs are written, **Then** no full IBAN, person name, file content or API key appears in them.

---

### US-05 - Download the status report (Priority: P2)

As a corporate ERP system, I want to download a pain.002.001.10 status report for my file, so that I can reconcile automatically which payments were rejected and why.

**Why this priority**: The ERP needs a machine-readable answer; without it the result lives only in the gateway.

**Independent Test**: Download the report of a partially accepted file and validate it against the pain.002.001.10 XSD.

**Acceptance Scenarios**:

1. **US-05.AC1** — **Given** a PARTIALLY_ACCEPTED file, **When** I GET its status report, **Then** I receive an XSD-valid pain.002 with GrpSts PART, one TxInfAndSts (TxSts RJCT plus reason) per rejected instruction, and NbOfTxsPerSts counts.
2. **US-05.AC2** — **Given** a fully accepted file, **When** I GET its report, **Then** GrpSts is ACTC and no transaction details are listed.
3. **US-05.AC3** — **Given** a file rejected at group level (FF01, AM16, AM18 or DU01), **When** I GET its report, **Then** GrpSts is RJCT with that reason and no transaction details.

---

### US-06 - Query a file and its rejections (Priority: P3)

As a payments operations analyst, I want to see a file's summary and its rejected instructions, so that I can tell the client exactly what to fix.

**Why this priority**: Humans can read the pain.002 in the meantime; the JSON query makes their work faster.

**Independent Test**: Query the summary and a page of rejected instructions of a processed file.

**Acceptance Scenarios**:

1. **US-06.AC1** — **Given** a processed file, **When** I GET `/v1/payment-files/{id}`, **Then** I see status, counts, MsgId, the accepted amount as a decimal string, and links to the report and the instructions.
2. **US-06.AC2** — **Given** a file with rejections, **When** I GET `/instructions?status=REJECTED&page=0&size=50`, **Then** I receive the rejected instructions ordered by sequence, each with reason code and description, plus page metadata.

---

### Edge Cases

| Case | Expected behavior | Rule |
| --- | --- | --- |
| Same bytes, different filename | 200, same id, nothing reprocessed | RN-01 |
| Same bytes, different client | New resource for the other client | RN-01, RN-14 |
| 10 identical uploads in parallel | 1 row; the losers get 200 with the winner's id | RN-01 |
| Reused MsgId with different content, previous file accepted | REJECTED DU01, no instructions stored | RN-02 |
| Reused MsgId, previous file REJECTED | Processed normally | RN-02 |
| Two files with the same MsgId at the same time | Exactly one is not REJECTED; the other is REJECTED DU01 after one retry | RN-02 |
| Empty upload or missing `file` part | 400 `invalid-request`, nothing stored | RN-03 |
| Request that is not multipart | 415 `unsupported-media-type`, nothing stored | RN-03 |
| `sha256` field in upper-case hex that matches the content | Accepted: the comparison ignores case | RN-03 |
| 20 MB + 1 byte | 413, nothing stored | RN-03 |
| DOCTYPE with an external entity (XXE) | FF01; the entity is never resolved | RN-03 |
| Billion laughs | FF01 in under 2 s, health UP | RN-03 |
| Namespace of another version (pain.001.001.03) | FF01 | RN-03 |
| IBAN with a space, or with lowercase letters in the country code | The XSD fails, so the whole file is FF01 | RN-03 |
| IBAN with lowercase letters after the country code (the XSD allows them) | AC03 on that instruction; the gateway never rewrites an IBAN | RN-07 |
| Creditor name made only of spaces | BE22 (the name is checked after trimming). An empty `<Nm/>` fails the XSD, so the file is FF01 | RN-10, RN-03 |
| NbOfTxs and CtrlSum both wrong | AM18, because the count comes first | RN-04, RN-11 |
| CtrlSum absent | Only the count is checked | RN-04 |
| ReqdExctnDt on 26 December or in the past | Block RJCT with DT01; other blocks continue | RN-05 |
| Amount 1500.000 (same value, 3 decimals) | AM12: the rule looks at the written scale. With 6 or more decimals the XSD fails first and the file is FF01 | RN-09 |
| BIC absent | Accepted | RN-08 |
| Every instruction rejected | REJECTED, GrpSts RJCT without a group reason, all listed in TxInfAndSts | RN-12, RN-13 |
| Database fails mid-file | Rollback, generic 500, zero rows; a resend processes from scratch | RN-01 |
| Inactive key | 401 | RN-14 |
| `size=500` on the instruction query | 400 `invalid-parameter` (maximum 200) | — |
| `X-Correlation-Id` with a line break | Ignored and replaced by a UUID (log injection) | — |

## Requirements *(mandatory)*

### Functional Requirements

Business rules keep their plan ids (RN-xx). Reason codes come from the ISO 20022 ExternalStatusReason1Code list; amount limits and decimals follow the EPC SCT C2PSP Implementation Guidelines 2025.

- **RN-01**: System MUST treat (client, SHA-256 of the bytes) as the file identity. The filename does not count.
  - Example: A sends `payroll.xml` → 201, id F1. A sends `payroll-copy.xml` with the same bytes → 200, id F1, `Idempotent-Replayed: true`. B sends the same bytes → 201, id F2.
- **RN-02**: System MUST keep MsgId unique per client among files that are not REJECTED. A reused MsgId → REJECTED DU01, with no instructions.
  - Example: F1 (MsgId QP-1002) ACCEPTED; F2 with other bytes and MsgId QP-1002 → REJECTED DU01. If F1 had been REJECTED FF01, F2 would be processed normally.
- **RN-03**: System MUST accept only files up to 20 MB that are well formed, have no DOCTYPE and are valid against the pain.001.001.09 XSD. Above the limit → 413 with no resource; any other failure → REJECTED FF01. A request without a non-empty `file` part → 400 `invalid-request`, and a request that is not multipart → 415 `unsupported-media-type`, both with nothing stored. When the optional `sha256` field is sent, it MUST equal the SHA-256 of the bytes (hex, compared without regard to case); otherwise 422 `checksum-mismatch` and nothing is stored.
  - Example: 25 MB → 413, nothing stored. `<!DOCTYPE ...>` → FF01. IBAN written with spaces → the XSD fails → FF01 for the whole file. `sha256` of another file → 422.
- **RN-04**: System MUST check control totals: NbOfTxs and CtrlSum (when present) of the group header and of each PmtInf match the content. Group mismatch → AM18 (count) or AM16 (sum); block mismatch → AM18 or AM17.
  - Example: NbOfTxs 4000 with 3,999 transactions → REJECTED AM18. CtrlSum 1000.00 with a sum of 999.99 → REJECTED AM16. CtrlSum absent → only the count is checked.
- **RN-05**: System MUST reject a block whose ReqdExctnDt is before the business date (today in Europe/Dublin, read from an injected clock) or is not a TARGET business day (closed on Saturdays, Sundays, 1 January, Good Friday, Easter Monday, 1 May, 25 and 26 December), with DT01.
  - Example: today 2027-03-24: 2027-03-26 (Good Friday) → DT01; 2027-03-30 → ok; 2027-03-23 → DT01.
- **RN-06**: System MUST reject a block whose debtor IBAN (DbtrAcct) fails the RN-07 check, with AC02.
  - Example: DbtrAcct IE29AIBK93115212345679 → AC02 on all 500 instructions of the block.
- **RN-07**: System MUST require a creditor IBAN that is present, is written in upper case, has a known country, has that country's length and passes mod-97 = 1. Otherwise AC03. The gateway never normalises an IBAN.
  - Example: IE29AIBK93115212345678 → ok; IE29AIBK93115212345679 → AC03; 21 characters → AC03; IE29aibk93115212345678 → AC03.
- **RN-08**: System MUST accept a missing creditor agent BIC; when present, its country (positions 5 and 6) MUST be an ISO 3166-1 code. Otherwise RC01.
  - Example: AIBKIE2D → ok; AIBKXX2D → RC01; absent → ok.
- **RN-09**: System MUST require currency EUR (AM03), at most 2 written decimals (AM12), an amount greater than zero (AM01) and at most 999,999,999.99 (AM02). Amounts are compared with `compareTo`.
  - Example: 1500.00 EUR → ok; 1500.001 → AM12; 1500.000 → AM12; 0.00 → AM01; 1000000000.00 → AM02; 100.00 USD → AM03.
- **RN-10**: System MUST require a creditor name that is not blank after trimming. Otherwise BE22.
  - Example: `<Cdtr>` without `<Nm>` → BE22; `<Nm>   </Nm>` → BE22.
- **RN-11**: System MUST record exactly one reason per item, by precedence. Group: FF01 → DU01 → AM18 → AM16. Block: AM18 → AM17 → DT01 → AC02 (instructions inherit the block reason). Instruction: AM03 → AM12 → AM01 → AM02 → AC03 → RC01 → BE22.
  - Example: 0.001 USD to an invalid IBAN with no creditor name → only AM03.
- **RN-12**: System MUST aggregate statuses. A block is RJCT when it has its own reason or all its instructions are rejected, PART when some are, ACTC when none are. A file is REJECTED when it has a group reason or all instructions are rejected, PARTIALLY_ACCEPTED when some are, ACCEPTED when none are. A reason found only at the end re-marks the affected instructions with that code: AM18 or AM16 on the whole file, AM18 or AM17 on the block's instructions.
  - Example: payroll of 4,000 with 12 AC03 → PARTIALLY_ACCEPTED. 10 instructions, all AC03 → REJECTED without a group reason.
- **RN-13**: System MUST build the pain.002 as follows: GrpSts ACTC, PART or RJCT; StsRsnInf at group level only for a group reason; OrgnlPmtInfAndSts only for PART or RJCT blocks; TxInfAndSts only for instructions rejected for their own reason; NbOfTxsPerSts with the ACTC and RJCT counts.
  - Example: payroll 4,000/12 → GrpSts PART, 12 TxInfAndSts RJCT AC03, NbOfTxsPerSts ACTC 3988 and RJCT 12. Unreadable FF01 file → GrpSts RJCT FF01, OrgnlMsgId NOTPROVIDED.
- **RN-14**: System MUST scope every read and write to the authenticated client. Another client's resource → 404, never 403.
  - Example: B GETs A's file F1 → 404 and an `access.denied` log event.

### Reason codes used

| Code | Level | Meaning |
| --- | --- | --- |
| FF01 | Group | Invalid file format (XSD, DOCTYPE, not well formed) |
| DU01 | Group | Duplicate message id |
| AM18 | Group or block | Number of transactions does not match |
| AM16 | Group | Control sum does not match |
| AM17 | Block | Block control sum does not match |
| DT01 | Block | Invalid requested execution date |
| AC02 | Block | Invalid debtor account |
| AM03 | Instruction | Currency not allowed |
| AM12 | Instruction | Invalid amount (decimals) |
| AM01 | Instruction | Zero amount |
| AM02 | Instruction | Amount above the allowed maximum |
| AC03 | Instruction | Invalid creditor account |
| RC01 | Instruction | Invalid BIC |
| BE22 | Instruction | Creditor name missing |

### Key Entities

- **Client**: a corporate customer identified by an API key (stored only as a hash); owns files.
- **Payment file**: one uploaded pain.001, identified by client and content hash; carries MsgId, declared totals, final status, counts and accepted amount.
- **Payment block**: one PmtInf of a file: debtor account, requested execution date, declared totals, block status and reason.
- **Payment instruction**: one CdtTrfTxInf: sequence, end-to-end id, amount and currency, creditor name, IBAN and BIC, status and reason.
- **Status report**: the pain.002.001.10 built from a file's result; not stored, generated on request.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 23 acceptance criteria (US-01 to US-07) pass as automated tests in CI.
- **SC-002**: The 4,000-instruction payroll with 12 invalid IBANs is processed with p99 ≤ 5 s from upload to response, measured locally (3 warm-ups + 20 runs, hardware recorded).
- **SC-003**: A 10,000-instruction file is processed with p99 ≤ 12 s, and the parser walks it with a 64 MB heap.
- **SC-004**: Under concurrency, identical uploads never create a second file and a shared MsgId never yields two non-rejected files.
- **SC-005**: Every pain.002 produced in tests is valid against the pain.002.001.10 XSD.
- **SC-006**: No full IBAN, person name, file content or API key appears in logs while processing the payroll scenario.
- **SC-007**: `make demo` runs the payroll scenario on a clean machine in under 5 minutes.
- **SC-008**: `GET /v1/payment-files/{id}` and one page of `/instructions` answer with p99 ≤ 200 ms, measured locally (3 warm-ups + 20 runs).
- **SC-009**: A file is processed at ≥ 1,000 instructions per second: 10,000 instructions divided by the p50 upload time of the 10,000-instruction file.
- **SC-010**: The application processes the payroll and the 10,000-instruction file inside a container limited to 512 MB of memory, with no restart and no out-of-memory error.
- **SC-011**: The CI pipeline on the `v0.1.0` tag finishes green in at most 8 minutes.

## Assumptions

- All data is synthetic; Quayside Pay and its clients are fictional.
- The gateway validates and reports; it does not execute payments (no pacs.008, no CSM, no debit).
- Only pain.001.001.09, SCT (not SCT Inst) and EUR are in scope.
- Clients authenticate with one API key each; OAuth2 client credentials come in a later version.
- Processing is synchronous, one database transaction per file, for files up to 20 MB.
- Out of scope for this feature: structured postal address rule (planned as RN-15 in V2), payee name check, cross-file instruction deduplication, debit-account ownership check, cut-off handling, raw-file storage, metrics beyond logs and health, any cloud resource, retention purge.

## Traceability

Every criterion has one acceptance test with the same identifier in its name. The Task column mirrors the Covers lines in `tasks.md`, which are the source of truth. The PR column is filled in when the criterion's test is merged.

| Criterion | Rules | Task | Acceptance test | PR |
| --- | --- | --- | --- | --- |
| US-01.AC1 | RN-03 | T-01 | `WalkingSkeletonAcceptanceIT.us01_ac1_uploadReturns201WithSha256` | |
| US-01.AC2 | RN-14 | T-02 | `AuthenticationAcceptanceIT.us01_ac2_missingOrInvalidKeyReturns401` | |
| US-01.AC3 | RN-03 | T-01 | `WalkingSkeletonAcceptanceIT.us01_ac3_fileOver20MbReturns413` | |
| US-01.AC4 | RN-03 | T-03 | `StructuralValidationAcceptanceIT.us01_ac4_checksumMismatchReturns422` | |
| US-02.AC1 | RN-01 | T-02 | `IdempotentUploadAcceptanceIT.us02_ac1_sameBytesReplayReturns200SameId` | |
| US-02.AC2 | RN-01 | T-02 | `IdempotentUploadAcceptanceIT.us02_ac2_parallelIdenticalUploadsCreateOneFile` | |
| US-02.AC3 | RN-02 | T-07 | `DuplicateSubmissionAcceptanceIT.us02_ac3_sameMsgIdDifferentContentIsDu01` | |
| US-02.AC4 | RN-02 | T-07 | `DuplicateSubmissionAcceptanceIT.us02_ac4_concurrentSameMsgIdOnlyOneAccepted` | |
| US-03.AC1 | RN-07, RN-12 | T-08 | `PayrollScenarioAcceptanceIT.us03_ac1_payroll4000With12InvalidIbans` | |
| US-03.AC2 | RN-08, RN-09, RN-10, RN-11 | T-05 | `BusinessValidationAcceptanceIT.us03_ac2_singleReasonByPrecedence` | |
| US-03.AC3 | RN-05, RN-06 | T-05 | `BusinessValidationAcceptanceIT.us03_ac3_invalidBlockRejectsOnlyThatBlock` | |
| US-03.AC4 | RN-04 | T-04 | `ParseAndPersistAcceptanceIT.us03_ac4_ctrlSumMismatchRejectsFileWithAm16` | |
| US-04.AC1 | RN-03 | T-03 | `StructuralValidationAcceptanceIT.us04_ac1_schemaInvalidIsRejectedWithFf01` | |
| US-04.AC2 | RN-03 | T-03 | `StructuralValidationAcceptanceIT.us04_ac2_xxeIsRejectedWithoutLeak` | |
| US-04.AC3 | RN-03 | T-03 | `StructuralValidationAcceptanceIT.us04_ac3_billionLaughsRejectedUnder2s` | |
| US-05.AC1 | RN-13 | T-06 | `StatusReportAcceptanceIT.us05_ac1_partialFileReportListsOnlyRejected` | |
| US-05.AC2 | RN-13 | T-06 | `StatusReportAcceptanceIT.us05_ac2_acceptedFileReportIsActc` | |
| US-05.AC3 | RN-02, RN-03, RN-04, RN-13 | T-06, T-07 | `StatusReportAcceptanceIT.us05_ac3_groupRejectionReportIsRjct`; `DuplicateSubmissionAcceptanceIT.us05_ac3_du01ReportIsRjct` | |
| US-06.AC1 | RN-12 | T-01, T-06 | `StatusReportAcceptanceIT.us06_ac1_summaryShowsCountsAndAmountAsString` | |
| US-06.AC2 | RN-12 | T-06 | `StatusReportAcceptanceIT.us06_ac2_listsRejectedInstructionsPaged` | |
| US-07.AC1 | RN-14 | T-02, T-06 | `AuthenticationAcceptanceIT.us07_ac1_otherClientGets404`; `StatusReportAcceptanceIT.us07_ac1_reportAndInstructionsOfOtherClientAre404` | |
| US-07.AC2 | RN-01, RN-14 | T-02 | `AuthenticationAcceptanceIT.us07_ac2_sameBytesDifferentClientsAreIsolated` | |
| US-07.AC3 | Constitution IX | T-08 | `PayrollScenarioAcceptanceIT.us07_ac3_logsNeverContainFullIban` | |
