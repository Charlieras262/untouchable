package io.qzz.toshiro.untouchable.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.qzz.toshiro.untouchable.internal.Json;

/** Seal content, already verified and parsed (SPEC §9.2). */
public final class SealPayload {
    /** Version of the seal format. */
    public static final long VERSION = 1;

    private final Map<String, Object> raw;

    private SealPayload(Map<String, Object> raw) {
        this.raw = Collections.unmodifiableMap(raw);
    }

    /** Parses and validates the structure. Throws IllegalArgumentException if something is missing. */
    @SuppressWarnings("unchecked")
    public static SealPayload parse(String json) {
        Object o = Json.parse(json);
        if (!(o instanceof Map)) throw new IllegalArgumentException("The seal is not a JSON object");
        Map<String, Object> m = (Map<String, Object>) o;
        Object v = m.get("v");
        if (!(v instanceof Long) || (Long) v != VERSION) throw new IllegalArgumentException("Unsupported seal version: " + v);
        for (String k : new String[] {"docId", "issuedAt", "format", "canon", "alg", "kid", "policy"}) {
            if (!(m.get(k) instanceof String)) throw new IllegalArgumentException("Missing \"" + k + "\" in the seal");
        }
        if (!(m.get("fingerprint") instanceof Map)) throw new IllegalArgumentException("Missing \"fingerprint\" in the seal");
        return new SealPayload(m);
    }

    public String docId() { return (String) raw.get("docId"); }
    public String issuedAt() { return (String) raw.get("issuedAt"); }
    public String format() { return (String) raw.get("format"); }
    public String canon() { return (String) raw.get("canon"); }
    public String algorithm() { return (String) raw.get("alg"); }
    public String keyId() { return (String) raw.get("kid"); }
    public String policy() { return (String) raw.get("policy"); }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fingerprint() { return (Map<String, Object>) raw.get("fingerprint"); }

    @SuppressWarnings("unchecked")
    public Map<String, Object> formatData() {
        Object o = raw.get("formatData");
        return o instanceof Map ? (Map<String, Object>) o : Collections.<String, Object>emptyMap();
    }

    public Map<String, String> claims() {
        Object o = raw.get("claims");
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (o instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
        return out;
    }

    /** Full payload (for handlers that need their own fields). */
    public Map<String, Object> raw() { return raw; }
}
