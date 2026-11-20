package io.qzz.toshiro.untouchable;

/** Error while sealing or processing a document (unsupported format, invalid options, corrupt file). */
public class UntouchableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UntouchableException(String message) {
        super(message);
    }

    public UntouchableException(String message, Throwable cause) {
        super(message, cause);
    }
}
