package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object PluginSignatureUtils {

    @Suppress("DEPRECATION")
    fun getSha256Fingerprints(context: Context, packageName: String): List<String> {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(packageName, flags)
        }
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.signingInfo?.apkContentsSigners
        } else {
            pkgInfo.signatures
        }
        val list = signatures?.mapNotNull { sig ->
            runCatching { sha256Hex(sig.toByteArray()) }.getOrNull()
        } ?: emptyList()
        return list.distinct()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
