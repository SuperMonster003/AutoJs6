package org.autojs.autojs.apkbuilder.template

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.android.apksig.ApkVerifier
import java.io.File
import java.io.IOException

internal data class RemoteBuildExpectedApkIdentity(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val signerCertificateSha256: String,
)

/**
 * Treats the plugin-returned file as untrusted until the archive, cryptographic signature, and
 * requested package identity all validate. Error messages intentionally avoid plugin-controlled
 * archive names and signature diagnostics.
 */
internal object RemoteBuildApkValidator {

    @Throws(IOException::class)
    fun validate(
        context: Context,
        apkFile: File,
        expectedIdentity: RemoteBuildExpectedApkIdentity,
    ) {
        RemoteBuildApkArchivePolicy.validate(apkFile)
        verifyCryptographicSignature(apkFile, expectedIdentity.signerCertificateSha256)
        verifyPackageIdentity(context.applicationContext, apkFile, expectedIdentity)
    }

    private fun verifyCryptographicSignature(
        apkFile: File,
        expectedSignerCertificateSha256: String,
    ) {
        val result = try {
            ApkVerifier.Builder(apkFile).build().verify()
        } catch (error: Throwable) {
            throw IOException("Remote output APK signature verification could not be completed.", error)
        }
        if (!result.isVerified || result.signerCertificates.isEmpty()) {
            throw IOException("Remote output APK signature verification failed.")
        }
        RemoteBuildExpectedSignerResolver.requireExactSignerMatch(
            signerCertificates = result.signerCertificates,
            expectedCertificateSha256 = expectedSignerCertificateSha256,
        )
    }

    @Suppress("DEPRECATION")
    private fun verifyPackageIdentity(
        context: Context,
        apkFile: File,
        expectedIdentity: RemoteBuildExpectedApkIdentity,
    ) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = try {
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } catch (error: Throwable) {
            throw IOException("Remote output APK manifest could not be parsed.", error)
        } ?: throw IOException("Remote output APK manifest could not be parsed.")

        if (
            packageInfo.packageName != expectedIdentity.packageName ||
            packageInfo.versionName != expectedIdentity.versionName ||
            packageInfo.longVersionCodeCompat() != expectedIdentity.versionCode
        ) {
            throw IOException("Remote output APK identity does not match the requested project.")
        }
        val hasPlatformVisibleSigner = packageInfo.signingCertificateCountCompat() > 0 ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                legacyArchiveSignerCount(context, apkFile, expectedIdentity) > 0)
        if (!hasPlatformVisibleSigner) {
            throw IOException("Remote output APK has no platform-visible signer.")
        }
    }

    /**
     * Some Android 9-11 PackageManager implementations parse an uninstalled V2/V3 archive's
     * identity with GET_SIGNING_CERTIFICATES but leave signingInfo empty. The deprecated archive
     * view still exposes the signer on those releases. This is only a platform-visibility
     * compatibility check: [verifyCryptographicSignature] remains the authoritative signature
     * verification gate and must already have succeeded.
     */
    @Suppress("DEPRECATION")
    private fun legacyArchiveSignerCount(
        context: Context,
        apkFile: File,
        expectedIdentity: RemoteBuildExpectedApkIdentity,
    ): Int {
        val packageInfo = try {
            context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_SIGNATURES,
            )
        } catch (_: Throwable) {
            null
        } ?: return 0
        if (
            packageInfo.packageName != expectedIdentity.packageName ||
            packageInfo.versionName != expectedIdentity.versionName ||
            packageInfo.longVersionCodeCompat() != expectedIdentity.versionCode
        ) {
            return 0
        }
        return packageInfo.signatures?.size ?: 0
    }

    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }

    private fun PackageInfo.signingCertificateCountCompat(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners?.size ?: 0
        } else {
            @Suppress("DEPRECATION")
            signatures?.size ?: 0
        }
}
