package io.qzz.toshiro.untouchable.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** pdf-canon/1 fingerprint and its representation inside the seal (SPEC §9.1, §9.2). */
final class PdfFingerprint {

    static final class Page {
        final String geom, text, images, ops;
        /** "Subtype:hash" of each non-signature annotation, sorted. */
        final List<String> annots;
        /** Tiles used to locate changes, or null if they were not stored. */
        final String tiles;

        Page(String geom, String text, String images, String ops, List<String> annots, String tiles) {
            this.geom = geom; this.text = text; this.images = images; this.ops = ops;
            this.annots = Collections.unmodifiableList(new ArrayList<String>(annots));
            this.tiles = tiles;
        }

        /** Key of the page content (without annotations), to detect moved pages. */
        String contentKey() {
            return geom + "|" + text + "|" + images + "|" + ops;
        }
    }

    final List<Page> pages;
    /** Form field (non-signature) → hash of its value. */
    final Map<String, String> forms;
    final String renderProfile;
    final String root;

    PdfFingerprint(List<Page> pages, Map<String, String> forms, String renderProfile) {
        this.pages = Collections.unmodifiableList(new ArrayList<Page>(pages));
        this.forms = Collections.unmodifiableMap(new TreeMap<String, String>(forms));
        this.renderProfile = renderProfile;
        this.root = computeRoot();
    }

    private String computeRoot() {
        StringBuilder sb = new StringBuilder();
        for (Page p : pages) {
            sb.append(p.geom).append(p.text).append(p.images).append(p.ops);
            for (String a : p.annots) sb.append(a).append(',');
            sb.append('|');
        }
        for (Map.Entry<String, String> e : forms.entrySet()) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        return Hashes.sha256(sb);
    }

    Map<String, Object> toMap() {
        List<Object> ps = new ArrayList<Object>();
        for (Page p : pages) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("geom", p.geom);
            m.put("text", p.text);
            m.put("images", p.images);
            m.put("ops", p.ops);
            m.put("annots", new ArrayList<Object>(p.annots));
            if (p.tiles != null) m.put("tiles", p.tiles);
            ps.add(m);
        }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("pageCount", (long) pages.size());
        m.put("pages", ps);
        m.put("forms", new LinkedHashMap<String, Object>(forms));
        m.put("renderProfile", renderProfile);
        m.put("root", root);
        return m;
    }

    /** Reads the fingerprint from the seal. Throws IllegalArgumentException if the structure is invalid. */
    static PdfFingerprint fromMap(Map<String, Object> m) {
        Object ps = m.get("pages");
        if (!(ps instanceof List)) throw new IllegalArgumentException("PDF fingerprint without pages");
        List<Page> pages = new ArrayList<Page>();
        for (Object o : (List<?>) ps) {
            if (!(o instanceof Map)) throw new IllegalArgumentException("Invalid page in the fingerprint");
            Map<?, ?> p = (Map<?, ?>) o;
            List<String> annots = new ArrayList<String>();
            Object as = p.get("annots");
            if (as instanceof List) for (Object a : (List<?>) as) annots.add(String.valueOf(a));
            pages.add(new Page(str(p, "geom"), str(p, "text"), str(p, "images"), str(p, "ops"), annots,
                    p.get("tiles") instanceof String ? (String) p.get("tiles") : null));
        }
        Map<String, String> forms = new TreeMap<String, String>();
        Object fs = m.get("forms");
        if (fs instanceof Map) for (Map.Entry<?, ?> e : ((Map<?, ?>) fs).entrySet()) forms.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        Object rp = m.get("renderProfile");
        PdfFingerprint fp = new PdfFingerprint(pages, forms, rp instanceof String ? (String) rp : null);
        if (m.get("root") != null && !fp.root.equals(m.get("root"))) {
            throw new IllegalArgumentException("The fingerprint root does not match its components");
        }
        return fp;
    }

    private static String str(Map<?, ?> m, String k) {
        Object v = m.get(k);
        if (!(v instanceof String)) throw new IllegalArgumentException("Missing \"" + k + "\" in the fingerprint");
        return (String) v;
    }
}
