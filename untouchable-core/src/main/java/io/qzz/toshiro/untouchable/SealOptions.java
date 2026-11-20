package io.qzz.toshiro.untouchable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sealing options (SPEC FR-1). Immutable; created with {@link #builder()}. */
public final class SealOptions {
    private final String docId;
    private final ChangePolicy policy;
    private final Map<String, String> claims;
    private final boolean replaceExisting;
    private final Map<Class<?>, Object> formatOptions;

    private SealOptions(Builder b) {
        this.docId = b.docId;
        this.policy = b.policy;
        this.claims = Collections.unmodifiableMap(new LinkedHashMap<String, String>(b.claims));
        this.replaceExisting = b.replaceExisting;
        this.formatOptions = Collections.unmodifiableMap(new LinkedHashMap<Class<?>, Object>(b.formatOptions));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Default options: random docId and the STRICT policy. */
    public static SealOptions defaults() {
        return builder().build();
    }

    /** docId chosen by the issuer, or null to generate a UUID. */
    public String docId() { return docId; }
    public ChangePolicy policy() { return policy; }
    public Map<String, String> claims() { return claims; }
    /** If the document is already sealed: true replaces the seal, false fails (SPEC FR-4). */
    public boolean replaceExisting() { return replaceExisting; }

    /** Format-specific options (e.g. PdfSealOptions), or null. */
    public <T> T formatOption(Class<T> type) {
        return type.cast(formatOptions.get(type));
    }

    public static final class Builder {
        private String docId;
        private ChangePolicy policy = Policies.STRICT;
        private final Map<String, String> claims = new LinkedHashMap<String, String>();
        private boolean replaceExisting;
        private final Map<Class<?>, Object> formatOptions = new LinkedHashMap<Class<?>, Object>();

        public Builder docId(String docId) { this.docId = docId; return this; }

        public Builder policy(ChangePolicy policy) {
            if (policy == null) throw new IllegalArgumentException("policy");
            this.policy = policy;
            return this;
        }

        /** Free-form metadata stored in the seal (visible to anyone who reads the document). */
        public Builder claim(String key, String value) { claims.put(key, value); return this; }

        public Builder replaceExisting(boolean replace) { this.replaceExisting = replace; return this; }

        /** Options for a specific format, indexed by their class. */
        public Builder formatOption(Object option) {
            if (option == null) throw new IllegalArgumentException("option");
            formatOptions.put(option.getClass(), option);
            return this;
        }

        public SealOptions build() {
            return new SealOptions(this);
        }
    }
}
