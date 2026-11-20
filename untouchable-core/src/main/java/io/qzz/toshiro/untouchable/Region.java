package io.qzz.toshiro.untouchable;

import java.util.Locale;

/**
 * Rectangle within a page, in the format's units (PDF points, origin at the bottom left).
 */
public final class Region {
    private final float x, y, width, height;

    public Region(float x, float y, float width, float height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    public float x() { return x; }
    public float y() { return y; }
    public float width() { return width; }
    public float height() { return height; }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "[x=%.1f, y=%.1f, w=%.1f, h=%.1f]", x, y, width, height);
    }
}
