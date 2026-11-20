package io.qzz.toshiro.untouchable;

/** A finding of the detailed report. */
public final class Finding {
    private final FindingType type;
    private final Severity severity;
    private final Integer page;
    private final Region region;
    private final String description;

    private Finding(FindingType type, Severity severity, Integer page, Region region, String description) {
        if (type == null) throw new IllegalArgumentException("type");
        this.type = type;
        this.severity = severity;
        this.page = page;
        this.region = region;
        this.description = description == null ? "" : description;
    }

    /** Finding without a severity: the policy assigns it at verification time. For format handlers. */
    public static Finding of(FindingType type, Integer page, Region region, String description) {
        return new Finding(type, null, page, region, description);
    }

    /** Copy with the given severity. */
    public Finding withSeverity(Severity severity) {
        return new Finding(type, severity, page, region, description);
    }

    public FindingType type() { return type; }

    /** Severity according to the seal's policy. */
    public Severity severity() { return severity; }

    /** Page (1-based), or null if the finding is about the whole document. */
    public Integer page() { return page; }

    /** Approximate area of the change within the page, or null if it could not be located. */
    public Region region() { return region; }

    public String description() { return description; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(severity == null ? "?" : severity).append(' ').append(type);
        if (page != null) sb.append(" p.").append(page);
        if (region != null) sb.append(' ').append(region);
        if (!description.isEmpty()) sb.append(": ").append(description);
        return sb.toString();
    }
}
