package org.autojs.plugin.jvmsource.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class JavaEntryCompatibilityTest {

    @Test
    public void implementsAndRunsEntryFromJava() throws Exception {
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
            public JvmCancellation cancellation() {
                return cancellation;
            }
        };
        AutoJsJvmEntry entry = value -> value.app().launch("com.example.app");

        assertEquals(Boolean.TRUE, entry.run(context));
        assertTrue(context.app().launch("com.example.app"));
        context.throwIfCancellationRequested();
    }
}
