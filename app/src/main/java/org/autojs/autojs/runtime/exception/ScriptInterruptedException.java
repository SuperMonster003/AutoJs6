package org.autojs.autojs.runtime.exception;

/**
 * Created by Stardust on Apr 30, 2017.
 */
public class ScriptInterruptedException extends ScriptException {

    public ScriptInterruptedException() {

    }

    public ScriptInterruptedException(Throwable e) {
        super(e);
    }

    public static boolean causedByInterrupted(Throwable e) {
        while (e != null) {
            if (e instanceof ScriptInterruptedException || e instanceof InterruptedException) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }

}
