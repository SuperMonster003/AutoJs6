package org.autojs.autojs.apkbuilder.template

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteApkBuildAdmissionPolicyTest {

    @Test
    fun runtimeSwitchOverridesOtherwiseCompatibleProvider() {
        assertEquals(
            RemoteApkBuildAdmissionPolicy.Decision.RUNTIME_DISABLED,
            decide(runtimeEnabled = false, capabilityAdvertised = true, advertisedProtocolVersion = 3),
        )
    }

    @Test
    fun missingCapabilityIsRejectedWhenRuntimeIsEnabled() {
        assertEquals(
            RemoteApkBuildAdmissionPolicy.Decision.CAPABILITY_MISSING,
            decide(runtimeEnabled = true, capabilityAdvertised = false, advertisedProtocolVersion = 3),
        )
    }

    @Test
    fun missingOrOlderProtocolIsRejected() {
        assertEquals(
            RemoteApkBuildAdmissionPolicy.Decision.PROTOCOL_TOO_OLD,
            decide(runtimeEnabled = true, capabilityAdvertised = true, advertisedProtocolVersion = 0),
        )
        assertEquals(
            RemoteApkBuildAdmissionPolicy.Decision.PROTOCOL_TOO_OLD,
            decide(runtimeEnabled = true, capabilityAdvertised = true, advertisedProtocolVersion = 2),
        )
    }

    @Test
    fun currentAndNewerProtocolsAreAllowed() {
        assertEquals(
            RemoteApkBuildAdmissionPolicy.Decision.ALLOW,
            decide(runtimeEnabled = true, capabilityAdvertised = true, advertisedProtocolVersion = 3),
        )
        assertEquals(
            RemoteApkBuildAdmissionPolicy.Decision.ALLOW,
            decide(runtimeEnabled = true, capabilityAdvertised = true, advertisedProtocolVersion = 4),
        )
    }

    @Test
    fun nonPositiveRequiredVersionStillRequiresARealAdvertisedProtocol() {
        assertEquals(
            RemoteApkBuildAdmissionPolicy.Decision.PROTOCOL_TOO_OLD,
            RemoteApkBuildAdmissionPolicy.evaluate(true, true, 0, 0),
        )
        assertEquals(
            RemoteApkBuildAdmissionPolicy.Decision.ALLOW,
            RemoteApkBuildAdmissionPolicy.evaluate(true, true, 1, 0),
        )
    }

    private fun decide(
        runtimeEnabled: Boolean,
        capabilityAdvertised: Boolean,
        advertisedProtocolVersion: Int,
    ) = RemoteApkBuildAdmissionPolicy.evaluate(
        runtimeEnabled = runtimeEnabled,
        capabilityAdvertised = capabilityAdvertised,
        advertisedProtocolVersion = advertisedProtocolVersion,
        requiredProtocolVersion = 3,
    )
}
