package io.qzz.toshiro.untouchable.pdf;

import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;

import io.qzz.toshiro.untouchable.UntouchableException;

/** Creates the empty signature fields declared by the issuer (SPEC FR-12). */
final class SignatureFields {
    private SignatureFields() {}

    static void add(PDDocument doc, List<SignatureFieldSpec> specs) throws IOException {
        if (specs.isEmpty()) return;
        PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
        if (form == null) {
            form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
        }
        for (SignatureFieldSpec spec : specs) {
            if (spec.page() > doc.getNumberOfPages()) {
                throw new UntouchableException("Signature field " + spec.name() + " points to page " + spec.page()
                        + " but the document has " + doc.getNumberOfPages());
            }
            for (PDField f : form.getFieldTree()) {
                if (spec.name().equals(f.getFullyQualifiedName())) {
                    throw new UntouchableException("A field named " + spec.name() + " already exists");
                }
            }
            PDPage page = doc.getPage(spec.page() - 1);
            PDSignatureField field = new PDSignatureField(form);
            field.setPartialName(spec.name());
            PDAnnotationWidget widget = field.getWidgets().get(0);
            widget.setRectangle(new PDRectangle(spec.rect().x(), spec.rect().y(), spec.rect().width(), spec.rect().height()));
            widget.setPage(page);
            widget.setPrinted(true);
            page.getAnnotations().add(widget);
            form.getFields().add(field);
            page.getCOSObject().setNeedToBeUpdated(true);
        }
        form.getCOSObject().setNeedToBeUpdated(true);
        doc.getDocumentCatalog().getCOSObject().setNeedToBeUpdated(true);
    }
}
