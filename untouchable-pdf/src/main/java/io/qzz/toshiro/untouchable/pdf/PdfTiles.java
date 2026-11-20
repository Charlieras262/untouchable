package io.qzz.toshiro.untouchable.pdf;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.annotation.AnnotationFilter;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import io.qzz.toshiro.untouchable.Region;

/**
 * Renders pages WITHOUT annotations, with fixed parameters (SPEC NFR-3), and hashes them per tile to locate
 * changes. Tiles never decide the verdict (FR-11).
 */
final class PdfTiles {
    static final int GRID = 8;
    static final float DPI = 72f;
    /** Identifies the rendering parameters; if they change, tiles are no longer comparable. */
    static final String PROFILE = "pdf-tiles/1;dpi=72;grid=8;aa=off";

    private final PDFRenderer renderer;

    PdfTiles(PDDocument doc) {
        renderer = new PDFRenderer(doc);
        renderer.setAnnotationsFilter(new AnnotationFilter() {
            @Override
            public boolean accept(PDAnnotation annotation) {
                return false;
            }
        });
        RenderingHints h = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        h.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        h.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        h.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        renderer.setRenderingHints(h);
    }

    BufferedImage render(int pageIndex) throws IOException {
        return renderer.renderImageWithDPI(pageIndex, DPI, ImageType.RGB);
    }

    /** "WxH:" + GRID×GRID hashes of 8 hex chars (32 bits): enough to locate, not to decide. */
    static String tiles(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        StringBuilder sb = new StringBuilder().append(w).append('x').append(h).append(':');
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                int[] r = cell(w, h, row, col);
                int tw = r[2] - r[0], th = r[3] - r[1];
                int[] px = tw * th == 0 ? new int[0] : img.getRGB(r[0], r[1], tw, th, null, 0, tw);
                sb.append(Hashes.sha256(px, tw, th), 0, 8);
            }
        }
        return sb.toString();
    }

    private static int[] cell(int w, int h, int row, int col) {
        return new int[] {col * w / GRID, row * h / GRID, (col + 1) * w / GRID, (row + 1) * h / GRID};
    }

    /**
     * PDF region covering the differing tiles, or null if it cannot be located (different sizes or no
     * visible differences).
     */
    static Region changedRegion(String stored, String current, PageGeometry geom) {
        if (stored == null || current == null) return null;
        int a = stored.indexOf(':'), b = current.indexOf(':');
        if (a < 0 || b < 0 || !stored.substring(0, a).equals(current.substring(0, b))) return null;
        String[] size = stored.substring(0, a).split("x");
        int w = Integer.parseInt(size[0]), h = Integer.parseInt(size[1]);
        String s = stored.substring(a + 1), c = current.substring(b + 1);
        if (s.length() != c.length() || s.length() != GRID * GRID * 8) return null;
        int u0 = Integer.MAX_VALUE, v0 = Integer.MAX_VALUE, u1 = -1, v1 = -1;
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                int i = (row * GRID + col) * 8;
                if (s.regionMatches(i, c, i, 8)) continue;
                int[] r = cell(w, h, row, col);
                u0 = Math.min(u0, r[0]); v0 = Math.min(v0, r[1]);
                u1 = Math.max(u1, r[2]); v1 = Math.max(v1, r[3]);
            }
        }
        return u1 < 0 ? null : geom.toPdfRegion(u0, v0, u1, v1);
    }

    /** Side of the cells used to measure overlap (6 px at 72 dpi ≈ 2 mm). */
    static final int OVERLAP_CELL = 6;

    /**
     * Fraction of the rectangle (in PDF coordinates) that has content underneath. It is split into cells of
     * {@link #OVERLAP_CELL} px and the cells with any non-white pixel are counted: that way a line of text
     * counts as covered area even though its strokes take few pixels.
     */
    static double inkFraction(BufferedImage img, PageGeometry geom, Region rect) {
        float[] r = geom.toImageRect(rect);
        int u0 = Math.max(0, Math.round(r[0])), v0 = Math.max(0, Math.round(r[1]));
        int u1 = Math.min(img.getWidth(), Math.round(r[2])), v1 = Math.min(img.getHeight(), Math.round(r[3]));
        long cells = 0, inked = 0;
        for (int cv = v0; cv < v1; cv += OVERLAP_CELL) {
            for (int cu = u0; cu < u1; cu += OVERLAP_CELL) {
                cells++;
                if (hasInk(img, cu, cv, Math.min(cu + OVERLAP_CELL, u1), Math.min(cv + OVERLAP_CELL, v1))) inked++;
            }
        }
        return cells == 0 ? 0 : (double) inked / cells;
    }

    private static boolean hasInk(BufferedImage img, int u0, int v0, int u1, int v1) {
        for (int v = v0; v < v1; v++) {
            for (int u = u0; u < u1; u++) {
                int p = img.getRGB(u, v);
                if (((p >> 16) & 0xff) < 240 || ((p >> 8) & 0xff) < 240 || (p & 0xff) < 240) return true;
            }
        }
        return false;
    }
}
