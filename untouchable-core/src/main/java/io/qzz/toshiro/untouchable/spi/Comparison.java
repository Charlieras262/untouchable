package io.qzz.toshiro.untouchable.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.qzz.toshiro.untouchable.Finding;
import io.qzz.toshiro.untouchable.SignatureInfo;
import io.qzz.toshiro.untouchable.VerificationMethod;

/** Result of comparing a document with its sealed fingerprint. */
public final class Comparison {
    private final List<Finding> findings;
    private final List<SignatureInfo> signatures;
    private final VerificationMethod method;

    public Comparison(List<Finding> findings, List<SignatureInfo> signatures) {
        this(findings, signatures, VerificationMethod.CONTENT);
    }

    public Comparison(List<Finding> findings, List<SignatureInfo> signatures, VerificationMethod method) {
        this.findings = Collections.unmodifiableList(new ArrayList<Finding>(findings));
        this.signatures = Collections.unmodifiableList(new ArrayList<SignatureInfo>(signatures));
        this.method = method == null ? VerificationMethod.CONTENT : method;
    }

    public VerificationMethod method() { return method; }

    public List<Finding> findings() { return findings; }
    public List<SignatureInfo> signatures() { return signatures; }
}
