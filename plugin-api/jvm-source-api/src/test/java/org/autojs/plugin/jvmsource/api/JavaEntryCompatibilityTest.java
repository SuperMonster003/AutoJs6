package org.autojs.plugin.jvmsource.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class JavaEntryCompatibilityTest {

    @Test
    public void implementsAndRunsEntryFromJava() throws Exception {
        StringBuilder output = new StringBuilder();
        String[] clipboard = new String[] { "initial" };
        JvmCancellation cancellation = new JvmCancellation() {
            @Override
            public boolean isCancellationRequested() {
                return false;
            }

            @Override
            public void throwIfCancellationRequested() {
                // Not cancelled.
            }
        };
        JvmScriptContext context = new JvmScriptContext() {
            @Override
            public JvmAppApi app() {
                return packageName -> packageName.equals("com.example.app");
            }

            @Override
            public JvmClipboardApi clipboard() {
                return new JvmClipboardApi() {
                    @Override
                    public String getText() {
                        return clipboard[0];
                    }

                    @Override
                    public void setText(String value) {
                        clipboard[0] = value;
                    }
                };
            }

            @Override
            public JvmConsoleApi console() {
                return new JvmConsoleApi() {
                    @Override
                    public void log(String message) {
                        output.append("log:").append(message).append(';');
                    }

                    @Override
                    public void error(String message) {
                        output.append("error:").append(message).append(';');
                    }
                };
            }

            @Override
            public JvmCancellation cancellation() {
                return cancellation;
            }

            @Override
            public void sleep(long millis) {
                if (millis < 0L) throw new IllegalArgumentException("millis");
            }

            @Override
            public void toast(String message) {
                output.append("toast:").append(message).append(';');
            }
        };
        AutoJsJvmEntry entry = value -> {
            value.console().log("hello");
            value.console().error("oops");
            value.sleep(0L);
            value.toast("ready");
            return value.app().launch("com.example.app");
        };

        assertEquals(Boolean.TRUE, entry.run(context));
        assertEquals("log:hello;error:oops;toast:ready;", output.toString());
        assertTrue(context.app().launch("com.example.app"));
        context.clipboard().setText("M9 clipboard");
        assertEquals("M9 clipboard", context.clipboard().getText());
        context.throwIfCancellationRequested();
    }
}
