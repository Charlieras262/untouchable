package io.qzz.toshiro.untouchable.pdf;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.pdfbox.pdmodel.PDDocument;

import io.qzz.toshiro.untouchable.Finding;
import io.qzz.toshiro.untouchable.FindingType;
import io.qzz.toshiro.untouchable.Region;
import io.qzz.toshiro.untouchable.SignatureInfo;
import io.qzz.toshiro.untouchable.VerificationMethod;
import io.qzz.toshiro.untouchable.VerifyOptions;
import io.qzz.toshiro.untouchable.spi.Comparison;
import io.qzz.toshiro.untouchable.spi.SealPayload;

/** Compares a PDF with its seal's fingerprint and produces the findings (SPEC FR-8 to FR-11). */
final class PdfComparator {
    private final PDDocument doc;
    private final byte[] file;
    private final PdfVerifyOptions options;
    private final List<Finding> findings = new ArrayList<Finding>();
    private final Map<Integer, BufferedImage> renders = new HashMap<Integer, BufferedImage>();
    private PdfTiles tiles;

    private PdfComparator(PDDocument doc, byte[] file, PdfVerifyOptions options) {
        this.doc = doc;
        this.file = file;
        this.options = options;
    }

    static Comparison compare(PDDocument doc, byte[] file, SealPayload payload, VerifyOptions verifyOptions)
            throws IOException {
        PdfVerifyOptions o = verifyOptions.formatOption(PdfVerifyOptions.class);
        return new PdfComparator(doc, file, o != null ? o : PdfVerifyOptions.defaults()).run(payload);
    }

    private Comparison run(SealPayload payload) throws IOException {
        Map<String, Object> formatData = payload.formatData();
        List<PdfSignatures.Sig> sigs = PdfSignatures.list(doc, file);

        // Certified-mode fast path; if it does not apply, continue by content.
        if (options.fastPath() && Boolean.TRUE.equals(formatData.get("certified"))
                && CertifiedRevisions.check(doc, file, formatData, sigs).ok()) {
            List<SignatureInfo> signatures = compareSignatures(formatData, sigs);
            return new Comparison(findings, signatures, VerificationMethod.CERTIFIED_REVISIONS);
        }

        PdfFingerprint stored = PdfFingerprint.fromMap(payload.fingerprint());
        PdfCanon.Result current = PdfCanon.compute(doc, false);
        boolean locate = options.locateChanges() && PdfTiles.PROFILE.equals(stored.renderProfile);

        comparePages(stored, current, locate);
        compareForms(stored.forms, current.fingerprint.forms, current.fieldPages);
        List<SignatureInfo> signatures = compareSignatures(formatData, sigs);
        return new Comparison(findings, signatures, VerificationMethod.CONTENT);
    }

    // ---------------------------------------------------------------- pages

    private void comparePages(PdfFingerprint stored, PdfCanon.Result current, boolean locate) throws IOException {
        List<PdfFingerprint.Page> s = stored.pages, c = current.fingerprint.pages;
        if (s.size() == c.size()) {
            if (!sameOrder(s, c) && sameMultiset(s, c)) {
                findings.add(Finding.of(FindingType.PAGE_REORDERED, null, null, "The page order changed"));
                return;
            }
            for (int i = 0; i < s.size(); i++) comparePage(s.get(i), c.get(i), i, current, locate);
            return;
        }
        // Different number of pages: align by content (longest common subsequence).
        int[][] lcs = new int[s.size() + 1][c.size() + 1];
        for (int i = s.size() - 1; i >= 0; i--) {
            for (int j = c.size() - 1; j >= 0; j--) {
                lcs[i][j] = s.get(i).contentKey().equals(c.get(j).contentKey())
                        ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        int i = 0, j = 0;
        while (i < s.size() || j < c.size()) {
            if (i < s.size() && j < c.size() && s.get(i).contentKey().equals(c.get(j).contentKey())) {
                comparePage(s.get(i), c.get(j), j, current, locate);
                i++; j++;
            } else if (j < c.size() && (i >= s.size() || lcs[i][j + 1] >= lcs[i + 1][j])) {
                findings.add(Finding.of(FindingType.PAGE_ADDED, j + 1, null, "Page added"));
                j++;
            } else {
                findings.add(Finding.of(FindingType.PAGE_REMOVED, null, null, "Page " + (i + 1) + " of the original was removed"));
                i++;
            }
        }
    }

    private static boolean sameOrder(List<PdfFingerprint.Page> a, List<PdfFingerprint.Page> b) {
        for (int i = 0; i < a.size(); i++) if (!a.get(i).contentKey().equals(b.get(i).contentKey())) return false;
        return true;
    }

    private static boolean sameMultiset(List<PdfFingerprint.Page> a, List<PdfFingerprint.Page> b) {
        List<String> x = new ArrayList<String>(), y = new ArrayList<String>();
        for (PdfFingerprint.Page p : a) x.add(p.contentKey());
        for (PdfFingerprint.Page p : b) y.add(p.contentKey());
        Collections.sort(x);
        Collections.sort(y);
        return x.equals(y);
    }

    private void comparePage(PdfFingerprint.Page s, PdfFingerprint.Page c, int index, PdfCanon.Result current,
                             boolean locate) throws IOException {
        int page = index + 1;
        boolean text = !s.text.equals(c.text), images = !s.images.equals(c.images);
        boolean graphics = !s.ops.equals(c.ops) && !text && !images;
        Region region = null;
        if (locate && (text || images || graphics) && s.tiles != null) {
            region = PdfTiles.changedRegion(s.tiles, PdfTiles.tiles(render(index)), new PageGeometry(doc.getPage(index), PdfTiles.DPI));
        }
        if (!s.geom.equals(c.geom)) {
            findings.add(Finding.of(FindingType.PAGE_GEOMETRY_CHANGED, page, null, "The page size, crop or rotation changed"));
        }
        if (text) findings.add(Finding.of(FindingType.TEXT_CHANGED, page, region, "The page text changed"));
        if (images) findings.add(Finding.of(FindingType.IMAGE_CHANGED, page, region, "An image on the page changed"));
        if (graphics) findings.add(Finding.of(FindingType.GRAPHICS_CHANGED, page, region, "The page drawing changed"));

        // Annotations (other than signatures): multiset difference.
        Map<String, Integer> count = new TreeMap<String, Integer>();
        for (String k : s.annots) count.put(k, (count.containsKey(k) ? count.get(k) : 0) - 1);
        for (String k : c.annots) count.put(k, (count.containsKey(k) ? count.get(k) : 0) + 1);
        for (Map.Entry<String, Integer> e : count.entrySet()) {
            String subtype = e.getKey().substring(0, e.getKey().indexOf(':'));
            for (int n = 0; n < Math.abs(e.getValue()); n++) {
                if (e.getValue() > 0) {
                    findings.add(Finding.of(FindingType.ANNOTATION_ADDED, page, annotRegion(current, index, e.getKey()),
                            "Annotation added: " + subtype));
                } else {
                    findings.add(Finding.of(FindingType.ANNOTATION_REMOVED, page, null, "Annotation removed: " + subtype));
                }
            }
        }
    }

    private static Region annotRegion(PdfCanon.Result current, int index, String key) {
        for (PdfCanon.AnnotRef a : current.annots.get(index)) if (a.key.equals(key)) return a.rect;
        return null;
    }

    // ---------------------------------------------------------------- forms

    private void compareForms(Map<String, String> stored, Map<String, String> current, Map<String, Integer> fieldPages) {
        Set<String> names = new TreeSet<String>(stored.keySet());
        names.addAll(current.keySet());
        for (String name : names) {
            String a = stored.get(name), b = current.get(name);
            if (a != null && a.equals(b)) continue;
            String what = a == null ? "Field added: " : b == null ? "Field removed: " : "Value changed in field ";
            findings.add(Finding.of(FindingType.FORM_FIELD_CHANGED, fieldPages.get(name), null, what + name));
        }
    }

    // ---------------------------------------------------------------- signatures

    private List<SignatureInfo> compareSignatures(Map<String, Object> formatData, List<PdfSignatures.Sig> sigs)
            throws IOException {
        Set<String> preexisting = strings(formatData.get("preexistingSignatures"));
        Set<String> declared = new HashSet<String>();
        Object fs = formatData.get("signatureFields");
        if (fs instanceof List) {
            for (Object o : (List<?>) fs) if (o instanceof Map) declared.add(String.valueOf(((Map<?, ?>) o).get("name")));
        }
        boolean certified = Boolean.TRUE.equals(formatData.get("certified"));
        Object issuerCert = formatData.get("certificationCert");

        List<SignatureInfo> infos = new ArrayList<SignatureInfo>();
        PdfSignatures.Sig certification = null;
        for (PdfSignatures.Sig sig : sigs) {
            // The issuer's certification: DocMDP and, if the seal says so, made with its certificate.
            boolean ours = certified && certification == null && sig.docMdpPermission != null && !preexisting.contains(sig.field)
                    && (issuerCert == null || issuerCert.equals(sig.signerCertSha256));
            if (ours) certification = sig;
            boolean added = !ours && !preexisting.contains(sig.field);
            infos.add(sig.toInfo(added));
            if (!added) continue;

            String who = sig.signer != null ? " by " + sig.signer : "";
            findings.add(Finding.of(FindingType.SIGNATURE_ADDED, sig.page, sig.region,
                    "Signature in field " + sig.field + who + " (" + sig.integrity + ")"));
            if (!declared.isEmpty() && !declared.contains(sig.field)) {
                findings.add(Finding.of(FindingType.SIGNATURE_OUTSIDE_DECLARED_FIELDS, sig.page, sig.region,
                        "Signature " + sig.field + " is not in any of the declared fields"));
            }
            if (options.locateChanges() && sig.page != null && sig.region != null) {
                int index = sig.page - 1;
                double ink = PdfTiles.inkFraction(render(index), new PageGeometry(doc.getPage(index), PdfTiles.DPI), sig.region);
                if (ink > options.overlapThreshold()) {
                    findings.add(Finding.of(FindingType.SIGNATURE_OVERLAPS_CONTENT, sig.page, sig.region,
                            String.format(Locale.ROOT, "Signature %s covers content over %.0f%% of its area", sig.field, ink * 100)));
                }
            }
        }
        if (certified && certification == null) {
            findings.add(Finding.of(FindingType.CERTIFICATION_INVALID, null, null,
                    "The issuer's certification signature is no longer in the document"));
        } else if (certification != null && certification.integrity != SignatureInfo.Integrity.VALID) {
            findings.add(Finding.of(FindingType.CERTIFICATION_INVALID, null, null,
                    "The certification signature is not valid (" + certification.integrity + ")"));
        }
        return infos;
    }

    private static Set<String> strings(Object o) {
        Set<String> out = new HashSet<String>();
        if (o instanceof List) for (Object x : (List<?>) o) out.add(String.valueOf(x));
        return out;
    }

    private BufferedImage render(int index) throws IOException {
        BufferedImage img = renders.get(index);
        if (img == null) {
            if (tiles == null) tiles = new PdfTiles(doc);
            img = tiles.render(index);
            renders.put(index, img);
        }
        return img;
    }
}
