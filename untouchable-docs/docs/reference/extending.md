---
sidebar_position: 3
title: Extending
---

# Extending

## Custom policies

See [Policies](../guides/policies.md#custom-policies).

## Format handlers

Every document format is a `FormatHandler`. The PDF support (`PdfFormatHandler`) is one; others (images, Office,
text) are planned. A handler knows how to fingerprint a format, store and read the seal, and compare a document
with its sealed fingerprint.

```java
public interface FormatHandler {
    String format();                         // "pdf", stored in the seal
    String canonVersion();                   // "pdf-canon/1": version of the fingerprint algorithm
    boolean supports(byte[] document);       // usually by the first bytes

    PreparedDocument prepare(byte[] document, SealOptions options) throws IOException;
    byte[] embed(PreparedDocument prepared, EmbeddedSeal seal, SealOptions options) throws IOException;
    EmbeddedSeal extract(byte[] document) throws IOException;
    Comparison compare(byte[] document, SealPayload payload, VerifyOptions options) throws IOException;
}
```

| Step | What the handler does |
| --- | --- |
| `prepare` | Applies format options that change the file and computes the fingerprint (`Map` of strings, numbers, lists and maps). |
| `embed` | Writes the signed seal into the file and returns the final bytes. |
| `extract` | Reads the seal back, or returns `null`. |
| `compare` | Returns findings **without** severity (the policy assigns it) and the signatures found. |

The library takes care of everything else: building and signing the payload, checking the seal signature and
the key, choosing the policy and computing the status.

### Registering a handler

Either list it in `META-INF/services/io.qzz.toshiro.untouchable.spi.FormatHandler` so `ServiceLoader` finds it,
or register it in the builder (it takes precedence over discovered handlers):

```java
Untouchable untouchable = Untouchable.builder()
    .handler(new MyFormatHandler())
    // signer, keyResolver…
    .build();
```

### Rules for a handler

- Be **deterministic**: the same content must give the same fingerprint on every JDK and operating system.
- Change `canonVersion()` whenever the fingerprint algorithm changes. Seals made with another version give
  `UNSUPPORTED` instead of a wrong verdict.
- Be **thread-safe**: one instance serves every call.
- Never execute content from the document (scripts, macros).
