package io.qzz.toshiro.untouchable;

/**
 * An electronic signature found in the verified document (SPEC FR-10). Informational only: the library
 * does not check the trust chain or legal validity.
 */
public final class SignatureInfo {

    /** Result of the cryptographic (not legal) verification of the signature. */
    public enum Integrity {
        /** The signature matches the bytes it claims to cover. */
        VALID,
        /** The signature does not match: the covered content changed or the signature is damaged. */
        INVALID,
        /** It could not be checked (unsupported signature format). */
        UNKNOWN
    }

    private final String fieldName, signerName, signDate, subFilter;
    private final Integrity integrity;
    private final boolean coversWholeDocument, certification, addedAfterSeal;
    private final Integer page;
    private final Region region;

    public SignatureInfo(String fieldName, String signerName, String signDate, String subFilter, Integrity integrity,
                         boolean coversWholeDocument, boolean certification, boolean addedAfterSeal,
                         Integer page, Region region) {
        this.fieldName = fieldName; this.signerName = signerName; this.signDate = signDate;
        this.subFilter = subFilter; this.integrity = integrity; this.coversWholeDocument = coversWholeDocument;
        this.certification = certification; this.addedAfterSeal = addedAfterSeal; this.page = page;
        this.region = region;
    }

    public String fieldName() { return fieldName; }
    /** Name declared by the signer (may be null). */
    public String signerName() { return signerName; }
    /** Date declared in the signature, ISO-8601 (may be null). It is not a trusted timestamp. */
    public String signDate() { return signDate; }
    public String subFilter() { return subFilter; }
    public Integrity integrity() { return integrity; }
    /** true if it is the latest revision: the signature covers the whole file. */
    public boolean coversWholeDocument() { return coversWholeDocument; }
    /** true if it is the certification signature (DocMDP). */
    public boolean isCertification() { return certification; }
    /** true if the signature was added after sealing. */
    public boolean isAddedAfterSeal() { return addedAfterSeal; }
    /** Page of the visible widget (1-based), or null if invisible. */
    public Integer page() { return page; }
    public Region region() { return region; }

    @Override
    public String toString() {
        return fieldName + " (" + integrity + (certification ? ", certification" : "")
                + (page != null ? ", p." + page + " " + region : ", invisible") + ")";
    }
}
