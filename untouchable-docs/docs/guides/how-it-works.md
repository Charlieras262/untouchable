---
sidebar_position: 1
title: How it works
---

# How it works

## The seal

When you seal a document, Untouchable:

1. computes the **canonical content** of the document — what it says and shows, independent of how the file
   is encoded;
2. builds a **payload** with that fingerprint, the document id, the policy and the key id;
3. signs the payload with your key (Ed25519 by default);
4. stores payload and signature **inside the file**.

When you verify, it reads the seal back, checks the signature, recomputes the fingerprint of the received
document and compares both.

```
 Issuer                          Outside world                         Verifier
 ──────                          ─────────────                         ────────
 seal(doc, key, policy) ──► sealed doc ──► (anything) ──► verify(doc, public key)
                                                                    │
                                          INTACT / INTACT_WITH_ALLOWED_CHANGES
                                          / MODIFIED / NO_SEAL / INVALID_SEAL
```

The issuer and the verifier can be the same system or different parties that only share the public key.

## What the PDF fingerprint covers

| Component | What changes it |
| --- | --- |
| **Geometry** | Page size, crop box or rotation. |
| **Text** | Any change in the extracted text (normalized: Unicode NFC, collapsed whitespace). |
| **Images** | Any change in the **decoded pixels** of an image (re-compression with the same pixels does not count). |
| **Drawing** | Content-stream operators: paths, colors, positions, fonts. |
| **Annotations** | Comments, stamps, links, form widgets — every annotation that is **not** a signature. |
| **Form values** | The value of every form field that is not a signature field. |

Numbers are rounded to 2 decimals, so tools that round coordinates when they rewrite the file (595.276 →
595.28) do not trigger a change.

**Signature fields and their widgets are not part of the fingerprint.** That is what lets a document be signed
after sealing without its fingerprint changing; whether a signature is *allowed* is decided by the
[policy](./policies.md).

## Locating changes

The seal also stores a coarse picture of each page: the page is rendered without annotations and split into an
8×8 grid, and each tile is hashed. When the text, an image or the drawing of a page changes, the differing tiles
give the approximate **region** of the change (`Finding.region()`).

Tiles **never decide the verdict** — they only help you find the change. A flattened or "printed to PDF"
document that looks identical is still `MODIFIED`, because its structure is no longer the sealed one.

## Where the seal lives

| Location | Why |
| --- | --- |
| An XMP metadata packet with its own namespace | Standard place for metadata; preserved by common editors. |
| An `/Untouchable` entry in the PDF catalog | Redundancy. It is read first. |

Both survived every legitimate operation tested in Adobe Acrobat (save as, sign, sign twice, fill in forms).
Tools that rebuild the PDF from scratch, such as Ghostscript or "print to PDF", drop the seal: the result is
`NO_SEAL`, which is the correct answer for a document that is no longer the sealed one.

## Two verification paths

| Path | When | How |
| --- | --- | --- |
| `CONTENT` | Always available. | Recomputes the fingerprint and compares it component by component. Works even if the file was rewritten. |
| `CERTIFIED_REVISIONS` | The PDF was sealed with a DocMDP certification. | Checks that the sealed revision is untouched and that later revisions only add signatures. 12–33× faster. Falls back to `CONTENT` for anything else. |

`VerificationResult.method()` tells you which one was used. See [Signing with Acrobat](./pdf-signatures.md) for
the certified mode.
