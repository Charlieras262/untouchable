---
sidebar_position: 1
title: Compatibility
---

# Compatibility

Compatibility is only guaranteed for what is listed here. Everything else is considered unverified.

## Java

| Java | Status | Ed25519 provider |
| --- | --- | --- |
| 8 | ✅ Build and full test suite | Bouncy Castle |
| 11 | ✅ Build and full test suite | Bouncy Castle |
| 17 | ✅ Build and full test suite | JDK |
| 21 | ✅ Build and full test suite | JDK |
| 25 | ✅ Build and full test suite | JDK |

Tested with Eclipse Temurin on Linux x64. **Across JDKs:** a document sealed on any of these versions verifies
the same way on all the others — same verdict, same findings, same located region.

Not tested yet: other distributions (Oracle, Zulu, Corretto), ARM, and Windows or macOS as the runtime of the
library.

## PDF tools

A tool is **compatible** when every legitimate operation gives `INTACT` or `INTACT_WITH_ALLOWED_CHANGES` and
every disallowed change is detected.

| Tool | Version | OS | Status | What was verified |
| --- | --- | --- | --- | --- |
| Adobe Acrobat Reader | 2026.003.22110 (64-bit) | Windows | ✅ Compatible | Save as; sign with text, vector or image appearance; second signature; sign declared fields of a certified PDF (incremental). Comments detected (`MODIFIED`) or blocked by Acrobat in certified PDFs. |
| Ghostscript (`pdfwrite`) | 10.08.0 | Linux | ❌ Not compatible (expected) | Rebuilds the whole PDF and drops the seal: always `NO_SEAL`. |

## Known restrictions

| Restriction | Detail |
| --- | --- |
| Acrobat Reader on Windows only | Acrobat Pro, Acrobat on macOS, Foxit, browser viewers, macOS Preview and mobile apps are not verified yet. |
| Per-version compatibility | Later Acrobat versions may change how they save. |
| Synthetic test documents | PDFs exported from Word or LibreOffice, PDF/A and third-party PDFs are not verified yet. |
| Self-signed certificates | Certified mode was tested with a test certificate only; trusted CAs and LTV are not verified. |
| Visible signatures | Invisible signatures were not tested in Acrobat (Reader does not offer them). |
| Aggressive rewrites | Tools that rebuild the PDF (Ghostscript, print to PDF) remove the seal: `NO_SEAL` or `MODIFIED` by design. |
| PDF only | Images, Office and text formats are planned. |
| Encrypted PDFs | Not supported: `UNSUPPORTED`. |

## Dependencies

| Dependency | Version | Module |
| --- | --- | --- |
| Apache PDFBox | 3.0.5 | `untouchable-pdf` |
| Bouncy Castle (`bcprov`, `bcpkix`) | 1.80 (`*-jdk18on`) | `untouchable-pdf`; optional in `untouchable-core` |
