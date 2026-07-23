package org.autojs.autojs.apkbuilder.template

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkBuilderPluginTrustPolicyTest {

    @Test
    fun `official signature is accepted in release builds`() {
        assertTrue(
            ApkBuilderPluginTrustPolicy.acceptsSignature(
                isDebugBuild = false,
                isOfficial = true,
                pluginFingerprints = emptyList(),
                hostFingerprints = emptyList(),
            )
        )
    }

    @Test
    fun `same signer exception is limited to debug builds`() {
        val pluginSigner = listOf("debug-certificate")
        val hostSigner = listOf("debug-certificate")

        assertTrue(
            ApkBuilderPluginTrustPolicy.acceptsSignature(
                isDebugBuild = true,
                isOfficial = false,
                pluginFingerprints = pluginSigner,
                hostFingerprints = hostSigner,
            )
        )
        assertFalse(
            ApkBuilderPluginTrustPolicy.acceptsSignature(
                isDebugBuild = false,
                isOfficial = false,
                pluginFingerprints = pluginSigner,
                hostFingerprints = hostSigner,
            )
        )
    }

    @Test
    fun `debug build rejects a plugin signed by a different certificate`() {
        assertFalse(
            ApkBuilderPluginTrustPolicy.acceptsSignature(
                isDebugBuild = true,
                isOfficial = false,
                pluginFingerprints = listOf("plugin-certificate"),
                hostFingerprints = listOf("host-certificate"),
            )
        )
    }
}
