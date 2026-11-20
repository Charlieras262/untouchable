package io.qzz.toshiro.untouchable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;

import io.qzz.toshiro.untouchable.crypto.KeyResolver;
import io.qzz.toshiro.untouchable.crypto.SealSigner;
import io.qzz.toshiro.untouchable.crypto.SealVerifier;
import io.qzz.toshiro.untouchable.internal.Json;
import io.qzz.toshiro.untouchable.spi.Comparison;
import io.qzz.toshiro.untouchable.spi.EmbeddedSeal;
import io.qzz.toshiro.untouchable.spi.FormatHandler;
import io.qzz.toshiro.untouchable.spi.PreparedDocument;
import io.qzz.toshiro.untouchable.spi.SealPayload;

/**
 * Entry point: seals documents and verifies whether they were modified.
 *
 * <pre>{@code
 * Untouchable u = Untouchable.builder()
 *     .signer(Ed25519.signer(privateKey, "key-2026"))
 *     .keyResolver(KeyResolver.of("key-2026", Ed25519.verifier(publicKey)))
 *     .build();
 *
 * byte[] sealed = u.seal(pdf, SealOptions.builder().policy(Policies.SIGNATURES_ONLY).build());
 * VerificationResult r = u.verify(received);
 * r.isTrusted();   // yes/no
 * r.findings();    // details
 * }</pre>
 *
 * Immutable and thread-safe as long as the handlers, the signer and the key resolver are.
 */
public final class Untouchable {
    /** Default maximum document size (SPEC NFR-5). */
    public static final int DEFAULT_MAX_DOCUMENT_BYTES = 256 * 1024 * 1024;

    private final List<FormatHandler> handlers;
    private final SealSigner signer;
    private final KeyResolver keyResolver;
    private final Map<String, ChangePolicy> policies;
    private final Clock clock;
    private final int maxDocumentBytes;

    private Untouchable(Builder b) {
        List<FormatHandler> hs = new ArrayList<FormatHandler>(b.handlers);
        if (b.discoverHandlers) {
            for (FormatHandler h : ServiceLoader.load(FormatHandler.class, Untouchable.class.getClassLoader())) {
                boolean present = false;
                for (FormatHandler x : hs) if (x.format().equals(h.format())) present = true;
                if (!present) hs.add(h);
            }
        }
        this.handlers = Collections.unmodifiableList(hs);
        this.signer = b.signer;
        this.keyResolver = b.keyResolver;
        Map<String, ChangePolicy> ps = new LinkedHashMap<String, ChangePolicy>();
        for (ChangePolicy p : Policies.builtIn()) ps.put(p.id(), p);
        for (ChangePolicy p : b.policies) ps.put(p.id(), p);
        this.policies = Collections.unmodifiableMap(ps);
        this.clock = b.clock;
        this.maxDocumentBytes = b.maxDocumentBytes;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Available formats (registered and discovered handlers). */
    public List<String> formats() {
        List<String> out = new ArrayList<String>();
        for (FormatHandler h : handlers) out.add(h.format());
        return out;
    }

    // ---------------------------------------------------------------- sealing

    /** Seals with the default options (STRICT policy). */
    public byte[] seal(byte[] document) {
        return seal(document, SealOptions.defaults());
    }

    /**
     * Seals the document and returns a sealed copy. The original document is not modified.
     *
     * @throws UntouchableException if the format is not supported, the document is already sealed (and
     *                              replacing it was not requested), the options are invalid or the file cannot be read
     */
    public byte[] seal(byte[] document, SealOptions options) {
        if (signer == null) throw new IllegalStateException("A signer must be configured in the builder to seal");
        checkSize(document);
        if (options == null) options = SealOptions.defaults();
        if (!policies.containsKey(options.policy().id())) {
            throw new UntouchableException("Policy not registered: " + options.policy().id());
        }
        FormatHandler h = handlerFor(document);
        if (h == null) throw new UntouchableException("Unsupported document format");
        try {
            if (!options.replaceExisting() && h.extract(document) != null) {
                throw new UntouchableException("The document already has a seal (use replaceExisting(true) to replace it)");
            }
            PreparedDocument prepared = h.prepare(document, options);

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("v", SealPayload.VERSION);
            payload.put("docId", options.docId() != null ? options.docId() : UUID.randomUUID().toString());
            payload.put("issuedAt", Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString());
            payload.put("format", h.format());
            payload.put("canon", h.canonVersion());
            payload.put("alg", signer.algorithm());
            payload.put("kid", signer.keyId());
            payload.put("policy", options.policy().id());
            if (!options.claims().isEmpty()) payload.put("claims", new LinkedHashMap<String, Object>(options.claims()));
            payload.put("fingerprint", prepared.fingerprint());
            if (!prepared.formatData().isEmpty()) payload.put("formatData", prepared.formatData());

            String json = Json.write(payload);
            String sig = Base64.getEncoder().encodeToString(signer.sign(json.getBytes(StandardCharsets.UTF_8)));
            return h.embed(prepared, new EmbeddedSeal(json, sig), options);
        } catch (IOException e) {
            throw new UntouchableException("Could not seal the document: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- verification

    public VerificationResult verify(byte[] document) {
        return verify(document, VerifyOptions.defaults());
    }

    /**
     * Verifies the document. Never throws because of the document's content: an unreadable or unknown
     * file gives {@link Status#UNSUPPORTED}.
     */
    public VerificationResult verify(byte[] document, VerifyOptions options) {
        if (keyResolver == null) throw new IllegalStateException("A keyResolver must be configured in the builder to verify");
        if (options == null) options = VerifyOptions.defaults();
        if (document == null || document.length > maxDocumentBytes) {
            return VerificationResult.of(Status.UNSUPPORTED, "Empty document or larger than the allowed maximum");
        }
        FormatHandler h = handlerFor(document);
        if (h == null) return VerificationResult.of(Status.UNSUPPORTED, "Unsupported document format");

        EmbeddedSeal embedded;
        try {
            embedded = h.extract(document);
        } catch (IOException e) {
            return VerificationResult.of(Status.UNSUPPORTED, "Could not read the document: " + e.getMessage());
        } catch (RuntimeException e) {
            return VerificationResult.of(Status.UNSUPPORTED, "Could not read the document: " + e);
        }
        if (embedded == null) return VerificationResult.of(Status.NO_SEAL, "The document has no seal");

        // 1) Seal signature (before interpreting its content).
        SealPayload payload;
        try {
            payload = SealPayload.parse(embedded.payload());
        } catch (IllegalArgumentException e) {
            return VerificationResult.of(Status.INVALID_SEAL, "Damaged seal: " + e.getMessage());
        }
        SealVerifier verifier = keyResolver.resolve(payload.keyId());
        if (verifier == null) {
            return VerificationResult.of(Status.INVALID_SEAL, "Unknown key: " + payload.keyId());
        }
        if (!verifier.algorithm().equals(payload.algorithm())) {
            return VerificationResult.of(Status.INVALID_SEAL,
                    "Unexpected algorithm for key " + payload.keyId() + ": " + payload.algorithm());
        }
        byte[] sig;
        try {
            sig = Base64.getDecoder().decode(embedded.signature());
        } catch (IllegalArgumentException e) {
            return VerificationResult.of(Status.INVALID_SEAL, "Damaged seal signature");
        }
        if (!verifier.verify(embedded.payload().getBytes(StandardCharsets.UTF_8), sig)) {
            return VerificationResult.of(Status.INVALID_SEAL, "The seal signature is not valid");
        }

        // 2) Is the seal compatible with this environment?
        SealInfo info = new SealInfo(payload.docId(), payload.issuedAt(), payload.format(), payload.policy(),
                payload.algorithm(), payload.keyId(), payload.claims());
        if (!h.format().equals(payload.format()) || !h.canonVersion().equals(payload.canon())) {
            return new VerificationResult(Status.UNSUPPORTED, "Fingerprint " + payload.format() + "/" + payload.canon()
                    + " is not supported by this version", info, Collections.<Finding>emptyList(),
                    Collections.<SignatureInfo>emptyList());
        }
        ChangePolicy policy = policies.get(payload.policy());
        if (policy == null) {
            return new VerificationResult(Status.UNSUPPORTED, "Unknown policy: " + payload.policy(), info,
                    Collections.<Finding>emptyList(), Collections.<SignatureInfo>emptyList());
        }

        // 3) Content comparison.
        Comparison c;
        try {
            c = h.compare(document, payload, options);
        } catch (IOException e) {
            return new VerificationResult(Status.UNSUPPORTED, "Could not analyze the document: " + e.getMessage(),
                    info, Collections.<Finding>emptyList(), Collections.<SignatureInfo>emptyList());
        } catch (RuntimeException e) {
            return new VerificationResult(Status.UNSUPPORTED, "Could not analyze the document: " + e, info,
                    Collections.<Finding>emptyList(), Collections.<SignatureInfo>emptyList());
        }

        List<Finding> findings = new ArrayList<Finding>();
        if (options.expectedDocId() != null && !options.expectedDocId().equals(payload.docId())) {
            findings.add(Finding.of(FindingType.DOC_ID_MISMATCH, null, null,
                    "Expected " + options.expectedDocId() + " but the seal belongs to " + payload.docId()));
        }
        findings.addAll(c.findings());
        List<Finding> rated = new ArrayList<Finding>();
        boolean violation = false, change = false;
        for (Finding f : findings) {
            Severity s = policy.severity(f.type());
            rated.add(f.withSeverity(s));
            if (s == Severity.VIOLATION) violation = true;
            if (f.type().isChange()) change = true;
        }
        Status status = violation ? Status.MODIFIED : change ? Status.INTACT_WITH_ALLOWED_CHANGES : Status.INTACT;
        return new VerificationResult(status, null, info, rated, c.signatures(), c.method());
    }

    // ---------------------------------------------------------------- internals

    private FormatHandler handlerFor(byte[] document) {
        if (document == null) return null;
        for (FormatHandler h : handlers) if (h.supports(document)) return h;
        return null;
    }

    private void checkSize(byte[] document) {
        if (document == null || document.length == 0) throw new UntouchableException("Empty document");
        if (document.length > maxDocumentBytes) {
            throw new UntouchableException("The document exceeds the maximum of " + maxDocumentBytes + " bytes");
        }
    }

    public static final class Builder {
        private final List<FormatHandler> handlers = new ArrayList<FormatHandler>();
        private final List<ChangePolicy> policies = new ArrayList<ChangePolicy>();
        private boolean discoverHandlers = true;
        private SealSigner signer;
        private KeyResolver keyResolver;
        private Clock clock = Clock.systemUTC();
        private int maxDocumentBytes = DEFAULT_MAX_DOCUMENT_BYTES;

        /** Seal signer. Required for sealing. */
        public Builder signer(SealSigner signer) { this.signer = signer; return this; }

        /** Key resolver for verification. Required for verifying. */
        public Builder keyResolver(KeyResolver keyResolver) { this.keyResolver = keyResolver; return this; }

        /** Registers a format handler (it takes precedence over discovered ones). */
        public Builder handler(FormatHandler handler) { handlers.add(handler); return this; }

        /** Disables handler discovery through ServiceLoader. */
        public Builder discoverHandlers(boolean discover) { this.discoverHandlers = discover; return this; }

        /** Registers a custom policy (needed both for sealing and for verifying). */
        public Builder policy(ChangePolicy policy) { policies.add(policy); return this; }

        public Builder clock(Clock clock) { this.clock = clock; return this; }

        public Builder maxDocumentBytes(int max) { this.maxDocumentBytes = max; return this; }

        public Untouchable build() {
            return new Untouchable(this);
        }
    }
}
