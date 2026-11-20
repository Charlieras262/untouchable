package io.qzz.toshiro.untouchable.pdf;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.pdfbox.contentstream.PDContentStream;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;
import org.apache.pdfbox.text.PDFTextStripper;

import io.qzz.toshiro.untouchable.Region;

/**
 * Canonical content of a PDF, version pdf-canon/1 (SPEC §9.1). Per page: geometry, text, images
 * (decoded pixels), normalized operators and non-signature annotations. Per document: values of the
 * non-signature form fields. Signature fields and widgets are not part of the fingerprint.
 */
final class PdfCanon {
    static final String VERSION = "pdf-canon/1";

    /** Annotation of the current document, with its position (to locate findings). */
    static final class AnnotRef {
        final String key, subtype;
        final Region rect;

        AnnotRef(String key, String subtype, Region rect) {
            this.key = key; this.subtype = subtype; this.rect = rect;
        }
    }

    static final class Result {
        final PdfFingerprint fingerprint;
        /** Annotations per page (0-based index). */
        final List<List<AnnotRef>> annots;
        /** Form field → page (1-based) of its first widget. */
        final Map<String, Integer> fieldPages;

        Result(PdfFingerprint fingerprint, List<List<AnnotRef>> annots, Map<String, Integer> fieldPages) {
            this.fingerprint = fingerprint; this.annots = annots; this.fieldPages = fieldPages;
        }
    }

    private final Map<COSBase, String> imageCache = new IdentityHashMap<COSBase, String>();

    private PdfCanon() {}

    static Result compute(PDDocument doc, boolean withTiles) throws IOException {
        return new PdfCanon().run(doc, withTiles);
    }

    private Result run(PDDocument doc, boolean withTiles) throws IOException {
        List<PdfFingerprint.Page> pages = new ArrayList<PdfFingerprint.Page>();
        List<List<AnnotRef>> annotRefs = new ArrayList<List<AnnotRef>>();
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        PdfTiles tiles = withTiles ? new PdfTiles(doc) : null;

        int n = doc.getNumberOfPages();
        for (int i = 0; i < n; i++) {
            PDPage page = doc.getPage(i);
            stripper.setStartPage(i + 1);
            stripper.setEndPage(i + 1);
            String text = Normalizer.normalize(stripper.getText(doc), Normalizer.Form.NFC).replaceAll("\\s+", " ").trim();

            List<String> images = new ArrayList<String>();
            String ops = opsHash(page, page.getResources(), newIdentitySet(), images);
            Collections.sort(images);

            List<AnnotRef> refs = new ArrayList<AnnotRef>();
            List<String> keys = new ArrayList<String>();
            for (PDAnnotation a : page.getAnnotations()) {
                if (isSignatureWidget(a)) continue;
                AnnotRef ref = describe(a);
                refs.add(ref);
                keys.add(ref.key);
            }
            Collections.sort(keys);
            annotRefs.add(refs);

            String tileHashes = null;
            if (tiles != null) {
                BufferedImage img = tiles.render(i);
                tileHashes = PdfTiles.tiles(img);
            }
            pages.add(new PdfFingerprint.Page(Hashes.sha256(geom(page)), Hashes.sha256(text),
                    Hashes.sha256(join(",", images)), ops, keys, tileHashes));
        }

        Map<String, String> forms = new TreeMap<String, String>();
        Map<String, Integer> fieldPages = new TreeMap<String, Integer>();
        PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
        if (form != null) {
            for (PDField f : form.getFieldTree()) {
                if (f instanceof PDTerminalField && !(f instanceof PDSignatureField)) {
                    String value = f.getValueAsString();
                    forms.put(f.getFullyQualifiedName(), Hashes.sha256(value == null ? "" : value));
                    List<PDAnnotationWidget> ws = ((PDTerminalField) f).getWidgets();
                    if (!ws.isEmpty()) {
                        int p = pageOf(doc, ws.get(0));
                        if (p >= 0) fieldPages.put(f.getFullyQualifiedName(), p + 1);
                    }
                }
            }
        }
        return new Result(new PdfFingerprint(pages, forms, withTiles ? PdfTiles.PROFILE : null), annotRefs, fieldPages);
    }

    // ---------------------------------------------------------------- components

    private static String geom(PDPage page) {
        return rect(page.getMediaBox()) + ";" + rect(page.getCropBox()) + ";rot="
                + (((page.getRotation() % 360) + 360) % 360) + ";uu=" + Hashes.num(page.getUserUnit());
    }

    private static String rect(PDRectangle r) {
        return "[" + Hashes.num(r.getLowerLeftX()) + "," + Hashes.num(r.getLowerLeftY()) + ","
                + Hashes.num(r.getUpperRightX()) + "," + Hashes.num(r.getUpperRightY()) + "]";
    }

    static boolean isSignatureWidget(PDAnnotation a) {
        if (!(a instanceof PDAnnotationWidget)) return false;
        COSDictionary d = a.getCOSObject();
        COSName ft = d.getCOSName(COSName.FT);
        if (ft == null) {
            COSDictionary parent = d.getCOSDictionary(COSName.PARENT);
            if (parent != null) ft = parent.getCOSName(COSName.FT);
        }
        return COSName.SIG.equals(ft);
    }

    private static String fieldName(PDAnnotation a) {
        COSDictionary d = a.getCOSObject();
        String t = d.getString(COSName.T);
        if (t == null) {
            COSDictionary parent = d.getCOSDictionary(COSName.PARENT);
            if (parent != null) t = parent.getString(COSName.T);
        }
        return t;
    }

    private static AnnotRef describe(PDAnnotation a) {
        PDRectangle r = a.getRectangle();
        String subtype = a.getSubtype() == null ? "?" : a.getSubtype();
        String rect = r == null ? "[]" : rect(r);
        String extra = a instanceof PDAnnotationWidget ? "field=" + fieldName(a) : "contents=" + a.getContents();
        String key = subtype + ":" + Hashes.sha256(subtype + "@" + rect + ";" + extra).substring(0, 16);
        Region region = r == null ? null : new Region(r.getLowerLeftX(), r.getLowerLeftY(), r.getWidth(), r.getHeight());
        return new AnnotRef(key, subtype, region);
    }

    static int pageOf(PDDocument doc, PDAnnotation a) throws IOException {
        if (a.getPage() != null) {
            int idx = doc.getPages().indexOf(a.getPage());
            if (idx >= 0) return idx;
        }
        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            for (PDAnnotation x : doc.getPage(i).getAnnotations()) {
                if (x.getCOSObject() == a.getCOSObject()) return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- operators

    private String opsHash(PDContentStream cs, PDResources res, Set<COSBase> visiting, List<String> imagesOut)
            throws IOException {
        List<Object> tokens = new PDFStreamParser(cs).parse();
        StringBuilder sb = new StringBuilder();
        List<COSBase> operands = new ArrayList<COSBase>();
        for (Object t : tokens) {
            if (t instanceof Operator) {
                Operator op = (Operator) t;
                String name = op.getName();
                sb.append(name).append('(');
                for (COSBase o : operands) {
                    if (o instanceof COSName) sb.append(resolveName(name, (COSName) o, res, visiting, imagesOut));
                    else sb.append(cos(o, 0));
                    sb.append(',');
                }
                if ("BI".equals(name) && op.getImageData() != null) {
                    String h = "inline:" + Hashes.sha256(op.getImageData()) + ":" + cos(op.getImageParameters(), 0);
                    imagesOut.add(Hashes.sha256(h));
                    sb.append(h);
                }
                sb.append(")\n");
                operands.clear();
            } else if (t instanceof COSBase) {
                operands.add((COSBase) t);
            }
        }
        return Hashes.sha256(sb);
    }

    private String resolveName(String op, COSName n, PDResources res, Set<COSBase> visiting, List<String> imagesOut)
            throws IOException {
        if (res == null) return "/" + n.getName();
        if ("Tf".equals(op)) {
            PDFont f = res.getFont(n);
            if (f == null) return "font:?";
            return "font:" + (f.getName() == null ? "?" : f.getName().replaceFirst("^[A-Z]{6}\\+", ""));
        }
        if ("Do".equals(op)) {
            PDXObject x = res.getXObject(n);
            if (x instanceof PDImageXObject) {
                String h = pixelHash((PDImageXObject) x);
                imagesOut.add(h);
                return "img:" + h;
            }
            if (x instanceof PDFormXObject) {
                PDFormXObject form = (PDFormXObject) x;
                COSBase key = form.getCOSObject();
                if (!visiting.add(key)) return "form:cycle";
                try {
                    PDResources fr = form.getResources() != null ? form.getResources() : res;
                    String bbox = form.getBBox() == null ? "" : rect(form.getBBox());
                    return "form:" + bbox + cos(form.getCOSObject().getDictionaryObject(COSName.MATRIX), 0)
                            + opsHash(form, fr, visiting, imagesOut);
                } finally {
                    visiting.remove(key);
                }
            }
            return "xobj:?";
        }
        COSName category = category(op);
        if (category != null) {
            COSDictionary cat = res.getCOSObject().getCOSDictionary(category);
            if (cat != null && cat.containsKey(n)) return op + ":" + cos(cat.getDictionaryObject(n), 0);
        }
        return "/" + n.getName();
    }

    private static COSName category(String op) {
        if ("gs".equals(op)) return COSName.EXT_G_STATE;
        if ("cs".equals(op) || "CS".equals(op)) return COSName.COLORSPACE;
        if ("sh".equals(op)) return COSName.SHADING;
        if ("scn".equals(op) || "SCN".equals(op)) return COSName.PATTERN;
        if ("BDC".equals(op) || "DP".equals(op)) return COSName.PROPERTIES;
        return null;
    }

    private String pixelHash(PDImageXObject img) throws IOException {
        String cached = imageCache.get(img.getCOSObject());
        if (cached != null) return cached;
        BufferedImage bi = img.getImage();
        int w = bi.getWidth(), h = bi.getHeight();
        String hash = Hashes.sha256(bi.getRGB(0, 0, w, h, null, 0, w), w, h);
        imageCache.put(img.getCOSObject(), hash);
        return hash;
    }

    /** Deterministic serialization of a COS object (sorted keys, streams by hash of their decoded content). */
    private static String cos(COSBase b, int depth) throws IOException {
        if (b instanceof COSObject) b = ((COSObject) b).getObject();
        if (b == null || b instanceof COSNull) return "null";
        if (depth > 6) return "…";
        if (b instanceof COSNumber) return Hashes.num(((COSNumber) b).floatValue());
        if (b instanceof COSName) return "/" + ((COSName) b).getName();
        if (b instanceof COSString) return "<" + ((COSString) b).toHexString() + ">";
        if (b instanceof COSBoolean) return String.valueOf(((COSBoolean) b).getValue());
        if (b instanceof COSArray) {
            COSArray a = (COSArray) b;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < a.size(); i++) sb.append(cos(a.get(i), depth + 1)).append(' ');
            return sb.append(']').toString();
        }
        if (b instanceof COSDictionary) {
            COSDictionary d = (COSDictionary) b;
            TreeMap<String, COSBase> sorted = new TreeMap<String, COSBase>();
            for (COSName k : d.keySet()) {
                if (k.equals(COSName.PARENT) || k.equals(COSName.LENGTH) || k.equals(COSName.FILTER)
                        || k.equals(COSName.DECODE_PARMS)) continue;
                sorted.put(k.getName(), d.getItem(k));
            }
            StringBuilder sb = new StringBuilder("<<");
            for (Map.Entry<String, COSBase> e : sorted.entrySet()) {
                sb.append('/').append(e.getKey()).append(' ').append(cos(e.getValue(), depth + 1)).append(' ');
            }
            sb.append(">>");
            if (b instanceof COSStream) {
                InputStream in = null;
                try {
                    in = ((COSStream) b).createInputStream();
                    sb.append("stream:").append(Hashes.sha256(Hashes.readAll(in)));
                } catch (IOException e) {
                    sb.append("stream:error");
                } finally {
                    if (in != null) in.close();
                }
            }
            return sb.toString();
        }
        return b.getClass().getSimpleName();
    }

    private static Set<COSBase> newIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<COSBase, Boolean>());
    }

    private static String join(String sep, List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}
