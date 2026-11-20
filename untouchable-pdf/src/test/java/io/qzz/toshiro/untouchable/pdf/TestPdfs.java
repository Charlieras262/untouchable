package io.qzz.toshiro.untouchable.pdf;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

/** Synthetic PDFs and modifications for the tests. */
final class TestPdfs {
    private TestPdfs() {}

    static PDType1Font helvetica() {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    /** Text document with N A4 pages and an image on the first one. */
    static byte[] text(int pages) throws IOException {
        PDDocument doc = new PDDocument();
        try {
            for (int p = 0; p < pages; p++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                PDPageContentStream cs = new PDPageContentStream(doc, page);
                cs.beginText();
                cs.setFont(helvetica(), 14);
                cs.newLineAtOffset(50, 780);
                cs.showText("Test document - page " + (p + 1));
                cs.setLeading(18);
                for (int i = 0; i < 10; i++) {
                    cs.newLine();
                    cs.showText("Line " + i + ": content that must not change.");
                }
                cs.endText();
                if (p == 0) {
                    BufferedImage img = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = img.createGraphics();
                    g.setColor(new Color(200, 120, 40));
                    g.fillRect(0, 0, 120, 80);
                    g.setColor(Color.BLUE);
                    g.fillOval(20, 10, 60, 60);
                    g.dispose();
                    cs.drawImage(LosslessFactory.createFromImage(doc, img), 50, 450, 120, 80);
                }
                cs.close();
            }
            return save(doc);
        } finally {
            doc.close();
        }
    }

    /** Form with an empty "name" text field. */
    static byte[] form() throws IOException {
        PDDocument doc = new PDDocument();
        try {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDAcroForm form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            PDResources dr = new PDResources();
            dr.put(COSName.HELV, helvetica());
            form.setDefaultResources(dr);
            form.setDefaultAppearance("/Helv 0 Tf 0 g");
            PDTextField tf = new PDTextField(form);
            tf.setPartialName("name");
            tf.setDefaultAppearance("/Helv 12 Tf 0 g");
            form.getFields().add(tf);
            PDAnnotationWidget w = tf.getWidgets().get(0);
            w.setRectangle(new PDRectangle(150, 640, 250, 20));
            w.setPage(page);
            page.getAnnotations().add(w);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            cs.beginText();
            cs.setFont(helvetica(), 12);
            cs.newLineAtOffset(50, 645);
            cs.showText("Name:");
            cs.endText();
            cs.close();
            return save(doc);
        } finally {
            doc.close();
        }
    }

    static byte[] save(PDDocument doc) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        doc.save(bos);
        return bos.toByteArray();
    }

    // ---------------------------------------------------------------- modifications

    /** Full rewrite with PDFBox, changing nothing (as Acrobat does when signing). */
    static byte[] rewrite(byte[] pdf) throws IOException {
        PDDocument doc = Loader.loadPDF(pdf);
        try {
            return save(doc);
        } finally {
            doc.close();
        }
    }

    static byte[] addText(byte[] pdf, int pageIndex, String text) throws IOException {
        PDDocument doc = Loader.loadPDF(pdf);
        try {
            PDPageContentStream cs = new PDPageContentStream(doc, doc.getPage(pageIndex),
                    PDPageContentStream.AppendMode.APPEND, true, true);
            cs.beginText();
            cs.setFont(helvetica(), 12);
            cs.newLineAtOffset(300, 300);
            cs.showText(text);
            cs.endText();
            cs.close();
            return save(doc);
        } finally {
            doc.close();
        }
    }

    static byte[] addComment(byte[] pdf) throws IOException {
        PDDocument doc = Loader.loadPDF(pdf);
        try {
            PDAnnotationText t = new PDAnnotationText();
            t.setRectangle(new PDRectangle(300, 600, 20, 20));
            t.setContents("puppies");
            doc.getPage(0).getAnnotations().add(t);
            return save(doc);
        } finally {
            doc.close();
        }
    }

    static byte[] fill(byte[] pdf, String field, String value) throws IOException {
        PDDocument doc = Loader.loadPDF(pdf);
        try {
            doc.getDocumentCatalog().getAcroForm(null).getField(field).setValue(value);
            return save(doc);
        } finally {
            doc.close();
        }
    }

    static byte[] removePage(byte[] pdf, int pageIndex) throws IOException {
        PDDocument doc = Loader.loadPDF(pdf);
        try {
            doc.removePage(pageIndex);
            return save(doc);
        } finally {
            doc.close();
        }
    }

    static byte[] addPage(byte[] pdf) throws IOException {
        PDDocument doc = Loader.loadPDF(pdf);
        try {
            doc.addPage(new PDPage(PDRectangle.A4));
            return save(doc);
        } finally {
            doc.close();
        }
    }

    // ---------------------------------------------------------------- incremental modifications
    // (as a tool that respects the certification but adds disallowed things would do them)

    static byte[] addTextIncremental(byte[] pdf, String text) throws IOException {
        PDDocument doc = Loader.loadPDF(pdf);
        try {
            org.apache.pdfbox.pdmodel.PDPage page = doc.getPage(0);
            PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true);
            cs.beginText();
            cs.setFont(helvetica(), 12);
            cs.newLineAtOffset(300, 300);
            cs.showText(text);
            cs.endText();
            cs.close();
            page.getCOSObject().setNeedToBeUpdated(true);
            return saveIncremental(doc);
        } finally {
            doc.close();
        }
    }

    static byte[] addCommentIncremental(byte[] pdf) throws IOException {
        PDDocument doc = Loader.loadPDF(pdf);
        try {
            org.apache.pdfbox.pdmodel.PDPage page = doc.getPage(0);
            PDAnnotationText t = new PDAnnotationText();
            t.setRectangle(new PDRectangle(300, 600, 20, 20));
            t.setContents("puppies");
            t.getCOSObject().setNeedToBeUpdated(true);
            page.getAnnotations().add(t);
            page.getCOSObject().setNeedToBeUpdated(true);
            org.apache.pdfbox.cos.COSArray annots = page.getCOSObject().getCOSArray(COSName.ANNOTS);
            if (annots != null) annots.setNeedToBeUpdated(true);
            return saveIncremental(doc);
        } finally {
            doc.close();
        }
    }

    static byte[] fillIncremental(byte[] pdf, String field, String value) throws IOException {
        PDDocument doc = Loader.loadPDF(pdf);
        try {
            org.apache.pdfbox.pdmodel.interactive.form.PDField f = doc.getDocumentCatalog().getAcroForm(null).getField(field);
            f.setValue(value);
            f.getCOSObject().setNeedToBeUpdated(true);
            for (PDAnnotationWidget w : ((org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField) f).getWidgets()) {
                w.getCOSObject().setNeedToBeUpdated(true);
                if (w.getAppearance() != null) w.getAppearance().getCOSObject().setNeedToBeUpdated(true);
            }
            return saveIncremental(doc);
        } finally {
            doc.close();
        }
    }

    static byte[] saveIncremental(PDDocument doc) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        doc.saveIncremental(bos);
        return bos.toByteArray();
    }

    static byte[] stripSeal(byte[] pdf) throws IOException {
        PDDocument doc = Loader.loadPDF(pdf);
        try {
            doc.getDocumentCatalog().getCOSObject().removeItem(SealStore.CATALOG_KEY);
            doc.getDocumentCatalog().setMetadata(null);
            return save(doc);
        } finally {
            doc.close();
        }
    }

    /** Changes the docId inside the catalog payload without re-signing (forgery). */
    static byte[] forgeCatalogPayload(byte[] pdf, String from, String to) throws IOException {
        PDDocument doc = Loader.loadPDF(pdf);
        try {
            org.apache.pdfbox.cos.COSDictionary d = doc.getDocumentCatalog().getCOSObject().getCOSDictionary(SealStore.CATALOG_KEY);
            COSName payload = COSName.getPDFName("Payload");
            d.setString(payload, d.getString(payload).replace(from, to));
            return save(doc);
        } finally {
            doc.close();
        }
    }
}
