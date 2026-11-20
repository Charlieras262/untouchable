package io.qzz.toshiro.untouchable.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Hashing and numeric normalization for the pdf-canon/1 fingerprint. */
final class Hashes {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Hashes() {}

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(byte[] data) {
        return hex(sha256().digest(data));
    }

    static String sha256(CharSequence s) {
        return sha256(s.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(int[] pixels, int w, int h) {
        MessageDigest md = sha256();
        byte[] buf = new byte[4];
        for (int v : new int[] {w, h}) md.update(intBytes(v, buf));
        for (int p : pixels) md.update(intBytes(p, buf));
        return hex(md.digest());
    }

    private static byte[] intBytes(int v, byte[] b) {
        b[0] = (byte) (v >>> 24); b[1] = (byte) (v >>> 16); b[2] = (byte) (v >>> 8); b[3] = (byte) v;
        return b;
    }

    static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = HEX[(bytes[i] >> 4) & 0xf];
            out[i * 2 + 1] = HEX[bytes[i] & 0xf];
        }
        return new String(out);
    }

    /** Numbers to 2 decimals (SPEC D10): absorbs rounding such as 595.276 → 595.28. */
    static String num(double d) {
        String s = String.format(Locale.ROOT, "%.2f", d);
        return s.equals("-0.00") ? "0.00" : s;
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
