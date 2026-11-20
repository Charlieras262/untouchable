package io.qzz.toshiro.untouchable.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;

import io.qzz.toshiro.untouchable.ChangePolicy;
import io.qzz.toshiro.untouchable.SealOptions;
import io.qzz.toshiro.untouchable.UntouchableException;
import io.qzz.toshiro.untouchable.VerifyOptions;
import io.qzz.toshiro.untouchable.spi.Comparison;
import io.qzz.toshiro.untouchable.spi.EmbeddedSeal;
import io.qzz.toshiro.untouchable.spi.FormatHandler;
import io.qzz.toshiro.untouchable.spi.PreparedDocument;
import io.qzz.toshiro.untouchable.spi.SealPayload;

/**
 * PDF handler (phase F1). It registers itself through ServiceLoader when {@code untouchable-pdf} is on the classpath.
 * <ul>
 *   <li>Fingerprint: pdf-canon/1 (SPEC §9.1).</li>
 *   <li>Seal: XMP + a {@code /Untouchable} catalog entry.</li>
 *   <li>Optional: declared signature fields (FR-12) and DocMDP certification (FR-5) — see {@link PdfSealOptions}.</li>
 *   <li>Already signed documents: sealed with an incremental save so their signatures are not invalidated.</li>
 * </ul>
 */
public final class PdfFormatHandler implements FormatHandler {

    @Override
    public String format() {
        return "pdf";
    }

    @Override
    public String canonVersion() {
        return PdfCanon.VERSION;
    }

    @Override
    public boolean supports(byte[] document) {
        int n = Math.min(document.length, 1024);
        return new String(document, 0, n, StandardCharsets.ISO_8859_1).contains("%PDF-");
    }

    // ---------------------------------------------------------------- sealing

    @Override
    public PreparedDocument prepare(byte[] document, SealOptions options) throws IOException {
        PdfSealOptions po = options.formatOption(PdfSealOptions.class);
        if (po == null) po = PdfSealOptions.builder().build();
        ChangePolicy policy = options.policy();

        byte[] bytes = document;
        List<String> preexisting;
        PDDocument doc = load(document);
        try {
            preexisting = signedFields(doc);
            if (po.certify()) {
                if (!preexisting.isEmpty()) {
                    throw new UntouchableException("Cannot certify a PDF that already has signatures: DocMDP must be the first one");
                }
                if (policy.allowsSignatures() && po.signatureFields().isEmpty()) {
                    throw new UntouchableException("With DocMDP certification and the " + policy.id()
                            + " policy, signature fields must be declared: Acrobat does not allow creating new fields in a certified document");
                }
                if (!policy.allowsSignatures() && !po.signatureFields().isEmpty()) {
                    throw new UntouchableException("The " + policy.id()
                            + " policy certifies with DocMDP P=1, which blocks signing: declaring signature fields makes no sense");
                }
            }
            if (!po.signatureFields().isEmpty()) {
                SignatureFields.add(doc, po.signatureFields());
                bytes = save(doc, !preexisting.isEmpty());
            }
        } finally {
            doc.close();
        }

        Map<String, Object> fingerprint;
        doc = load(bytes);
        try {
            fingerprint = PdfCanon.compute(doc, po.storeTiles()).fingerprint.toMap();
        } finally {
            doc.close();
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        if (!po.signatureFields().isEmpty()) {
            List<Object> fields = new ArrayList<Object>();
            for (SignatureFieldSpec f : po.signatureFields()) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("name", f.name());
                m.put("page", (long) f.page());
                List<Object> rect = new ArrayList<Object>();
                rect.add(Hashes.num(f.rect().x()));
                rect.add(Hashes.num(f.rect().y()));
                rect.add(Hashes.num(f.rect().width()));
                rect.add(Hashes.num(f.rect().height()));
                m.put("rect", rect);
                fields.add(m);
            }
            data.put("signatureFields", fields);
        }
        if (po.certify()) {
            data.put("certified", Boolean.TRUE);
            data.put("docMdp", (long) docMdpPermission(policy));
            // Binds the certification to the issuer: the fast path only trusts a certification made with this
            // certificate. Without it, anyone could modify the content and certify it with their own.
            try {
                data.put("certificationCert", Hashes.sha256(po.certificationChain().get(0).getEncoded()));
            } catch (java.security.cert.CertificateEncodingException e) {
                throw new UntouchableException("Invalid certification certificate", e);
            }
        }
        if (!preexisting.isEmpty()) data.put("preexistingSignatures", new ArrayList<Object>(preexisting));
        return new PreparedDocument(bytes, fingerprint, data);
    }

    @Override
    public byte[] embed(PreparedDocument prepared, EmbeddedSeal seal, SealOptions options) throws IOException {
        PdfSealOptions po = options.formatOption(PdfSealOptions.class);
        byte[] out;
        PDDocument doc = load(prepared.document());
        try {
            boolean signed = !signedFields(doc).isEmpty();
            SealStore.write(doc, seal);
            out = save(doc, signed);
        } finally {
            doc.close();
        }
        if (po != null && po.certify()) {
            out = DocMdp.certify(out, po.certificationKey(), po.certificationChain(), docMdpPermission(options.policy()));
        }
        return out;
    }

    private static int docMdpPermission(ChangePolicy policy) {
        return policy.allowsSignatures() ? 2 : 1;
    }

    // ---------------------------------------------------------------- verification

    @Override
    public EmbeddedSeal extract(byte[] document) throws IOException {
        PDDocument doc = load(document);
        try {
            return SealStore.read(doc);
        } finally {
            doc.close();
        }
    }

    @Override
    public Comparison compare(byte[] document, SealPayload payload, VerifyOptions options) throws IOException {
        PDDocument doc = load(document);
        try {
            return PdfComparator.compare(doc, document, payload, options);
        } finally {
            doc.close();
        }
    }

    // ---------------------------------------------------------------- utilities

    static PDDocument load(byte[] bytes) throws IOException {
        PDDocument doc = Loader.loadPDF(bytes);
        if (doc.isEncrypted()) {
            doc.close();
            throw new IOException("Encrypted PDF: not supported");
        }
        return doc;
    }

    /** Full save, or incremental if the document already has signatures (so they are not invalidated). */
    private static byte[] save(PDDocument doc, boolean incremental) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (incremental) doc.saveIncremental(bos);
        else doc.save(bos);
        return bos.toByteArray();
    }

    private static List<String> signedFields(PDDocument doc) throws IOException {
        List<String> out = new ArrayList<String>();
        for (PDSignatureField f : doc.getSignatureFields()) if (f.getSignature() != null) out.add(f.getFullyQualifiedName());
        return out;
    }
}
