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
    public void moduleSourceProviderV3IsIndependentFromRuntimeContractV2() {
        assertEquals(2, NodeJsRuntimeContract.CONTRACT_VERSION);
        assertEquals(3, NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION);
        assertEquals(1, NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_MIN_CONTRACT_VERSION);
        assertEquals(3, NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_MAX_CONTRACT_VERSION);
        assertTrue(NodeJsRuntimeContract.supportsModuleSourceProviderContractVersion(3));
        assertTrue(NodeJsRuntimeContract.supportsModuleSourceProviderContractVersion(2));
        assertTrue(NodeJsRuntimeContract.supportsModuleSourceProviderContractVersion(1));
        assertFalse(NodeJsRuntimeContract.supportsModuleSourceProviderContractVersion(0));
        assertFalse(NodeJsRuntimeContract.supportsModuleSourceProviderContractVersion(4));
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
    public void terminalLauncherContractIsAdditiveAndStable() {
        assertEquals("nodeCli", NodeJsRuntimeContract.CAPABILITY_NODE_CLI);
        assertEquals("1", NodeJsRuntimeContract.NODE_CLI_SCHEMA_VERSION);
        assertEquals("nodeCliSchema", NodeJsRuntimeContract.KEY_NODE_CLI_SCHEMA);
        assertEquals("nodeCliExecutable", NodeJsRuntimeContract.KEY_NODE_CLI_EXECUTABLE);
        assertEquals("nodeCliCommands", NodeJsRuntimeContract.KEY_NODE_CLI_COMMANDS);
        assertEquals("nodeCliArchive", NodeJsRuntimeContract.KEY_NODE_CLI_ARCHIVE);
        assertEquals("nodeCliArchiveSha256", NodeJsRuntimeContract.KEY_NODE_CLI_ARCHIVE_SHA256);
        assertEquals("nodeCliArchiveRoot", NodeJsRuntimeContract.KEY_NODE_CLI_ARCHIVE_ROOT);
        assertEquals("nodeCliArchiveEntryCount", NodeJsRuntimeContract.KEY_NODE_CLI_ARCHIVE_ENTRY_COUNT);
        assertEquals("nodeCliArchiveBytes", NodeJsRuntimeContract.KEY_NODE_CLI_ARCHIVE_BYTES);
        assertEquals("nodeCliNpmVersion", NodeJsRuntimeContract.KEY_NODE_CLI_NPM_VERSION);
        assertEquals("nodeCliCorepackVersion", NodeJsRuntimeContract.KEY_NODE_CLI_COREPACK_VERSION);
        // The manifest contract is read without Binder, so the AIDL contract range is untouched.
        assertEquals(2, NodeJsRuntimeContract.CONTRACT_VERSION);
        assertEquals(3, NodeJsRuntimeContract.MAX_CONTRACT_VERSION);
    }

    @Test
    public void terminalLauncherMetaDataNamesShareThePluginPrefix() {
        String prefix = "org.autojs.plugin.nodejs.";
        assertEquals(prefix, NodeJsPluginCapabilityKeys.NODE_CLI_META_DATA_PREFIX);
        assertEquals(prefix + "NODE_CLI_SCHEMA", NodeJsPluginCapabilityKeys.NODE_CLI_SCHEMA);
        assertEquals(prefix + "NODE_CLI_EXECUTABLE", NodeJsPluginCapabilityKeys.NODE_CLI_EXECUTABLE);
        assertEquals(prefix + "NODE_CLI_COMMANDS", NodeJsPluginCapabilityKeys.NODE_CLI_COMMANDS);
        assertEquals(prefix + "NODE_CLI_ARCHIVE", NodeJsPluginCapabilityKeys.NODE_CLI_ARCHIVE);
        assertEquals(prefix + "NODE_CLI_ARCHIVE_SHA256", NodeJsPluginCapabilityKeys.NODE_CLI_ARCHIVE_SHA256);
        assertEquals(prefix + "NODE_CLI_ARCHIVE_ROOT", NodeJsPluginCapabilityKeys.NODE_CLI_ARCHIVE_ROOT);
        assertEquals(prefix + "NODE_CLI_ARCHIVE_ENTRY_COUNT", NodeJsPluginCapabilityKeys.NODE_CLI_ARCHIVE_ENTRY_COUNT);
        assertEquals(prefix + "NODE_CLI_ARCHIVE_BYTES", NodeJsPluginCapabilityKeys.NODE_CLI_ARCHIVE_BYTES);
        assertEquals(prefix + "NODE_CLI_NPM_VERSION", NodeJsPluginCapabilityKeys.NODE_CLI_NPM_VERSION);
        assertEquals(prefix + "NODE_CLI_COREPACK_VERSION", NodeJsPluginCapabilityKeys.NODE_CLI_COREPACK_VERSION);
    }

    @Test
    public void rawTypeScriptCompilerRequirementHasAStableFailureCode() {
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

    @Test
    public void moduleSourceProviderV3PublishesBoundedTypeScriptCompilationSemantics() {
        assertEquals(
                "compile_missing_typescript",
                NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_COMPILE_MISSING_TYPESCRIPT);
        assertEquals(
                "compiled_typescript",
                NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_COMPILED_TYPESCRIPT);
        assertEquals(
                "moduleSourceProviderInputFd",
                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_INPUT_FD);
        assertEquals(
                "moduleSourceProviderInputBytes",
                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_INPUT_BYTES);
        assertEquals(
                "moduleSourceProviderInputSha256",
                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_INPUT_SHA256);
        assertEquals(
                "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_FAILED",
                NodeJsRuntimeContract.ERROR_TYPESCRIPT_COMPILATION_FAILED);
        assertEquals(
                "hostTypeScriptOnDemandCompilation",
                NodeJsRuntimeContract.CAPABILITY_HOST_TYPESCRIPT_ON_DEMAND_COMPILATION);
    }

    @Test
    public void inspectorUsesAnExplicitPerExecutionRequestKey() {
        assertEquals("inspectorEnabled", NodeJsRuntimeContract.KEY_INSPECTOR_ENABLED);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void executionModeIsLiveWhileRuntimeAdapterRemainsAStableLegacyKey() {
        assertEquals("executionMode", NodeJsRuntimeContract.KEY_EXECUTION_MODE);
        assertEquals("runtimeAdapter", NodeJsRuntimeContract.KEY_RUNTIME_ADAPTER);
    }
}
