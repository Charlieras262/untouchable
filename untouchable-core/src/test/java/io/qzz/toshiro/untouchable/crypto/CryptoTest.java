package io.qzz.toshiro.untouchable.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class CryptoTest {
    private static final byte[] DATA = "seal data".getBytes(StandardCharsets.UTF_8);

    @Test
    void ed25519SignsAndVerifies() {
        KeyPair kp = Ed25519.generateKeyPair();
        SealSigner signer = Ed25519.signer(kp.getPrivate(), "k1");
        byte[] sig = signer.sign(DATA);
        assertEquals(64, sig.length);
        assertEquals("Ed25519", signer.algorithm());
        assertTrue(Ed25519.verifier(kp.getPublic()).verify(DATA, sig));

        byte[] other = Arrays.copyOf(DATA, DATA.length);
        other[0] ^= 1;
        assertFalse(Ed25519.verifier(kp.getPublic()).verify(other, sig));
        assertFalse(Ed25519.verifier(Ed25519.generateKeyPair().getPublic()).verify(DATA, sig));
    }

    @Test
    void ed25519KeysRoundTripThroughEncodings() {
        KeyPair kp = Ed25519.generateKeyPair();
        SealSigner signer = Ed25519.signer(Ed25519.privateKey(kp.getPrivate().getEncoded()), "k1");
        SealVerifier verifier = Ed25519.verifier(Ed25519.publicKey(kp.getPublic().getEncoded()));
        assertTrue(verifier.verify(DATA, signer.sign(DATA)));
    }

    @Test
    void ed25519SignaturesAreDeterministic() {
        KeyPair kp = Ed25519.generateKeyPair();
        SealSigner signer = Ed25519.signer(kp.getPrivate(), "k1");
        assertTrue(Arrays.equals(signer.sign(DATA), signer.sign(DATA)));
    }

    @Test
    void reportsImplementationForThisJdk() {
        String spec = System.getProperty("java.specification.version");
        boolean modern = !spec.startsWith("1.") && Integer.parseInt(spec) >= 15;
        assertEquals(modern ? "JDK" : "BouncyCastle", Ed25519.implementation());
    }

    @Test
    void hmacSignsAndVerifies() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        SealSigner signer = Hmac.signer(key, "h1");
        assertTrue(Hmac.verifier(key).verify(DATA, signer.sign(DATA)));
        byte[] other = key.clone();
        other[0] = 8;
        assertFalse(Hmac.verifier(other).verify(DATA, signer.sign(DATA)));
        assertThrows(IllegalArgumentException.class, () -> Hmac.signer(new byte[16], "h1"));
    }
}
