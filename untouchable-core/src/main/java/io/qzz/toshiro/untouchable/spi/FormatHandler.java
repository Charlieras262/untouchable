package io.qzz.toshiro.untouchable.spi;

import java.io.IOException;

import io.qzz.toshiro.untouchable.SealOptions;
import io.qzz.toshiro.untouchable.VerifyOptions;

/**
 * Handler for one document format (SPEC §5). It knows how to compute the canonical content, store and read
 * the seal inside the file, and compare a received document with the sealed fingerprint.
 * <p>
 * Handlers are discovered with {@link java.util.ServiceLoader} ({@code META-INF/services/io.qzz.toshiro.untouchable.spi.FormatHandler})
 * or registered in the {@code Untouchable} builder. Implementations must be safe to use from
 * from several threads.
 */
public interface FormatHandler {

    /** Format identifier stored in the seal ("pdf"). */
    String format();

    /** Version of the canonical content algorithm ("pdf-canon/1"). */
    String canonVersion();

    /** true if this handler understands the document (usually by its first bytes). */
    boolean supports(byte[] document);

    /**
     * Prepares the document for sealing: applies the format options that modify the file (e.g.
     * creating signature fields) and computes the fingerprint of the result.
     */
    PreparedDocument prepare(byte[] document, SealOptions options) throws IOException;

    /** Writes the seal into the prepared document and returns the final file. */
    byte[] embed(PreparedDocument prepared, EmbeddedSeal seal, SealOptions options) throws IOException;

    /** Reads the document's seal, or null if it has none. */
    EmbeddedSeal extract(byte[] document) throws IOException;

    /**
     * Compares the document with the seal's fingerprint. Returns the findings without a severity (the
     * policy assigns it) and the signatures found.
     */
    Comparison compare(byte[] document, SealPayload payload, VerifyOptions options) throws IOException;
}
