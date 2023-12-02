package org.autojs.autojs.runtime.exception;

/**
 * Created by Stardust on Jan 29, 2017.
 */
public class ScriptException extends RuntimeException {

    public ScriptException(String message, Throwable cause) {
        super(message, cause);
    }

    public ScriptException(String message) {
        super(message);
    }

    public ScriptException() {
    }

    public ScriptException(Throwable cause) {
        super(cause);
    }

}
