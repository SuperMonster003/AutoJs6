package org.autojs.autojs.ui.main.scripts

import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.os.Build
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
            setAppLabel(verified.appLabel)
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
            PackageInstallStatusCoordinator.register(context, sessionId, verified.packageName, verified.appLabel)
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
                val callbackIntent = PackageInstallStatusCoordinator.callbackIntent(
                    context = context,
                    sessionId = sessionId,
                )
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val callback = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callbackIntent,
                    flags,
                )
                session.commit(callback.intentSender)
            }
            return sessionId
        } catch (error: Throwable) {
            runCatching { packageInstaller.abandonSession(sessionId) }
            runCatching { PackageInstallStatusCoordinator.acknowledge(context, sessionId) }
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
        if (baseInfo.signerDigests().isEmpty() && legacyArchiveSignerDigests(packageManager, baseApk.file).isEmpty()) {
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
        val appLabel = runCatching {
            baseInfo.applicationInfo?.apply {
                sourceDir = baseApk.file.absolutePath
                publicSourceDir = baseApk.file.absolutePath
            }?.loadLabel(packageManager)?.toString()?.takeIf(String::isNotBlank)?.take(256)
        }.getOrNull() ?: packageName
        return VerifiedPackageSet(packageName, appLabel)
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

    /**
     * Android 9-11 may parse an uninstalled V2/V3 archive with GET_SIGNING_CERTIFICATES but
     * leave signingInfo empty, while the deprecated archive view still exposes the signer.
     * This is only a platform-visibility check: the session commit remains the authoritative
     * signature verification.
     *
     * zh-CN: Android 9-11 用 GET_SIGNING_CERTIFICATES 解析未安装的 V2/V3 归档时 signingInfo 可能为空,
     * 而已弃用的归档视图仍能拿到签名者. 这里只做平台可见性检查, 会话提交仍是权威的签名校验.
     */
    @Suppress("DEPRECATION")
    private fun legacyArchiveSignerDigests(packageManager: PackageManager, apkFile: File): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptySet()
        val info = runCatching {
            packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
        }.getOrNull() ?: return emptySet()
        return info.signatures.orEmpty().mapTo(linkedSetOf()) { signature -> signature.sha256() }
    }

    private fun android.content.pm.Signature.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

    private data class VerifiedPackageSet(
        val packageName: String,
        val appLabel: String,
    )
}
