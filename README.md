# Untouchable

A Java library to **seal** documents and **detect** whether their content was modified afterwards, even if
another tool rewrote the file. It tolerates the changes the issuer allows, such as adding electronic
signatures.

- **Java 8 (1.8) and later.**
- **Apache 2.0** license.
- Supported format today: **PDF**.

Specification: [SPEC.md](SPEC.md) · The compatibility spike that validated the design lives in the private `untouchable-spike` repository.

> Status: **0.1.0-SNAPSHOT (phase F1)**, not yet published to Maven Central.

## How it works

1. **Seal:** the library computes a fingerprint of the **content** (text, images, drawing, annotations,
   forms), signs it with the issuer's key (Ed25519 by default) and stores it inside the PDF itself.
2. **Verify:** it recomputes the fingerprint of the received document and compares it with the sealed one.
   Changing bytes without changing the content (re-saving, signing, updating metadata) does not count as
   a modification.

## Modules

| Artifact | Contents | Dependencies |
|---|---|---|
| `io.qzz.toshiro:untouchable-core` | API, seal, cryptography, policies | None required; Bouncy Castle optional (Ed25519 on Java 8–14) |
| `io.qzz.toshiro:untouchable-pdf` | PDF format | PDFBox 3.0.5, Bouncy Castle 1.80 |

```xml
<dependency>
  <groupId>io.qzz.toshiro</groupId>
  <artifactId>untouchable-pdf</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Usage

```java
// Keys: the library does not store them (use a keystore, KMS, etc.).
KeyPair keys = Ed25519.generateKeyPair();

Untouchable untouchable = Untouchable.builder()
    .signer(Ed25519.signer(keys.getPrivate(), "key-2026"))
    .keyResolver(KeyResolver.of("key-2026", Ed25519.verifier(keys.getPublic())))
    .build();   // the PDF handler is discovered automatically

// Seal: only adding signatures will be allowed.
byte[] sealed = untouchable.seal(pdf, SealOptions.builder()
    .docId("invoice-123")
    .policy(Policies.SIGNATURES_ONLY)
    .build());

// Verify what comes back.
VerificationResult r = untouchable.verify(received, VerifyOptions.expectDocId("invoice-123"));
r.isTrusted();     // yes/no
r.status();        // INTACT, INTACT_WITH_ALLOWED_CHANGES, MODIFIED, NO_SEAL, INVALID_SEAL, UNSUPPORTED
r.findings();      // details: [VIOLATION TEXT_CHANGED p.2 [x=…], INFO SIGNATURE_ADDED p.1 …]
r.signatures();    // signatures found (informational)
r.method();        // CONTENT or CERTIFIED_REVISIONS (certified-mode fast path)
```

### Policies

| Policy | Allows |
|---|---|
| `Policies.STRICT` *(default)* | Only changes that do not touch the content (re-saving, metadata). |
| `Policies.SIGNATURES_ONLY` | The above + new signatures. |
| `Policies.SIGNATURES_AND_FORMS` | The above + filling in existing form fields. |

The policy is stored inside the signed seal: the verifier cannot relax it. Custom policies can be
registered with `Untouchable.builder().policy(...)`.

### Documents signed with Adobe Acrobat

Acrobat **rewrites the whole file** when signing a non-certified PDF; content verification tolerates that.
To make it sign incrementally, the document can be certified with DocMDP. In that case the **signature
fields must be declared**, because Acrobat does not allow creating new fields in a certified document:

```java
SealOptions options = SealOptions.builder()
    .policy(Policies.SIGNATURES_ONLY)
    .formatOption(PdfSealOptions.builder()
        .signatureField("Signature1", 1, new Region(50, 40, 200, 60))  // page 1, PDF points
        .certifyWith(certPrivateKey, certificate)                       // optional (X.509, RSA or EC)
        .build())
    .build();
```

With the certification, verification uses a **fast path**: it checks that the sealed revision is still
intact and that later revisions only add signatures, without recomputing the content fingerprint. It is
between 12 and 33 times faster on 10- to 50-page documents. It only applies if the certification was
made with the issuer's certificate; for any other change, the content is verified.

What is detected and reported:
- a signature that is not in a declared field (`SIGNATURE_OUTSIDE_DECLARED_FIELDS`);
- a signature that covers page content (`SIGNATURE_OVERLAPS_CONTENT`, above 5% of its area);
- a certification that is no longer valid (`CERTIFICATION_INVALID`).

## Verified compatibility

Details and restrictions in [SPEC §10.1](SPEC.md):

- **Java 8, 11, 17, 21 and 25** (Eclipse Temurin, Linux x64) and OpenJDK 27: build and 48/48 tests on each.
- **Across JDKs:** a seal made on any of those versions verifies the same way on all the others
  (5×5 cross test).
- Adobe Acrobat Reader 2026.003 (Windows): the seal of PDFs sealed by this library survives saving and
  signing (certified and non-certified), and comments are detected or blocked by Acrobat.

## Build

All you need is JDK 8 or later: the wrapper downloads Maven.

```bash
./mvnw verify
```

With [mise](https://mise.jdx.dev), to try another Java version:

```bash
mise exec java@temurin-8 -- ./mvnw verify
```
