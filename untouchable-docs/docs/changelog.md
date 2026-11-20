---
sidebar_position: 5
title: Changelog
---

# Changelog

## 0.1.0-SNAPSHOT

_Unreleased_ · First preview. PDF only, not yet published to Maven Central.

### Features

- `untouchable-core`: `Untouchable` facade (`seal` / `verify`), statuses, findings with severity, policies
  (`strict`, `signatures-only`, `signatures-and-forms`) and custom `ChangePolicy`, Ed25519 (JDK or Bouncy Castle)
  and HMAC-SHA256 signers, `KeyResolver` with key ids, `FormatHandler` SPI.
- `untouchable-pdf`: content fingerprint `pdf-canon/1` (geometry, text, decoded images, operators, annotations,
  form values), seal stored in XMP and in the catalog, change location with tiles.
- Declared signature fields and DocMDP certification, with a fast verification path over incremental revisions
  bound to the issuer's certificate.
- Warnings for signatures that cover page content, signatures outside the declared fields and broken
  certifications.

### Compatibility

- Java 8, 11, 17, 21 and 25; a seal made on one JDK verifies the same way on the others.
- Adobe Acrobat Reader 2026.003 (Windows): the seal survives saving and signing, certified and non-certified.
