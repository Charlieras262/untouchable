package io.qzz.toshiro.untouchable.pdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import io.qzz.toshiro.untouchable.ChangePolicy;
import io.qzz.toshiro.untouchable.Finding;
import io.qzz.toshiro.untouchable.FindingType;
import io.qzz.toshiro.untouchable.Policies;
import io.qzz.toshiro.untouchable.Region;
import io.qzz.toshiro.untouchable.SealOptions;
import io.qzz.toshiro.untouchable.Status;
import io.qzz.toshiro.untouchable.Untouchable;
import io.qzz.toshiro.untouchable.VerificationMethod;
import io.qzz.toshiro.untouchable.VerificationResult;
import io.qzz.toshiro.untouchable.VerifyOptions;
import io.qzz.toshiro.untouchable.crypto.Ed25519;
import io.qzz.toshiro.untouchable.crypto.KeyResolver;

/** Certified-mode fast path (verification by revisions) and its fallback cases. */
class CertifiedRevisionsTest {
    private static final KeyPair KEYS = Ed25519.generateKeyPair();
    private static final Untouchable U = Untouchable.builder()
            .signer(Ed25519.signer(KEYS.getPrivate(), "k1"))
            .keyResolver(KeyResolver.of("k1", Ed25519.verifier(KEYS.getPublic())))
            .build();

    private static byte[] certified(byte[] pdf, ChangePolicy policy) {
        return U.seal(pdf, SealOptions.builder().policy(policy)
                .formatOption(PdfSealOptions.builder()
                        .signatureField("Signature1", 1, new Region(50, 40, 200, 60))
                        .signatureField("Signature2", 1, new Region(300, 40, 200, 60))
                        .certifyWith(TestSigning.KEYS.getPrivate(), TestSigning.CERT)
                        .build())
                .build());
    }

    private static Finding first(VerificationResult r, FindingType type) {
        for (Finding f : r.findings()) if (f.type() == type) return f;
        return null;
    }

    /** Reason why the fast path does not apply, or null if it does. */
    private static String fastPathRejection(byte[] pdf) throws Exception {
        PDDocument doc = Loader.loadPDF(pdf);
        try {
            Map<String, Object> formatData = io.qzz.toshiro.untouchable.spi.SealPayload
                    .parse(SealStore.read(doc).payload()).formatData();
            return CertifiedRevisions.check(doc, pdf, formatData, PdfSignatures.list(doc, pdf)).reason;
        } finally {
            doc.close();
        }
    }

    // ---------------------------------------------------------------- accepted by the fast path

    @Test
    void untouchedCertifiedDocumentUsesFastPath() throws Exception {
        VerificationResult r = U.verify(certified(TestPdfs.text(1), Policies.SIGNATURES_ONLY));
        assertEquals(Status.INTACT, r.status(), r.toString());
        assertEquals(VerificationMethod.CERTIFIED_REVISIONS, r.method());
    }

    @Test
    void signaturesInDeclaredFieldsUseFastPath() throws Exception {
        byte[] sealed = certified(TestPdfs.text(2), Policies.SIGNATURES_ONLY);
        byte[] one = TestSigning.signField(sealed, "Signature1");
        assertNull(fastPathRejection(one));
        VerificationResult r1 = U.verify(one);
        assertEquals(Status.INTACT_WITH_ALLOWED_CHANGES, r1.status(), r1.toString());
        assertEquals(VerificationMethod.CERTIFIED_REVISIONS, r1.method());
        assertNotNull(first(r1, FindingType.SIGNATURE_ADDED));

        byte[] two = TestSigning.signField(one, "Signature2");
        assertArrayEquals(one, Arrays.copyOf(two, one.length));
        VerificationResult r2 = U.verify(two);
        assertEquals(VerificationMethod.CERTIFIED_REVISIONS, r2.method(), String.valueOf(fastPathRejection(two)));
        assertEquals(Status.INTACT_WITH_ALLOWED_CHANGES, r2.status());
        assertEquals(3, r2.signatures().size());
        assertFalse(r2.hasWarnings(), r2.toString());
    }

    @Test
    void fastPathAndContentAgreeOnTheVerdict() throws Exception {
        byte[] signed = TestSigning.signField(certified(TestPdfs.text(1), Policies.SIGNATURES_ONLY), "Signature1");
        VerifyOptions content = VerifyOptions.builder().formatOption(PdfVerifyOptions.builder().fastPath(false).build()).build();
        VerificationResult slow = U.verify(signed, content), fast = U.verify(signed);
        assertEquals(VerificationMethod.CONTENT, slow.method());
        assertEquals(VerificationMethod.CERTIFIED_REVISIONS, fast.method());
        assertEquals(slow.status(), fast.status());
        assertEquals(slow.findings().size(), fast.findings().size());
    }

    // ---------------------------------------------------------------- rejected: verified by content

    @Test
    void incrementalTextChangeFallsBackAndIsDetected() throws Exception {
        byte[] sealed = certified(TestPdfs.text(1), Policies.SIGNATURES_ONLY);
        byte[] tampered = TestPdfs.addTextIncremental(sealed, "puppies");
        assertArrayEquals(sealed, Arrays.copyOf(tampered, sealed.length), "the modification must be incremental");
        System.out.println("texto agregado → " + fastPathRejection(tampered));
        assertNotNull(fastPathRejection(tampered));
        VerificationResult r = U.verify(tampered);
        assertEquals(VerificationMethod.CONTENT, r.method());
        assertEquals(Status.MODIFIED, r.status());
        assertNotNull(first(r, FindingType.TEXT_CHANGED), r.toString());
    }

    @Test
    void incrementalCommentFallsBackAndIsDetected() throws Exception {
        byte[] sealed = certified(TestPdfs.text(1), Policies.SIGNATURES_ONLY);
        byte[] tampered = TestPdfs.addCommentIncremental(sealed);
        assertArrayEquals(sealed, Arrays.copyOf(tampered, sealed.length), "the modification must be incremental");
        System.out.println("comentario → " + fastPathRejection(tampered));
        assertNotNull(fastPathRejection(tampered));
        VerificationResult r = U.verify(tampered);
        assertEquals(VerificationMethod.CONTENT, r.method());
        assertEquals(Status.MODIFIED, r.status());
        assertNotNull(first(r, FindingType.ANNOTATION_ADDED), r.toString());
    }

    @Test
    void incrementalFormFillFallsBackAndIsDetected() throws Exception {
        byte[] sealed = certified(TestPdfs.form(), Policies.SIGNATURES_ONLY);
        byte[] tampered = TestPdfs.fillIncremental(sealed, "name", "John");
        assertArrayEquals(sealed, Arrays.copyOf(tampered, sealed.length), "the modification must be incremental");
        System.out.println("formulario → " + fastPathRejection(tampered));
        assertNotNull(fastPathRejection(tampered));
        VerificationResult r = U.verify(tampered);
        assertEquals(VerificationMethod.CONTENT, r.method());
        assertEquals(Status.MODIFIED, r.status());
        assertNotNull(first(r, FindingType.FORM_FIELD_CHANGED), r.toString());
    }

    @Test
    void certificationByAnotherCertificateIsNotTrusted() throws Exception {
        byte[] sealed = certified(TestPdfs.text(1), Policies.SIGNATURES_ONLY);
        PDDocument doc = Loader.loadPDF(sealed);
        try {
            Map<String, Object> formatData = new LinkedHashMap<String, Object>(io.qzz.toshiro.untouchable.spi.SealPayload
                    .parse(SealStore.read(doc).payload()).formatData());
            formatData.put("certificationCert", "00" + ((String) formatData.get("certificationCert")).substring(2));
            CertifiedRevisions.Outcome o = CertifiedRevisions.check(doc, sealed, formatData, PdfSignatures.list(doc, sealed));
            assertFalse(o.ok());
            assertTrue(o.reason.contains("certificate"), o.reason);
        } finally {
            doc.close();
        }
    }

    @Test
    void attackerCannotCertifyAModifiedUncertifiedDocument() throws Exception {
        // Sealed without certification; the attacker changes the text and certifies with their own certificate.
        byte[] sealed = U.seal(TestPdfs.text(1), SealOptions.builder().policy(Policies.SIGNATURES_ONLY).build());
        byte[] attacked = DocMdp.certify(TestPdfs.addText(sealed, 0, "puppies"),
                TestSigning.KEYS.getPrivate(), java.util.Collections.singletonList(TestSigning.CERT), 2);
        VerificationResult r = U.verify(attacked);
        assertEquals(VerificationMethod.CONTENT, r.method());
        assertEquals(Status.MODIFIED, r.status());
    }

    @Test
    void rewrittenCertifiedDocumentFallsBack() throws Exception {
        byte[] signed = TestSigning.signField(certified(TestPdfs.text(1), Policies.SIGNATURES_ONLY), "Signature1");
        VerificationResult r = U.verify(TestPdfs.rewrite(signed));
        assertEquals(VerificationMethod.CONTENT, r.method());
        assertTrue(r.isTrusted(), r.toString());
        assertNotNull(first(r, FindingType.CERTIFICATION_INVALID));
    }
}
