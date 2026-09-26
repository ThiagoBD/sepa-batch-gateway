# ISO 20022 Message Contract: Payment File Intake

The official XSDs live in `src/main/resources/xsd/`. Business rules are in [spec.md](../spec.md) (RN-03 to RN-13).

| File | Version and namespace | Direction | Rules |
| --- | --- | --- | --- |
| pain.001 | pain.001.001.09, `urn:iso:std:iso:20022:tech:xsd:pain.001.001.09` | ERP → gateway | UTF-8, up to 20 MB, no DOCTYPE, valid against the ISO XSD; RN-03 to RN-11 |
| pain.002 | pain.002.001.10 | gateway → ERP | GrpSts ACTC, PART or RJCT; TxInfAndSts only for rejected transactions; NbOfTxsPerSts (RN-13) |

Profile: EPC SEPA Credit Transfer, Customer-to-PSP Implementation Guidelines 2025.

## pain.001.001.09: what the gateway reads

| Level | Elements | Used for |
| --- | --- | --- |
| Group header (`GrpHdr`) | `MsgId`, `NbOfTxs`, `CtrlSum` | Duplicate MsgId (RN-02), group totals (RN-04) |
| Payment block (`PmtInf`) | `PmtInfId`, `NbOfTxs`, `CtrlSum`, `ReqdExctnDt`, `Dbtr/Nm`, `DbtrAcct/Id/IBAN` | Block totals (RN-04), execution date (RN-05), debtor account (RN-06) |
| Transaction (`CdtTrfTxInf`) | `PmtId/InstrId`, `PmtId/EndToEndId`, `Amt/InstdAmt` and `Ccy`, `CdtrAgt/FinInstnId/BICFI`, `Cdtr/Nm`, `CdtrAcct/Id/IBAN` | Amount and currency (RN-09), BIC (RN-08), creditor name (RN-10), creditor IBAN (RN-07) |

Reading: the whole file is validated against the XSD first; then a StAX cursor unmarshals only `GrpHdr`, the header children of each `PmtInf` and each `CdtTrfTxInf` on its own. The whole `PmtInf` is never unmarshalled.

Postal addresses (`PstlAdr`) are neither validated nor stored in the MVP; the structured-address rule is planned for V2.

## pain.002.001.10: what the gateway writes

- `GrpHdr`: new `MsgId` (`STS-` plus a short id), `CreDtTm`.
- `OrgnlGrpInfAndSts`: `OrgnlMsgId` (`NOTPROVIDED` when the file could not be read), `OrgnlMsgNmId` = `pain.001.001.09`, `OrgnlNbOfTxs`, `OrgnlCtrlSum`, `GrpSts`, `StsRsnInf` only for a group reason, `NbOfTxsPerSts` with the ACTC and RJCT counts.
- `OrgnlPmtInfAndSts`: only for PART or RJCT blocks.
- `TxInfAndSts`: only for transactions rejected for their own reason, with `OrgnlInstrId`, `OrgnlEndToEndId`, `TxSts` RJCT and `StsRsnInf/Rsn/Cd`.

Every report produced in tests is validated against the pain.002.001.10 XSD before any other assertion.

Excerpt for the payroll scenario (element order follows the XSD):

```xml
<OrgnlGrpInfAndSts>
  <OrgnlMsgId>LIFFEY-PAYROLL-20261002</OrgnlMsgId>
  <OrgnlMsgNmId>pain.001.001.09</OrgnlMsgNmId>
  <OrgnlNbOfTxs>4000</OrgnlNbOfTxs>
  <GrpSts>PART</GrpSts>
  <NbOfTxsPerSts><DtldNbOfTxs>3988</DtldNbOfTxs><DtldSts>ACTC</DtldSts></NbOfTxsPerSts>
  <NbOfTxsPerSts><DtldNbOfTxs>12</DtldNbOfTxs><DtldSts>RJCT</DtldSts></NbOfTxsPerSts>
</OrgnlGrpInfAndSts>
<OrgnlPmtInfAndSts>
  <OrgnlPmtInfId>PAYROLL-OCT</OrgnlPmtInfId>
  <PmtInfSts>PART</PmtInfSts>
  <TxInfAndSts>
    <OrgnlEndToEndId>EMP-0042</OrgnlEndToEndId>
    <TxSts>RJCT</TxSts>
    <StsRsnInf><Rsn><Cd>AC03</Cd></Rsn></StsRsnInf>
  </TxInfAndSts>
</OrgnlPmtInfAndSts>
```

## Events

None in the MVP: there is no broker. V3 plans S3 object notifications through SQS, with at-least-once delivery made harmless by RN-01.
