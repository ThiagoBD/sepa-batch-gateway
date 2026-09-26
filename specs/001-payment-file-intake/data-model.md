# Data Model: Payment File Intake (MVP)

Four tables in PostgreSQL 18. Idempotency lives in two constraints, not in code. Money is `numeric` in the database and `BigDecimal` in Java.

## api_client

| Column | Type | Constraint / note |
| --- | --- | --- |
| id | uuid | PK, `DEFAULT uuidv7()` |
| name | varchar(100) | NOT NULL, UNIQUE |
| api_key_hash | char(64) | NOT NULL, `ux_api_client_key_hash` UNIQUE. SHA-256 hex; the key itself is never stored |
| active | boolean | NOT NULL DEFAULT true |
| created_at | timestamptz | NOT NULL DEFAULT now() |

## payment_file

| Column | Type | Constraint / note |
| --- | --- | --- |
| id | uuid | PK, `DEFAULT uuidv7()`, time-ordered |
| client_id | uuid | NOT NULL, FK `api_client` |
| original_filename | varchar(255) | NOT NULL; informative only, never a key |
| sha256 | char(64) | NOT NULL |
| size_bytes | bigint | NOT NULL, CHECK > 0 |
| status | varchar(20) | NOT NULL, CHECK IN (RECEIVED, ACCEPTED, PARTIALLY_ACCEPTED, REJECTED) |
| group_reason_code | varchar(4) | NULL; allowed only when status is REJECTED |
| rejection_detail | varchar(500) | NULL; first XSD violation with line and column |
| message_id | varchar(35) | NULL; GrpHdr/MsgId |
| declared_tx_count | integer | NULL; GrpHdr/NbOfTxs |
| declared_ctrl_sum | numeric | NULL; no fixed precision, keeps exactly what was sent |
| accepted_count, rejected_count | integer | NOT NULL DEFAULT 0, CHECK ≥ 0 |
| accepted_amount | numeric(18,2) | NOT NULL DEFAULT 0 |
| processing_ms | integer | NULL |
| correlation_id | varchar(64) | NULL |
| received_at | timestamptz | NOT NULL DEFAULT now() |
| processed_at | timestamptz | NULL |

Constraints and indexes:

- `ux_payment_file_client_sha256` UNIQUE (client_id, sha256) enforces RN-01.
- `ux_payment_file_client_msgid_active` UNIQUE (client_id, message_id) WHERE status <> 'REJECTED' AND message_id IS NOT NULL enforces RN-02.
- `ix_payment_file_client_received` (client_id, received_at DESC) prepares the file listing of V2.

## payment_block (one per PmtInf)

| Column | Type | Constraint / note |
| --- | --- | --- |
| id | bigint | PK, GENERATED ALWAYS AS IDENTITY |
| file_id | uuid | NOT NULL, FK `payment_file` ON DELETE CASCADE |
| sequence_no | integer | NOT NULL, UNIQUE (file_id, sequence_no) |
| pmt_inf_id | varchar(35) | NOT NULL |
| requested_execution_date | date | NOT NULL |
| debtor_name | varchar(140) | NULL |
| debtor_iban | varchar(34) | NOT NULL |
| declared_tx_count | integer | NOT NULL |
| declared_ctrl_sum | numeric | NULL |
| status | varchar(4) | NOT NULL, CHECK IN (ACTC, PART, RJCT) |
| reason_code | varchar(4) | NULL; DT01, AC02, AM17 or AM18 |

## payment_instruction (one per CdtTrfTxInf)

| Column | Type | Constraint / note |
| --- | --- | --- |
| id | bigint | PK, GENERATED ALWAYS AS IDENTITY |
| file_id | uuid | NOT NULL, FK `payment_file`; redundant on purpose, for queries and future partitioning |
| block_id | bigint | NOT NULL, FK `payment_block` |
| sequence_no | integer | NOT NULL, UNIQUE (file_id, sequence_no); position in the file, from 1 |
| instr_id | varchar(35) | NULL |
| end_to_end_id | varchar(35) | NOT NULL |
| amount | numeric(18,5) | NOT NULL; exact as the XSD allows (18 digits, 5 decimals); RN-09 allows at most 2 |
| currency | char(3) | NOT NULL |
| creditor_name | varchar(140) | NULL; personal data |
| creditor_iban | varchar(34) | NULL; personal data |
| creditor_bic | varchar(11) | NULL |
| status | varchar(8) | NOT NULL, CHECK IN (ACCEPTED, REJECTED) |
| reason_code | varchar(4) | NULL; `ck_instruction_reason`: (status = 'REJECTED') = (reason_code IS NOT NULL) |

Index `ix_instruction_file_status_seq` (file_id, status, sequence_no) serves the paginated query and the streamed rejections of the pain.002.

Not stored: postal addresses and the raw file (raw-file storage comes in V2).

## State transitions

```text
payment_file:  RECEIVED ──► ACCEPTED
                        ├──► PARTIALLY_ACCEPTED
                        └──► REJECTED (group reason, or every instruction rejected)
```

- Within the one transaction per file, `RECEIVED` is the state between insert and final update; after commit a file is always in a final state.
- A block is `RJCT` when it has its own reason or every instruction is rejected, `PART` when some are, `ACTC` when none are (RN-12).
- An instruction is `ACCEPTED` or `REJECTED` with exactly one reason (RN-11). A reason found at the end of a block or file re-marks the affected instructions (RN-12).

## Migrations

Applied migrations are never edited. Each migration lands with the first test that uses it.

| Version | File | Content | Subtask |
| --- | --- | --- | --- |
| V1 | `V1__create_payment_file.sql` | id, original_filename, sha256, size_bytes, status with the full CHECK, received_at | T-01.3 |
| V2 | `V2__create_api_client.sql` | `api_client` table | T-02.1 |
| V3 | `V3__scope_payment_file_by_client.sql` | client_id NOT NULL + FK, `ux_payment_file_client_sha256`, `ix_payment_file_client_received` | T-02.3 |
| V4 | `V4__add_rejection_fields_to_payment_file.sql` | group_reason_code, rejection_detail, processed_at, correlation_id, reason CHECK | T-03.3 |
| V5 | `V5__add_group_header_to_payment_file.sql` | message_id, declared_tx_count, declared_ctrl_sum | T-03.5 |
| V6 | `V6__create_payment_block_and_instruction.sql` | `payment_block`, `payment_instruction`, index, counters and processing_ms on `payment_file` | T-04.3 |
| V7 | `V7__add_message_id_uniqueness.sql` | `ux_payment_file_client_msgid_active` (partial unique index) | T-07.1 |

V3 adds a NOT NULL column without a default. That is safe only because nothing but development databases exist before the first release (`make clean` recreates them).
