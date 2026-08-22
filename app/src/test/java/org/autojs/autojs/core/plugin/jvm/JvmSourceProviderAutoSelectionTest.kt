package org.autojs.autojs.core.plugin.jvm

import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JvmSourceProviderAutoSelectionTest {
    @Test
    fun retainsOnlyAStillAdmissiblePersistedComponent() {
        assertEquals(
            OLD_COMPONENT,
            JvmSourceProviderAutoSelection.retainPersisted(OLD_COMPONENT, candidate(OLD_COMPONENT)),
        )
        assertNull(JvmSourceProviderAutoSelection.retainPersisted(OLD_COMPONENT, null))
        assertNull(
            JvmSourceProviderAutoSelection.retainPersisted(
                OLD_COMPONENT,
                candidate(OLD_COMPONENT).copy(serviceEnabled = false),
            ),
        )
        assertNull(
            JvmSourceProviderAutoSelection.retainPersisted(
                OLD_COMPONENT,
                candidate(NEW_COMPONENT),
            ),
        )
    }

    @Test
    fun selectsTheNewPackageAfterTheOldPersistedPackageDisappears() {
        assertNull(JvmSourceProviderAutoSelection.retainPersisted(OLD_COMPONENT, null))
        assertEquals(
            NEW_COMPONENT,
            JvmSourceProviderAutoSelection.singleAdmissible(listOf(candidate(NEW_COMPONENT)))?.component,
        )
    }

    @Test
    fun refusesAmbiguousOrUntrustedAutomaticSelection() {
        assertNull(
            JvmSourceProviderAutoSelection.singleAdmissible(
                listOf(candidate(OLD_COMPONENT), candidate(NEW_COMPONENT)),
            ),
        )
        assertNull(
            JvmSourceProviderAutoSelection.singleAdmissible(
                listOf(candidate(NEW_COMPONENT).copy(providerAuthorized = false)),
            ),
        )
    }

    private fun candidate(component: JvmSourceComponent) = JvmSourceServiceCandidate(
        component = component,
        declaredAction = JvmSourceContract.SERVICE_ACTION,
        exported = true,
        applicationEnabled = true,
        serviceEnabled = true,
        pluginEnabled = true,
        providerAuthorized = true,
        requiredPermission = JvmSourceContract.PLUGIN_PERMISSION,
        compilerProcessName = "${component.packageName}:compiler",
        packageUid = if (component == OLD_COMPONENT) 10_001 else 10_002,
        versionCode = if (component == OLD_COMPONENT) 1 else 2,
        lastUpdateTime = if (component == OLD_COMPONENT) 100 else 200,
        signerSha256 = setOf("aa".repeat(32)),
    )

    private companion object {
        val OLD_COMPONENT = JvmSourceComponent(
            "org.autojs.plugin.jvmsource.java",
            "org.autojs.plugin.jvmsource.java.service.JavaSourceCompilerService",
        )
        val NEW_COMPONENT = JvmSourceComponent(
            "io.github.supermonster003.autojs6.plugin.java.runtime",
            "org.autojs.plugin.jvmsource.java.service.JavaSourceCompilerService",
        )
    }
}
