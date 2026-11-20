package io.qzz.toshiro.untouchable;

/**
 * Change policy: defines the severity of each finding type. The issuer chooses it when sealing and it is
 * stored (by its id) inside the signed seal (SPEC FR-8a). The built-in policies are in
 * {@link Policies}; custom ones are registered in the {@link Untouchable} builder, both for sealing and
 * for verifying.
 */
public interface ChangePolicy {
    /** Stable identifier stored in the seal (e.g. "signatures-only"). */
    String id();

    /** Severity of a finding of this type under this policy. */
    Severity severity(FindingType type);

    /** true if the policy allows signatures added after sealing. */
    default boolean allowsSignatures() {
        return severity(FindingType.SIGNATURE_ADDED) != Severity.VIOLATION;
    }
}
