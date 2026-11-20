---
sidebar_position: 5
title: Signing with Acrobat
---

# Signing with Acrobat

A common flow: your system generates a PDF, the user downloads it, signs it with Adobe Acrobat and uploads it
again. You want to be sure it is the same document, with only signatures added.

## Non-certified mode

Seal with `signatures-only` and verify what comes back:

```java
byte[] sealed = untouchable.seal(pdf, SealOptions.builder()
    .docId(documentId)
    .policy(Policies.SIGNATURES_ONLY)
    .build());
```

Acrobat **rewrites the whole file** when it signs a non-certified PDF, so a byte-level check would always fail.
Untouchable verifies the content, so the result is `INTACT_WITH_ALLOWED_CHANGES` with a `SIGNATURE_ADDED`
finding. This is the simplest setup and needs nothing but the seal key.

## Certified mode

With an X.509 certificate, the PDF can also be **certified with DocMDP**. Acrobat then always signs
**incrementally** (it appends the signature and leaves the sealed bytes untouched), and the library can verify
through its fast path.

```java
byte[] sealed = untouchable.seal(pdf, SealOptions.builder()
    .docId(documentId)
    .policy(Policies.SIGNATURES_ONLY)
    .formatOption(PdfSealOptions.builder()
        .signatureField("Signature1", 1, new Region(50, 40, 200, 60))   // page 1, PDF points
        .signatureField("Signature2", 1, new Region(300, 40, 200, 60))
        .certifyWith(certificatePrivateKey, certificate)                 // RSA or EC
        .build())
    .build());
```

:::warning Declare the signature fields
In a certified document Acrobat **does not allow creating new signature fields**: users can only sign fields
that already exist. That is why certified mode with a policy that allows signatures **requires** declared fields;
without them `seal` fails with a clear error. Users then sign by clicking the field.
:::

What certified mode adds:

| | Non-certified | Certified |
| --- | --- | --- |
| How Acrobat saves when signing | Full rewrite | Incremental |
| Verification method | `CONTENT` | `CERTIFIED_REVISIONS` (12–33× faster), `CONTENT` as fallback |
| Comments in Acrobat | Allowed by Acrobat, detected as `MODIFIED` | Blocked by Acrobat |
| Requires | Seal key | Seal key + X.509 certificate + declared fields |

The DocMDP level follows the policy: `signatures-only` and `signatures-and-forms` certify with P=2 (signing and
form filling allowed); `strict` certifies with P=1, which blocks signing altogether.

### How the fast path stays safe

The fast path only applies when:

1. the certification signature is valid and was made with **the issuer's certificate** (its hash is stored in the
   seal — an attacker cannot certify a modified document with their own certificate);
2. it covers the revision that contains the seal, so that revision is intact;
3. every later revision only adds signatures, their fields and appearances, validation data or metadata.

Anything else — an edited page, a new comment, a removed object — makes the library fall back to content
verification, which reports exactly what changed.

## Warnings worth checking

| Finding | When |
| --- | --- |
| `SIGNATURE_OVERLAPS_CONTENT` | A signature appearance covers more than 5% of page content: a signature image could hide text. |
| `SIGNATURE_OUTSIDE_DECLARED_FIELDS` | You declared fields and a signature is somewhere else. |
| `CERTIFICATION_INVALID` | The file was rewritten after certification (for example by a tool other than Acrobat). The content is still checked; only the certification is lost. |

## Documents that are already signed

If the PDF you seal already has signatures, the seal is written with an incremental save so they stay valid, and
those signatures are not reported as added. Certified mode cannot be used on them: DocMDP must be the first
signature in a document.
