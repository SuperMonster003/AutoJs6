package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginAbiUtilsTest {

    private val fullDistribution = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    @Test fun x86DoesNotMatchAnX8664AssetListedBeforeItsOwnSplit() {
        val assets = listOf("plugin-v1-x86_64-deadbeef.apk", "plugin-v1-x86-deadbeef.apk")
        assertFalse(PluginAbiUtils.assetMatchesAbi(assets[0], "x86"))
        assertEquals(assets[1], assets.first { PluginAbiUtils.assetMatchesAbi(it, "x86") })
        assertTrue(PluginAbiUtils.assetMatchesAbi(assets[0], "x86_64"))
        assertFalse(PluginAbiUtils.assetMatchesAbi(assets[1], "x86_64"))
    }

    @Test fun abiAliasesAndArmFamiliesKeepTheirIdentity() {
        assertTrue(PluginAbiUtils.assetMatchesAbi("plugin-AMD64.apk", "x86_64"))
        assertFalse(PluginAbiUtils.assetMatchesAbi("plugin-AMD64.apk", "x86"))
        assertTrue(PluginAbiUtils.assetMatchesAbi("plugin-ARM64-V8A.apk", "arm64-v8a"))
        assertFalse(PluginAbiUtils.assetMatchesAbi("plugin-arm64-v8a.apk", "armeabi-v7a"))
        assertFalse(PluginAbiUtils.assetMatchesAbi("plugin-universal.apk", "x86"))
    }

    @Test fun indexRestoresFullAceDistributionWhenLegacyInfoOnlyReportsTheInstalledSplit() {
        assertEquals(fullDistribution, PluginAbiUtils.resolveSupportedAbisForDisplay(null, fullDistribution, listOf("x86")))
    }

    @Test fun legacyEmptyInfoDoesNotTurnAnIndexedNativePluginIntoAllAbis() {
        assertEquals(fullDistribution, PluginAbiUtils.resolveSupportedAbisForDisplay(null, fullDistribution, emptyList()))
    }

    @Test fun explicitBuildDeclarationWinsEvenWhenIndexDescribesAnotherRelease() {
        val declared = listOf("arm64-v8a", "x86_64")
        assertEquals(declared, PluginAbiUtils.resolveSupportedAbisForDisplay(declared, fullDistribution, listOf("x86_64")))
    }

    @Test fun explicitUniversalAndUnknownStayDistinct() {
        assertEquals(emptyList<String>(), PluginAbiUtils.resolveSupportedAbisForDisplay(emptyList(), fullDistribution, listOf("x86")))
        assertEquals(emptyList<String>(), PluginAbiUtils.resolveSupportedAbisForDisplay(null, emptyList(), listOf("x86")))
        assertEquals(listOf("x86"), PluginAbiUtils.resolveSupportedAbisForDisplay(null, null, listOf("x86")))
        assertNull(PluginAbiUtils.resolveSupportedAbisForDisplay(null, null, null))
    }
}
