---
sidebar_position: 2
title: Your first seal
---

# Your first seal

This page seals a PDF, verifies it, and then shows what happens when someone changes it.

## 1. Create the keys

The seal is signed with an **Ed25519** key pair. The private key seals; the public key verifies. Generate a
pair once and store it somewhere safe (a keystore, a KMS, a secrets manager): the library never stores keys.

```java
KeyPair keys = Ed25519.generateKeyPair();

byte[] privateKeyPkcs8 = keys.getPrivate().getEncoded(); // keep it secret
byte[] publicKeyX509 = keys.getPublic().getEncoded();    // can be shared with verifiers
```

Later, load them back with `Ed25519.privateKey(bytes)` and `Ed25519.publicKey(bytes)`. See [Keys](../guides/keys.md).

## 2. Build an `Untouchable` instance

```java
Untouchable untouchable = Untouchable.builder()
    .signer(Ed25519.signer(keys.getPrivate(), "key-2026"))
    .keyResolver(KeyResolver.of("key-2026", Ed25519.verifier(keys.getPublic())))
    .build();
```

- `signer` is needed to **seal**; `keyResolver` is needed to **verify**. A service that only verifies needs only
  the public key.
- `"key-2026"` is the key id (`kid`). It is stored in the seal so that you can rotate keys later.
- The PDF handler is discovered automatically when `untouchable-pdf` is on the classpath.
- The instance is immutable and thread-safe: create it once and reuse it.

## 3. Seal

```java
byte[] pdf = Files.readAllBytes(Paths.get("invoice.pdf"));

byte[] sealed = untouchable.seal(pdf, SealOptions.builder()
    .docId("invoice-123")              // your own identifier; a UUID if omitted
    .policy(Policies.SIGNATURES_ONLY)  // signatures may be added later
    .claim("customer", "ACME")         // optional free-form metadata
    .build());

Files.write(Paths.get("invoice-sealed.pdf"), sealed);
```

The sealed PDF looks exactly the same: the seal lives in the file's metadata, not on the page.

## 4. Verify

```java
VerificationResult result = untouchable.verify(sealed, VerifyOptions.expectDocId("invoice-123"));

result.isTrusted();        // true
result.status();           // INTACT
result.seal().docId();     // "invoice-123"
result.seal().issuedAt();  // "2026-11-20T10:15:30Z"
```

`expectDocId` makes sure you got **the document you expect** and not another validly sealed one.

## 5. Change something

If someone adds a sticky note, edits the text or removes a page, the verdict changes:

```java
VerificationResult result = untouchable.verify(tampered);

result.isTrusted();  // false
result.status();     // MODIFIED
for (Finding f : result.findings()) {
    System.out.println(f);
    // VIOLATION TEXT_CHANGED p.2 [x=297.0, y=106.9, w=74.0, h=105.0]: The page text changed
}
```

If the document is signed instead (with Acrobat or any PDF signing tool), the status is
`INTACT_WITH_ALLOWED_CHANGES` and the signature is listed in `result.signatures()`.

## Next

- Learn what each status and finding means in [Verification results](../guides/verification-results.md).
- If your users sign with Acrobat, read [Signing with Acrobat](../guides/pdf-signatures.md).
