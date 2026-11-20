---
sidebar_position: 4
title: Keys
---

# Keys

The library never stores keys. You give it a `SealSigner` to seal and a `KeyResolver` to verify; where the keys
come from (a keystore, a KMS, an HSM, environment variables) is up to you.

## Ed25519 (default)

Asymmetric: only the holder of the private key can seal, and anyone with the public key can verify.

```java
KeyPair pair = Ed25519.generateKeyPair();

// Store them…
byte[] pkcs8 = pair.getPrivate().getEncoded();
byte[] x509 = pair.getPublic().getEncoded();

// …and load them back.
PrivateKey privateKey = Ed25519.privateKey(pkcs8);
PublicKey publicKey = Ed25519.publicKey(x509);

SealSigner signer = Ed25519.signer(privateKey, "key-2026");
SealVerifier verifier = Ed25519.verifier(publicKey);
```

On Java 15 and later the JDK implements Ed25519. On Java 8 – 14 the library uses Bouncy Castle
(`bcprov-jdk18on`), loaded through reflection. `Ed25519.implementation()` returns `"JDK"` or
`"BouncyCastle"`. Signatures are deterministic, so a seal made on Java 21 verifies on Java 8 and vice versa.

## HMAC-SHA256

Symmetric (`HS256`), for when **only your own system** verifies. Whoever can verify can also seal, so never use
it when the verifier is a third party. The secret must be at least 32 bytes long.

```java
byte[] secret = loadSecret(); // ≥ 32 bytes
Untouchable untouchable = Untouchable.builder()
    .signer(Hmac.signer(secret, "hmac-2026"))
    .keyResolver(KeyResolver.of("hmac-2026", Hmac.verifier(secret)))
    .build();
```

## Key rotation

Every seal stores the **key id** (`kid`) of the key that signed it. To rotate, start sealing with a new key id
and keep the old public keys in the resolver:

```java
Map<String, SealVerifier> verifiers = new HashMap<>();
verifiers.put("key-2025", Ed25519.verifier(publicKey2025));
verifiers.put("key-2026", Ed25519.verifier(publicKey2026));

Untouchable untouchable = Untouchable.builder()
    .signer(Ed25519.signer(privateKey2026, "key-2026"))
    .keyResolver(KeyResolver.of(verifiers))
    .build();
```

Documents sealed with an id the resolver does not know give `INVALID_SEAL` with the reason
`Unknown key: <kid>`. `KeyResolver` is an interface, so it can also look keys up in a database or a KMS on demand.
