---
sidebar_position: 1
slug: /intro
title: Introduction
---

# Untouchable

**Untouchable** is a Java library that **seals** documents and later **detects** whether their content was
modified — even if another tool re-saved or completely rewrote the file. The issuer decides which changes are
allowed (for example, adding electronic signatures) and the library tells them apart from everything else.

```java
byte[] sealed = untouchable.seal(pdf, SealOptions.builder().policy(Policies.SIGNATURES_ONLY).build());
VerificationResult result = untouchable.verify(received);
result.isTrusted(); // yes / no
```

## Why not just hash the file?

Many legitimate operations change the bytes of a document without changing what it says: signing it, updating
its metadata, or simply opening and saving it. Adobe Acrobat, for instance, **rewrites the whole PDF** when it
signs a non-certified document. A file hash breaks every time; Untouchable fingerprints the **content** instead
(text, images, drawing, annotations, form values), so it only reacts to real changes.

## What you get

- **A seal inside the file:** an Ed25519-signed fingerprint of the content, stored in the PDF itself. No
  database or sidecar file is needed.
- **A yes/no verdict and a detailed report:** `INTACT`, `INTACT_WITH_ALLOWED_CHANGES`, `MODIFIED`, `NO_SEAL`,
  `INVALID_SEAL` or `UNSUPPORTED`, plus findings that say what changed, on which page and roughly where.
- **Policies:** `strict`, `signatures-only` or `signatures-and-forms`, chosen by the issuer and stored in the
  signed seal, so the verifier cannot relax them.
- **Acrobat-friendly signing:** declared signature fields and an optional DocMDP certification that make
  Acrobat sign incrementally, with a fast verification path for that case.
- **Java 8 and later**, with no mandatory dependencies in the core.

## Compatibility

| Untouchable | Java | Formats | Notes |
| --- | --- | --- | --- |
| **0.1.0-SNAPSHOT** | 8, 11, 17, 21, 25 | PDF | Preview. Not yet on Maven Central. See [Compatibility](./reference/compatibility.md). |

## Next steps

- [Install the library](./getting-started/installation.mdx).
- Seal and verify [your first PDF](./getting-started/first-seal.md).
- Read [how it works](./guides/how-it-works.md) to understand what the fingerprint covers.
