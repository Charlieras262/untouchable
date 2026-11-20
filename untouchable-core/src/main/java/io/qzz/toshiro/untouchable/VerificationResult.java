package io.qzz.toshiro.untouchable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of {@link Untouchable#verify}: simple verdict + detailed report (SPEC FR-6). */
public final class VerificationResult {
    private final Status status;
    private final String reason;
    private final SealInfo seal;
    private final List<Finding> findings;
    private final List<SignatureInfo> signatures;
    private final VerificationMethod method;

    VerificationResult(Status status, String reason, SealInfo seal, List<Finding> findings,
                       List<SignatureInfo> signatures) {
        this(status, reason, seal, findings, signatures, null);
    }

    VerificationResult(Status status, String reason, SealInfo seal, List<Finding> findings,
                       List<SignatureInfo> signatures, VerificationMethod method) {
        this.method = method;
        this.status = status;
        this.reason = reason;
        this.seal = seal;
        this.findings = Collections.unmodifiableList(new ArrayList<Finding>(findings));
        this.signatures = Collections.unmodifiableList(new ArrayList<SignatureInfo>(signatures));
    }

    static VerificationResult of(Status status, String reason) {
        return new VerificationResult(status, reason, null, Collections.<Finding>emptyList(),
                Collections.<SignatureInfo>emptyList());
    }

    /** Yes/no verdict: true only for INTACT and INTACT_WITH_ALLOWED_CHANGES. */
    public boolean isTrusted() { return status.isTrusted(); }

    public Status status() { return status; }

    /** Short explanation for NO_SEAL, INVALID_SEAL and UNSUPPORTED; null otherwise. */
    public String reason() { return reason; }

    /** Seal data, or null if there is no valid seal. */
    public SealInfo seal() { return seal; }

    /** All findings, with their severity. */
    public List<Finding> findings() { return findings; }

    /** Findings of a given severity. */
    public List<Finding> findings(Severity severity) {
        List<Finding> out = new ArrayList<Finding>();
        for (Finding f : findings) if (f.severity() == severity) out.add(f);
        return out;
    }

    /** true if there are WARNING findings. */
    public boolean hasWarnings() { return !findings(Severity.WARNING).isEmpty(); }

    /** How the content was compared, or null if it was not compared (NO_SEAL, INVALID_SEAL, UNSUPPORTED). */
    public VerificationMethod method() { return method; }

    /** Electronic signatures found in the document (informational, SPEC FR-10). */
    public List<SignatureInfo> signatures() { return signatures; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(status.name());
        if (method != null) sb.append(" [").append(method).append(']');
        if (reason != null) sb.append(" (").append(reason).append(')');
        for (Finding f : findings) sb.append("\n  ").append(f);
        return sb.toString();
    }
}
