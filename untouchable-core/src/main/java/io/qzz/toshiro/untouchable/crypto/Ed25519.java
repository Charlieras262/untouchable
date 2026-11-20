package io.qzz.toshiro.untouchable.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import io.qzz.toshiro.untouchable.UntouchableException;

/**
 * Ed25519 (RFC 8032), the default seal algorithm (SPEC §9.3).
 * <p>
 * On Java 15+ it uses the JDK provider. On Java 8–14 it uses Bouncy Castle ({@code bcprov-jdk18on}), which
 * must be on the classpath; it is loaded through reflection so that the core does not depend on it.
 * Signatures are deterministic: the JDK and Bouncy Castle produce and accept the same ones.
 */
public final class Ed25519 {
    public static final String ALGORITHM = "Ed25519";
    private static final String BC = "org.bouncycastle.jce.provider.BouncyCastleProvider";

    private static volatile Provider provider;
    private static volatile Boolean jdkSupport;

    private Ed25519() {}

    /** "JDK" or "BouncyCastle", depending on which implementation is used in this environment. */
    public static String implementation() {
        return jdkSupported() ? "JDK" : "BouncyCastle";
    }

    public static SealSigner signer(PrivateKey key, final String keyId) {
        if (keyId == null || keyId.isEmpty()) throw new IllegalArgumentException("keyId");
        final PrivateKey k = normalize(key);
        return new SealSigner() {
            @Override public String algorithm() { return ALGORITHM; }
            @Override public String keyId() { return keyId; }
            @Override public byte[] sign(byte[] data) {
                try {
                    Signature s = signature();
                    s.initSign(k);
                    s.update(data);
                    return s.sign();
                } catch (GeneralSecurityException e) {
                    throw new UntouchableException("Could not sign with Ed25519", e);
                }
            }
        };
    }

    public static SealVerifier verifier(PublicKey key) {
        final PublicKey k = normalize(key);
        return new SealVerifier() {
            @Override public String algorithm() { return ALGORITHM; }
            @Override public boolean verify(byte[] data, byte[] signature) {
                try {
                    Signature s = signature();
                    s.initVerify(k);
                    s.update(data);
                    return s.verify(signature);
                } catch (GeneralSecurityException e) {
                    return false;
                } catch (RuntimeException e) {
                    return false;
                }
            }
        };
    }

    public static KeyPair generateKeyPair() {
        try {
            return jdkSupported() ? KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair()
                    : KeyPairGenerator.getInstance(ALGORITHM, bouncyCastle()).generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new UntouchableException("Could not generate an Ed25519 key pair", e);
        }
    }

    /** Public key from its X.509 encoding (SubjectPublicKeyInfo, DER). */
    public static PublicKey publicKey(byte[] x509) {
        try {
            return keyFactory().generatePublic(new X509EncodedKeySpec(x509));
        } catch (GeneralSecurityException e) {
            throw new UntouchableException("Invalid Ed25519 public key", e);
        }
    }

    /** Private key from its PKCS#8 encoding (DER). */
    public static PrivateKey privateKey(byte[] pkcs8) {
        try {
            return keyFactory().generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (GeneralSecurityException e) {
            throw new UntouchableException("Invalid Ed25519 private key", e);
        }
    }

    // ---------------------------------------------------------------- internals

    /** Re-creates the key with the chosen provider, so keys and signatures from different providers are not mixed. */
    private static PrivateKey normalize(PrivateKey key) {
        if (key == null) throw new IllegalArgumentException("key");
        return privateKey(key.getEncoded());
    }

    private static PublicKey normalize(PublicKey key) {
        if (key == null) throw new IllegalArgumentException("key");
        return publicKey(key.getEncoded());
    }

    private static boolean jdkSupported() {
        Boolean s = jdkSupport;
        if (s == null) {
            try {
                Signature.getInstance(ALGORITHM);
                KeyFactory.getInstance(ALGORITHM);
                s = Boolean.TRUE;
            } catch (NoSuchAlgorithmException e) {
                s = Boolean.FALSE;
            }
            jdkSupport = s;
        }
        return s;
    }

    private static Signature signature() throws GeneralSecurityException {
        return jdkSupported() ? Signature.getInstance(ALGORITHM) : Signature.getInstance(ALGORITHM, bouncyCastle());
    }

    private static KeyFactory keyFactory() throws GeneralSecurityException {
        return jdkSupported() ? KeyFactory.getInstance(ALGORITHM) : KeyFactory.getInstance(ALGORITHM, bouncyCastle());
    }

    private static Provider bouncyCastle() {
        Provider p = provider;
        if (p == null) {
            try {
                p = (Provider) Class.forName(BC).getConstructor().newInstance();
            } catch (ClassNotFoundException e) {
                throw new UntouchableException("Ed25519 requires Java 15+ or Bouncy Castle (bcprov-jdk18on) on the "
                        + "classpath. This environment is Java " + System.getProperty("java.version") + ".", e);
            } catch (ReflectiveOperationException e) {
                throw new UntouchableException("Could not initialize Bouncy Castle", e);
            }
            provider = p;
        }
        return p;
    }
}
