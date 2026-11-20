package io.qzz.toshiro.untouchable.pdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import io.qzz.toshiro.untouchable.ChangePolicy;
import io.qzz.toshiro.untouchable.Finding;
import io.qzz.toshiro.untouchable.FindingType;
import io.qzz.toshiro.untouchable.Policies;
import io.qzz.toshiro.untouchable.Region;
import io.qzz.toshiro.untouchable.SealOptions;
import io.qzz.toshiro.untouchable.Severity;
import io.qzz.toshiro.untouchable.SignatureInfo;
import io.qzz.toshiro.untouchable.Status;
import io.qzz.toshiro.untouchable.Untouchable;
import io.qzz.toshiro.untouchable.UntouchableException;
import io.qzz.toshiro.untouchable.VerificationResult;
import io.qzz.toshiro.untouchable.crypto.Ed25519;
import io.qzz.toshiro.untouchable.crypto.KeyResolver;

class PdfFormatHandlerTest {
    private static final KeyPair KEYS = Ed25519.generateKeyPair();
    /** The PDF handler is discovered through ServiceLoader, as an application would use it. */
    private static final Untouchable U = Untouchable.builder()
            .signer(Ed25519.signer(KEYS.getPrivate(), "k1"))
            .keyResolver(KeyResolver.of("k1", Ed25519.verifier(KEYS.getPublic())))
            .build();

    private static SealOptions policy(ChangePolicy p) {
        return SealOptions.builder().policy(p).build();
    }

    private static List<FindingType> types(VerificationResult r) {
        List<FindingType> out = new ArrayList<FindingType>();
        for (Finding f : r.findings()) out.add(f.type());
        return out;
    }

    private static Finding first(VerificationResult r, FindingType type) {
        for (Finding f : r.findings()) if (f.type() == type) return f;
        return null;
    }

    // ---------------------------------------------------------------- basics

    @Test
    void discoversPdfHandler() {
        assertTrue(U.formats().contains("pdf"));
    }

    @Test
    void sealedPdfIsIntact() throws Exception {
        byte[] sealed = U.seal(TestPdfs.text(2), SealOptions.builder().docId("invoice-1").build());
        VerificationResult r = U.verify(sealed);
        assertEquals(Status.INTACT, r.status(), r.toString());
        assertEquals("invoice-1", r.seal().docId());
        assertEquals("pdf", r.seal().format());
    }

    @Test
    void sealSurvivesFullRewrite() throws Exception {
        byte[] sealed = U.seal(TestPdfs.text(2));
        assertEquals(Status.INTACT, U.verify(TestPdfs.rewrite(sealed)).status());
    }

    @Test
    void pdfWithoutSealHasNoSeal() throws Exception {
        assertEquals(Status.NO_SEAL, U.verify(TestPdfs.text(1)).status());
        assertEquals(Status.NO_SEAL, U.verify(TestPdfs.stripSeal(U.seal(TestPdfs.text(1)))).status());
    }

    @Test
    void forgedSealIsInvalid() throws Exception {
        byte[] sealed = U.seal(TestPdfs.text(1), SealOptions.builder().docId("kittens").build());
        assertEquals(Status.INVALID_SEAL, U.verify(TestPdfs.forgeCatalogPayload(sealed, "kittens", "puppies")).status());
    }

    @Test
    void notAPdfIsUnsupported() {
        assertEquals(Status.UNSUPPORTED, U.verify("hola".getBytes()).status());
        assertEquals(Status.UNSUPPORTED, U.verify("%PDF-1.7 roto".getBytes()).status());
    }

    // ---------------------------------------------------------------- content changes

    @Test
    void changedTextIsDetectedAndLocated() throws Exception {
        byte[] sealed = U.seal(TestPdfs.text(2), policy(Policies.SIGNATURES_ONLY));
        VerificationResult r = U.verify(TestPdfs.addText(sealed, 1, "puppies"));
        assertEquals(Status.MODIFIED, r.status());
        Finding f = first(r, FindingType.TEXT_CHANGED);
        assertNotNull(f, r.toString());
        assertEquals(Integer.valueOf(2), f.page());
        assertEquals(Severity.VIOLATION, f.severity());
        Region region = f.region();
        assertNotNull(region, "the change should be located with the tiles");
        assertTrue(region.x() <= 300 && region.x() + region.width() >= 300, region.toString());
        assertTrue(region.y() <= 305 && region.y() + region.height() >= 300, region.toString());
    }

    @Test
    void addedCommentIsDetected() throws Exception {
        byte[] sealed = U.seal(TestPdfs.text(1), policy(Policies.SIGNATURES_ONLY));
        VerificationResult r = U.verify(TestPdfs.addComment(sealed));
        assertEquals(Status.MODIFIED, r.status());
        Finding f = first(r, FindingType.ANNOTATION_ADDED);
        assertNotNull(f, r.toString());
        assertEquals(Integer.valueOf(1), f.page());
        assertNotNull(f.region());
    }

    @Test
    void removedAndAddedPagesAreDetected() throws Exception {
        byte[] sealed = U.seal(TestPdfs.text(3));
        VerificationResult removed = U.verify(TestPdfs.removePage(sealed, 1));
        assertEquals(Status.MODIFIED, removed.status());
        assertEquals(Arrays.asList(FindingType.PAGE_REMOVED), types(removed));

        VerificationResult added = U.verify(TestPdfs.addPage(sealed));
        assertEquals(Arrays.asList(FindingType.PAGE_ADDED), types(added));
        assertEquals(Integer.valueOf(4), added.findings().get(0).page());
    }

    @Test
    void formFillDependsOnPolicy() throws Exception {
        byte[] onlySignatures = U.seal(TestPdfs.form(), policy(Policies.SIGNATURES_ONLY));
        VerificationResult r = U.verify(TestPdfs.fill(onlySignatures, "name", "John"));
        assertEquals(Status.MODIFIED, r.status());
        assertEquals(Integer.valueOf(1), first(r, FindingType.FORM_FIELD_CHANGED).page());

        byte[] forms = U.seal(TestPdfs.form(), policy(Policies.SIGNATURES_AND_FORMS));
        VerificationResult ok = U.verify(TestPdfs.fill(forms, "name", "John"));
        assertEquals(Status.INTACT_WITH_ALLOWED_CHANGES, ok.status(), ok.toString());
    }

    // ---------------------------------------------------------------- signatures

    @Test
    void addedSignatureIsAllowedOnlyByPolicy() throws Exception {
        byte[] sealed = U.seal(TestPdfs.text(1), policy(Policies.SIGNATURES_ONLY));
        byte[] signed = TestSigning.signInvisible(sealed);
        VerificationResult r = U.verify(signed);
        assertEquals(Status.INTACT_WITH_ALLOWED_CHANGES, r.status(), r.toString());
        assertEquals(Severity.INFO, first(r, FindingType.SIGNATURE_ADDED).severity());
        assertEquals(1, r.signatures().size());
        SignatureInfo s = r.signatures().get(0);
        assertEquals(SignatureInfo.Integrity.VALID, s.integrity());
        assertTrue(s.isAddedAfterSeal());
        assertTrue(s.coversWholeDocument());

        byte[] strict = U.seal(TestPdfs.text(1));
        assertEquals(Status.MODIFIED, U.verify(TestSigning.signInvisible(strict)).status());
    }

    @Test
    void signatureOverContentIsWarned() throws Exception {
        byte[] sealed = U.seal(TestPdfs.text(1), policy(Policies.SIGNATURES_ONLY));
        byte[] covering = TestSigning.signNewVisibleField(sealed, "Covering", new PDRectangle(40, 700, 300, 90));
        VerificationResult r = U.verify(covering);
        assertEquals(Status.INTACT_WITH_ALLOWED_CHANGES, r.status());
        assertTrue(r.hasWarnings(), r.toString() + " " + r.signatures());
        assertNotNull(first(r, FindingType.SIGNATURE_OVERLAPS_CONTENT), r.toString());

        byte[] clean = TestSigning.signNewVisibleField(sealed, "Below", new PDRectangle(300, 40, 200, 60));
        assertFalse(U.verify(clean).hasWarnings(), U.verify(clean).toString());
    }

    @Test
    void declaredFieldsCanBeSignedAndOthersAreWarned() throws Exception {
        SealOptions opts = SealOptions.builder().policy(Policies.SIGNATURES_ONLY)
                .formatOption(PdfSealOptions.builder()
                        .signatureField("Signature1", 1, new Region(50, 40, 200, 60))
                        .signatureField("Signature2", 1, new Region(300, 40, 200, 60))
                        .build())
                .build();
        byte[] sealed = U.seal(TestPdfs.text(1), opts);
        assertEquals(Status.INTACT, U.verify(sealed).status(), "empty fields are not content");

        byte[] signed = TestSigning.signField(TestSigning.signField(sealed, "Signature1"), "Signature2");
        VerificationResult r = U.verify(signed);
        assertEquals(Status.INTACT_WITH_ALLOWED_CHANGES, r.status(), r.toString());
        assertFalse(r.hasWarnings(), r.toString());
        assertEquals(2, r.signatures().size());

        VerificationResult outside = U.verify(TestSigning.signNewVisibleField(sealed, "Other", new PDRectangle(300, 120, 150, 40)));
        assertNotNull(first(outside, FindingType.SIGNATURE_OUTSIDE_DECLARED_FIELDS), outside.toString());
        assertTrue(outside.isTrusted());
    }

    @Test
    void certifiedDocumentKeepsIncrementalSignaturesValid() throws Exception {
        SealOptions opts = SealOptions.builder().policy(Policies.SIGNATURES_ONLY)
                .formatOption(PdfSealOptions.builder()
                        .signatureField("Signature1", 1, new Region(50, 40, 200, 60))
                        .certifyWith(TestSigning.KEYS.getPrivate(), TestSigning.CERT)
                        .build())
                .build();
        byte[] sealed = U.seal(TestPdfs.text(1), opts);
        VerificationResult r = U.verify(sealed);
        assertEquals(Status.INTACT, r.status(), r.toString());
        assertEquals(1, r.signatures().size());
        assertTrue(r.signatures().get(0).isCertification());
        assertEquals(SignatureInfo.Integrity.VALID, r.signatures().get(0).integrity());

        byte[] signed = TestSigning.signField(sealed, "Signature1");
        assertArrayEquals(sealed, Arrays.copyOf(signed, sealed.length), "the signature must be incremental");
        VerificationResult s = U.verify(signed);
        assertEquals(Status.INTACT_WITH_ALLOWED_CHANGES, s.status(), s.toString());
        assertNull(first(s, FindingType.CERTIFICATION_INVALID));

        // Rewriting the file breaks the certification: the content is still intact, but a warning is raised.
        VerificationResult rewritten = U.verify(TestPdfs.rewrite(signed));
        assertTrue(rewritten.isTrusted(), rewritten.toString());
        assertNotNull(first(rewritten, FindingType.CERTIFICATION_INVALID), rewritten.toString());
    }

    @Test
    void certificationRequiresDeclaredFieldsWhenSignaturesAreAllowed() throws Exception {
        final SealOptions opts = SealOptions.builder().policy(Policies.SIGNATURES_ONLY)
                .formatOption(PdfSealOptions.builder().certifyWith(TestSigning.KEYS.getPrivate(), TestSigning.CERT).build())
                .build();
        final byte[] pdf = TestPdfs.text(1);
        assertThrows(UntouchableException.class, () -> U.seal(pdf, opts));

        // With STRICT it is certified with P=1, without fields.
        SealOptions strict = SealOptions.builder()
                .formatOption(PdfSealOptions.builder().certifyWith(TestSigning.KEYS.getPrivate(), TestSigning.CERT).build())
                .build();
        assertEquals(Status.INTACT, U.verify(U.seal(pdf, strict)).status());
    }

    @Test
    void alreadySignedPdfIsSealedWithoutBreakingItsSignature() throws Exception {
        byte[] signedBefore = TestSigning.signInvisible(TestPdfs.text(1));
        byte[] sealed = U.seal(signedBefore, policy(Policies.SIGNATURES_ONLY));
        assertArrayEquals(signedBefore, Arrays.copyOf(sealed, signedBefore.length), "sealing must be incremental");
        VerificationResult r = U.verify(sealed);
        assertEquals(Status.INTACT, r.status(), r.toString());
        assertEquals(1, r.signatures().size());
        assertEquals(SignatureInfo.Integrity.VALID, r.signatures().get(0).integrity());
        assertFalse(r.signatures().get(0).isAddedAfterSeal());
    }

    @Test
    void expectedDocIdProtectsAgainstSwappingDocuments() throws Exception {
        byte[] kittens = U.seal(TestPdfs.text(1), SealOptions.builder().docId("kittens").build());
        VerificationResult r = U.verify(kittens, io.qzz.toshiro.untouchable.VerifyOptions.expectDocId("puppies"));
        assertEquals(Status.MODIFIED, r.status());
        assertEquals(FindingType.DOC_ID_MISMATCH, r.findings().get(0).type());
    }
}
