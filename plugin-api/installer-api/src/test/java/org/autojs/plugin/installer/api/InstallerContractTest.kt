package org.autojs.plugin.installer.api

import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class InstallerContractTest {

    @Test
    fun identityAndDiscoveryLiteralsAreStable() {
        assertEquals(PluginActions.INFO, InstallerActions.INFO)
        assertEquals("org.autojs.plugin.INSTALLER", InstallerActions.SERVICE_ACTION)
        assertEquals("installer", InstallerActions.SERVICE_CATEGORY)
        assertEquals("org.autojs.permission.PLUGIN", InstallerActions.PLUGIN_PERMISSION)
        assertEquals("org.autojs.plugin.INSTALLER_SETTINGS", InstallerActions.OPEN_SETTINGS)

        assertEquals("three-setup-installer", InstallerIds.PLUGIN_ID)
        assertEquals("installer", InstallerIds.ENGINE)
        assertEquals("default", InstallerIds.VARIANT_DEFAULT)
        assertEquals("io.github.supermonster003.autojs6.plugin.three.setup.installer", InstallerIds.DEFAULT_PACKAGE_NAME)
        assertTrue(InstallerIds.REQUIRED_HOST_VERSION_CODE > 5298L)

        assertEquals(PluginCapabilityKeys.REQUIRES_HOST_VERSION, InstallerCapabilityKeys.REQUIRES_HOST_VERSION)
        assertEquals("installerContractVersion", InstallerCapabilityKeys.CONTRACT_VERSION)
        assertEquals("installerAuthorizers", InstallerCapabilityKeys.AUTHORIZERS)
        assertEquals("installerFeatures", InstallerCapabilityKeys.FEATURES_KEY)
        assertEquals("installerMaxBatch", InstallerCapabilityKeys.MAX_BATCH)
        assertEquals("installerMaxSplits", InstallerCapabilityKeys.MAX_SPLITS)
        assertEquals("installerSettingsVersion", InstallerCapabilityKeys.SETTINGS_VERSION)
        assertEquals(InstallerCapabilityKeys.FEATURES.size, InstallerCapabilityKeys.FEATURES.toSet().size)
        assertEquals(listOf("batch", "splits", "delete-source", "silent-uninstall", "default-installer", "users", "inspect"), InstallerCapabilityKeys.FEATURES)
    }

    @Test
    fun contractVersionRangeIncludesItsBoundsAndRejectsNeighbours() {
        assertEquals(1, InstallerContract.CONTRACT_VERSION)
        assertEquals(1, InstallerContract.MIN_CONTRACT_VERSION)
        assertTrue(InstallerContract.MIN_CONTRACT_VERSION <= InstallerContract.MAX_CONTRACT_VERSION)
        assertTrue(InstallerContract.supportsContractVersion(InstallerContract.MIN_CONTRACT_VERSION))
        assertTrue(InstallerContract.supportsContractVersion(InstallerContract.CONTRACT_VERSION))
        assertTrue(InstallerContract.supportsContractVersion(InstallerContract.MAX_CONTRACT_VERSION))
        assertFalse(InstallerContract.supportsContractVersion(InstallerContract.MIN_CONTRACT_VERSION - 1))
        assertFalse(InstallerContract.supportsContractVersion(InstallerContract.MAX_CONTRACT_VERSION + 1))
    }

    @Test
    fun bundleKeysAreUniqueAndFrozen() {
        val keys = constants("KEY_")
        assertEquals(keys.size, keys.values.toSet().size)
        assertEquals("contractVersion", InstallerContract.KEY_CONTRACT_VERSION)
        assertEquals("hostVersionCode", InstallerContract.KEY_HOST_VERSION_CODE)
        assertEquals("requestJson", InstallerContract.KEY_REQUEST_JSON)
        assertEquals("resultJson", InstallerContract.KEY_RESULT_JSON)
        assertEquals("errorJson", InstallerContract.KEY_ERROR_JSON)
        assertEquals("detailJson", InstallerContract.KEY_DETAIL_JSON)
        assertEquals("statusJson", InstallerContract.KEY_STATUS_JSON)
        assertEquals("hostSessionId", InstallerContract.KEY_HOST_SESSION_ID)
    }

    @Test
    fun documentFieldsAreUniqueWithinTheirDocuments() {
        val fields = constants("FIELD_")
        // The nested error object and the user entries repeat top-level names (`status`, `id`, `name`, `running`).
        val nestedPrefixes = listOf("FIELD_ERROR_", "FIELD_USER_", "FIELD_SPLIT_")
        val topLevel = fields.filterKeys { key -> nestedPrefixes.none(key::startsWith) }
        assertEquals(topLevel.size, topLevel.values.toSet().size)
        nestedPrefixes.forEach { prefix ->
            val nested = fields.filterKeys { it.startsWith(prefix) }
            assertEquals(nested.size, nested.values.toSet().size)
        }
    }

    @Test
    fun closedValueSetsAreConsistent() {
        assertEquals(listOf("none", "shizuku", "root"), InstallerContract.AUTHORIZERS)
        assertTrue(InstallerContract.isAuthorizer("auto"))
        InstallerContract.AUTHORIZERS.forEach { assertTrue(InstallerContract.isAuthorizer(it)) }
        assertFalse(InstallerContract.isAuthorizer("dhizuku"))
        assertFalse(InstallerContract.isAuthorizer(null))
        assertFalse(InstallerContract.AUTHORIZER_AUTO in InstallerContract.AUTHORIZERS)

        assertEquals(listOf("auto", "dialog", "silent"), InstallerContract.INTERACTIONS)
        assertTrue(InstallerContract.isInteraction("silent"))
        assertFalse(InstallerContract.isInteraction("notification"))

        assertEquals(8, InstallerContract.STAGES.size)
        assertEquals(InstallerContract.STAGES.size, InstallerContract.STAGES.toSet().size)
        assertEquals(setOf("completed", "failed", "cancelled"), InstallerContract.TERMINAL_STAGES)
        assertTrue(InstallerContract.TERMINAL_STAGES.all { it in InstallerContract.STAGES })
        assertTrue(InstallerContract.isTerminalStage("failed"))
        assertFalse(InstallerContract.isTerminalStage("writing"))
        assertTrue(InstallerContract.isStage("writing"))
        assertFalse(InstallerContract.isStage("verifying"))

        assertEquals(listOf("apk", "apks", "xapk", "apkm", "apkz", "zip", "aab", "unknown"), InstallerContract.FORMATS)
        assertEquals("current", InstallerContract.USER_CURRENT)
        assertEquals("all", InstallerContract.USER_ALL)
    }

    @Test
    fun ceilingsMatchTheRoadmap() {
        assertEquals(32, InstallerContract.MAX_BATCH_SOURCES)
        assertEquals(64, InstallerContract.MAX_SPLITS_PER_PACKAGE)
        assertEquals(65_536, InstallerContract.MAX_JSON_BYTES)
        assertEquals(255, InstallerContract.MAX_INSTALLER_PACKAGE_LENGTH)
        assertEquals(255, InstallerContract.MAX_DISPLAY_NAME_LENGTH)
        assertEquals(65_536, InstallerContract.MAX_ICON_BYTES)
        assertEquals(4, InstallerContract.MAX_CONCURRENT_SESSIONS)
        assertEquals(300_000L, InstallerContract.DEFAULT_USER_ACTION_TIMEOUT_MILLIS)
        assertEquals(1_800_000L, InstallerContract.DEFAULT_SESSION_TIMEOUT_MILLIS)
        assertTrue(InstallerContract.DEFAULT_SESSION_TIMEOUT_MILLIS <= InstallerContract.MAX_SESSION_TIMEOUT_MILLIS)
        assertTrue(InstallerContract.DEFAULT_USER_ACTION_TIMEOUT_MILLIS < InstallerContract.DEFAULT_SESSION_TIMEOUT_MILLIS)
    }

    @Test
    fun errorCodesAreClosedUniqueAndSelfDescribing() {
        assertEquals(20, InstallerErrorCodes.ALL.size)
        assertEquals(InstallerErrorCodes.ALL.size, InstallerErrorCodes.ALL.toSet().size)
        InstallerErrorCodes.ALL.forEach { code ->
            assertTrue(code, Regex("[A-Z][A-Z_]+").matches(code))
            assertTrue(InstallerErrorCodes.isKnown(code))
        }
        assertFalse(InstallerErrorCodes.isKnown("NOPE"))
        assertFalse(InstallerErrorCodes.isKnown(null))
        assertTrue(InstallerErrorCodes.RETRYABLE_DEFAULTS.all { it in InstallerErrorCodes.ALL })
        assertTrue(InstallerErrorCodes.isRetryableByDefault(InstallerErrorCodes.TIMEOUT))
        assertFalse(InstallerErrorCodes.isRetryableByDefault(InstallerErrorCodes.USER_CANCELLED))
        // Every public String constant of the object is listed exactly once in ALL.
        val declared = constantsOf(InstallerErrorCodes::class.java)
        assertEquals(declared.values.sorted(), InstallerErrorCodes.ALL.sorted())
    }

    private fun constants(prefix: String): Map<String, String> =
        constantsOf(InstallerContract::class.java).filterKeys { it.startsWith(prefix) }

    private fun constantsOf(type: Class<*>): Map<String, String> = type.declaredFields
        .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        .associate { field -> field.isAccessible = true; field.name to field.get(null) as String }
}
