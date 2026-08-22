package org.autojs.autojs.core.plugin.jvm

import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmSourceProviderPolicyTest {
    @Test
    fun componentParserRequiresExactFullyQualifiedPersistence() {
        assertEquals(COMPONENT, JvmSourceComponent.parseExact(COMPONENT.flattened))
        assertNull(JvmSourceComponent.parseExact(" $PACKAGE/$SERVICE"))
        assertNull(JvmSourceComponent.parseExact("$PACKAGE/.service.Compiler"))
        assertNull(JvmSourceComponent.parseExact("$PACKAGE/$SERVICE/extra"))
    }

    @Test
    fun candidateMustMatchEveryDiscoveryAndTrustBoundary() {
        assertNull(JvmSourceProviderPolicy.rejection(candidate(), COMPONENT))
        assertEquals(
            JvmSourceCandidateRejection.WRONG_ACTION,
            JvmSourceProviderPolicy.rejection(candidate(action = "wrong"), COMPONENT),
        )
        assertEquals(
            JvmSourceCandidateRejection.NOT_EXPORTED,
            JvmSourceProviderPolicy.rejection(candidate(exported = false), COMPONENT),
        )
        assertEquals(
            JvmSourceCandidateRejection.DISABLED,
            JvmSourceProviderPolicy.rejection(candidate(pluginEnabled = false), COMPONENT),
        )
        assertEquals(
            JvmSourceCandidateRejection.UNAUTHORIZED,
            JvmSourceProviderPolicy.rejection(candidate(authorized = false), COMPONENT),
        )
        assertEquals(
            JvmSourceCandidateRejection.WRONG_PERMISSION,
            JvmSourceProviderPolicy.rejection(candidate(permission = "wrong.permission"), COMPONENT),
        )
        assertEquals(
            JvmSourceCandidateRejection.WRONG_COMPILER_PROCESS,
            JvmSourceProviderPolicy.rejection(candidate(compilerProcessName = "$PACKAGE:other"), COMPONENT),
        )
        assertEquals(
            JvmSourceCandidateRejection.INVALID_IDENTITY,
            JvmSourceProviderPolicy.rejection(candidate().copy(packageUid = 0), COMPONENT),
        )
        assertEquals(
            JvmSourceCandidateRejection.INVALID_IDENTITY,
            JvmSourceProviderPolicy.rejection(candidate().copy(lastUpdateTime = 0), COMPONENT),
        )
    }

    @Test
    fun selectionPinsOnlyExactComponentAndRevalidationDetectsAllIdentityDrift() {
        val selected = candidate()
        val pinned = requireNotNull(
            JvmSourceProviderPolicy.selectAndPin(
                listOf(selected, candidate(component = JvmSourceComponent(PACKAGE, "$PACKAGE.OtherService"))),
                COMPONENT,
            ),
        )

        assertTrue(JvmSourceProviderPolicy.revalidate(pinned, selected))
        assertFalse(JvmSourceProviderPolicy.revalidate(pinned, selected.copy(packageUid = 10_002)))
        assertFalse(JvmSourceProviderPolicy.revalidate(pinned, selected.copy(versionCode = 8)))
        assertFalse(JvmSourceProviderPolicy.revalidate(pinned, selected.copy(lastUpdateTime = 124)))
        assertFalse(JvmSourceProviderPolicy.revalidate(pinned, selected.copy(compilerProcessName = "$PACKAGE:other")))
        assertFalse(JvmSourceProviderPolicy.revalidate(pinned, selected.copy(signerSha256 = setOf("bb".repeat(32)))))
    }

    @Test
    fun ordinaryPluginAuthorizationCannotReplaceCompilerSameSignerTrust() {
        assertTrue(JvmSourceCompilerTrustPolicy.isAuthorized(sameSignerAsHost = true))
        assertFalse(JvmSourceCompilerTrustPolicy.isAuthorized(sameSignerAsHost = false))
    }

    private fun candidate(
        component: JvmSourceComponent = COMPONENT,
        action: String = JvmSourceContract.SERVICE_ACTION,
        exported: Boolean = true,
        pluginEnabled: Boolean = true,
        authorized: Boolean = true,
        permission: String? = JvmSourceContract.PLUGIN_PERMISSION,
        compilerProcessName: String = "$PACKAGE:compiler",
    ) = JvmSourceServiceCandidate(
        component = component,
        declaredAction = action,
        exported = exported,
        applicationEnabled = true,
        serviceEnabled = true,
        pluginEnabled = pluginEnabled,
        providerAuthorized = authorized,
        requiredPermission = permission,
        compilerProcessName = compilerProcessName,
        packageUid = 10_001,
        versionCode = 7,
        lastUpdateTime = 123,
        signerSha256 = setOf("aa".repeat(32)),
    )

    private companion object {
        const val PACKAGE = "org.example.jvmprovider"
        const val SERVICE = "org.example.jvmprovider.service.CompilerService"
        val COMPONENT = JvmSourceComponent(PACKAGE, SERVICE)
    }
}
