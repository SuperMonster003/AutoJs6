package org.autojs.autojs.core.plugin.ai

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.plugin.ai.text.api.AiTextProtocol
import java.security.MessageDigest

/**
 * Read-only Android service discovery for AI text providers.
 *
 * This adapter neither binds a service nor constructs a selectable provider candidate. It only
 * snapshots local package facts so a later Binder handshake can obtain provider metadata and
 * capabilities before the existing selection policy is allowed to run.
 */
internal class AndroidAiTextProviderDiscovery(
    context: Context,
) : AiTextProviderDiscoverySource {

    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager

    override fun discoverExactAction(action: String): AiTextProviderDiscoverySourceResult {
        if (action != AiTextProtocol.ACTION_TEXT_PROVIDER) {
            return AiTextProviderDiscoverySourceResult.WrongAction
        }
        return queryExactAction(packageName = null)
    }

    override fun inspectExact(
        component: AiTextProviderComponent,
    ): AiTextProviderDiscoverySourceResult = when (
        val source = queryExactAction(packageName = component.packageName)
    ) {
        AiTextProviderDiscoverySourceResult.WrongAction -> source
        is AiTextProviderDiscoverySourceResult.QueryFailed -> source
        is AiTextProviderDiscoverySourceResult.Completed ->
            AiTextProviderDiscoverySourceResult.Completed(
                source.entries.filter { entry -> entry.mayDescribe(component) },
            )
    }

    private fun queryExactAction(packageName: String?): AiTextProviderDiscoverySourceResult {
        val resolved = try {
            val intent = Intent(AiTextProtocol.ACTION_TEXT_PROVIDER).apply {
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                if (packageName != null) setPackage(packageName)
            }
            val flags = PackageManager.MATCH_DISABLED_COMPONENTS.toLong()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(flags))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentServices(intent, flags.toInt())
            }
        } catch (_: Exception) {
            return AiTextProviderDiscoverySourceResult.QueryFailed()
        }
        return AiTextProviderDiscoverySourceResult.Completed(
            resolved.map { resolveInfo ->
                val serviceInfo = resolveInfo.serviceInfo
                if (serviceInfo == null) {
                    AiTextProviderDiscoverySourceEntry.ReadFailed(reference = null)
                } else {
                    readEntry(serviceInfo)
                }
            },
        )
    }

    private fun readEntry(serviceInfo: ServiceInfo): AiTextProviderDiscoverySourceEntry {
        val reference = AiTextProviderDiscoveryReference(
            packageName = serviceInfo.packageName,
            className = serviceInfo.name,
        )
        return try {
            val packageName = serviceInfo.packageName
            val packageFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(packageFlags.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, packageFlags)
            }
            val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(
                        PackageManager.MATCH_DISABLED_COMPONENTS.toLong(),
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
            }
            val resolvedApplicationInfo = requireNotNull(serviceInfo.applicationInfo) {
                "AI text provider service has no application identity"
            }
            require(packageInfo.packageName == packageName) {
                "AI text provider package snapshot changed"
            }
            require(applicationInfo.packageName == packageName) {
                "AI text provider application snapshot changed"
            }
            require(
                resolvedApplicationInfo.packageName == packageName &&
                    resolvedApplicationInfo.uid == applicationInfo.uid,
            ) {
                "AI text provider service identity changed"
            }
            val signatures = requireNotNull(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }) { "AI text provider has no current signer" }
            require(signatures.isNotEmpty()) { "AI text provider has no current signer" }
            val signerSha256 = signatures.map { signature ->
                sha256Hex(signature.toByteArray())
            }
            AiTextProviderDiscoverySourceEntry.Read(
                AiTextProviderDiscoveryInput(
                    packageName = packageName,
                    className = serviceInfo.name,
                    declaredAction = AiTextProtocol.ACTION_TEXT_PROVIDER,
                    exported = serviceInfo.exported,
                    applicationEnabled = applicationInfo.enabled,
                    serviceEnabled = serviceInfo.enabled,
                    requiredPermission = serviceInfo.permission,
                    packageUid = applicationInfo.uid,
                    versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
                    lastUpdateTime = packageInfo.lastUpdateTime,
                    signerSha256 = signerSha256,
                    officialSignerMatch = signerSha256.any {
                        it in PluginTrustManager.OFFICIAL_SHA_256
                    },
                    sameSignerAsHost = packageManager.checkSignatures(
                        applicationContext.packageName,
                        packageName,
                    ) == PackageManager.SIGNATURE_MATCH,
                    debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
                ),
            )
        } catch (_: Exception) {
            AiTextProviderDiscoverySourceEntry.ReadFailed(reference)
        }
    }

    private fun AiTextProviderDiscoverySourceEntry.mayDescribe(
        component: AiTextProviderComponent,
    ): Boolean = when (this) {
        is AiTextProviderDiscoverySourceEntry.Read ->
            input.packageName == component.packageName && input.className == component.className
        is AiTextProviderDiscoverySourceEntry.ReadFailed -> reference == null ||
            reference.packageName == component.packageName && reference.className == component.className
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val result = StringBuilder(digest.size * 2)
        digest.forEach { value ->
            val unsigned = value.toInt() and 0xff
            result.append(HEX_DIGITS[unsigned ushr 4])
            result.append(HEX_DIGITS[unsigned and 0x0f])
        }
        return result.toString()
    }

    private companion object {
        const val HEX_DIGITS = "0123456789abcdef"
    }
}
