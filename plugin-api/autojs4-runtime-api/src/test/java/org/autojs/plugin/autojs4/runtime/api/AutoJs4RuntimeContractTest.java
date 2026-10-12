package org.autojs.plugin.autojs4.runtime.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class AutoJs4RuntimeContractTest {
    @Test
    public void identityIsIndependentFromOtherEngines() {
        assertEquals("autojs4-runtime", AutoJs4PluginIds.ID);
        assertEquals("autojs4", AutoJs4PluginIds.ENGINE);
        assertEquals("default", AutoJs4PluginIds.VARIANT_DEFAULT);
        assertEquals("org.autojs.plugin.autojs4.RUNTIME", AutoJs4PluginActions.RUNTIME);
        assertEquals("autojs4-runtime", AutoJs4PluginActions.CATEGORY);
        assertEquals("org.autojs.plugin.INFO", AutoJs4PluginActions.INFO);
        assertEquals(
                "org.autojs.plugin.autojs4.runtime.api.IAutoJs4RuntimePlugin",
                AutoJs4RuntimeContract.BINDER_DESCRIPTOR
        );
        assertEquals("org.autojs.permission.PLUGIN", AutoJs4RuntimeContract.PLUGIN_PERMISSION);
    }

    @Test
    public void protocolVersionAndLimitsArePositiveAndBounded() {
        assertEquals(1, AutoJs4RuntimeContract.PROTOCOL_VERSION);
        assertTrue(AutoJs4RuntimeContract.REQUIRES_HOST_VERSION > 0L);
        assertTrue(AutoJs4RuntimeContract.MAX_PATH_BYTES > 0);
        assertTrue(AutoJs4RuntimeContract.MAX_ARGUMENTS > 0);
        assertTrue(AutoJs4RuntimeContract.MAX_ARGUMENT_VALUE_BYTES > AutoJs4RuntimeContract.MAX_ARGUMENT_KEY_BYTES);
        assertTrue(AutoJs4RuntimeContract.MAX_TERMINAL_MESSAGE_BYTES <= AutoJs4RuntimeContract.MAX_LOG_CHUNK_BYTES);
        assertTrue(AutoJs4RuntimeContract.MAX_STACK_TRACE_BYTES >= AutoJs4RuntimeContract.MAX_TERMINAL_MESSAGE_BYTES);
        assertTrue(AutoJs4RuntimeContract.MAX_CONCURRENT_EXECUTIONS > 0);
        assertTrue(AutoJs4RuntimeContract.ACCEPT_TIMEOUT_MILLIS > 0L);
        assertTrue(AutoJs4RuntimeContract.CANCEL_CONFIRM_TIMEOUT_MILLIS > 0L);
        assertTrue(AutoJs4RuntimeContract.PENDING_CANCELLATION_TTL_MILLIS >= AutoJs4RuntimeContract.CANCEL_CONFIRM_TIMEOUT_MILLIS);
        assertEquals(1, AutoJs4RuntimeContract.DEFAULT_LOOP_TIMES);
    }

    @Test
    public void sourceTypesEventsAndErrorCodesAreExplicitAndDistinct() {
        assertEquals("javascript", AutoJs4RuntimeContract.SOURCE_TYPE_JAVASCRIPT);
        assertEquals("auto", AutoJs4RuntimeContract.SOURCE_TYPE_AUTO);
        assertEquals("started", AutoJs4RuntimeContract.EVENT_STARTED);
        assertEquals("log", AutoJs4RuntimeContract.EVENT_LOG);
        assertEquals("finished", AutoJs4RuntimeContract.EVENT_FINISHED);

        List<String> errors = Arrays.asList(
                AutoJs4RuntimeContract.ERROR_INVALID_REQUEST,
                AutoJs4RuntimeContract.ERROR_PERMISSION_DENIED,
                AutoJs4RuntimeContract.ERROR_FILE_NOT_FOUND,
                AutoJs4RuntimeContract.ERROR_PROJECT_INVALID,
                AutoJs4RuntimeContract.ERROR_BUSY,
                AutoJs4RuntimeContract.ERROR_RUNTIME_UNAVAILABLE,
                AutoJs4RuntimeContract.ERROR_CANCELLED,
                AutoJs4RuntimeContract.ERROR_SCRIPT_EXCEPTION,
                AutoJs4RuntimeContract.ERROR_INTERNAL
        );
        assertEquals(errors.size(), new HashSet<>(errors).size());
        for (String error : errors) {
            assertTrue(error, error.matches("[A-Z_]+"));
        }

        List<String> permissions = Arrays.asList(
                AutoJs4RuntimeContract.PERMISSION_STORAGE,
                AutoJs4RuntimeContract.PERMISSION_OVERLAY,
                AutoJs4RuntimeContract.PERMISSION_ACCESSIBILITY
        );
        assertEquals(permissions.size(), new HashSet<>(permissions).size());
    }

    @Test
    public void capabilityKeysAreNamespacedAndDistinct() {
        List<String> keys = Arrays.asList(
                AutoJs4PluginCapabilityKeys.CONTRACT_VERSION,
                AutoJs4PluginCapabilityKeys.AUTOJS4_VERSION_NAME,
                AutoJs4PluginCapabilityKeys.AUTOJS4_VERSION_CODE,
                AutoJs4PluginCapabilityKeys.AUTOJS4_COMMIT,
                AutoJs4PluginCapabilityKeys.RHINO_VERSION,
                AutoJs4PluginCapabilityKeys.SOURCE_TRANSPORT,
                AutoJs4PluginCapabilityKeys.EXECUTION_MODEL,
                AutoJs4PluginCapabilityKeys.SUPPORTS_UI_MODE,
                AutoJs4PluginCapabilityKeys.SUPPORTS_AUTO_FILES,
                AutoJs4PluginCapabilityKeys.SUPPORTS_PROJECTS,
                AutoJs4PluginCapabilityKeys.SUPPORTS_CANCELLATION,
                AutoJs4PluginCapabilityKeys.SUPPORTS_STREAMING_LOG
        );
        assertEquals(keys.size(), new HashSet<>(keys).size());
        for (String key : keys) {
            assertTrue(key, key.startsWith("org.autojs.plugin.autojs4."));
        }
        assertNotEquals(AutoJs4RuntimeContract.KEY_ERROR_CODE, AutoJs4RuntimeContract.KEY_ERROR_MESSAGE);
        assertEquals("file-path", AutoJs4RuntimeContract.SOURCE_TRANSPORT_FILE_PATH);
        assertEquals("1.7.7.2", AutoJs4RuntimeContract.RHINO_VERSION);
    }
}
