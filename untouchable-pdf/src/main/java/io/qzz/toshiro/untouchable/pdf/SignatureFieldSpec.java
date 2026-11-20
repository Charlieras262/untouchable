package io.qzz.toshiro.untouchable.pdf;

import io.qzz.toshiro.untouchable.Region;

/** Empty signature field the issuer pre-creates when sealing (SPEC FR-12). */
public final class SignatureFieldSpec {
    private final String name;
    private final int page;
    private final Region rect;

    /**
     * @param name field name (unique in the document)
     * @param page page, 1-based
     * @param rect position in PDF points (origin at the bottom left of the page)
     */
    public SignatureFieldSpec(String name, int page, Region rect) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name");
        if (page < 1) throw new IllegalArgumentException("page must be >= 1");
        if (rect == null || rect.width() <= 0 || rect.height() <= 0) throw new IllegalArgumentException("rect");
        this.name = name; this.page = page; this.rect = rect;
    }

    public String name() { return name; }
    public int page() { return page; }
    public Region rect() { return rect; }
}
