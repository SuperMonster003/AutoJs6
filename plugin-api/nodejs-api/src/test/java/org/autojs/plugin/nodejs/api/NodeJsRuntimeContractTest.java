package org.autojs.plugin.nodejs.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class NodeJsRuntimeContractTest {

    @Test
    public void supportedContractRangeIncludesBothBoundsAndCurrentVersion() {
        assertTrue(NodeJsRuntimeContract.MIN_CONTRACT_VERSION
                <= NodeJsRuntimeContract.MAX_CONTRACT_VERSION);
        assertTrue(NodeJsRuntimeContract.supportsContractVersion(
                NodeJsRuntimeContract.MIN_CONTRACT_VERSION));
        assertTrue(NodeJsRuntimeContract.supportsContractVersion(
                NodeJsRuntimeContract.CONTRACT_VERSION));
        assertTrue(NodeJsRuntimeContract.supportsContractVersion(
                NodeJsRuntimeContract.MAX_CONTRACT_VERSION));
    }

    @Test
    public void supportedContractRangeRejectsVersionsOutsideBothBounds() {
        assertFalse(NodeJsRuntimeContract.supportsContractVersion(
                NodeJsRuntimeContract.MIN_CONTRACT_VERSION - 1));
        assertFalse(NodeJsRuntimeContract.supportsContractVersion(
                NodeJsRuntimeContract.MAX_CONTRACT_VERSION + 1));
    }

    @Test
    public void moduleSourceProviderV2IsIndependentFromRuntimeContractV2() {
        assertEquals(2, NodeJsRuntimeContract.CONTRACT_VERSION);
        assertEquals(2, NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION);
        assertEquals(1, NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_MIN_CONTRACT_VERSION);
        assertEquals(2, NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_MAX_CONTRACT_VERSION);
        assertTrue(NodeJsRuntimeContract.supportsModuleSourceProviderContractVersion(2));
        assertTrue(NodeJsRuntimeContract.supportsModuleSourceProviderContractVersion(1));
        assertFalse(NodeJsRuntimeContract.supportsModuleSourceProviderContractVersion(0));
        assertFalse(NodeJsRuntimeContract.supportsModuleSourceProviderContractVersion(3));
    }

    @Test
    public void moduleSourceProviderV2PublishesDistinctMaterializationSemantics() {
        assertEquals(
                "moduleSourceProviderOperation",
                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_OPERATION);
        assertEquals(
                "moduleSourceProviderDeadlineElapsedRealtimeMs",
                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_DEADLINE_ELAPSED_REALTIME_MS);
        assertEquals(
                "resolve_existing",
                NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_RESOLVE_EXISTING);
        assertEquals(
                "materialize_missing_plaintext",
                NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_MATERIALIZE_MISSING_PLAINTEXT);
        assertEquals(
                "plaintext",
                NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_PLAINTEXT);
        assertNotEquals(
                NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_PLAINTEXT,
                NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_DECRYPTED);
        assertNotEquals(
                NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_PLAINTEXT,
                NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_ENCRYPTED);
        assertEquals(
                "hostPlaintextModuleSourceMaterialization",
                NodeJsRuntimeContract.CAPABILITY_HOST_PLAINTEXT_MODULE_SOURCE_MATERIALIZATION);
    }

    @Test
    public void legacyTypeScriptStrippingIsAnExplicitMigrationOnlyRequestKey() {
        assertEquals(
                "legacyTypeScriptStrippingEnabled",
                NodeJsRuntimeContract.KEY_LEGACY_TYPESCRIPT_STRIPPING_ENABLED);
        assertEquals(
                "ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED",
                NodeJsRuntimeContract.ERROR_TYPESCRIPT_COMPILER_REQUIRED);
    }

    @Test
    public void precompiledTypeScriptSnapshotHasStableRequestAndFailureCodes() {
        assertEquals(
                "typeScriptPrecompiledSnapshot",
                NodeJsRuntimeContract.KEY_TYPESCRIPT_PRECOMPILED_SNAPSHOT);
        assertEquals(
                "typeScriptPrecompiledSourceNames",
                NodeJsRuntimeContract.KEY_TYPESCRIPT_PRECOMPILED_SOURCE_NAMES);
        assertEquals(
                "ERR_AUTOJS6_TYPESCRIPT_SNAPSHOT_MODULE_NOT_FOUND",
                NodeJsRuntimeContract.ERROR_TYPESCRIPT_SNAPSHOT_MODULE_NOT_FOUND);
        assertEquals(
                "ERR_AUTOJS6_TYPESCRIPT_SNAPSHOT_MODULE_AMBIGUOUS",
                NodeJsRuntimeContract.ERROR_TYPESCRIPT_SNAPSHOT_MODULE_AMBIGUOUS);
    }
}
