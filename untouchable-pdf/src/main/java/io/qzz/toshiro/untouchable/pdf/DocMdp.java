package io.qzz.toshiro.untouchable.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.List;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import io.qzz.toshiro.untouchable.UntouchableException;

/** Invisible DocMDP certification signature, in an incremental revision (SPEC FR-5). */
final class DocMdp {
    private static final COSName REFERENCE = COSName.getPDFName("Reference");
    private static final COSName TRANSFORM_METHOD = COSName.getPDFName("TransformMethod");
    private static final COSName TRANSFORM_PARAMS = COSName.getPDFName("TransformParams");

    private DocMdp() {}

    static byte[] certify(byte[] pdf, final PrivateKey key, final List<X509Certificate> chain, int permission)
            throws IOException {
        PDDocument doc = PdfFormatHandler.load(pdf);
        try {
            PDSignature sig = new PDSignature();
            sig.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            sig.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            sig.setReason("Document sealed with Untouchable (DocMDP P=" + permission + ")");
            sig.setSignDate(Calendar.getInstance());
            setPermission(doc, sig, permission);

            SignatureInterface signer = new SignatureInterface() {
                @Override
                public byte[] sign(InputStream content) throws IOException {
                    try {
                        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
                        ContentSigner cs = new JcaContentSignerBuilder(algorithm(key)).build(key);
                        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                                new JcaDigestCalculatorProviderBuilder().build()).build(cs, chain.get(0)));
                        gen.addCertificates(new JcaCertStore(chain));
                        return gen.generate(new CMSProcessableByteArray(Hashes.readAll(content)), false).getEncoded();
                    } catch (UntouchableException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new IOException("Could not generate the certification signature", e);
                    }
                }
            };
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            SignatureOptions opts = new SignatureOptions();
            try {
                opts.setPreferredSignatureSize(SignatureOptions.DEFAULT_SIGNATURE_SIZE * 2);
                doc.addSignature(sig, signer, opts);
                doc.saveIncremental(out);
            } finally {
                opts.close();
            }
            return out.toByteArray();
        } finally {
            doc.close();
        }
    }

    private static String algorithm(PrivateKey key) {
        String a = key.getAlgorithm();
        if ("RSA".equalsIgnoreCase(a)) return "SHA256withRSA";
        if ("EC".equalsIgnoreCase(a) || "ECDSA".equalsIgnoreCase(a)) return "SHA256withECDSA";
        throw new UntouchableException("Key type not supported for certification: " + a + " (use RSA or EC)");
    }

    /** DocMDP dictionaries (ISO 32000, 12.8.2.2), like SigUtils.setMDPPermission in the PDFBox examples. */
    private static void setPermission(PDDocument doc, PDSignature signature, int p) {
        COSDictionary transform = new COSDictionary();
        transform.setItem(COSName.TYPE, TRANSFORM_PARAMS);
        transform.setInt(COSName.P, p);
        transform.setName(COSName.V, "1.2");
        transform.setNeedToBeUpdated(true);

        COSDictionary ref = new COSDictionary();
        ref.setItem(COSName.TYPE, COSName.getPDFName("SigRef"));
        ref.setItem(TRANSFORM_METHOD, COSName.DOCMDP);
        ref.setItem(COSName.getPDFName("DigestMethod"), COSName.getPDFName("SHA1"));
        ref.setItem(TRANSFORM_PARAMS, transform);
        ref.setNeedToBeUpdated(true);

        COSArray refs = new COSArray();
        refs.add(ref);
        refs.setNeedToBeUpdated(true);
        signature.getCOSObject().setItem(REFERENCE, refs);

        COSDictionary catalog = doc.getDocumentCatalog().getCOSObject();
        COSDictionary perms = new COSDictionary();
        perms.setItem(COSName.DOCMDP, signature);
        perms.setNeedToBeUpdated(true);
        catalog.setItem(COSName.PERMS, perms);
        catalog.setNeedToBeUpdated(true);
    }

    /** DocMDP P if the signature is a certification signature; otherwise null. */
    static Integer permission(PDSignature s) {
        COSArray refs = s.getCOSObject().getCOSArray(REFERENCE);
        if (refs == null) return null;
        for (int i = 0; i < refs.size(); i++) {
            if (refs.getObject(i) instanceof COSDictionary) {
                COSDictionary ref = (COSDictionary) refs.getObject(i);
                if (COSName.DOCMDP.equals(ref.getCOSName(TRANSFORM_METHOD))) {
                    COSDictionary tp = ref.getCOSDictionary(TRANSFORM_PARAMS);
                    return tp == null ? 2 : tp.getInt(COSName.P, 2);
                }
            }
        }
        return null;
    }
}
