---
sidebar_position: 1
title: Development and testing
---

# Development and testing

The repository contains:

| Path | Purpose |
| --- | --- |
| `untouchable-core` | Core module: API, seal, cryptography, policies. |
| `untouchable-pdf` | PDF format handler. |
| `untouchable-docs` | This documentation (Docusaurus). |
| `SPEC.md` | Design specification and decisions. |

## Requirements

Any JDK from **Java 8** on. The Maven wrapper downloads Maven 3.9 by itself (Maven 4 needs Java 17, so it is not
used).

```bash
./mvnw verify
```

The code must stay **Java 8 compatible**: on JDK 9+ the build compiles with `--release 8`, so a Java 9+ API
fails the build. Do not use `var`, records, arrow `switch`, text blocks, `List.of` and similar.

## Test on several JDKs

The library is tested on Java 8, 11, 17, 21 and 25. With [mise](https://mise.jdx.dev):

```bash
mise install java@temurin-8 java@temurin-11 java@temurin-17 java@temurin-21 java@temurin-25
```

```bash
for v in 8 11 17 21 25; do mise exec java@temurin-$v -- ./mvnw -B clean verify; done
```

## Tests

| Suite | Covers |
| --- | --- |
| `UntouchableTest` | Statuses and policies, with a test text format. |
| `CryptoTest`, `JsonTest` | Ed25519, HMAC and the canonical JSON. |
| `PdfFormatHandlerTest` | Sealing PDFs and detecting changes: text, comments, pages, forms, signatures, forged and removed seals. |
| `CertifiedRevisionsTest` | The certified fast path and its fallbacks, including attacks. |

Every bug fix starts with a test that reproduces it.

## Compatibility rounds

Behavior with real tools (Adobe Acrobat and others) is checked in a separate, private repository
(`untouchable-spike`), which keeps the test documents and the results. Update
[Compatibility](../reference/compatibility.md) after each round.

## Documentation

```bash
cd untouchable-docs
npm ci
npm start      # live preview
npm run build  # production build, fails on broken links
```

The docs need Node 20 or newer.

Every push to `main` that changes `untouchable-docs/` publishes the site to GitHub Pages through the
**Deploy docs** workflow. It can also be run by hand from the Actions tab.
