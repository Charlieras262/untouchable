package io.qzz.toshiro.untouchable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Verification options. Immutable; created with {@link #builder()}. */
public final class VerifyOptions {
    private final String expectedDocId;
    private final Map<Class<?>, Object> formatOptions;

    private VerifyOptions(Builder b) {
        this.expectedDocId = b.expectedDocId;
        this.formatOptions = Collections.unmodifiableMap(new LinkedHashMap<Class<?>, Object>(b.formatOptions));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static VerifyOptions defaults() {
        return builder().build();
    }

    /** Shortcut: require a specific docId (protects against replacement by another valid document). */
    public static VerifyOptions expectDocId(String docId) {
        return builder().expectDocId(docId).build();
    }

    public String expectedDocId() { return expectedDocId; }

    public <T> T formatOption(Class<T> type) {
        return type.cast(formatOptions.get(type));
    }

    public static final class Builder {
        private String expectedDocId;
        private final Map<Class<?>, Object> formatOptions = new LinkedHashMap<Class<?>, Object>();

        public Builder expectDocId(String docId) { this.expectedDocId = docId; return this; }

        public Builder formatOption(Object option) {
            if (option == null) throw new IllegalArgumentException("option");
            formatOptions.put(option.getClass(), option);
            return this;
        }

        public VerifyOptions build() {
            return new VerifyOptions(this);
        }
    }
}
