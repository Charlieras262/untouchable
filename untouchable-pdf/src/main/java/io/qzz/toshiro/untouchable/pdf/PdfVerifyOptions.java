package io.qzz.toshiro.untouchable.pdf;

/** PDF-specific verification options. Passed with {@code VerifyOptions.builder().formatOption(...)}. */
public final class PdfVerifyOptions {
    /** Default SIGNATURE_OVERLAPS_CONTENT threshold: 5% of the widget area (SPEC D11). */
    public static final double DEFAULT_OVERLAP_THRESHOLD = 0.05;

    private final double overlapThreshold;
    private final boolean locateChanges;
    private final boolean fastPath;

    private PdfVerifyOptions(Builder b) {
        this.overlapThreshold = b.overlapThreshold;
        this.locateChanges = b.locateChanges;
        this.fastPath = b.fastPath;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PdfVerifyOptions defaults() {
        return builder().build();
    }

    /** Fraction of a signature's area that may fall on content without a warning (0..1). */
    public double overlapThreshold() { return overlapThreshold; }

    /** Render pages to locate changes within them and to measure overlaps. */
    public boolean locateChanges() { return locateChanges; }

    /**
     * Use the certified-mode fast path when it applies (on by default). Turning it off forces content
     * verification, which also locates changes within the pages.
     */
    public boolean fastPath() { return fastPath; }

    public static final class Builder {
        private double overlapThreshold = DEFAULT_OVERLAP_THRESHOLD;
        private boolean locateChanges = true;
        private boolean fastPath = true;

        public Builder overlapThreshold(double threshold) {
            if (threshold < 0 || threshold > 1) throw new IllegalArgumentException("threshold must be between 0 and 1");
            this.overlapThreshold = threshold;
            return this;
        }

        public Builder locateChanges(boolean locate) { this.locateChanges = locate; return this; }

        public Builder fastPath(boolean fastPath) { this.fastPath = fastPath; return this; }

        public PdfVerifyOptions build() {
            return new PdfVerifyOptions(this);
        }
    }
}
