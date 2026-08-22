package org.autojs.autojs.core.plugin.jvm

/** Classifies provider state drift without treating package-manager state as runtime evidence. */
internal object JvmSourceProviderLifecyclePolicy {
    enum class Loss {
        UNINSTALLED,
        DISABLED,
        UPDATED,
        ROLLED_BACK,
        IDENTITY_CHANGED,
    }

    fun loss(
        pinned: JvmSourcePinnedIdentity,
        current: JvmSourceServiceCandidate?,
    ): Loss? = when {
        current == null -> Loss.UNINSTALLED
        !current.applicationEnabled || !current.serviceEnabled || !current.pluginEnabled -> Loss.DISABLED
        JvmSourceProviderPolicy.rejection(current, pinned.component) != null ||
            current.component != pinned.component ||
            current.compilerProcessName != pinned.compilerProcessName ||
            current.packageUid != pinned.packageUid ||
            current.signerSha256.map(String::lowercase).distinct().sorted() != pinned.signerSha256 ->
            Loss.IDENTITY_CHANGED
        current.versionCode < pinned.versionCode -> Loss.ROLLED_BACK
        current.versionCode != pinned.versionCode || current.lastUpdateTime != pinned.lastUpdateTime -> Loss.UPDATED
        else -> null
    }
}

internal enum class JvmSourceProviderTransportLoss {
    BINDER_DIED,
    SERVICE_DISCONNECTED,
    BINDING_DIED,
}
