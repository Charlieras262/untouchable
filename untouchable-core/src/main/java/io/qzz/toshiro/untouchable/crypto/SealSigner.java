package io.qzz.toshiro.untouchable.crypto;

/** Signs the seal content. Implementations: {@link Ed25519#signer}, {@link Hmac#signer}. */
public interface SealSigner {
    /** Algorithm stored in the seal ("Ed25519", "HS256"). */
    String algorithm();

    /** Identifier of the key stored in the seal ({@code kid}), to allow key rotation. */
    String keyId();

    byte[] sign(byte[] data);
}
