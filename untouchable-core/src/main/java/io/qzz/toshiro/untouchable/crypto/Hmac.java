package io.qzz.toshiro.untouchable.crypto;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.qzz.toshiro.untouchable.UntouchableException;

/**
 * HMAC-SHA256 ("HS256"): symmetric option for when only the system itself verifies (SPEC §9.3).
 * Whoever can verify can also seal: do not use it if the verifier is a third party.
 */
public final class Hmac {
    public static final String ALGORITHM = "HS256";
    private static final int MIN_KEY_BYTES = 32;

    private Hmac() {}

    public static SealSigner signer(byte[] secret, final String keyId) {
        if (keyId == null || keyId.isEmpty()) throw new IllegalArgumentException("keyId");
        final byte[] key = check(secret);
        return new SealSigner() {
            @Override public String algorithm() { return ALGORITHM; }
            @Override public String keyId() { return keyId; }
            @Override public byte[] sign(byte[] data) { return mac(key, data); }
        };
    }

    public static SealVerifier verifier(byte[] secret) {
        final byte[] key = check(secret);
        return new SealVerifier() {
            @Override public String algorithm() { return ALGORITHM; }
            @Override public boolean verify(byte[] data, byte[] signature) {
                return MessageDigest.isEqual(mac(key, data), signature);
            }
        };
    }

    private static byte[] check(byte[] secret) {
        if (secret == null || secret.length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException("The HMAC key must be at least " + MIN_KEY_BYTES + " bytes long");
        }
        return Arrays.copyOf(secret, secret.length);
    }

    private static byte[] mac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new UntouchableException("HMAC-SHA256 not available", e);
        }
    }
}
