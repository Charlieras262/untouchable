package io.qzz.toshiro.untouchable.spi;

/**
 * The seal as stored in the document: the payload in canonical JSON (exactly the signed bytes) and the
 * signature in Base64.
 */
public final class EmbeddedSeal {
    private final String payload;
    private final String signature;

    public EmbeddedSeal(String payload, String signature) {
        if (payload == null || signature == null) throw new IllegalArgumentException("payload/signature");
        this.payload = payload;
        this.signature = signature;
    }

    public String payload() { return payload; }
    public String signature() { return signature; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EmbeddedSeal)) return false;
        EmbeddedSeal s = (EmbeddedSeal) o;
        return payload.equals(s.payload) && signature.equals(s.signature);
    }

    @Override
    public int hashCode() {
        return payload.hashCode() * 31 + signature.hashCode();
    }
}
