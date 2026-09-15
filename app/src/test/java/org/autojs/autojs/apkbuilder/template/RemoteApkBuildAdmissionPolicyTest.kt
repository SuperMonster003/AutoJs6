package org.autojs.autojs.apkbuilder.template

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteApkBuildAdmissionPolicyTest {

    @Test
    fun missingCapabilityIsRejected() {
        assertEquals(
            RemoteApkBuildAdmissionPolicy.Decision.CAPABILITY_MISSING,
            decide(capabilityAdvertised = false, advertisedProtocolVersion = 3),
        )
    }

    @Test
    fun missingOrOlderProtocolIsRejected() {
        assertEquals(
            RemoteApkBuildAdmissionPolicy.Decision.PROTOCOL_TOO_OLD,
            decide(capabilityAdvertised = true, advertisedProtocolVersion = 0),
        )
        assertEquals(
            RemoteApkBuildAdmissionPolicy.Decision.PROTOCOL_TOO_OLD,
            decide(capabilityAdvertised = true, advertisedProtocolVersion = 2),
        )
    }

    @Test
    fun currentAndNewerProtocolsAreAllowed() {
        assertEquals(
            RemoteApkBuildAdmissionPolicy.Decision.ALLOW,
            decide(capabilityAdvertised = true, advertisedProtocolVersion = 3),
        )
        assertEquals(
            RemoteApkBuildAdmissionPolicy.Decision.ALLOW,
            decide(capabilityAdvertised = true, advertisedProtocolVersion = 4),
        )
    }

    @Test
    fun nonPositiveRequiredVersionStillRequiresARealAdvertisedProtocol() {
        assertEquals(
            RemoteApkBuildAdmissionPolicy.Decision.PROTOCOL_TOO_OLD,
            RemoteApkBuildAdmissionPolicy.evaluate(true, 0, 0),
        )
        assertEquals(
            RemoteApkBuildAdmissionPolicy.Decision.ALLOW,
            RemoteApkBuildAdmissionPolicy.evaluate(true, 1, 0),
        )
    }

    private fun decide(
        capabilityAdvertised: Boolean,
        advertisedProtocolVersion: Int,
    ) = RemoteApkBuildAdmissionPolicy.evaluate(
        capabilityAdvertised = capabilityAdvertised,
        advertisedProtocolVersion = advertisedProtocolVersion,
        requiredProtocolVersion = 3,
    )
}
