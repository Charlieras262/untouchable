---
sidebar_position: 3
title: Verification results
---

# Verification results

`verify` never throws because of the document: an unreadable or unknown file gives `UNSUPPORTED`. Everything
you need is in the returned `VerificationResult`.

```java
VerificationResult r = untouchable.verify(pdf, VerifyOptions.expectDocId("invoice-123"));

r.isTrusted();               // yes/no
r.status();                  // see below
r.reason();                  // short explanation for NO_SEAL, INVALID_SEAL and UNSUPPORTED
r.seal();                    // docId, issuedAt, policyId, keyId, claims (null if there is no valid seal)
r.findings();                // every finding, with its severity
r.findings(Severity.WARNING);
r.hasWarnings();
r.signatures();              // electronic signatures found in the document
r.method();                  // CONTENT or CERTIFIED_REVISIONS
```

## Statuses

| Status | Meaning | `isTrusted()` |
| --- | --- | --- |
| `INTACT` | Content identical to the sealed one. | ✅ |
| `INTACT_WITH_ALLOWED_CHANGES` | Identical content; only changes the policy allows (e.g. signatures). | ✅ |
| `MODIFIED` | The content differs, or there are changes the policy does not allow. | ❌ |
| `NO_SEAL` | The document has no seal: another file, or the seal was removed. | ❌ |
| `INVALID_SEAL` | The seal is there but its signature is invalid, the key is unknown or the seal is damaged. | ❌ |
| `UNSUPPORTED` | Unknown format, unknown policy or fingerprint version, or unreadable file. | ❌ |

## Findings

Each `Finding` has a `type()`, a `severity()` (`INFO`, `WARNING` or `VIOLATION`, assigned by the
[policy](./policies.md)), a `page()` (1-based, or `null` for the whole document), an optional `region()` and a
human-readable `description()`.

```java
for (Finding f : r.findings()) {
    System.out.println(f);
}
// VIOLATION TEXT_CHANGED p.2 [x=297.0, y=106.9, w=74.0, h=105.0]: The page text changed
// INFO SIGNATURE_ADDED p.1 [x=50.0, y=40.0, w=200.0, h=60.0]: Signature in field Signature1 by Jane Doe (VALID)
```

`Region` is in PDF points with the origin at the bottom-left corner of the page.

### Finding types

| Type | Meaning |
| --- | --- |
| `PAGE_ADDED` / `PAGE_REMOVED` / `PAGE_REORDERED` | Pages were added, removed or moved. |
| `PAGE_GEOMETRY_CHANGED` | Page size, crop box or rotation changed. |
| `TEXT_CHANGED` | The page text changed. |
| `IMAGE_CHANGED` | An image on the page changed. |
| `GRAPHICS_CHANGED` | The drawing changed (paths, colors, positions) without changing text or images. |
| `CONTENT_CHANGED` | Content change with no further detail (formats without a breakdown). |
| `ANNOTATION_ADDED` / `ANNOTATION_REMOVED` | A non-signature annotation (comment, stamp, link…) was added or removed. |
| `FORM_FIELD_CHANGED` | A form field value changed, or a field was added or removed. |
| `SIGNATURE_ADDED` | A signature was added after sealing. |
| `SIGNATURE_OVERLAPS_CONTENT` | A signature appearance covers page content (warning). |
| `SIGNATURE_OUTSIDE_DECLARED_FIELDS` | Signature fields were declared and a signature is not in any of them (warning). |
| `CERTIFICATION_INVALID` | The DocMDP certification applied when sealing is no longer valid (warning). |
| `DOC_ID_MISMATCH` | The document is not the one you asked for with `expectDocId`. |

## Signatures

`r.signatures()` lists every electronic signature found, with:

| Method | Meaning |
| --- | --- |
| `fieldName()`, `signerName()`, `signDate()` | As declared in the signature. The date is not a trusted timestamp. |
| `integrity()` | `VALID`, `INVALID` or `UNKNOWN`: whether the signature matches the bytes it covers. |
| `isCertification()` | The DocMDP certification signature. |
| `isAddedAfterSeal()` | Added after the document was sealed. |
| `coversWholeDocument()` | It is the last revision of the file. |
| `page()`, `region()` | Where the visible widget is (`null` for invisible signatures). |

:::note Cryptographic, not legal
Untouchable checks that each signature matches the bytes it covers. It does **not** check the certificate
chain, revocation or legal validity (eIDAS). Use a dedicated validator such as EU DSS for that.
:::

## PDF verification options

```java
VerifyOptions options = VerifyOptions.builder()
    .expectDocId("invoice-123")
    .formatOption(PdfVerifyOptions.builder()
        .overlapThreshold(0.10)   // default 0.05: 5% of the signature area
        .locateChanges(true)      // render pages to locate changes (default true)
        .fastPath(true)           // use CERTIFIED_REVISIONS when possible (default true)
        .build())
    .build();
```
