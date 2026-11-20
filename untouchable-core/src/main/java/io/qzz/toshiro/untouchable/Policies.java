package io.qzz.toshiro.untouchable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Built-in policies (SPEC FR-8a). */
public final class Policies {

    /** Only metadata and rewrites without content changes are allowed. The default. */
    public static final ChangePolicy STRICT = new BuiltIn("strict", false, false);

    /** The above + new signatures with their appearances. */
    public static final ChangePolicy SIGNATURES_ONLY = new BuiltIn("signatures-only", true, false);

    /** The above + filling in existing form fields. */
    public static final ChangePolicy SIGNATURES_AND_FORMS = new BuiltIn("signatures-and-forms", true, true);

    private static final List<ChangePolicy> ALL =
            Collections.unmodifiableList(Arrays.asList(STRICT, SIGNATURES_ONLY, SIGNATURES_AND_FORMS));

    private Policies() {}

    public static List<ChangePolicy> builtIn() {
        return ALL;
    }

    /** Built-in policy with that id, or null. */
    public static ChangePolicy byId(String id) {
        for (ChangePolicy p : ALL) if (p.id().equals(id)) return p;
        return null;
    }

    private static final class BuiltIn implements ChangePolicy {
        private final String id;
        private final boolean signatures, forms;

        BuiltIn(String id, boolean signatures, boolean forms) {
            this.id = id; this.signatures = signatures; this.forms = forms;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Severity severity(FindingType type) {
            switch (type) {
                case SIGNATURE_ADDED:
                    return signatures ? Severity.INFO : Severity.VIOLATION;
                case FORM_FIELD_CHANGED:
                    return forms ? Severity.INFO : Severity.VIOLATION;
                case SIGNATURE_OVERLAPS_CONTENT:
                case SIGNATURE_OUTSIDE_DECLARED_FIELDS:
                case CERTIFICATION_INVALID:
                    return Severity.WARNING;
                default:
                    return Severity.VIOLATION;
            }
        }

        @Override
        public String toString() {
            return id;
        }
    }
}
