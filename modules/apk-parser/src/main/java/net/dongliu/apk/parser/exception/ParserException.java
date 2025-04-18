package net.dongliu.apk.parser.exception;

/**
 * throwed when parse failed.
 *
 * @author dongliu
 */
public class ParserException extends RuntimeException {
    private static final long serialVersionUID = -669279149141454276L;

    public ParserException(final String msg) {
        super(msg);
    }

    public ParserException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ParserException(final Throwable cause) {
        super(cause);
    }

    public ParserException(final String message, final Throwable cause, final boolean enableSuppression,
                           final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public ParserException() {
    }
}
