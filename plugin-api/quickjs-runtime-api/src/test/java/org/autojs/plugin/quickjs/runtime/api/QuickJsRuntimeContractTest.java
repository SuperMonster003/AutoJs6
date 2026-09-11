package org.autojs.plugin.quickjs.runtime.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuickJsRuntimeContractTest {
    @Test
    public void identityIsIndependentFromOtherJavaScriptEngines() {
        assertEquals("quickjs-runtime", QuickJsPluginIds.ID);
        assertEquals("quickjs", QuickJsPluginIds.ENGINE);
        assertEquals("org.autojs.plugin.quickjs.RUNTIME", QuickJsPluginActions.RUNTIME);
        assertEquals(
                "org.autojs.plugin.quickjs.runtime.api.IQuickJsRuntimePlugin",
                QuickJsRuntimeContract.BINDER_DESCRIPTOR
        );
    }

    @Test
    public void protocolLimitsArePositiveAndBounded() {
        assertTrue(QuickJsRuntimeContract.MAX_SOURCE_BYTES > 0L);
        assertTrue(QuickJsRuntimeContract.MAX_OUTPUT_BYTES > 0L);
        assertTrue(
                QuickJsRuntimeContract.DEFAULT_TIMEOUT_MILLIS <=
                        QuickJsRuntimeContract.MAX_TIMEOUT_MILLIS
        );
        assertTrue(
                QuickJsRuntimeContract.DEFAULT_MEMORY_LIMIT_BYTES <=
                        QuickJsRuntimeContract.MAX_MEMORY_LIMIT_BYTES
        );
        assertTrue(
                QuickJsRuntimeContract.DEFAULT_STACK_LIMIT_BYTES <=
                        QuickJsRuntimeContract.MAX_STACK_LIMIT_BYTES
        );
    }

    @Test
    public void sourceModesAreExplicit() {
        assertEquals("script", QuickJsRuntimeContract.SOURCE_MODE_SCRIPT);
        assertEquals("module", QuickJsRuntimeContract.SOURCE_MODE_MODULE);
    }
}
