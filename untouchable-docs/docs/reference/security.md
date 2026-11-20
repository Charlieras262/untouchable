---
sidebar_position: 2
title: Security model
---

# Security model

Untouchable cannot **prevent** anyone from editing a file they hold. What it guarantees is that a disallowed
change is **detected**.

## What is detected

| Attack | Result |
| --- | --- |
| Uploading a different document | `NO_SEAL`, `MODIFIED`, or `DOC_ID_MISMATCH` when you use `expectDocId`. |
| Copying the seal of a valid document onto another | The fingerprint does not match → `MODIFIED`. |
| Editing text or replacing an image | `TEXT_CHANGED` / `IMAGE_CHANGED`, with the page and region. |
| Adding, removing or reordering pages | `PAGE_ADDED` / `PAGE_REMOVED` / `PAGE_REORDERED`. |
| Covering content with a comment or stamp | `ANNOTATION_ADDED`. |
| Covering content with a large signature image | `SIGNATURE_OVERLAPS_CONTENT` warning. |
| Forging a seal | The attacker does not have the private key → `INVALID_SEAL`. |
| Editing the seal (for example its `docId`) | The seal signature no longer matches → `INVALID_SEAL`. |
| Removing the seal | `NO_SEAL`. |
| Modifying a document and certifying it with another certificate | The fast path only trusts the issuer's certificate; content verification reports the change. |
| Replacing a document with an older valid one (replay) | Not detected by the seal alone. Use `VerifyOptions.expectDocId(id)` and keep track of which id you expect. |

## What is out of scope

- **Legal validity of signatures.** The library checks that each signature matches the bytes it covers, not the
  certificate chain, revocation or eIDAS rules. Use a dedicated validator such as EU DSS.
- **Confidentiality.** The seal is signed, not encrypted. `claims` are readable by anyone who opens the file.
- **Who made a change.** The library says what changed, not who changed it.
- **Flattened or printed documents.** A document printed to PDF may look identical, but it is `MODIFIED`.

## Recommendations

- Keep the **private key** in a keystore, KMS or HSM; a service that only verifies needs only the public key.
- Prefer **Ed25519** over HMAC whenever the verifier is not the issuer.
- Always pass the **expected `docId`** when you know which document should come back.
- Treat **warnings** as a signal to review the document, especially `SIGNATURE_OVERLAPS_CONTENT`.
- Limit the size of uploaded documents (`maxDocumentBytes`, default 256 MB).
