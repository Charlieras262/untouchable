package io.qzz.toshiro.untouchable;

/** Finding types of the detailed report (SPEC §7.4). */
public enum FindingType {
    PAGE_ADDED(true),
    PAGE_REMOVED(true),
    PAGE_REORDERED(true),
    PAGE_GEOMETRY_CHANGED(true),
    TEXT_CHANGED(true),
    IMAGE_CHANGED(true),
    /** The page drawing changed (paths, colors, positions) without changing text or images. */
    GRAPHICS_CHANGED(true),
    /** Content change with no further detail (formats without a breakdown). */
    CONTENT_CHANGED(true),
    ANNOTATION_ADDED(true),
    ANNOTATION_REMOVED(true),
    FORM_FIELD_CHANGED(true),
    SIGNATURE_ADDED(true),
    /** A signature appearance covers page content. */
    SIGNATURE_OVERLAPS_CONTENT(false),
    /** Signature fields were declared and a signature is not in any of them (SPEC FR-12). */
    SIGNATURE_OUTSIDE_DECLARED_FIELDS(false),
    /** The certification signature (DocMDP) applied when sealing is no longer valid or is gone. */
    CERTIFICATION_INVALID(false),
    /** The document has a different docId than the one the verifier expects (SPEC §9.3). */
    DOC_ID_MISMATCH(true);

    private final boolean change;

    FindingType(boolean change) {
        this.change = change;
    }

    /** true if the finding describes a change in the document; false if it is a warning about it. */
    public boolean isChange() {
        return change;
    }
}
