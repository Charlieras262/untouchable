package io.qzz.toshiro.untouchable.pdf;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.qzz.toshiro.untouchable.Region;

/**
 * PDF-specific sealing options. Passed with {@code SealOptions.builder().formatOption(...)}.
 *
 * <pre>{@code
 * PdfSealOptions pdf = PdfSealOptions.builder()
 *     .signatureField("Signature1", 1, new Region(50, 40, 200, 60))
 *     .certifyWith(privateKey, certificate)   // optional: certified mode (SPEC FR-5)
 *     .build();
 * }</pre>
 */
public final class PdfSealOptions {
    private final List<SignatureFieldSpec> signatureFields;
    private final PrivateKey certificationKey;
    private final List<X509Certificate> certificationChain;
    private final boolean storeTiles;

    private PdfSealOptions(Builder b) {
        this.signatureFields = Collections.unmodifiableList(new ArrayList<SignatureFieldSpec>(b.signatureFields));
        this.certificationKey = b.certificationKey;
        this.certificationChain = Collections.unmodifiableList(new ArrayList<X509Certificate>(b.certificationChain));
        this.storeTiles = b.storeTiles;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<SignatureFieldSpec> signatureFields() { return signatureFields; }
    public boolean certify() { return certificationKey != null; }
    public PrivateKey certificationKey() { return certificationKey; }
    public List<X509Certificate> certificationChain() { return certificationChain; }
    /** Store in the seal the tiles used to locate changes within the page. */
    public boolean storeTiles() { return storeTiles; }

    public static final class Builder {
        private final List<SignatureFieldSpec> signatureFields = new ArrayList<SignatureFieldSpec>();
        private PrivateKey certificationKey;
        private final List<X509Certificate> certificationChain = new ArrayList<X509Certificate>();
        private boolean storeTiles = true;

        /** Declares an empty signature field (1-based page, rectangle in PDF points). */
        public Builder signatureField(String name, int page, Region rect) {
            signatureFields.add(new SignatureFieldSpec(name, page, rect));
            return this;
        }

        /**
         * Applies a DocMDP certification signature with this certificate (RSA or EC). The first certificate in
         * the chain is the signer's. With policies that allow signatures, signature fields must be declared.
         */
        public Builder certifyWith(PrivateKey key, X509Certificate... chain) {
            if (key == null || chain == null || chain.length == 0) {
                throw new IllegalArgumentException("A private key and at least one certificate are required");
            }
            this.certificationKey = key;
            this.certificationChain.clear();
            this.certificationChain.addAll(Arrays.asList(chain));
            return this;
        }

        public Builder storeTiles(boolean store) { this.storeTiles = store; return this; }

        public PdfSealOptions build() {
            return new PdfSealOptions(this);
        }
    }
}
