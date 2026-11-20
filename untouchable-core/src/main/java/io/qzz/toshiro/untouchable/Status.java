package io.qzz.toshiro.untouchable;

/** Verification verdict (SPEC §7.3). */
public enum Status {
    /** Content identical to the sealed one. */
    INTACT(true),
    /** Identical content; only changes allowed by the policy (e.g. signatures). */
    INTACT_WITH_ALLOWED_CHANGES(true),
    /** The content differs from the sealed one, or there are changes the policy does not allow. */
    MODIFIED(false),
    /** The document has no seal. */
    NO_SEAL(false),
    /** The seal exists but its signature is invalid, the key is unknown or the seal is damaged. */
    INVALID_SEAL(false),
    /** Unsupported format, fingerprint version or policy, or unreadable file. */
    UNSUPPORTED(false);

    private final boolean trusted;

    Status(boolean trusted) {
        this.trusted = trusted;
    }

    public boolean isTrusted() {
        return trusted;
    }
}
