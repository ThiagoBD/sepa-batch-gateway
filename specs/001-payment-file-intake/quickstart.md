# Quickstart: Payment File Intake (MVP)

How to run the gateway locally and check the feature by hand. Each step works once the task in the right-hand column is merged; the automated acceptance tests cover the same ground.

## Prerequisites

- JDK 25 (Temurin), Docker with Compose, `make`, `curl`, `openssl`, `xmllint` (libxml2)
- No cloud account and no real data: every fixture is synthetic

## Run

```bash
cp .env.example .env                                # local database password, never committed
export SEPA_DEMO_API_KEY="$(openssl rand -hex 32)"  # demo client key, seeded as a hash in the local profile
make demo                                           # builds, starts app + PostgreSQL, sends the payroll, downloads the pain.002
```

Stop and remove everything with `make clean`.

## Validate by hand

| Step | Command | Expected | Task |
| --- | --- | --- | --- |
| Health | `curl -s localhost:8080/actuator/health` | `{"status":"UP"}` | T-01 |
| Upload without a key | `curl -i -F file=@fixtures/pain001-10tx.xml localhost:8080/v1/payment-files` | 401 problem+json | T-02 |
| Upload | `curl -i -H "X-API-Key: $SEPA_DEMO_API_KEY" -F file=@fixtures/pain001-10tx.xml localhost:8080/v1/payment-files` | 201 and `Location` (T-01); status ACCEPTED once T-04 is merged | T-01, T-04 |
| Replay | the same command again | 200, same id, `Idempotent-Replayed: true` | T-02 |
| XXE | upload `fixtures/xxe.xml` | 201, REJECTED, FF01, no local file content anywhere | T-03 |
| Mixed file | upload `fixtures/mixed-validity.xml` | 201, PARTIALLY_ACCEPTED; AC03, AM12 and DT01 on the rejected lines | T-05 |
| Rejections | `curl -H "X-API-Key: $SEPA_DEMO_API_KEY" "localhost:8080/v1/payment-files/{id}/instructions?status=REJECTED&size=5"` | 5 items ordered by `sequenceNo`, page metadata | T-06 |
| Report | `curl -H "X-API-Key: $SEPA_DEMO_API_KEY" -o pain002.xml localhost:8080/v1/payment-files/{id}/status-report` then `xmllint --noout --schema src/main/resources/xsd/pain.002.001.10.xsd pain002.xml` | `pain002.xml validates` | T-06 |
| Reused MsgId | upload `fixtures/payroll-same-msgid.xml` after the payroll | 201, REJECTED, DU01 | T-07 |
| Payroll scenario | upload `fixtures/payroll-4000-12-invalid-ibans.xml` | PARTIALLY_ACCEPTED, 3,988 / 12, all AC03 | T-08 |

## Automated checks

```bash
./mvnw -B verify                  # unit, integration and acceptance tests with Testcontainers
./mvnw -B verify -Pmemory-proof   # streaming parse of 10,000 instructions with a 64 MB heap
python3 -m unittest discover -s tools   # tests of the pain.001 generator
tools/measure.sh                  # SC-002, SC-003, SC-008 to SC-010 in a 512 MB container → docs/results/; exits non-zero on a miss
```
