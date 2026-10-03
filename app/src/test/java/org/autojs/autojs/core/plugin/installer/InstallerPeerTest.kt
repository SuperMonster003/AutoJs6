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
        for ((base, max) in listOf(null to null, 0L to 2L, -1L to 2L, 4L to 4L,
            2L to 1L, 1L to 0L, 4_294_967_297L to null, 1L to Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { InstallerPeer.negotiate(base, max, knownFeatures, knownAuthorizers) }
        }
        assertEquals(3, InstallerPeer.negotiate(1, 4, knownFeatures, knownAuthorizers).contractVersion)
        assertEquals(2, InstallerPeer.negotiate(2, null, knownFeatures, knownAuthorizers).contractVersion)
    }

    @Test fun advancedOptionsRequireBothV3AndTheLiveFeatureIncludingExplicitNoOps() {
        val options = listOf(
            InstallerJson.InstallOptions(grantAllRequestedPermissions = false),
            InstallerJson.InstallOptions(requestUpdateOwnership = false),
            InstallerJson.InstallOptions(dexopt = "none"),
            InstallerJson.InstallOptions(installReason = "unknown"),
            InstallerJson.InstallOptions(packageSource = "unspecified"),
            InstallerJson.InstallOptions(installReason = null, providedOptions = setOf("installReason")),
            InstallerJson.InstallOptions(packageSource = null, providedOptions = setOf("packageSource")),
        )
        for (version in listOf(1L, 2L, 3L)) {
            for (features in listOf(emptySet(), knownFeatures)) {
                val peer = InstallerPeer.negotiate(1, version, features, knownAuthorizers)
                peer.requireInstall("auto", "auto")
                for (value in options) {
                    if (version == 3L && InstallerCapabilityKeys.FEATURE_ADVANCED_INSTALL_OPTIONS in features) {
                        peer.requireInstall("auto", "auto", value)
                    } else refused(InstallerErrorCodes.INVALID_ARGUMENT) { peer.requireInstall("auto", "auto", value) }
                }
            }
        }
        // Discovery of one plugin cannot authorize another Binder whose live capability changed.
        val replacement = InstallerPeer.negotiate(1, 3, emptySet(), knownAuthorizers)
        refused(InstallerErrorCodes.INVALID_ARGUMENT) { replacement.requireInstall("auto", "auto", options.first()) }
    }

    @Test fun sourceProfileOptInNeedsPresenceInformationAndTheSamePeersV3Feature() {
        val absent = InstallerJson.InstallOptions(providedOptions = emptySet())
        for (version in listOf(1L, 2L, 3L)) for (features in listOf(emptySet(), knownFeatures)) {
            val peer = InstallerPeer.negotiate(1, version, features, knownAuthorizers)
            assertEquals(version == 3L && InstallerCapabilityKeys.FEATURE_SOURCE_PROFILES in features, peer.usesSourceProfiles(absent))
            assertFalse(peer.usesSourceProfiles(InstallerJson.InstallOptions()))
        }
        val advancedOnly = InstallerPeer.negotiate(1, 3, setOf(InstallerCapabilityKeys.FEATURE_ADVANCED_INSTALL_OPTIONS), knownAuthorizers)
        assertFalse(advancedOnly.usesSourceProfiles(absent))
        advancedOnly.requireInstall("auto", "auto", InstallerJson.InstallOptions(providedOptions = setOf("installReason")))
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
