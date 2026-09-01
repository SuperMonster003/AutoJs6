package org.autojs.autojs.apkbuilder.template

import android.content.Context
import org.autojs.autojs.core.plugin.center.PluginSignatureUtils
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig

/**
 * APK building receives project sources and signing passwords, so its trust boundary is stricter
 * than the general-purpose user-authorized plugin policy.
 */
internal object ApkBuilderPluginTrustPolicy {

    fun isAccepted(context: Context, pluginPackageName: String): Boolean {
        val trustInfo = PluginTrustManager.resolveTrustInfo(context, pluginPackageName)
        if (trustInfo.isOfficial) {
            return true
        }
        if (!BuildConfig.DEBUG) {
            return false
        }
        return acceptsSignature(
            isDebugBuild = true,
            isOfficial = false,
            pluginFingerprints = trustInfo.fingerprintsSha256,
            hostFingerprints = PluginSignatureUtils.getSha256Fingerprints(context, context.packageName),
        )
    }

    fun requireAccepted(context: Context, pluginPackageName: String) {
        if (isAccepted(context, pluginPackageName)) {
            return
        }
        throw SecurityException(
            "APK Builder plugin signing certificate is not trusted: " +
                    "package=$pluginPackageName, sha256=${fingerprintSummary(context, pluginPackageName)}"
        )
    }

    fun fingerprintSummary(context: Context, pluginPackageName: String): String {
        return formatFingerprints(
            PluginTrustManager.resolveTrustInfo(context, pluginPackageName).fingerprintsSha256
        )
    }

    internal fun acceptsSignature(
        isDebugBuild: Boolean,
        isOfficial: Boolean,
        pluginFingerprints: Collection<String>,
        hostFingerprints: Collection<String>,
    ): Boolean {
        if (isOfficial) {
            return true
        }
        if (!isDebugBuild || pluginFingerprints.isEmpty() || hostFingerprints.isEmpty()) {
            return false
        }
        return pluginFingerprints.any(hostFingerprints.toSet()::contains)
    }

    internal fun formatFingerprints(fingerprints: Collection<String>): String {
        return fingerprints
            .map(String::lowercase)
            .distinct()
            .sorted()
            .joinToString(", ")
            .ifBlank { "unavailable" }
    }
}
