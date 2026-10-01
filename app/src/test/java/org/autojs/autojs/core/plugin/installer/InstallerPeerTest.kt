package org.autojs.autojs.core.plugin.installer

import org.autojs.plugin.installer.api.InstallerCapabilityKeys
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.junit.Assert.*
import org.junit.Test

class InstallerPeerTest {
    private val knownAuthorizers = InstallerContract.AUTHORIZERS.toSet()
    private val knownFeatures = InstallerCapabilityKeys.FEATURES.toSet()

    @Test fun legacyPeersKeepV1DespiteUnnegotiatedNewFeaturesAndAuthorizers() {
        val peer = InstallerPeer.negotiate(1, null, knownFeatures, knownAuthorizers)
        assertEquals(1, peer.contractVersion)
        assertFalse("dhizuku" in peer.authorizers)
        peer.requireInstall("root", "silent")
        peer.requireDefault("auto", "preferred")
        refused(InstallerErrorCodes.AUTHORIZER_UNAVAILABLE) { peer.requireAuthorizer("dhizuku") }
        refused(InstallerErrorCodes.INVALID_ARGUMENT) { peer.requireInstall("none", "notification") }
        refused(InstallerErrorCodes.INVALID_ARGUMENT) { peer.requireDefault("root", "persistent") }
    }

    @Test fun versionAloneDoesNotAdvertiseNotificationOrPersistentDefault() {
        val peer = InstallerPeer.negotiate(1, 2, emptySet(), knownAuthorizers)
        assertEquals(2, peer.contractVersion)
        peer.requireAuthorizer("dhizuku")
        refused(InstallerErrorCodes.INVALID_ARGUMENT) { peer.requireInstall("dhizuku", "notification") }
        refused(InstallerErrorCodes.INVALID_ARGUMENT) { peer.requireDefault("dhizuku", "persistent") }
        peer.requireDefault("dhizuku", "preferred")
    }

    @Test fun advertisedV2ExtensionsAreAcceptedButDoNotCreateAnUnadvertisedAuthorizer() {
        val peer = InstallerPeer.negotiate(1, 2, knownFeatures, knownAuthorizers)
        peer.requireInstall("dhizuku", "notification")
        peer.requireDefault("dhizuku", "persistent")
        val withoutDhizuku = InstallerPeer.negotiate(1, 2, knownFeatures, setOf("none", "root"))
        refused(InstallerErrorCodes.AUTHORIZER_UNAVAILABLE) { withoutDhizuku.requireInstall("dhizuku", "auto") }
        refused(InstallerErrorCodes.AUTHORIZER_UNAVAILABLE) { withoutDhizuku.requireDefault("dhizuku", "persistent") }
        withoutDhizuku.requireInstall("auto", "notification")
    }

    @Test fun versionRangesRejectOverflowAndUnsupportedMinimumButNegotiateAFutureMaximum() {
        for ((base, max) in listOf(null to null, 0L to 2L, -1L to 2L, 3L to 3L,
            2L to 1L, 1L to 0L, 4_294_967_297L to null, 1L to Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { InstallerPeer.negotiate(base, max, knownFeatures, knownAuthorizers) }
        }
        assertEquals(2, InstallerPeer.negotiate(1, 3, knownFeatures, knownAuthorizers).contractVersion)
        assertEquals(2, InstallerPeer.negotiate(2, null, knownFeatures, knownAuthorizers).contractVersion)
    }

    @Test fun unknownVocabularyNeverBecomesSupported() {
        val peer = InstallerPeer.negotiate(1, 2, setOf("invented"), setOf("invented", "none"))
        assertEquals(emptySet<String>(), peer.features)
        assertEquals(setOf("none"), peer.authorizers)
        assertThrows(IllegalArgumentException::class.java) { peer.requireAuthorizer("invented") }
        assertThrows(IllegalArgumentException::class.java) { peer.requireInstall("none", "invented") }
        assertThrows(IllegalArgumentException::class.java) { peer.requireDefault("none", "invented") }
    }

    private fun refused(code: String, operation: () -> Unit) {
        val failure = assertThrows(InstallerPluginException::class.java, operation)
        assertEquals(code, failure.code)
        assertFalse(failure.retryable)
    }
}
