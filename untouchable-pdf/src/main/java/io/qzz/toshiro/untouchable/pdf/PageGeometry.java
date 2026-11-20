package io.qzz.toshiro.untouchable.pdf;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import io.qzz.toshiro.untouchable.Region;

/**
 * Conversion between pixels of the rendered page (origin at the top left, rotation applied)
 * and PDF coordinates (origin at the bottom left, unrotated).
 */
final class PageGeometry {
    private final float llx, lly, urx, ury, scale;
    private final int rotation;

    PageGeometry(PDPage page, float dpi) {
        PDRectangle crop = page.getCropBox();
        this.llx = crop.getLowerLeftX();
        this.lly = crop.getLowerLeftY();
        this.urx = crop.getUpperRightX();
        this.ury = crop.getUpperRightY();
        this.rotation = ((page.getRotation() % 360) + 360) % 360;
        this.scale = dpi / 72f;
    }

    /** Image point (u, v) → PDF point {x, y}. */
    float[] toPdf(float u, float v) {
        float a = u / scale, b = v / scale;
        switch (rotation) {
            case 90: return new float[] {llx + b, lly + a};
            case 180: return new float[] {urx - a, lly + b};
            case 270: return new float[] {urx - b, ury - a};
            default: return new float[] {llx + a, ury - b};
        }
    }

    /** PDF point (x, y) → image point {u, v}. */
    float[] toImage(float x, float y) {
        switch (rotation) {
            case 90: return new float[] {(y - lly) * scale, (x - llx) * scale};
            case 180: return new float[] {(urx - x) * scale, (y - lly) * scale};
            case 270: return new float[] {(ury - y) * scale, (urx - x) * scale};
            default: return new float[] {(x - llx) * scale, (ury - y) * scale};
        }
    }

    /** Image rectangle [u0,v0,u1,v1] → PDF region. */
    Region toPdfRegion(float u0, float v0, float u1, float v1) {
        float[] a = toPdf(u0, v0), b = toPdf(u1, v1);
        float x0 = Math.min(a[0], b[0]), x1 = Math.max(a[0], b[0]);
        float y0 = Math.min(a[1], b[1]), y1 = Math.max(a[1], b[1]);
        return new Region(x0, y0, x1 - x0, y1 - y0);
    }

    /** PDF region → image rectangle {u0, v0, u1, v1}. */
    float[] toImageRect(Region r) {
        float[] a = toImage(r.x(), r.y()), b = toImage(r.x() + r.width(), r.y() + r.height());
        return new float[] {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])};
    }
}
