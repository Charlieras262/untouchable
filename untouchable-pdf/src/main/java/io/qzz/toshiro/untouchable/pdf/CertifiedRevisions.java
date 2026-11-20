package io.qzz.toshiro.untouchable.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;

import io.qzz.toshiro.untouchable.SignatureInfo;
import io.qzz.toshiro.untouchable.spi.EmbeddedSeal;

/**
 * Certified-mode fast path (SPEC §9.1). Decides, without recomputing the content fingerprint, that the
 * document only changed in allowed ways:
 * <ol>
 *   <li>the DocMDP certification signature is valid, was made with the issuer's certificate (stored in the
 *   seal) and its {@code ByteRange} covers the revision that contains the seal: that revision is intact;</li>
 *   <li>every object added or modified in later revisions is a signature, its field or widget, a signature
 *   appearance, validation data (DSS), metadata or structure (xref / object streams);</li>
 *   <li>the catalog, the AcroForm and the pages only change in the keys a signature touches, and only to add
 *   signature fields or widgets; no object is removed and the document root does not change.</li>
 * </ol>
 * If something does not fit, it returns the reason and verification continues by content: the fast path can
 * only speed things up, never accept something that content verification would reject as a content change.
 */
final class CertifiedRevisions {
    private static final Set<String> CATALOG_ALLOWED = set("AcroForm", "DSS", "Metadata", "Extensions", "Version");
    private static final Set<String> ACROFORM_ALLOWED = set("Fields", "SigFlags", "DR", "DA", "NeedAppearances");
    private static final Set<String> PAGE_ALLOWED = set("Annots");
    private static final COSName DSS = COSName.getPDFName("DSS");
    private static final COSName OBJ_STM = COSName.getPDFName("ObjStm");

    /** Result: {@code certification != null} if the fast path applies; otherwise {@code reason} explains why. */
    static final class Outcome {
        final PdfSignatures.Sig certification;
        final String reason;

        private Outcome(PdfSignatures.Sig certification, String reason) {
            this.certification = certification;
            this.reason = reason;
        }

        boolean ok() { return certification != null; }

        static Outcome fail(String reason) { return new Outcome(null, reason); }
    }

    private final PDDocument doc;
    private final byte[] file;
    private PDDocument revision;

    private CertifiedRevisions(PDDocument doc, byte[] file) {
        this.doc = doc;
        this.file = file;
    }

    static Outcome check(PDDocument doc, byte[] file, Map<String, Object> formatData, List<PdfSignatures.Sig> sigs)
            throws IOException {
        CertifiedRevisions c = new CertifiedRevisions(doc, file);
        try {
            return c.run(formatData, sigs);
        } finally {
            if (c.revision != null) c.revision.close();
        }
    }

    private Outcome run(Map<String, Object> formatData, List<PdfSignatures.Sig> sigs) throws IOException {
        if (!Boolean.TRUE.equals(formatData.get("certified"))) return Outcome.fail("the seal is not in certified mode");
        Object expected = formatData.get("certificationCert");
        if (!(expected instanceof String)) return Outcome.fail("the seal does not identify the issuer's certificate");

        // 1) The issuer's certification: valid and covering the sealed revision.
        PdfSignatures.Sig cert = null;
        for (PdfSignatures.Sig s : sigs) {
            if (s.docMdpPermission == null) continue;
            if (cert != null) return Outcome.fail("there is more than one certification signature");
            cert = s;
        }
        if (cert == null) return Outcome.fail("there is no certification signature");
        if (cert.integrity != SignatureInfo.Integrity.VALID) return Outcome.fail("the certification is not valid");
        if (!expected.equals(cert.signerCertSha256)) return Outcome.fail("the certification was not made with the issuer's certificate");
        if (cert.coveredRevision == null || cert.coveredRevision[1] > file.length) {
            return Outcome.fail("the certification does not cover a complete revision");
        }
        int end = (int) cert.coveredRevision[1];

        // 2) The seal is in the certified revision.
        revision = PdfFormatHandler.load(Arrays.copyOf(file, end));
        EmbeddedSeal sealed = SealStore.read(revision), current = SealStore.read(doc);
        if (sealed == null || !sealed.equals(current)) return Outcome.fail("the seal is not in the certified revision");
        if (end == file.length) return new Outcome(cert, null);

        // 3) Later revisions: only allowed changes.
        String problem = checkLaterRevisions(end);
        return problem == null ? new Outcome(cert, null) : Outcome.fail(problem);
    }

    private String checkLaterRevisions(long end) throws IOException {
        COSDocument cos0 = revision.getDocument(), cos1 = doc.getDocument();
        Map<COSObjectKey, Long> xref0 = cos0.getXrefTable(), xref1 = cos1.getXrefTable();
        for (COSObjectKey k : xref0.keySet()) {
            if (!xref1.containsKey(k)) return "object " + k + " was removed";
        }
        COSObjectKey root0 = key(cos0.getTrailer().getItem(COSName.ROOT));
        COSObjectKey root1 = key(cos1.getTrailer().getItem(COSName.ROOT));
        if (root0 == null || !root0.equals(root1)) return "the document root changed";

        // Objects allowed in the current document.
        Set<COSObjectKey> signatureObjects = new HashSet<COSObjectKey>();
        Set<COSObjectKey> newAllowed = new HashSet<COSObjectKey>();
        for (PDSignatureField f : doc.getSignatureFields()) {
            add(signatureObjects, f.getCOSObject().getKey());
            add(signatureObjects, key(f.getCOSObject().getItem(COSName.V)));
            for (PDAnnotationWidget w : f.getWidgets()) {
                add(signatureObjects, w.getCOSObject().getKey());
                reach(w.getCOSObject().getItem(COSName.AP), newAllowed);
            }
        }
        COSDictionary catalog1 = doc.getDocumentCatalog().getCOSObject();
        reach(catalog1.getItem(DSS), newAllowed);
        COSBase acroForm1 = catalog1.getItem(COSName.ACRO_FORM);
        if (resolve(acroForm1) instanceof COSDictionary) {
            reach(((COSDictionary) resolve(acroForm1)).getItem(COSName.DR), newAllowed);
        }
        Set<COSObjectKey> metadata = new HashSet<COSObjectKey>();
        add(metadata, key(cos1.getTrailer().getItem(COSName.INFO)));
        add(metadata, key(catalog1.getItem(COSName.METADATA)));
        Map<COSObjectKey, Integer> pages = new HashMap<COSObjectKey, Integer>();
        Map<COSObjectKey, Integer> annotArrays = new HashMap<COSObjectKey, Integer>();
        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            PDPage p = doc.getPage(i);
            if (p.getCOSObject().getKey() != null) pages.put(p.getCOSObject().getKey(), i);
            COSObjectKey ak = key(p.getCOSObject().getItem(COSName.ANNOTS));
            if (ak != null) annotArrays.put(ak, i);
        }
        COSObjectKey catalogKey = root1, acroFormKey = key(acroForm1);
        COSObjectKey fieldsKey = resolve(acroForm1) instanceof COSDictionary
                ? key(((COSDictionary) resolve(acroForm1)).getItem(COSName.FIELDS)) : null;

        for (Map.Entry<COSObjectKey, Long> e : xref1.entrySet()) {
            if (!changedAfter(e.getValue(), xref1, end)) continue;
            COSObjectKey k = e.getKey();
            boolean existed = xref0.containsKey(k);
            COSBase o1 = object(cos1, k);
            COSName type = o1 instanceof COSDictionary ? ((COSDictionary) o1).getCOSName(COSName.TYPE) : null;

            if (COSName.XREF.equals(type) || OBJ_STM.equals(type)) continue;
            if (signatureObjects.contains(k) || metadata.contains(k)) continue;
            if (!existed && newAllowed.contains(k)) continue;

            String problem;
            if (k.equals(catalogKey)) {
                problem = checkCatalog((COSDictionary) object(cos0, k), (COSDictionary) o1, signatureObjects);
            } else if (k.equals(acroFormKey)) {
                problem = checkAcroForm((COSDictionary) object(cos0, k), (COSDictionary) o1, signatureObjects);
            } else if (k.equals(fieldsKey)) {
                problem = existed ? checkAdded(object(cos0, k), o1, signatureObjects, "fields") : "the field list was replaced";
            } else if (pages.containsKey(k) && existed) {
                COSDictionary p0 = (COSDictionary) object(cos0, k), p1 = (COSDictionary) o1;
                problem = differentKeys(p0, p1, PAGE_ALLOWED, "page " + (pages.get(k) + 1));
                if (problem == null) problem = checkAdded(p0.getItem(COSName.ANNOTS), p1.getItem(COSName.ANNOTS),
                        signatureObjects, "annotations of page " + (pages.get(k) + 1));
            } else if (annotArrays.containsKey(k) && existed) {
                problem = checkAdded(object(cos0, k), o1, signatureObjects, "annotations of page " + (annotArrays.get(k) + 1));
            } else {
                problem = "object " + k + (type != null ? " (" + type.getName() + ")" : "") + (existed ? " was modified" : " was added");
            }
            if (problem != null) return problem;
        }
        return null;
    }

    private String checkCatalog(COSDictionary c0, COSDictionary c1, Set<COSObjectKey> signatureObjects) {
        String problem = differentKeys(c0, c1, CATALOG_ALLOWED, "the catalog");
        if (problem != null) return problem;
        COSBase a0 = c0.getItem(COSName.ACRO_FORM), a1 = c1.getItem(COSName.ACRO_FORM);
        if (a0 instanceof COSObject || a1 instanceof COSObject) {
            // Indirect form: if it changes, it is checked as an object of its own.
            return a0 instanceof COSObject && a1 instanceof COSObject && key(a0).equals(key(a1)) ? null : "the form was replaced";
        }
        if (a0 == null && a1 == null) return null;
        if (!(a0 instanceof COSDictionary) || !(a1 instanceof COSDictionary)) return "the form was added or removed";
        return checkAcroForm((COSDictionary) a0, (COSDictionary) a1, signatureObjects);
    }

    private String checkAcroForm(COSDictionary f0, COSDictionary f1, Set<COSObjectKey> signatureObjects) {
        String problem = differentKeys(f0, f1, ACROFORM_ALLOWED, "the form");
        if (problem != null) return problem;
        COSBase x = f0.getItem(COSName.FIELDS), y = f1.getItem(COSName.FIELDS);
        if (x instanceof COSObject && y instanceof COSObject && key(x).equals(key(y))) return null;
        return checkAdded(x, y, signatureObjects, "fields");
    }

    /** Every element of the previous list is still there; the new ones are signature objects. */
    private static String checkAdded(COSBase before, COSBase after, Set<COSObjectKey> signatureObjects, String what) {
        List<String> a = elements(resolve(before)), b = elements(resolve(after));
        if (a == null || b == null) return "the list of " + what + " is not valid";
        List<String> extra = new ArrayList<String>(b);
        for (String s : a) {
            if (!extra.remove(s)) return "an element was removed from the list of " + what;
        }
        for (String s : extra) {
            if (!s.startsWith("ref:") || !signatureObjects.contains(parseKey(s))) {
                return "something that is not a signature was added to the list of " + what;
            }
        }
        return null;
    }

    private static List<String> elements(COSBase arr) {
        if (arr == null) return Collections.emptyList();
        if (!(arr instanceof COSArray)) return null;
        List<String> out = new ArrayList<String>();
        for (COSBase b : (COSArray) arr) {
            COSObjectKey k = key(b);
            out.add(k != null ? "ref:" + k.getNumber() + ":" + k.getGeneration() : "direct:" + shallow(b));
        }
        return out;
    }

    private static COSObjectKey parseKey(String ref) {
        String[] p = ref.split(":");
        return new COSObjectKey(Long.parseLong(p[1]), Integer.parseInt(p[2]));
    }

    private static String differentKeys(COSDictionary d0, COSDictionary d1, Set<String> allowed, String what) {
        if (d0 == null || d1 == null) return "falta " + what;
        Set<COSName> keys = new HashSet<COSName>(d0.keySet());
        keys.addAll(d1.keySet());
        for (COSName k : keys) {
            if (allowed.contains(k.getName())) continue;
            if (!shallow(d0.getItem(k)).equals(shallow(d1.getItem(k)))) return "/" + k.getName() + " changed in " + what;
        }
        return null;
    }

    /** Representation used for comparison: indirect references are compared by object number. */
    private static String shallow(COSBase b) {
        if (b == null || b instanceof COSNull) return "null";
        COSObjectKey k = key(b);
        if (k != null) return "ref:" + k.getNumber() + ":" + k.getGeneration();
        if (b instanceof COSNumber) return "n:" + ((COSNumber) b).floatValue();
        if (b instanceof COSName) return "/" + ((COSName) b).getName();
        if (b instanceof COSString) return "s:" + ((COSString) b).toHexString();
        if (b instanceof COSBoolean) return "b:" + ((COSBoolean) b).getValue();
        if (b instanceof COSArray) {
            StringBuilder sb = new StringBuilder("[");
            for (COSBase x : (COSArray) b) sb.append(shallow(x)).append(' ');
            return sb.append(']').toString();
        }
        if (b instanceof COSDictionary) {
            List<String> parts = new ArrayList<String>();
            for (Map.Entry<COSName, COSBase> e : ((COSDictionary) b).entrySet()) {
                parts.add(e.getKey().getName() + "=" + shallow(e.getValue()));
            }
            Collections.sort(parts);
            return "<<" + parts + ">>";
        }
        return b.getClass().getSimpleName();
    }

    // ---------------------------------------------------------------- utilities

    /** Is the current version of the object written after the end of the certified revision? */
    private static boolean changedAfter(Long offset, Map<COSObjectKey, Long> xref, long end) {
        if (offset == null) return false;
        long effective = offset;
        if (offset < 0) { // compressed object: the value is -(object stream number)
            Long streamOffset = xref.get(new COSObjectKey(-offset, 0));
            if (streamOffset == null) return true;
            effective = streamOffset;
        }
        return effective >= end;
    }

    private static COSBase object(COSDocument cos, COSObjectKey k) {
        COSObject o = cos.getObjectFromPool(k);
        return o == null ? null : o.getObject();
    }

    private static COSObjectKey key(COSBase b) {
        return b instanceof COSObject ? b.getKey() : null;
    }

    private static COSBase resolve(COSBase b) {
        return b instanceof COSObject ? ((COSObject) b).getObject() : b;
    }

    private static void add(Set<COSObjectKey> set, COSObjectKey k) {
        if (k != null) set.add(k);
    }

    /** Every indirect object reachable from {@code b}. */
    private static void reach(COSBase b, Set<COSObjectKey> out) {
        reach(b, out, 0);
    }

    private static void reach(COSBase b, Set<COSObjectKey> out, int depth) {
        if (b == null || depth > 64) return;
        if (b instanceof COSObject) {
            COSObjectKey k = b.getKey();
            if (k == null || !out.add(k)) return;
            reach(((COSObject) b).getObject(), out, depth + 1);
        } else if (b instanceof COSDictionary) {
            for (COSBase v : ((COSDictionary) b).getValues()) reach(v, out, depth + 1);
        } else if (b instanceof COSArray) {
            for (COSBase v : (COSArray) b) reach(v, out, depth + 1);
        }
    }

    private static Set<String> set(String... names) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(names)));
    }
}
