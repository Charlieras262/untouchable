# Untouchable — Specification (draft v0.5)

> A Java library to **seal** documents and **detect** whether their content was modified after sealing,
> while tolerating legitimate changes such as adding electronic signatures.

Status: draft for discussion · Date: 2026-11-20

---

## 1. Problem

A document leaves a controlled environment (a system, an organization, a person) and at some point comes
back or arrives somewhere else. What happened to it along the way is unknown. Whoever receives it needs to
answer:

1. Is it **the same document** that was issued? (not "I downloaded kittens and got puppies back")
2. Is the **content** still intact, even if another tool re-saved or rewrote the file?
3. Are the only changes the ones the issuer **allowed** (e.g. adding signatures)?
4. If there were disallowed changes, **where are they?**

Comparing bytes or a hash of the whole file does not work: many legitimate operations change the bytes
without changing the content (signing, updating metadata, re-saving, rewriting the internal structure).

### 1.1 Example use cases

They are illustrative: the library is general-purpose and not designed around any of them.

- **External signing:** a system generates a PDF, the user signs it with their own tool (e.g. Adobe
  Acrobat, which sometimes rewrites the file) and uploads it again. *(This case started the idea.)*
- **Issued documents:** certificates, attestations or reports that a third party presents later and that
  must be validated without asking the issuer.
- **Evidence:** images or documents that must not be altered between capture and review.
- **Exchange between organizations:** contracts or spreadsheets that travel between parties and must
  arrive without content changes.

## 2. Goals

| # | Goal |
|---|---|
| G1 | Seal a document **inside the file itself**, without altering its visible content. |
| G2 | Verify a document and return a simple verdict (yes/no) **and** an optional detailed report. |
| G3 | Verification is based on **content**, not bytes: it survives rewrites, recompression and metadata changes. |
| G4 | Tell **allowed changes** (added signatures) apart from **disallowed changes**, through a configurable policy. |
| G5 | A third party **cannot forge** a valid seal without the system's key. |
| G6 | Extensible per-format architecture: PDF → images → Office → text → any file. |
| G7 | Lightweight: **Java 8 (1.8) and later**, few dependencies and permissive licenses (Apache 2.0 / MIT). |

## 3. Out of scope (non-goals)

- **Preventing** modification. It is impossible to stop someone from editing a file they hold; the library
  guarantees that **any disallowed modification is detected**.
- Checking the **legal validity** of Adobe signatures (certificate chain, revocation, eIDAS). This can be
  delegated to EU DSS as an optional integration (see §11).
- Requiring a **number of signatures** or **specific signers**. The library only reports the added
  signatures; the application decides whether they are enough.
- Accepting **flattened** or "printed to PDF" documents: they are always `MODIFIED`.
- Confidentiality or encryption of the document.
- Identifying who made a change.
- Robust watermarks or anti-piracy.

## 4. Context: existing solutions

| Solution | What it provides | Why it is not enough on its own |
|---|---|---|
| **EU DSS** | PAdES/XAdES/CAdES signing and validation; modification detection across PDF revisions. | Heavyweight, focused on eIDAS/PKI and only on standard signature formats. |
| **PDF certification signature (DocMDP)** | PDF standard: the author "certifies" the document and declares which changes are allowed (e.g. signing only). | Requires an X.509 certificate. If a tool rewrites the file, the certification breaks. |
| **Apache PDFBox** | Reading, editing, signing and rendering PDFs (Apache 2.0). | A toolkit: it has no notion of a "content seal". |
| **iText** | Very complete PDF signing. | AGPL or commercial license. |
| **Apache POI / Santuario** | Signatures in Office and XML. | Format-specific. |
| **C2PA** | Content provenance standard (images and video). | Immature Java ecosystem; media-oriented. |

**Untouchable's value proposition:** a single, simple API (`seal` / `verify`) that works on the document's
**canonical content** and understands which changes are legitimate (signatures). It covers exactly the
case where standard signatures fail: a rewritten document whose content is intact.

## 5. Concepts

- **Seal:** a cryptographically signed structure embedded in the document. It contains the document
  identifier and the fingerprint of its content.
- **Canonical content:** a normalized representation of "what the document says and shows", independent
  of how it is serialized into bytes. Each format defines its own.
- **Fingerprint:** a set of hashes of the canonical content, organized by parts (pages, images, regions)
  so that changes can be located.
- **Change policy (`ChangePolicy`):** rules that define which differences are accepted
  (e.g. "added signatures: yes; new annotations: no").
- **Format handler (`FormatHandler`):** a plugin that knows, for a specific format, how to extract the
  canonical content, embed and read the seal, and classify differences.

## 6. Overall flow

```
 Issuer                          Outside world                         Verifier
 ──────                          ─────────────                         ────────
 document
   │
 seal(doc, key, policy) ──► sealed doc ──► (anything) ──► verify(doc, public key)
                                                                    │
                                          INTACT / INTACT_WITH_ALLOWED_CHANGES
                                          / MODIFIED / NO_SEAL / INVALID_SEAL
```

The issuer and the verifier can be the same system ("external signing" case) or different parties that
only share the public key ("issued documents" case).

## 7. Functional requirements

### 7.1 Sealing

- **FR-1** `seal` takes the document, the signing key and options (docId, change policy, free-form
  metadata) and returns the sealed document.
- **FR-2** Sealing **does not alter the canonical content**: `verify` right after `seal` must return `INTACT`.
- **FR-3** The seal is embedded in a location of the format that common editors preserve
  (for PDF: XMP + a custom catalog entry, for redundancy; see §9.1).
- **FR-4** Sealing a document that already has a seal replaces it or rejects it, depending on configuration.
- **FR-5** (PDF) If an X.509 certificate is configured, also apply a **DocMDP certification signature**.
  It is **recommended** for `signatures-only` and `signatures-and-forms`: the spike showed that with
  DocMDP Acrobat **always saves incrementally**, while without it Acrobat rewrites the whole file (spike
  report, §4.3). Without a certificate, only the library's own seal (Ed25519) is used and verification is
  content-based (§9.1, non-certified mode).
  - The DocMDP level derives from the policy: `strict` → P=1 (tools block signing);
    `signatures-only` and `signatures-and-forms` → P=2. P=2 allows signing and filling in forms, so the
    "signatures only" restriction is enforced by Untouchable at verification time (FR-8a), not by DocMDP.
  - With P=2, Acrobat **does not allow creating new signature fields**: it only signs existing ones. That
    is why DocMDP with a policy that allows signatures **requires** declaring signature fields (FR-12); if
    there are none, `seal` fails with a clear error.
- **FR-12** (PDF) `seal` can **declare signature fields** (name, page, rectangle), which are created empty
  in the document before sealing and certifying. Signature fields and their widgets are not part of the
  fingerprint, so signing them does not change the canonical content. If fields were declared, a
  signature that is not in any of them produces `SIGNATURE_OUTSIDE_DECLARED_FIELDS` (warning).

### 7.2 Verification

- **FR-6** `verify` returns a `VerificationResult` with:
  - `status` (see §7.3) and `isTrusted()`, a boolean ready for "yes/no";
  - the detailed findings, and the method used (`CONTENT` or `CERTIFIED_REVISIONS`).
- **FR-7** Validate the seal's cryptographic signature with the system key (by `keyId`).
- **FR-8** Recompute the fingerprint of the received document, **excluding** what the policy allows, and
  compare it with the sealed fingerprint.
- **FR-8a** The **issuer chooses the policy when sealing** and it is stored inside the signed seal: the
  verifier cannot relax it. Built-in policies:

  | Policy | Allows | Typical use |
  |---|---|---|
  | `strict` *(default)* | Only metadata and internal-structure rewrites with no content change. | Issued documents, evidence. |
  | `signatures-only` | The above + new signatures with their appearances (including images) and signature validation data (DSS/VRI in PDF). | External signing. |
  | `signatures-and-forms` | The above + filling in **existing** form fields. | Forms to be completed and signed. |

  Anything the policy does not allow is a violation. **Custom policies** can be registered through an SPI
  (`ChangePolicy`); the seal stores its identifier, and the verifier must know it or the result is
  `UNSUPPORTED`.
- **FR-9** Locate differences: page, kind of change and, when possible, region (bounding box).
- **FR-10** Detect and report **added signatures** (count, signer name if available, declared date, page
  and rectangle). This is **informational only**: the library does not require a count or specific
  signers, nor does it check the legal validity of signatures.
- **FR-11** A flattened or "printed to PDF" document is reported as `MODIFIED`, even if it looks the same.
  The visual comparison (tiles) is only used to **locate** changes, **never to accept** a document.

### 7.3 Verification statuses

| Status | Meaning | `isTrusted()` |
|---|---|---|
| `INTACT` | Content identical to the sealed one; no changes. | ✅ |
| `INTACT_WITH_ALLOWED_CHANGES` | Identical content; only changes allowed by the policy (signatures). | ✅ |
| `MODIFIED` | The content differs from the sealed one. | ❌ |
| `NO_SEAL` | The document has no seal (another file, or the seal was removed). | ❌ |
| `INVALID_SEAL` | The seal exists but its signature is invalid or the key is unknown (forgery). | ❌ |
| `UNSUPPORTED` | Unsupported format or corrupt file. | ❌ |

### 7.4 Finding types (detailed report)

`PAGE_ADDED`, `PAGE_REMOVED`, `PAGE_REORDERED`, `PAGE_GEOMETRY_CHANGED`, `TEXT_CHANGED`,
`IMAGE_CHANGED`, `GRAPHICS_CHANGED`, `CONTENT_CHANGED`, `ANNOTATION_ADDED`, `ANNOTATION_REMOVED`,
`FORM_FIELD_CHANGED`, `SIGNATURE_ADDED`, `SIGNATURE_OVERLAPS_CONTENT` *(warning)*,
`SIGNATURE_OUTSIDE_DECLARED_FIELDS` *(warning, FR-12)*, `CERTIFICATION_INVALID` *(warning)*,
`DOC_ID_MISMATCH`.

`SIGNATURE_OVERLAPS_CONTENT` is raised when more than **5%** of the widget area (measured in 6×6 px cells
at 72 dpi: a cell counts if any pixel in it is drawn) falls on content of the page rendered without
annotations. The threshold is configurable.

The severity of each finding depends on the policy: for example, `SIGNATURE_ADDED` is `INFO` under
`signatures-only` and `VIOLATION` under `strict`.

Each finding includes: type, severity (`INFO` / `WARNING` / `VIOLATION`), page, optional region and a
human-readable description.

## 8. Threat model

| Attack | How it is detected |
|---|---|
| Uploading a different document (kittens → puppies) | `NO_SEAL`, or the seal does not match the fingerprint → `MODIFIED`. |
| Copying the seal of a valid document onto another | The fingerprint does not match → `MODIFIED`. |
| Editing the text or an image | Different page or resource hash → `MODIFIED` + location. |
| Adding, removing or reordering pages | Per-page fingerprint → `PAGE_*`. |
| Covering content with an annotation or visual stamp | Non-signature annotations are not allowed → `ANNOTATION_ADDED`. |
| Covering content with a **signature image** | `SIGNATURE_OVERLAPS_CONTENT` warning (widget area over rendered content, 5% threshold). Confirmed in the spike: a signature over the text gave 11%. With declared fields (FR-12), also `SIGNATURE_OUTSIDE_DECLARED_FIELDS`. |
| Forging a seal | The attacker does not have the system's private key → `INVALID_SEAL`. |
| Removing the seal | `NO_SEAL` → rejected. |
| Modifying a document and certifying it with one's own certificate | The fast path only trusts a certification made with the issuer's certificate (its hash is in the seal); otherwise the content is verified → `MODIFIED`. |
| Hidden content: layers (OCG), JavaScript or invisible text | Included in the canonical content; detected as a change. *(To be refined in the design)* |
| Reusing an old valid document instead of the current one (replay) | Not detected by the seal alone. Mitigation: the system checks the expected `docId` (§9.3). |

## 9. Proposed design

### 9.1 PDF (phase 1)

**Canonical content per page:**
1. Geometry: MediaBox, CropBox and rotation.
2. Extracted and normalized text (order, whitespace, Unicode NFC).
3. Hash of each image, computed over the **decoded pixels** (not over the compressed stream).
4. Decoded content-stream operators, normalized and with resource references resolved.
5. Visual location: rendering of the page **without annotations** at a fixed resolution + per-tile hashes
   (N×M grid) to locate regions.

Items 1–4 decide the verdict. Item 5 is only used to locate the change in the detailed report; it never
turns a document into an accepted one (FR-11).
Annotations are handled separately: they are inventoried, and by default only signature widgets are allowed.
At document level, the values of non-signature form fields are added.

**Numeric normalization:** all coordinates and numeric operands are rounded to **2 decimals**. The spike
showed that some tools round when rewriting (595.276 → 595.28), and with 3 decimals that showed up as a
geometry change.

**Stability of `ops`:** it stays in the verdict, because it was stable in every Acrobat rewrite. Tools
that re-emit content streams (e.g. Ghostscript) change it even when text, images and rendering are equal;
in practice those tools also remove the seal (`NO_SEAL`). Future improvement: normalize by interpreting
the graphics state (final position of each glyph, path and image) instead of comparing operators.

**Verification modes** (for policies that allow signatures):

| Mode | Sealing requirements | Verification |
|---|---|---|
| **Certified** *(recommended)* | X.509 certificate → DocMDP P=2 + declared signature fields (FR-5, FR-12) | **Fast, by revisions** (`VerificationMethod.CERTIFIED_REVISIONS`): (1) the seal is valid; (2) there is exactly one certification signature, it is valid, **it was made with the issuer's certificate** (its hash is in the seal) and its `ByteRange` covers the revision that contains the seal, which proves that revision is intact; (3) appended revisions only contain signature objects (dictionaries, fields, widgets), appearances, DSS and new form resources, metadata (XMP, Info) and structure (xref, object streams); the catalog, the AcroForm and the pages only change in the keys a signature touches and only to add signature fields or widgets; no object is removed and the root does not change. If anything fails, verification falls back to content. |
| **Non-certified** | The seal only | **By content:** recompute the fingerprint excluding what is allowed and compare it with the sealed one. Works even when the tool rewrites the file (confirmed with Acrobat Reader). |

In both modes, content verification is the safety net and the one that produces the detailed per-page report.

**Where the seal goes:** in an XMP packet with its own namespace (`untouchable:`) and, for redundancy, in
a catalog entry (`/Untouchable`). In the spike both survived every legitimate Acrobat operation (save as,
sign, sign twice, fill in forms). They are lost with aggressive rewrites (Ghostscript, print to PDF),
which must give `NO_SEAL` / `MODIFIED` anyway. A server-side registry by `docId` remains an optional
integration (§11).

### 9.2 Seal structure

```json
{
  "v": 1,
  "docId": "uuid",
  "issuedAt": "2026-11-20T12:00:00Z",
  "format": "pdf",
  "canon": "pdf-canon/1",
  "alg": "Ed25519",
  "kid": "system-key-2026",
  "policy": "signatures-only",
  "claims": { "…": "free-form system metadata" },
  "fingerprint": {
    "pageCount": 3,
    "pages": [ { "geom": "…", "text": "…", "images": "…", "ops": "…", "annots": ["…"], "tiles": "…" } ],
    "forms": { "field": "…" },
    "renderProfile": "pdf-tiles/1;dpi=72;grid=8;aa=off",
    "root": "…"
  },
  "formatData": {
    "signatureFields": [ { "name": "Signature1", "page": 1, "rect": ["50.00", "40.00", "200.00", "60.00"] } ],
    "certified": true,
    "docMdp": 2,
    "certificationCert": "sha256 of the issuer's certificate"
  }
}
```

- The payload is signed separately; the signature (Base64) is stored next to it, not inside it.
- `canon` versions the canonicalization algorithm: a version change must not invalidate old seals.
- `kid` allows key rotation.
- `formatData.signatureFields` only appears if signature fields were declared (FR-12).
- `renderProfile` identifies how the tiles were computed (NFR-3).
- No hash of the original revision is needed: in certified mode, the DocMDP signature already covers
  that revision (§9.1).
- In certified mode, `formatData.certificationCert` stores the SHA-256 of the issuer's certificate.
  Without that binding, an attacker could modify a document and certify it with their own certificate,
  and the fast path would accept it.
- Serialization: **canonical JSON (RFC 8785)**, because it is readable when debugging and size is not an
  issue in PDF/Office. The canonical serialization is what gets signed.

### 9.3 Keys and trust

- By default, **Ed25519** (asymmetric): only the system can seal, and anyone with the public key can verify.
  - Ed25519 is only in the JDK since **Java 15**. On Java 8–14 **Bouncy Castle** is used
    (`bcprov-jdk18on`, Java 8 compatible). The library chooses automatically: JCA if the algorithm is
    available, otherwise Bouncy Castle. If neither is available, the error says so explicitly.
  - Ed25519 signatures are deterministic (RFC 8032): JCA and Bouncy Castle produce and verify the same
    signatures, so a seal made on Java 21 verifies on Java 8 and vice versa.
- Optional: **HMAC-SHA256**, if only the system itself verifies.
- The library does **not** store keys: it receives a `KeyResolver` (KMS, keystore, HSM).
- The caller can require a specific `docId`: `VerifyOptions.expectDocId(id)`, against replay.

### 9.4 API

```java
Untouchable u = Untouchable.builder()
    .signer(Ed25519.signer(privateKey, "system-key-2026"))
    .keyResolver(KeyResolver.of("system-key-2026", Ed25519.verifier(publicKey)))
    .build();

byte[] sealed = u.seal(pdfBytes, SealOptions.builder()
    .docId(id)
    .policy(Policies.SIGNATURES_ONLY)
    .formatOption(PdfSealOptions.builder()
        .signatureField("Signature1", 1, new Region(50, 40, 200, 60))   // FR-12
        .certifyWith(certPrivateKey, certificate)                       // optional: certified mode (FR-5)
        .build())
    .build());

VerificationResult r = u.verify(uploadedBytes, VerifyOptions.expectDocId(id));
r.isTrusted();         // yes/no
r.status();            // INTACT_WITH_ALLOWED_CHANGES
r.findings();          // [INFO SIGNATURE_ADDED p.1, …]
r.method();            // CERTIFIED_REVISIONS or CONTENT
```

### 9.5 Extensibility (future phases)

| Format | Canonical content | Where the seal goes | Allowed changes |
|---|---|---|---|
| **PDF** (F1) | §9.1 | XMP + catalog | Signatures and their appearance |
| **Images** PNG/JPEG/TIFF/WebP (F2) | Decoded pixels + per-tile hashes | XMP / iTXt chunk / APP segment | Metadata. *Lossy recompression: to be defined* |
| **Office** OOXML: docx/xlsx/pptx (F3) | Normalized XML parts (C14N), excluding docProps and signatures | Custom part of the OPC package | Office XAdES signatures, metadata |
| **Text** (F4) | Normalized text (NFC, line endings) | Language-specific comment block (front matter, `#`, `//`) or fallback | *To be defined* |
| **Generic** (F5) | Bytes | Trailer appended at the end (if the format tolerates it) or sidecar | None |

## 10. Non-functional requirements

- **NFR-1** **Java 8 (1.8) and later.**
  - Compiled with `--release 8`: no Java 9+ APIs or syntax in the library code (no `record`, `var`,
    arrow `switch`, text blocks, `List.of`, `HexFormat`, etc.).
  - Dependencies with Java 8 support: PDFBox 3.0.x and Bouncy Castle `*-jdk18on` qualify.
  - Core without mandatory dependencies. Bouncy Castle is **optional in the core** (only needed for
    Ed25519 on Java 8–14) and **mandatory in the PDF module** (DocMDP signing and CMS verification), so
    PDF users get it transitively.
  - Continuous integration against LTS **8, 11, 17, 21 and 25** and the latest available version.
  - It can be published as a multi-release JAR if a newer Java version brings something useful, without
    breaking Java 8.
- **NFR-1a** **Same fingerprint on any JDK:** sealing on one JDK and verifying on another (e.g. sealing on
  Java 21 and verifying on Java 8) must give the same verdict. The verdict components (geometry, text,
  images, operators) must not depend on the Java version. See the rendering risk in NFR-3.
- **NFR-2** Modules: `untouchable-core`, `untouchable-pdf`, `untouchable-image`, `untouchable-office`, …
- **NFR-3** Determinism: the same input always gives the same fingerprint. Tile rendering uses **fixed**
  parameters: antialiasing off (with antialiasing on, most tiles change), fixed DPI and no annotations.
  Identical results across operating systems are still to be confirmed (§12).
  - **Rasterizer:** Java 8 uses Pisces and Java 9+ uses Marlin. Both are the **antialiased** rasterizers;
    with antialiasing off, Java2D uses a different fill path. In the cross-JDK test (Temurin 8u514 vs.
    OpenJDK 27) the tiles were **identical in all 104 PDFs**. This reinforces that antialiasing must be
    off. The seal still stores a `renderProfile`: if the verifier has a different profile, the tiles are
    still compared, but differences are flagged as "possibly environmental". The verdict does not change,
    because tiles never decide (FR-11).
  - **Images:** the decoded-pixel hash (including JPEG) was identical between Java 8 and Java 27.
- **NFR-4** Performance target: verify a 20-page PDF in under 2 s on common hardware. *(To be validated.)*
- **NFR-5** Safety against malicious files: limits on size, pages and memory; never run JavaScript.
- **NFR-6** Project license: **Apache 2.0**.
- **NFR-7** Maven coordinates: `groupId` **`io.qzz.toshiro`**; one `artifactId` per module
  (`untouchable-core`, `untouchable-pdf`, …). Root package: `io.qzz.toshiro.untouchable`.

## 10.1 Verified compatibility and restrictions

Compatibility is **only guaranteed for what is in this table**. Everything else is considered unverified
until a spike round confirms it. It is updated in each round and in each library release.

**"Compatible" criterion:** with that tool and version, (1) every legitimate operation gives `INTACT` or
`INTACT_WITH_ALLOWED_CHANGES`, and (2) every disallowed change is detected.

### Verified tools (round 1, 2026-11-20)

| Tool | Version | Operating system | Status | Verified operations |
|---|---|---|---|---|
| Adobe Acrobat Reader | Continuous Release **2026.003.22110**, 64-bit | **Windows** | ✅ Compatible | Save as; signing with text, vector-graphic and raster-image appearances; second signature; signing pre-created fields under DocMDP P=2. Detects: comment, form fill, print to PDF (Microsoft Print to PDF). |
| Ghostscript (`pdfwrite`) | 10.08.0 | Linux | ❌ Not compatible (expected) | Rewrites the whole document and removes the seal: always `NO_SEAL`. |

### Environment where it was verified

- Sealing, preparation and verification: **Linux amd64**, OpenJDK **27**, Apache PDFBox **3.0.5**,
  Bouncy Castle **1.80**.
- **Across JDKs (verified):** seals made on OpenJDK 27 (JDK Ed25519) and fingerprints recomputed on
  **Eclipse Temurin 1.8.0_514** (Bouncy Castle Ed25519): **104 of 104** PDFs with an identical
  fingerprint, the 61 valid seals verified on both and identical tiles.
- **Library on five JDKs (verified):** Eclipse Temurin **8.0.512, 11.0.33, 17.0.21, 21.0.13 and 25.0.5**
  (installed with mise), Linux x64: full build and **48/48 tests** on each. 5×5 cross test (seal on one
  JDK, verify on another, with text and a JPEG image): all **50 verifications identical** (sealed →
  `INTACT`; modified → `MODIFIED` with `TEXT_CHANGED` and the same region).
- Sealing and verification were done **on the same operating system**. Sealing on one system and
  verifying on another (e.g. Linux → Windows) is **not verified**.
- Deterministic tile rendering is **only confirmed on the same operating system**, without
  antialiasing. It does not affect the verdict (tiles only locate changes), but it does affect the
  detailed report.

### Known restrictions

| # | Restriction |
|---|---|
| L1 | **Acrobat on Windows only.** Acrobat for macOS was not tested. |
| L2 | **Acrobat Reader only.** Not verified: Acrobat Pro (certify, optimize, edit), Foxit, browser viewers (Chrome, Firefox), macOS Preview, Okular, mobile apps or national signing tools. |
| L3 | **Later Acrobat versions** may change how they save. Compatibility is declared per version. |
| L4 | **Synthetic corpus.** Only PDFs generated with PDFBox (text, images, form, multi-page, scanned, layers). Not verified: PDFs exported from Word or LibreOffice, printed from a browser, PDF/A, third-party PDFs with object streams, or PDFs signed before sealing. |
| L5 | **Self-signed certificate.** Certified mode was only tested with a test certificate (Acrobat shows it as "issuer unknown"). Certificates from a trusted CA and LTV were not tested. |
| L6 | **Visible signatures only.** Invisible signatures were not tested (Reader does not offer them). |
| L7 | **With DocMDP, Acrobat only signs pre-created fields.** A certified document without declared signature fields cannot be signed in Acrobat (FR-5, FR-12). |
| L8 | **Aggressive rewrites.** Tools that rebuild the PDF (Ghostscript, print to PDF) remove the seal: the result is `NO_SEAL` or `MODIFIED` by design, not a bug. |
| L9 | **PDF only.** Images, Office and text were not tested (F2–F4). |
| L10 | **Verified JDKs: Temurin 8, 11, 17, 21 and 25 and OpenJDK 27, Linux x64 only.** Other distributions (Oracle, Zulu, Corretto), other architectures (ARM) and Windows/macOS as the library's runtime were not tested. |

## 11. Optional integrations

- **EU DSS:** legal validation of the Adobe signatures found (certificates, revocation, timestamps).
- **RFC 3161 timestamp:** prove *when* the document was sealed.
- **Server-side registry:** store the fingerprint by `docId` as a backup, in case the embedded seal is lost.

## 12. Risks and compatibility spike (before detailed design)

> The spike lives in the private repository **`untouchable-spike`** (a sibling folder of this one): plan
> in `SPIKE.md`, round 1 results and proposed changes (P1–P10) in `spike/REPORT.md`.

We need to check how the most common tools of each format handle sealed documents. For PDF: Adobe
Acrobat, Foxit, browsers, macOS Preview and national signing tools.

1. Open and re-save a sealed PDF (XMP + catalog only): does it save incrementally or rewrite? Does the seal survive?
2. Sign it: does anything other than the signature change? And if it already has a **DocMDP
   certification**, does that force an incremental save?
3. An image signature: where does the image end up (widget, page content, flattened)?
4. "Save as" / "Optimize PDF" / print to PDF: what changes in the canonical content?
5. Is PDFBox rendering stable across versions and operating systems? Is the hash deterministic?
6. The equivalent for images (editors, viewers, messaging apps that recompress) and Office (Word,
   LibreOffice, Google Docs on export), in their phases.

**Round 1 result** (Acrobat Reader + Ghostscript):
- Without certification, Acrobat **rewrites** the file when signing, but the seal and the content
  fingerprint survived in 26 of 26 legitimate operations.
- With DocMDP P=2 and pre-created signature fields, Acrobat **always saves incrementally**.
  Without pre-created fields, it does not allow signing.
- The signature (text, vector graphic or image) always lives inside the widget.
- Disallowed changes were detected in 6 of 6.

This led to the verification modes in §9.1, FR-12 and the 2-decimal normalization.
Pending: other tools, determinism across operating systems and the external corpus.

## 13. Decisions

| # | Topic | Decision |
|---|---|---|
| D1 | Changes allowed after sealing | **Decided by the issuer** through the policy stored in the seal. Built-in: `strict` (default), `signatures-only` and `signatures-and-forms`, plus an SPI for custom policies (FR-8a). |
| D2 | Added signatures | **Reported only** (FR-10); the application decides whether they are enough. |
| D3 | Flattened / printed-to-PDF documents | **`MODIFIED`.** The visual comparison only locates changes, it never accepts (FR-11). |
| D4 | X.509 certificate / DocMDP | **Recommended** for policies that allow signatures, but not mandatory. With a certificate: DocMDP (level derived from the policy) + declared signature fields + seal. Without a certificate: Ed25519 seal only (FR-5). |
| D5 | Seal serialization | **Canonical JSON (RFC 8785)** (§9.2). |
| D6 | Approach | **General purpose.** The cases in §1.1 are examples and do not drive the design. |
| D7 | License and coordinates | **Apache 2.0**, `groupId` `io.qzz.toshiro` (NFR-6, NFR-7). |
| D8 | PDF modes | **Certified** (fast verification by revisions) and **non-certified** (by content); content is always the safety net (§9.1). |
| D9 | Signature fields | The issuer can **declare signature fields**; they are mandatory with DocMDP (FR-12). |
| D10 | Canonical content | Numbers to **2 decimals**; `ops` is kept; tiles rendered without antialiasing (§9.1, NFR-3). |
| D11 | Overlap threshold | `SIGNATURE_OVERLAPS_CONTENT` above **5%** of the widget area, configurable (§7.4). |
| D12 | Compatibility | Declared **per tool, version and operating system** verified in the spike; anything not listed is not guaranteed (§10.1). |
| D13 | Java version | **Java 8 (1.8) and later**, compiled with `--release 8`. Ed25519 via JCA (Java 15+) or Bouncy Castle (Java 8–14). Same fingerprint on any JDK, verified on Java 8, 11, 17, 21 and 25 (NFR-1, NFR-1a, NFR-3). |
| D14 | Certified-mode fast path | By incremental revisions, only if the certification was made with the issuer's certificate (hash in the seal); for any unrecognized object, content verification. Can be disabled with `PdfVerifyOptions.fastPath(false)` (§9.1). |

### Pending

- Spike round 2: Acrobat Pro, Foxit, Chrome, Okular, Preview, `qpdf`/`mutool`, determinism across
  operating systems, sealing and verifying on different systems, and the external corpus (spike report §6, §10.1).
- **Cross-JDK test:** done with the library on Temurin 8, 11, 17, 21 and 25 (§10.1). Still missing on
  other distributions, on ARM and with Windows/macOS as the runtime.

## 14. Roadmap

| Phase | Deliverable |
|---|---|
| F0 | Compatibility spike (§12). Round 1 done; round 2 pending |
| F1 | ✅ `core` + `pdf`: seal/verify, statuses, per-page report, `strict`, `signatures-only` and `signatures-and-forms` policies, non-certified mode. 0.1.0-SNAPSHOT |
| F1.0b | ✅ DocMDP, declared signature fields and fast path by revisions, bound to the issuer's certificate. Between 12 and 33 times faster than content verification (10–50 pages) |
| F1.1 | ✅ Change location with tiles and the signature overlap warning |
| F1.2 | ✅ Tests on Java 8, 11, 17, 21 and 25 (test suite + cross test). ⏳ Acrobat round with PDFs sealed by the library |
| F2 | Images |
| F3 | Office (OOXML) |
| F4 | Text |
| F5 | Generic and other formats |
