package io.qzz.toshiro.untouchable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.qzz.toshiro.untouchable.crypto.Ed25519;
import io.qzz.toshiro.untouchable.crypto.Hmac;
import io.qzz.toshiro.untouchable.crypto.KeyResolver;
import io.qzz.toshiro.untouchable.spi.Comparison;
import io.qzz.toshiro.untouchable.spi.EmbeddedSeal;
import io.qzz.toshiro.untouchable.spi.FormatHandler;
import io.qzz.toshiro.untouchable.spi.PreparedDocument;
import io.qzz.toshiro.untouchable.spi.SealPayload;

/** Sealing logic and verdicts, using a test text format. */
class UntouchableTest {
    private static final KeyPair KEYS = Ed25519.generateKeyPair();

    private static Untouchable untouchable() {
        return Untouchable.builder()
                .discoverHandlers(false)
                .handler(new TextHandler())
                .signer(Ed25519.signer(KEYS.getPrivate(), "k1"))
                .keyResolver(KeyResolver.of("k1", Ed25519.verifier(KEYS.getPublic())))
                .clock(Clock.fixed(Instant.parse("2026-11-20T10:15:30.123Z"), ZoneOffset.UTC))
                .build();
    }

    /** Replaces text only in the content, without touching the seal. */
    private static byte[] editContent(String sealed, String from, String to) {
        int i = sealed.indexOf(TextHandler.MARK);
        return (sealed.substring(0, i).replace(from, to) + sealed.substring(i)).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] doc(String content) {
        return ("TXT\n" + content).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void sealedDocumentIsIntact() {
        Untouchable u = untouchable();
        byte[] sealed = u.seal(doc("kittens"), SealOptions.builder().docId("doc-1").claim("origin", "test").build());
        VerificationResult r = u.verify(sealed);
        assertEquals(Status.INTACT, r.status());
        assertTrue(r.isTrusted());
        assertTrue(r.findings().isEmpty());
        assertEquals("doc-1", r.seal().docId());
        assertEquals("2026-11-20T10:15:30Z", r.seal().issuedAt());
        assertEquals("strict", r.seal().policyId());
        assertEquals("test", r.seal().claims().get("origin"));
    }

    @Test
    void changedContentIsModified() {
        Untouchable u = untouchable();
        String sealed = new String(u.seal(doc("kittens")), StandardCharsets.UTF_8);
        VerificationResult r = u.verify(editContent(sealed, "kittens", "puppies"));
        assertEquals(Status.MODIFIED, r.status());
        assertFalse(r.isTrusted());
        assertEquals(FindingType.CONTENT_CHANGED, r.findings().get(0).type());
        assertEquals(Severity.VIOLATION, r.findings().get(0).severity());
    }

    @Test
    void documentWithoutSealHasNoSeal() {
        assertEquals(Status.NO_SEAL, untouchable().verify(doc("puppies")).status());
    }

    @Test
    void tamperedPayloadIsInvalidSeal() {
        Untouchable u = untouchable();
        String sealed = new String(u.seal(doc("kittens"), SealOptions.builder().docId("doc-1").build()), StandardCharsets.UTF_8);
        byte[] forged = sealed.replace("doc-1", "doc-2").getBytes(StandardCharsets.UTF_8);
        VerificationResult r = u.verify(forged);
        assertEquals(Status.INVALID_SEAL, r.status());
        assertNotNull(r.reason());
    }

    @Test
    void unknownKeyIsInvalidSeal() {
        byte[] sealed = untouchable().seal(doc("kittens"));
        KeyPair other = Ed25519.generateKeyPair();
        Untouchable verifier = Untouchable.builder().discoverHandlers(false).handler(new TextHandler())
                .keyResolver(KeyResolver.of("k1", Ed25519.verifier(other.getPublic()))).build();
        assertEquals(Status.INVALID_SEAL, verifier.verify(sealed).status());

        Untouchable noKey = Untouchable.builder().discoverHandlers(false).handler(new TextHandler())
                .keyResolver(KeyResolver.of("other", Ed25519.verifier(KEYS.getPublic()))).build();
        assertEquals(Status.INVALID_SEAL, noKey.verify(sealed).status());
    }

    @Test
    void algorithmMismatchIsInvalidSeal() {
        byte[] secret = new byte[32];
        byte[] sealed = untouchable().seal(doc("kittens"));
        Untouchable hmacVerifier = Untouchable.builder().discoverHandlers(false).handler(new TextHandler())
                .keyResolver(KeyResolver.of("k1", Hmac.verifier(secret))).build();
        assertEquals(Status.INVALID_SEAL, hmacVerifier.verify(sealed).status());
    }

    @Test
    void hmacWorksEndToEnd() {
        byte[] secret = "a 32-byte HMAC secret key!!!!!!!".getBytes(StandardCharsets.UTF_8);
        Untouchable u = Untouchable.builder().discoverHandlers(false).handler(new TextHandler())
                .signer(Hmac.signer(secret, "h1")).keyResolver(KeyResolver.of("h1", Hmac.verifier(secret))).build();
        assertEquals(Status.INTACT, u.verify(u.seal(doc("kittens"))).status());
    }

    @Test
    void expectedDocIdMismatchIsModified() {
        Untouchable u = untouchable();
        byte[] sealed = u.seal(doc("kittens"), SealOptions.builder().docId("doc-1").build());
        VerificationResult r = u.verify(sealed, VerifyOptions.expectDocId("doc-9"));
        assertEquals(Status.MODIFIED, r.status());
        assertEquals(FindingType.DOC_ID_MISMATCH, r.findings().get(0).type());
        assertEquals(Status.INTACT, u.verify(sealed, VerifyOptions.expectDocId("doc-1")).status());
    }

    @Test
    void sealingTwiceRequiresReplaceExisting() {
        Untouchable u = untouchable();
        byte[] sealed = u.seal(doc("kittens"), SealOptions.builder().docId("a").build());
        assertThrows(UntouchableException.class, () -> u.seal(sealed));
        byte[] resealed = u.seal(sealed, SealOptions.builder().docId("b").replaceExisting(true).build());
        VerificationResult r = u.verify(resealed);
        assertEquals(Status.INTACT, r.status());
        assertEquals("b", r.seal().docId());
    }

    @Test
    void policyDecidesSeverity() {
        Untouchable u = untouchable();
        String sealed = new String(u.seal(doc("kittens"), SealOptions.builder().policy(Policies.SIGNATURES_ONLY).build()),
                StandardCharsets.UTF_8);
        VerificationResult r = u.verify(editContent(sealed, "kittens", "kittens\n#SIGNED"));
        assertEquals(Status.INTACT_WITH_ALLOWED_CHANGES, r.status());
        assertEquals(Severity.INFO, r.findings().get(0).severity());

        String strict = new String(u.seal(doc("kittens")), StandardCharsets.UTF_8);
        assertEquals(Status.MODIFIED, u.verify(editContent(strict, "kittens", "kittens\n#SIGNED")).status());
    }

    @Test
    void customPolicyMustBeKnownToVerifier() {
        ChangePolicy lax = new ChangePolicy() {
            @Override public String id() { return "lax"; }
            @Override public Severity severity(FindingType type) { return Severity.INFO; }
        };
        Untouchable sealer = Untouchable.builder().discoverHandlers(false).handler(new TextHandler()).policy(lax)
                .signer(Ed25519.signer(KEYS.getPrivate(), "k1"))
                .keyResolver(KeyResolver.of("k1", Ed25519.verifier(KEYS.getPublic()))).build();
        byte[] sealed = sealer.seal(doc("kittens"), SealOptions.builder().policy(lax).build());
        assertEquals(Status.INTACT, sealer.verify(sealed).status());
        assertEquals(Status.UNSUPPORTED, untouchable().verify(sealed).status());
        assertThrows(UntouchableException.class, () -> untouchable().seal(doc("x"), SealOptions.builder().policy(lax).build()));
    }

    @Test
    void unknownFormatIsUnsupported() {
        assertEquals(Status.UNSUPPORTED, untouchable().verify("hola".getBytes(StandardCharsets.UTF_8)).status());
        assertThrows(UntouchableException.class, () -> untouchable().seal("hola".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void documentSizeIsLimited() {
        Untouchable u = Untouchable.builder().discoverHandlers(false).handler(new TextHandler()).maxDocumentBytes(8)
                .signer(Ed25519.signer(KEYS.getPrivate(), "k1"))
                .keyResolver(KeyResolver.of("k1", Ed25519.verifier(KEYS.getPublic()))).build();
        assertThrows(UntouchableException.class, () -> u.seal(doc("demasiado largo")));
        assertEquals(Status.UNSUPPORTED, u.verify(doc("demasiado largo")).status());
    }

    /**
     * Test format: "TXT\n" + content [+ "\n#SIGNED"] + "\n--SEAL--\n" + payload + "\n" + signature.
     * "#SIGNED" simulates an added signature (a change some policies allow).
     */
    static final class TextHandler implements FormatHandler {
        private static final String MARK = "\n--SEAL--\n";

        @Override public String format() { return "txt"; }
        @Override public String canonVersion() { return "txt/1"; }

        @Override
        public boolean supports(byte[] document) {
            return new String(document, StandardCharsets.UTF_8).startsWith("TXT\n");
        }

        private static String content(String s) {
            int i = s.indexOf(MARK);
            String c = i < 0 ? s : s.substring(0, i);
            return c.replace("\n#SIGNED", "");
        }

        @Override
        public PreparedDocument prepare(byte[] document, SealOptions options) {
            String s = new String(document, StandardCharsets.UTF_8);
            Map<String, Object> fp = new LinkedHashMap<String, Object>();
            fp.put("content", content(s));
            return new PreparedDocument(content(s).getBytes(StandardCharsets.UTF_8), fp, null);
        }

        @Override
        public byte[] embed(PreparedDocument prepared, EmbeddedSeal seal, SealOptions options) {
            return (new String(prepared.document(), StandardCharsets.UTF_8) + MARK + seal.payload() + "\n" + seal.signature())
                    .getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public EmbeddedSeal extract(byte[] document) throws IOException {
            String s = new String(document, StandardCharsets.UTF_8);
            int i = s.indexOf(MARK);
            if (i < 0) return null;
            String[] parts = s.substring(i + MARK.length()).split("\n");
            List<String> l = new ArrayList<String>(Arrays.asList(parts));
            if (l.size() < 2) throw new IOException("incomplete seal");
            return new EmbeddedSeal(l.get(0), l.get(1));
        }

        @Override
        public Comparison compare(byte[] document, SealPayload payload, VerifyOptions options) {
            String s = new String(document, StandardCharsets.UTF_8);
            List<Finding> findings = new ArrayList<Finding>();
            if (!content(s).equals(payload.fingerprint().get("content"))) {
                findings.add(Finding.of(FindingType.CONTENT_CHANGED, null, null, "contenido distinto"));
            }
            int seal = s.indexOf(MARK);
            if ((seal < 0 ? s : s.substring(0, seal)).contains("\n#SIGNED")) {
                findings.add(Finding.of(FindingType.SIGNATURE_ADDED, null, null, "simulated signature"));
            }
            return new Comparison(findings, Collections.<SignatureInfo>emptyList());
        }
    }
}
