---
slug: introducing-untouchable
title: Introducing Untouchable 0.1.0
authors: [carlos]
tags: [release]
---

Untouchable is a Java library that seals documents and detects whether their content changed, even after
another tool rewrote the file. The first preview supports PDF and runs on Java 8 and later.

<!-- truncate -->

## Why

A document leaves your system, gets signed with Adobe Acrobat and comes back. Was it the same document? A hash
of the file cannot tell: Acrobat rewrites the whole PDF when it signs. Untouchable fingerprints what the
document **says and shows** and stores that fingerprint, signed with your key, inside the file.

## What 0.1.0 does

- Seals PDFs and verifies them with a yes/no verdict and a detailed report: what changed, on which page and
  roughly where.
- Lets the issuer decide what may change: nothing, signatures, or signatures and form fields.
- Works with Acrobat signatures, in plain or certified mode. In certified mode Acrobat signs incrementally and
  verification takes milliseconds.
- Runs on Java 8, 11, 17, 21 and 25, and a seal made on one JDK verifies on every other.

## Try it

It is not on Maven Central yet. Follow the [installation guide](/docs/getting-started/installation) to build it
locally, then seal [your first PDF](/docs/getting-started/first-seal).
