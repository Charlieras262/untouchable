package io.qzz.toshiro.untouchable.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Document ready to be sealed: its bytes, its fingerprint and the format data that goes into the seal. */
public final class PreparedDocument {
    private final byte[] document;
    private final Map<String, Object> fingerprint;
    private final Map<String, Object> formatData;

    /**
     * @param fingerprint fingerprint (only String, Long/Integer, Boolean, List and Map; see canonical JSON)
     * @param formatData  format-specific data stored in the seal (may be empty)
     */
    public PreparedDocument(byte[] document, Map<String, Object> fingerprint, Map<String, Object> formatData) {
        this.document = document;
        this.fingerprint = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(fingerprint));
        this.formatData = formatData == null ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(formatData));
    }

    public byte[] document() { return document; }
    public Map<String, Object> fingerprint() { return fingerprint; }
    public Map<String, Object> formatData() { return formatData; }
}
