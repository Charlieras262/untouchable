package io.qzz.toshiro.untouchable;

import java.util.Collections;
import java.util.Map;

/** Data of the seal found (only if its signature is valid). */
public final class SealInfo {
    private final String docId, issuedAt, format, policyId, algorithm, keyId;
    private final Map<String, String> claims;

    public SealInfo(String docId, String issuedAt, String format, String policyId, String algorithm, String keyId,
                    Map<String, String> claims) {
        this.docId = docId; this.issuedAt = issuedAt; this.format = format; this.policyId = policyId;
        this.algorithm = algorithm; this.keyId = keyId;
        this.claims = claims == null ? Collections.<String, String>emptyMap() : Collections.unmodifiableMap(claims);
    }

    public String docId() { return docId; }
    /** Sealing time (ISO-8601, UTC), as declared by the issuer. */
    public String issuedAt() { return issuedAt; }
    public String format() { return format; }
    public String policyId() { return policyId; }
    public String algorithm() { return algorithm; }
    public String keyId() { return keyId; }
    /** Free-form metadata the issuer stored in the seal. */
    public Map<String, String> claims() { return claims; }
}
