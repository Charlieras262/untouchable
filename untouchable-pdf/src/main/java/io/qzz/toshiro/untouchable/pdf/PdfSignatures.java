package io.qzz.toshiro.untouchable.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.util.Selector;

import io.qzz.toshiro.untouchable.Region;
import io.qzz.toshiro.untouchable.SignatureInfo;

/** Inventory of the document's signatures and cryptographic (not legal) verification of each one. */
final class PdfSignatures {

    static final class Sig {
        final String field;
        final Integer docMdpPermission;
        final SignatureInfo.Integrity integrity;
        final boolean coversWholeDocument;
        final Integer page;
        final Region region;
        final String signer, date, subFilter;
        /** SHA-256 of the signer's certificate (DER), or null if it could not be read. */
        final String signerCertSha256;
        /** {start, end} of the bytes the signature covers (end = end of its revision), or null. */
        final long[] coveredRevision;

        Sig(String field, Integer docMdpPermission, SignatureInfo.Integrity integrity, boolean coversWholeDocument,
            Integer page, Region region, String signer, String date, String subFilter, String signerCertSha256,
            long[] coveredRevision) {
            this.field = field; this.docMdpPermission = docMdpPermission; this.integrity = integrity;
            this.coversWholeDocument = coversWholeDocument; this.page = page; this.region = region;
            this.signer = signer; this.date = date; this.subFilter = subFilter;
            this.signerCertSha256 = signerCertSha256; this.coveredRevision = coveredRevision;
        }

        SignatureInfo toInfo(boolean addedAfterSeal) {
            return new SignatureInfo(field, signer, date, subFilter, integrity, coversWholeDocument,
                    docMdpPermission != null, addedAfterSeal, page, region);
        }
    }

    private PdfSignatures() {}

    /** Only signature fields that hold a signature (empty ones are ignored). */
    static List<Sig> list(PDDocument doc, byte[] file) throws IOException {
        List<Sig> out = new ArrayList<Sig>();
        for (PDSignatureField f : doc.getSignatureFields()) {
            PDSignature s = f.getSignature();
            if (s == null) continue;
            int[] br = s.getByteRange();
            boolean whole = br != null && br.length == 4 && (long) br[2] + br[3] == file.length;
            Integer page = null;
            Region region = null;
            if (!f.getWidgets().isEmpty()) {
                PDAnnotationWidget w = f.getWidgets().get(0);
                PDRectangle r = w.getRectangle();
                if (r != null && r.getWidth() > 0 && r.getHeight() > 0) {
                    int p = PdfCanon.pageOf(doc, w);
                    if (p >= 0) page = p + 1;
                    region = new Region(r.getLowerLeftX(), r.getLowerLeftY(), r.getWidth(), r.getHeight());
                }
            }
            String date = s.getSignDate() == null ? null : s.getSignDate().toInstant().toString();
            String[] certHash = new String[1];
            SignatureInfo.Integrity integrity = verify(s, file, certHash);
            long[] covered = br != null && br.length == 4 && br[0] == 0 ? new long[] {0, (long) br[2] + br[3]} : null;
            out.add(new Sig(f.getFullyQualifiedName(), DocMdp.permission(s), integrity, whole, page, region,
                    s.getName(), date, s.getSubFilter(), certHash[0], covered));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static SignatureInfo.Integrity verify(PDSignature s, byte[] file, String[] certHashOut) {
        String sf = s.getSubFilter();
        if (!"adbe.pkcs7.detached".equals(sf) && !"ETSI.CAdES.detached".equals(sf)) return SignatureInfo.Integrity.UNKNOWN;
        try {
            CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(s.getSignedContent(file)), s.getContents());
            for (SignerInformation si : cms.getSignerInfos().getSigners()) {
                Collection<X509CertificateHolder> certs =
                        cms.getCertificates().getMatches((Selector<X509CertificateHolder>) si.getSID());
                if (certs.isEmpty()) return SignatureInfo.Integrity.UNKNOWN;
                if (certHashOut[0] == null) certHashOut[0] = Hashes.sha256(certs.iterator().next().getEncoded());
                if (!si.verify(new JcaSimpleSignerInfoVerifierBuilder().build(certs.iterator().next()))) {
                    return SignatureInfo.Integrity.INVALID;
                }
            }
            return SignatureInfo.Integrity.VALID;
        } catch (Exception e) {
            return SignatureInfo.Integrity.INVALID;
        }
    }
}
