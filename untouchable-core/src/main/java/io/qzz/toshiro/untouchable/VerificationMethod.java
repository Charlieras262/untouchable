package io.qzz.toshiro.untouchable;

/** How the document was compared with its seal (SPEC §9.1, verification modes). */
public enum VerificationMethod {
    /** The content fingerprint was recomputed and compared with the sealed one. Always works. */
    CONTENT,
    /**
     * Certified-mode fast path: the sealed revision is intact (proven by the issuer's certification
     * signature) and later revisions only add allowed changes.
     */
    CERTIFIED_REVISIONS
}
