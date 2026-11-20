package io.qzz.toshiro.untouchable;

/** Severity of a finding, assigned by the seal's policy. */
public enum Severity {
    /** Allowed change or informational data. */
    INFO,
    /** Does not invalidate the document, but it is worth reviewing. */
    WARNING,
    /** Disallowed change: the document is not trustworthy. */
    VIOLATION
}
