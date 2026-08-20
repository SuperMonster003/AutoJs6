package org.autojs.autojs.ui.main.scripts

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.os.Build
import android.os.Bundle
import java.io.File
import java.io.IOException
import java.security.MessageDigest

internal object AndroidPackageSessionInstaller {

    fun install(
        context: Context,
        archive: AndroidPackageArchive,
        stagingDirectory: File,
    ): Int {
        if (!archive.canInstall) throw IOException("The package archive is not installable")
        val stagedApks = archive.stageSelectedApks(stagingDirectory)
        val verified = verifyPackageSet(context.packageManager, stagedApks)
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(verified.packageName)
            setSize(stagedApks.sumOf { it.file.length() })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setInstallReason(PackageManager.INSTALL_REASON_USER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE)
            }
        }
        val sessionId = packageInstaller.createSession(params)
        try {
            packageInstaller.openSession(sessionId).use { session ->
                stagedApks.forEachIndexed { index, staged ->
                    val sessionName = staged.manifest.splitName
                        ?.takeIf(String::isNotBlank)
                        ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
                        ?.let { "${index.toString().padStart(3, '0')}-$it.apk" }
                        ?: "base.apk"
                    val output = session.openWrite(sessionName, 0L, staged.file.length())
                    try {
                        staged.file.inputStream().buffered().use { input -> input.copyTo(output) }
                        session.fsync(output)
                    } finally {
                        output.close()
                    }
                }
                val callbackIntent = PackageInstallStatusActivity.createIntent(
                    context = context,
                    sessionId = sessionId,
                )
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val callback = PendingIntent.getActivity(
                    context,
                    sessionId,
                    callbackIntent,
                    flags,
                    createPendingIntentOptions(),
                )
                session.commit(callback.intentSender)
            }
            return sessionId
        } catch (error: Throwable) {
            runCatching { packageInstaller.abandonSession(sessionId) }
            throw error
        }
    }

    private fun verifyPackageSet(
        packageManager: PackageManager,
        stagedApks: List<StagedApk>,
    ): VerifiedPackageSet {
        if (stagedApks.isEmpty()) throw IOException("The selected APK set is empty")
        val baseApk = stagedApks.singleOrNull { it.manifest.splitName.isNullOrBlank() }
            ?: throw IOException("The selected APK set must contain exactly one base APK")
        val packageName = baseApk.manifest.packageName?.takeIf(String::isNotBlank)
            ?: throw IOException("Base APK package name is missing")
        val versionCode = baseApk.manifest.versionCode
            ?: throw IOException("Base APK version code is missing")

        stagedApks.forEach { staged ->
            if (staged.manifest.packageName != packageName) {
                throw IOException("APK package names do not match")
            }
            if (staged.manifest.versionCode != versionCode) {
                throw IOException("APK version codes do not match")
            }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        @Suppress("DEPRECATION")
        val baseInfo = packageManager.getPackageArchiveInfo(baseApk.file.absolutePath, flags)
            ?: throw IOException("Unable to parse the base APK")
        if (baseInfo.packageName != packageName || baseInfo.longVersionCodeCompat() != versionCode) {
            throw IOException("Base APK metadata does not match its manifest")
        }
        if (baseInfo.signerDigests().isEmpty()) {
            throw IOException("Base APK has no verifiable signer")
        }

        // getPackageArchiveInfo() treats a single split APK as a monolithic/base package and
        // rejects it. API 36 exposes a verifier that accepts any APK file, including a split.
        // Older releases still perform the authoritative cross-APK signature check when the
        // PackageInstaller session is committed.
        if (Build.VERSION.SDK_INT >= 36) {
            val signerSets = stagedApks.map { staged ->
                PackageManager.getVerifiedSigningInfo(
                    staged.file.absolutePath,
                    SigningInfo.VERSION_JAR,
                ).apkContentsSigners
                    .mapTo(linkedSetOf()) { signature ->
                        signature.sha256()
                    }
                    .takeIf(Set<String>::isNotEmpty)
                    ?: throw IOException("APK has no verifiable signer: ${staged.file.name}")
            }
            if (signerSets.any { it != signerSets.first() }) {
                throw IOException("APK signing certificates do not match")
            }
        }
        return VerifiedPackageSet(packageName)
    }

    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }

    private fun PackageInfo.signerDigests(): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            signatures.orEmpty()
        }
        return signatures.mapTo(linkedSetOf()) { signature ->
            signature.sha256()
        }
    }

    private fun android.content.pm.Signature.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

    /**
     * PackageInstaller can deliver its user-confirmation callback after the coordinator has left
     * the foreground. The callback PendingIntent exists solely to open that confirmation flow, so
     * opt its creator in explicitly on Android releases that require it.
     */
    private fun createPendingIntentOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < 34) return null
        return ActivityOptions.makeBasic()
            .setPendingIntentCreatorBackgroundActivityStartMode(
                if (Build.VERSION.SDK_INT >= 36) {
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                } else {
                    @Suppress("DEPRECATION")
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                },
            )
            .toBundle()
    }

    private data class VerifiedPackageSet(
        val packageName: String,
    )
}
