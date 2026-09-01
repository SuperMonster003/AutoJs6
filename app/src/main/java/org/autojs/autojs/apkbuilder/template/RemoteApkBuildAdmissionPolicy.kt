package org.autojs.autojs.apkbuilder.template

/** Pure, fail-closed admission policy for the experimental remote APK build path. */
internal object RemoteApkBuildAdmissionPolicy {

    enum class Decision {
        ALLOW,
        RUNTIME_DISABLED,
        CAPABILITY_MISSING,
        PROTOCOL_TOO_OLD,
    }

    fun evaluate(
        runtimeEnabled: Boolean,
        capabilityAdvertised: Boolean,
        advertisedProtocolVersion: Int,
        requiredProtocolVersion: Int,
    ): Decision {
        if (!runtimeEnabled) {
            return Decision.RUNTIME_DISABLED
        }
        if (!capabilityAdvertised) {
            return Decision.CAPABILITY_MISSING
        }
        val effectiveRequiredProtocolVersion = requiredProtocolVersion.coerceAtLeast(1)
        if (advertisedProtocolVersion < effectiveRequiredProtocolVersion) {
            return Decision.PROTOCOL_TOO_OLD
        }
        return Decision.ALLOW
    }
}
