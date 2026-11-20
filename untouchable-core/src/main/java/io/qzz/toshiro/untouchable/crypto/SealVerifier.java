package io.qzz.toshiro.untouchable.crypto;

/** Verifies the seal signature. Implementations: {@link Ed25519#verifier}, {@link Hmac#verifier}. */
public interface SealVerifier {
    String algorithm();

    boolean verify(byte[] data, byte[] signature);
}
