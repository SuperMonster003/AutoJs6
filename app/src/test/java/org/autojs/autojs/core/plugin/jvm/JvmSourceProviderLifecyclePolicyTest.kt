package org.autojs.autojs.core.plugin.jvm

import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JvmSourceProviderLifecyclePolicyTest {
    private val component = JvmSourceComponent("org.example.provider", "org.example.provider.CompilerService")
    private val candidate = JvmSourceServiceCandidate(
        component = component,
        declaredAction = JvmSourceContract.SERVICE_ACTION,
        exported = true,
        applicationEnabled = true,
        serviceEnabled = true,
        pluginEnabled = true,
        providerAuthorized = true,
        requiredPermission = JvmSourceContract.PLUGIN_PERMISSION,
        compilerProcessName = "org.example.provider:compiler",
        packageUid = 20_000,
        versionCode = 7,
        lastUpdateTime = 11,
        signerSha256 = setOf("a".repeat(64)),
    )
    private val pinned = candidate.pin()

    @Test
    fun classifiesUninstallDisableAndUpdateBeforeDispatch() {
        assertEquals(JvmSourceProviderLifecyclePolicy.Loss.UNINSTALLED, lifecycleLoss(null))
        assertEquals(
            JvmSourceProviderLifecyclePolicy.Loss.DISABLED,
            lifecycleLoss(candidate.copy(pluginEnabled = false)),
        )
        assertEquals(
            JvmSourceProviderLifecyclePolicy.Loss.UPDATED,
            lifecycleLoss(candidate.copy(versionCode = 8, lastUpdateTime = 12)),
        )
        assertEquals(
            JvmSourceProviderLifecyclePolicy.Loss.ROLLED_BACK,
            lifecycleLoss(candidate.copy(versionCode = 6, lastUpdateTime = 12)),
        )
    }

    @Test
    fun stableIdentityIsAcceptedAndOtherDriftFailsClosed() {
        assertNull(lifecycleLoss(candidate))
        assertEquals(
            JvmSourceProviderLifecyclePolicy.Loss.IDENTITY_CHANGED,
            lifecycleLoss(candidate.copy(signerSha256 = setOf("b".repeat(64)))),
        )
        assertEquals(
            JvmSourceProviderLifecyclePolicy.Loss.IDENTITY_CHANGED,
            lifecycleLoss(
                candidate.copy(
                    versionCode = 8,
                    lastUpdateTime = 12,
                    signerSha256 = setOf("b".repeat(64)),
                ),
            ),
        )
    }

    private fun lifecycleLoss(current: JvmSourceServiceCandidate?) =
        JvmSourceProviderLifecyclePolicy.loss(pinned, current)
}
