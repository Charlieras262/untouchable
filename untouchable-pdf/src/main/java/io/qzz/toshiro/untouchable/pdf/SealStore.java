package io.qzz.toshiro.untouchable.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.common.PDMetadata;

import io.qzz.toshiro.untouchable.spi.EmbeddedSeal;

/**
 * Stores and reads the seal in two places (SPEC §9.1): an XMP packet with its own namespace and, for
 * redundancy, an {@code /Untouchable} catalog entry. When reading, the catalog takes precedence.
 */
final class SealStore {
    static final String NS = "https://toshiro.qzz.io/ns/untouchable/1.0/";
    static final COSName CATALOG_KEY = COSName.getPDFName("Untouchable");
    private static final COSName PAYLOAD = COSName.getPDFName("Payload");
    private static final COSName SIG = COSName.getPDFName("Sig");
    private static final Pattern OUR_DESCRIPTION = Pattern.compile(
            "<rdf:Description[^>]*xmlns:untouchable=\"" + Pattern.quote(NS) + "\"[^>]*>.*?</rdf:Description>",
            Pattern.DOTALL);

    private SealStore() {}

    /** Writes the seal (replacing a previous one). Marks the objects for a possible incremental save. */
    static void write(PDDocument doc, EmbeddedSeal seal) throws IOException {
        PDDocumentCatalog cat = doc.getDocumentCatalog();

        COSDictionary d = new COSDictionary();
        d.setInt(COSName.V, 1);
        d.setString(PAYLOAD, seal.payload());
        d.setString(SIG, seal.signature());
        d.setNeedToBeUpdated(true);
        cat.getCOSObject().setItem(CATALOG_KEY, d);

        String b64 = Base64.getEncoder().encodeToString(seal.payload().getBytes(StandardCharsets.UTF_8));
        String desc = "<rdf:Description rdf:about=\"\" xmlns:untouchable=\"" + NS + "\">"
                + "<untouchable:payload>" + b64 + "</untouchable:payload>"
                + "<untouchable:sig>" + seal.signature() + "</untouchable:sig>"
                + "</rdf:Description>";
        PDMetadata existing = cat.getMetadata();
        String old = existing == null ? null : new String(existing.toByteArray(), StandardCharsets.UTF_8);
        String xmp;
        if (old != null && old.contains("</rdf:RDF>")) {
            old = OUR_DESCRIPTION.matcher(old).replaceAll("");
            xmp = old.replaceFirst("</rdf:RDF>", Matcher.quoteReplacement(desc + "</rdf:RDF>"));
        } else {
            xmp = "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n"
                    + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
                    + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" + desc + "</rdf:RDF>"
                    + "</x:xmpmeta>\n<?xpacket end=\"w\"?>";
        }
        PDMetadata meta = new PDMetadata(doc);
        meta.importXMPMetadata(xmp.getBytes(StandardCharsets.UTF_8));
        COSStream ms = meta.getCOSObject();
        ms.setNeedToBeUpdated(true);
        cat.setMetadata(meta);
        cat.getCOSObject().setNeedToBeUpdated(true);
    }

    /** Reads the seal: the catalog first, otherwise the XMP. Null if there is none. */
    static EmbeddedSeal read(PDDocument doc) {
        EmbeddedSeal s = readCatalog(doc);
        return s != null ? s : readXmp(doc);
    }

    static EmbeddedSeal readCatalog(PDDocument doc) {
        COSDictionary d = doc.getDocumentCatalog().getCOSObject().getCOSDictionary(CATALOG_KEY);
        if (d == null) return null;
        String payload = d.getString(PAYLOAD), sig = d.getString(SIG);
        return payload == null || sig == null ? null : new EmbeddedSeal(payload, sig);
    }

    static EmbeddedSeal readXmp(PDDocument doc) {
        try {
            PDMetadata meta = doc.getDocumentCatalog().getMetadata();
            if (meta == null) return null;
            String xmp = new String(meta.toByteArray(), StandardCharsets.UTF_8);
            // The prefix may change if another tool rewrites the XMP: look it up by namespace.
            Matcher ns = Pattern.compile("xmlns:([\\w.-]+)=\"" + Pattern.quote(NS) + "\"").matcher(xmp);
            if (!ns.find()) return null;
            String p = Pattern.quote(ns.group(1));
            String payload = value(xmp, p, "payload"), sig = value(xmp, p, "sig");
            if (payload == null || sig == null) return null;
            String json = new String(Base64.getMimeDecoder().decode(payload), StandardCharsets.UTF_8);
            return new EmbeddedSeal(json, sig.replaceAll("\\s", ""));
        } catch (IOException e) {
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String value(String xmp, String prefix, String prop) {
        Matcher m = Pattern.compile(prefix + ":" + prop + "(?:>([^<]*)<|=\"([^\"]*)\")").matcher(xmp);
        if (!m.find()) return null;
        return (m.group(1) != null ? m.group(1) : m.group(2)).trim();
    }
}
