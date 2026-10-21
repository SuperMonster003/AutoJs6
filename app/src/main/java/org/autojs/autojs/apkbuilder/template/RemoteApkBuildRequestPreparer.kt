package org.autojs.autojs.apkbuilder.template

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.content.pm.PackageInfoCompat
import com.google.gson.GsonBuilder
import org.autojs.autojs.core.plugin.PluginAutoWake
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginWakeManager
import org.autojs.autojs.core.plugin.imagequant.ImageQuantPluginHost
import org.autojs.autojs.core.plugin.nativealign.ElfDynamicInfo
import org.autojs.autojs.core.plugin.opencv.BundledOpenCvRuntime
import org.autojs.autojs.core.plugin.opencv.OpenCvPluginHost
import org.autojs.autojs.core.plugin.typescript.TypeScriptApkBuildCompiler
import org.autojs.autojs.core.plugin.typescript.TypeScriptApkBuildPlan
import org.autojs.autojs.apkbuilder.ApkBuildLibrary
import org.autojs.autojs.apkbuilder.ApkBuildLibraryCatalog
import org.autojs.autojs.apkbuilder.ApkPluginLibrary
import org.autojs.autojs.apkbuilder.ApkPluginLibraryVariant
import org.autojs.autojs.apkbuilder.keystore.AESUtils
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.ScriptFileEncoding
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.apkbuilder.template.ApkBuildProgress
import org.autojs.plugin.apkbuilder.template.ApkBuildRequest
import org.autojs.plugin.apkbuilder.template.ApkBuildRequestExtraKeys
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateProtocol
import org.autojs.plugin.apkbuilder.template.TypeScriptBuildStagingCipher
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.opencv.api.OpenCvPluginContract
import org.autojs.plugin.nodejs.api.NodeJsPluginActions
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.SyncFailedException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object RemoteApkBuildRequestPreparer {

    private const val ARCHIVE_FORMAT_VERSION = 1
    private const val SOURCE_ROOT_ENTRY = "project"
    private const val REMOTE_ICON_ENTRY = "__autojs6_remote__/icon.png"
    private const val BUFFER_SIZE = 256 * 1024
    private const val PLUGIN_INFO_QUERY_TIMEOUT_SECONDS = 60L
    private val remoteConfigGson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    private const val HOST_SOURCE_LABEL = "AutoJs6"
    private val NATIVE_LIBRARY_ENTRY_ROOTS = listOf("lib", "libs")
    private val PLUGIN_WAKE_RETRY_DELAYS_MS = longArrayOf(300L, 800L, 1500L)

    private data class SelectedPluginArtifact(
        val library: ApkBuildLibrary,
        val serviceInfo: ServiceInfo,
        val variant: ApkPluginLibraryVariant,
    )

    private data class ArchivedProjectSource(
        val path: String,
        val kind: String,
    )

    private class TypeScriptStagingEncryption private constructor(
        val key: ByteArray,
        val encryptedPaths: Set<String>,
    ) : Closeable {

        fun addTo(extras: Bundle) {
            extras.putInt(
                ApkBuildRequestExtraKeys.TYPESCRIPT_STAGING_ENCRYPTION_VERSION,
                TypeScriptBuildStagingCipher.VERSION,
            )
            extras.putByteArray(
                ApkBuildRequestExtraKeys.TYPESCRIPT_STAGING_ENCRYPTION_KEY,
                key.copyOf(),
            )
            extras.putStringArrayList(
                ApkBuildRequestExtraKeys.TYPESCRIPT_STAGING_ENCRYPTED_PATHS,
                ArrayList(encryptedPaths.sorted()),
            )
        }

        override fun close() {
            key.fill(0)
        }

        companion object {
            fun forPlan(plan: TypeScriptApkBuildPlan?): TypeScriptStagingEncryption? {
                if (plan?.requiresEncryptedStaging != true) return null
                val encryptedPaths = plan.entries
                    .map { entry -> entry.relativePath }
                    .filter(::isJavaScriptPath)
                    .toSet()
                require(encryptedPaths.isNotEmpty()) {
                    "Encrypted TypeScript APK output contains no JavaScript staging entries."
                }
                return TypeScriptStagingEncryption(
                    key = TypeScriptBuildStagingCipher.generateKey(),
                    encryptedPaths = encryptedPaths,
                )
            }

            private fun isJavaScriptPath(path: String): Boolean {
                val lower = path.lowercase()
                return lower.endsWith(".js") ||
                    lower.endsWith(".mjs") ||
                    lower.endsWith(".cjs")
            }
        }
    }

    data class Prepared(
        val request: ApkBuildRequest,
        val archiveFile: File,
        val nativeLibrariesArchiveFile: File?,
        val expectedSignerCertificateSha256: String,
        val nativeLibraryAlignments: Map<String, Long?> = emptyMap(),
        val warnings: List<String> = emptyList(),
        val nativeAlignmentMetadataFile: File? = null,
    ) : Closeable {
        override fun close() {
            runCatching { request.projectArchiveFd?.close() }
            runCatching { request.nativeLibrariesArchiveFd?.close() }
            runCatching { request.keyStoreFd?.close() }
            clearTypeScriptStagingKey(request.extras)
        }

        fun cleanup() {
            close()
            deletePreparedFile(archiveFile)
            nativeLibrariesArchiveFile?.let(::deletePreparedFile)
            nativeAlignmentMetadataFile?.let(::deletePreparedFile)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun prepare(
        context: Context,
        projectConfig: ProjectConfig,
        outputFileName: String,
        cancelSignal: AtomicBoolean? = null,
        progressListener: ((ApkBuildProgress) -> Unit)? = null,
    ): Prepared {
        val appContext = context.applicationContext
        val source = File(projectConfig.sourcePath ?: throw IOException("Project source path is not set."))
        if (!source.exists()) {
            throw IOException("Project source path does not exist: ${source.path}")
        }
        ensureNotCancelled(cancelSignal)
        validateEmbeddedNodeBuildRequirements(projectConfig, source)

        notifyProgress(progressListener, "Preparing remote build workspace", source.path)
        val cacheDir = File(appContext.cacheDir, "apk-remote-build-input").apply {
            mkdirs()
            listFiles()?.forEach { PFiles.deleteRecursively(it) }
        }
        ensureNotCancelled(cancelSignal)
        val archiveFile = File(cacheDir, "project-${System.nanoTime()}.zip")
        val nativeAlignmentMetadataFile = File(cacheDir, archiveFile.nameWithoutExtension + "-native-alignment.json")
        var nativeLibrariesArchiveFile: File? = null
        var projectArchiveFd: ParcelFileDescriptor? = null
        var nativeLibrariesArchiveFd: ParcelFileDescriptor? = null
        var keyStoreFd: ParcelFileDescriptor? = null
        var typeScriptPlan: TypeScriptApkBuildPlan? = null
        var typeScriptStagingEncryption: TypeScriptStagingEncryption? = null
        var requestExtras: Bundle? = null
        try {
            notifyProgress(progressListener, "Preparing remote build config", projectConfig.packageName)
            val remoteConfig = cloneForRemoteRequest(projectConfig)
            if (requiresNodeRuntime(projectConfig, source)) {
                remoteConfig.setLibs((remoteConfig.libs + ApkBuildLibrary.NODEJS_RUNTIME.label).distinct())
                val shellManifest=org.autojs.autojs.engine.NodeBridgePermissionManifest.fromJsonSources(projectConfig.toJson(false),File(source,"package.json").takeIf { it.isFile }?.readText())
                remoteConfig.setPermissions((remoteConfig.permissions + shellManifest.androidPermissions.filter {
                    it in setOf("android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.SCHEDULE_EXACT_ALARM", "android.permission.WAKE_LOCK", "android.permission.VIBRATE", "android.permission.WRITE_SETTINGS", "android.permission.MODIFY_AUDIO_SETTINGS")
                }).distinct())
                if ("android.permission.POST_NOTIFICATIONS" in shellManifest.androidPermissions) remoteConfig.setPermissions((remoteConfig.permissions + "android.permission.POST_NOTIFICATIONS").distinct())
                if("moe.shizuku.manager.permission.API_V23" in shellManifest.androidPermissions)remoteConfig.setPermissions((remoteConfig.permissions+"moe.shizuku.manager.permission.API_V23").distinct())
                remoteConfig.setPermissions((remoteConfig.permissions + NodeJsPluginActions.PACKAGED_RUNTIME_PERMISSION).distinct())
                if (!shellManifest.enforced || org.autojs.autojs.engine.NodeBridgePermissionManifest.missingCapabilities(
                        shellManifest.permissions, listOf(org.autojs.autojs.engine.NodeBridgePermissionManifest.AI)).isEmpty()) {
                    remoteConfig.setPermissions((remoteConfig.permissions + org.autojs.autojs.core.plugin.ai.ThreeStoneAiOfficialPlugin.RUNTIME_PERMISSION).distinct())
                }
                if (!shellManifest.enforced || org.autojs.autojs.engine.NodeBridgePermissionManifest.missingCapabilities(
                        shellManifest.permissions, listOf(org.autojs.autojs.engine.NodeBridgePermissionManifest.MAIL)).isEmpty()) {
                    remoteConfig.setPermissions((remoteConfig.permissions + org.autojs.plugin.mail.api.MailActions.PACKAGED_PERMISSION).distinct())
                }
                if (!shellManifest.enforced || org.autojs.autojs.engine.NodeBridgePermissionManifest.missingCapabilities(
                        shellManifest.permissions, listOf(org.autojs.autojs.engine.NodeBridgePermissionManifest.EPUB)).isEmpty()) {
                    remoteConfig.setPermissions((remoteConfig.permissions + org.autojs.plugin.epub.api.EpubActions.PACKAGED_PERMISSION).distinct())
                }
                if (!shellManifest.enforced || org.autojs.autojs.engine.NodeBridgePermissionManifest.missingCapabilities(
                        shellManifest.permissions, listOf(org.autojs.autojs.engine.NodeBridgePermissionManifest.SPEECH)).isEmpty()) {
                    remoteConfig.setPermissions((remoteConfig.permissions + org.autojs.plugin.speech.api.SpeechPluginActions.PACKAGED_PERMISSION).distinct())
                }
            }
            if (requiresBunRuntime(projectConfig, source)) {
                org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants.validatePackagedCapabilities(projectConfig.bunConfig?.hostCapabilities.orEmpty())
                val deviceCapabilities = org.autojs.autojs.engine.BunAutomationBridge.nodeCapabilities(projectConfig.bunConfig?.hostCapabilities.orEmpty())
                val devicePermissions = org.autojs.autojs.engine.NodeBridgePermissionManifest.fromDeclaredCapabilities(deviceCapabilities).androidPermissions.filter {
                    it in setOf("android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.SCHEDULE_EXACT_ALARM", "android.permission.WAKE_LOCK", "android.permission.VIBRATE", "android.permission.WRITE_SETTINGS", "android.permission.MODIFY_AUDIO_SETTINGS")
                }
                remoteConfig.setPermissions((remoteConfig.permissions + devicePermissions).distinct())
                if ("notifications.post" in projectConfig.bunConfig?.hostCapabilities.orEmpty()) remoteConfig.setPermissions((remoteConfig.permissions + "android.permission.POST_NOTIFICATIONS").distinct())
                if("shell.shizuku" in projectConfig.bunConfig?.hostCapabilities.orEmpty())remoteConfig.setPermissions((remoteConfig.permissions+"moe.shizuku.manager.permission.API_V23").distinct())
                remoteConfig.setPermissions((remoteConfig.permissions + org.autojs.plugin.bun.runtime.api.BunPluginActions.PACKAGED_RUNTIME_PERMISSION).distinct())
                if (org.autojs.autojs.engine.NodeBridgePermissionManifest.AI in deviceCapabilities) {
                    remoteConfig.setPermissions((remoteConfig.permissions + org.autojs.autojs.core.plugin.ai.ThreeStoneAiOfficialPlugin.RUNTIME_PERMISSION).distinct())
                }
                if (org.autojs.autojs.engine.NodeBridgePermissionManifest.MAIL in deviceCapabilities) {
                    remoteConfig.setPermissions((remoteConfig.permissions + org.autojs.plugin.mail.api.MailActions.PACKAGED_PERMISSION).distinct())
                }
                if (org.autojs.autojs.engine.NodeBridgePermissionManifest.EPUB in deviceCapabilities) {
                    remoteConfig.setPermissions((remoteConfig.permissions + org.autojs.plugin.epub.api.EpubActions.PACKAGED_PERMISSION).distinct())
                }
                if (org.autojs.autojs.engine.NodeBridgePermissionManifest.SPEECH in deviceCapabilities) {
                    remoteConfig.setPermissions((remoteConfig.permissions + org.autojs.plugin.speech.api.SpeechPluginActions.PACKAGED_PERMISSION).distinct())
                }
            }
            typeScriptPlan = TypeScriptApkBuildCompiler.prepare(
                context = appContext,
                projectConfig = projectConfig,
                source = source,
                cancelSignal = cancelSignal,
            )
            typeScriptPlan?.let { plan ->
                remoteConfig.setMainScriptFileName(plan.mainScriptPath)
            }
            typeScriptStagingEncryption = TypeScriptStagingEncryption.forPlan(typeScriptPlan)
            notifyProgress(progressListener, "Archiving project files", source.path)
            val archivedSource = writeProjectArchive(
                archiveFile,
                source,
                projectConfig,
                remoteConfig,
                typeScriptPlan,
                typeScriptStagingEncryption,
                cancelSignal,
            )
            typeScriptPlan?.close()
            typeScriptPlan = null
            ensureNotCancelled(cancelSignal)
            notifyProgress(progressListener, "Preparing remote build inputs", projectConfig.libs.joinToString(", "))
            nativeLibrariesArchiveFile = writeBuildInputsArchive(
                context = appContext,
                cacheDir = cacheDir,
                projectConfig = projectConfig,
                cancelSignal = cancelSignal,
                progressListener = progressListener,
            )
            val nativeLibraryAlignments = NativeBuildAlignment.inspect(nativeLibrariesArchiveFile)
            // A local sidecar keeps the existing strict Binder extras contract compatible.
            NativeBuildAlignment.writeMetadata(nativeAlignmentMetadataFile, nativeLibraryAlignments)
            val nativeAlignmentWarnings = nativeLibraryAlignments.filter { it.needsWarning }.map { library ->
                appContext.getString(R.string.warning_apk_native_page_alignment_unsupported, library.path, library.minLoadAlign ?: 0L)
            }
            nativeAlignmentWarnings.forEach { warning -> notifyProgress(progressListener, warning, null) }
            ensureNotCancelled(cancelSignal)

            val keyStore = projectConfig.keyStore
            val keyStoreFile = keyStore?.absolutePath
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
            if (keyStoreFile != null && !keyStoreFile.isFile) {
                throw IOException("KeyStore file does not exist: ${keyStoreFile.path}")
            }

            notifyProgress(progressListener, "Verifying project archive", archiveFile.name)
            val projectArchiveSha256 = sha256(archiveFile, cancelSignal)
            val projectArchiveUncompressedSizeBytes = zipUncompressedSizeBytes(
                archiveFile,
                "project archive",
                cancelSignal,
            )
            notifyProgress(progressListener, "Verifying remote build inputs", nativeLibrariesArchiveFile?.name)
            val nativeLibrariesArchiveSha256 = nativeLibrariesArchiveFile?.let {
                sha256(it, cancelSignal)
            }
            val nativeLibrariesArchiveUncompressedSizeBytes = nativeLibrariesArchiveFile?.let {
                zipUncompressedSizeBytes(it, "native build input archive", cancelSignal)
            } ?: 0L
            val keyStoreSha256 = keyStoreFile?.let { file ->
                notifyProgress(progressListener, "Preparing keystore", file.path)
                sha256(file, cancelSignal)
            }
            val keyStorePassword = keyStore?.password
                ?.takeIf { it.isNotBlank() }
                ?.let(AESUtils::decrypt)
            val keyAliasPassword = keyStore?.aliasPassword
                ?.takeIf { it.isNotBlank() }
                ?.let(AESUtils::decrypt)
            val expectedSignerCertificateSha256 = RemoteBuildExpectedSignerResolver.resolve(
                context = appContext,
                keyStoreFile = keyStoreFile,
                keyStorePassword = keyStorePassword,
                keyAlias = keyStore?.alias,
            )
            ensureNotCancelled(cancelSignal)
            notifyProgress(progressListener, "Opening remote build request", outputFileName)

            projectArchiveFd = ParcelFileDescriptor.open(
                archiveFile,
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
            nativeLibrariesArchiveFd = nativeLibrariesArchiveFile?.let {
                ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            keyStoreFd = keyStoreFile?.let {
                ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            requestExtras = Bundle().apply {
                putInt(ApkBuildRequestExtraKeys.ARCHIVE_FORMAT_VERSION, ARCHIVE_FORMAT_VERSION)
                putString(
                    ApkBuildRequestExtraKeys.SOURCE_KIND,
                    archivedSource.kind,
                )
                putString(ApkBuildRequestExtraKeys.SOURCE_ROOT_PATH, SOURCE_ROOT_ENTRY)
                putString(ApkBuildRequestExtraKeys.SOURCE_PATH, archivedSource.path)
                putLong(
                    ApkBuildRequestExtraKeys.PROJECT_ARCHIVE_UNCOMPRESSED_SIZE_BYTES,
                    projectArchiveUncompressedSizeBytes,
                )
                putLong(
                    ApkBuildRequestExtraKeys.NATIVE_LIBRARIES_ARCHIVE_UNCOMPRESSED_SIZE_BYTES,
                    nativeLibrariesArchiveUncompressedSizeBytes,
                )
                if (remoteConfig.iconPath == REMOTE_ICON_ENTRY) {
                    putString(ApkBuildRequestExtraKeys.ICON_PATH, REMOTE_ICON_ENTRY)
                }
                putString(ApkBuildRequestExtraKeys.HOST_OUTPUT_FILE_NAME, outputFileName)
                typeScriptStagingEncryption?.addTo(this)
            }
            typeScriptStagingEncryption?.close()
            typeScriptStagingEncryption = null
            val request = ApkBuildRequest(
                hostPackageName = appContext.packageName,
                hostVersionName = BuildConfig.VERSION_NAME,
                hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
                requiredProtocolVersion = ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION,
                projectArchiveFd = projectArchiveFd,
                projectArchiveSizeBytes = archiveFile.length(),
                projectArchiveSha256 = projectArchiveSha256,
                nativeLibrariesArchiveFd = nativeLibrariesArchiveFd,
                nativeLibrariesArchiveSizeBytes = nativeLibrariesArchiveFile?.length() ?: 0L,
                nativeLibrariesArchiveSha256 = nativeLibrariesArchiveSha256,
                projectConfigJson = serializeProjectConfigForRemoteRequest(remoteConfig),
                keyStoreFd = keyStoreFd,
                keyStoreSizeBytes = keyStoreFile?.length() ?: 0L,
                keyStoreSha256 = keyStoreSha256,
                keyStorePassword = keyStorePassword,
                keyAlias = keyStore?.alias,
                keyAliasPassword = keyAliasPassword,
                outputFileName = outputFileName,
                allowRiskyBuild = true,
                extras = requestExtras,
            )
            return Prepared(
                request = request,
                nativeLibraryAlignments = nativeLibraryAlignments.associate { it.path to it.minLoadAlign },
                nativeAlignmentMetadataFile = nativeAlignmentMetadataFile,
                warnings = nativeAlignmentWarnings,
                archiveFile = archiveFile,
                nativeLibrariesArchiveFile = nativeLibrariesArchiveFile,
                expectedSignerCertificateSha256 = expectedSignerCertificateSha256,
            )
        } catch (error: Throwable) {
            runCatching { projectArchiveFd?.close() }
            runCatching { nativeLibrariesArchiveFd?.close() }
            runCatching { keyStoreFd?.close() }
            runCatching { typeScriptPlan?.close() }
            runCatching { typeScriptStagingEncryption?.close() }
            clearTypeScriptStagingKey(requestExtras)
            deletePreparedFile(archiveFile)
            deletePreparedFile(nativeAlignmentMetadataFile)
            nativeLibrariesArchiveFile?.let(::deletePreparedFile)
            throw error
        }
    }

    private fun cloneForRemoteRequest(projectConfig: ProjectConfig): ProjectConfig {
        val json = projectConfig.toJson(false)
        return ProjectConfig.fromJson(json)
            ?.setIconPath(null)
            ?: throw IOException("Failed to clone project config for remote build request.")
    }

    /**
     * A fresh Gson instance intentionally bypasses ProjectConfig.toJson(), whose job is to retain
     * legacy source keys. The plugin protocol always receives canonical keys; the source file is
     * updated separately by merging only build metadata back into the original ProjectConfig.
     */
    internal fun serializeProjectConfigForRemoteRequest(projectConfig: ProjectConfig): String {
        return remoteConfigGson.toJson(projectConfig)
    }

    private fun validateEmbeddedNodeBuildRequirements(projectConfig: ProjectConfig, source: File) {
        val resolvedEntry = resolveProjectEntry(source, projectConfig.mainScriptFileName)
            ?.takeIf(File::isFile)
        val entryExecutionMode = resolvedEntry?.let { entry ->
            val flags = EncryptedScriptFileHeader.getHeaderFlags(entry)
            var mode = if (flags == EncryptedScriptFileHeader.FLAG_INVALID_FILE) {
                ScriptFileEncoding.openReader(entry).use(JavaScriptSource::parseExecutionMode).mode
            } else {
                flags.toInt()
            }
            if (JavaScriptSource.isQuickJsFileName(entry.name)) {
                mode = mode or JavaScriptSource.EXECUTION_MODE_QUICKJS
            } else if (JavaScriptSource.isBunFileName(entry.name)) {
                mode = mode or JavaScriptSource.EXECUTION_MODE_BUN
            } else if (
                JavaScriptSource.isNodeJsFileName(entry.name) &&
                (
                    mode and JavaScriptSource.EXECUTION_MODE_QUICKJS == 0 ||
                        !entry.name.endsWith(".mjs", ignoreCase = true)
                )
            ) {
                mode = mode or JavaScriptSource.EXECUTION_MODE_NODEJS
            }
            JavaScriptSource.validateExclusiveEngineMode(mode)
            mode
        }
        if (
            (entryExecutionMode ?: 0) and JavaScriptSource.EXECUTION_MODE_QUICKJS != 0 ||
            requestsExternalQuickJsRuntime(
                projectType = projectConfig.projectType,
                configuredEntryName = projectConfig.mainScriptFileName,
                sourceName = source.name,
                entryExecutionMode = entryExecutionMode,
            )
        ) {
            throw IOException(
                "QuickJS Runtime is provided by an external AutoJs6 plugin and cannot be embedded " +
                    "in a packaged APK. Run QuickJS scripts from the AutoJs6 host with the " +
                    "QuickJS Runtime plugin installed."
            )
        }
        if (hasLegacyEmbeddedNodeLibrary(projectConfig.libs)) {
            throw IOException(
                "Embedded Node.js packaging was retired. Select Node.js Runtime to use the installed external plugin."
            )
        }
    }

    internal fun hasLegacyEmbeddedNodeLibrary(libraries: List<String>): Boolean = libraries.any { selected ->
        val normalized = selected.trim()
        normalized.equals(ApkBuildLibrary.EMBEDDED_NODE_JS.label, ignoreCase = true) ||
            ApkBuildLibrary.EMBEDDED_NODE_JS.aliases.any { normalized.equals(it, ignoreCase = true) }
    }

    @JvmStatic
    @JvmOverloads
    fun requiresNodeRuntime(projectConfig: ProjectConfig?, source: File, includeSelectedLibraries: Boolean = true): Boolean {
        val config = projectConfig ?: ProjectConfig()
        val entry = resolveProjectEntry(source, config.mainScriptFileName)?.takeIf(File::isFile)
        val mode = entry?.let {
            val flags = EncryptedScriptFileHeader.getHeaderFlags(it)
            if (flags == EncryptedScriptFileHeader.FLAG_INVALID_FILE) {
                ScriptFileEncoding.openReader(it).use(JavaScriptSource::parseExecutionMode).mode
            } else flags.toInt()
        }
        if ((mode ?: 0) and (JavaScriptSource.EXECUTION_MODE_QUICKJS or JavaScriptSource.EXECUTION_MODE_BUN) != 0) return false
        return requiresNodeRuntime(
            selectedLibraries = if (includeSelectedLibraries) config.libs else emptyList(),
            projectType = config.projectType,
            hasNodeConfig = config.nodeConfig != null,
            configuredEntryName = config.mainScriptFileName,
            sourceName = source.name,
            entryExecutionMode = mode,
        )
    }

    internal fun requiresBunRuntime(config: ProjectConfig, source: File): Boolean {
        val entry = resolveProjectEntry(source, config.mainScriptFileName)?.takeIf(File::isFile)
        val mode = entry?.let {
            val flags = EncryptedScriptFileHeader.getHeaderFlags(it)
            if (flags == EncryptedScriptFileHeader.FLAG_INVALID_FILE) ScriptFileEncoding.openReader(it).use(JavaScriptSource::parseExecutionMode).mode else flags.toInt()
        }
        return requestsExternalBunRuntime(config.projectType, config.mainScriptFileName, source.name, mode)
    }

    internal fun requestsExternalQuickJsRuntime(
        projectType: String?,
        configuredEntryName: String?,
        sourceName: String?,
        entryExecutionMode: Int?,
    ): Boolean =
        projectType?.trim()?.equals("quickjs", ignoreCase = true) == true ||
            JavaScriptSource.isQuickJsFileName(configuredEntryName) ||
            JavaScriptSource.isQuickJsFileName(sourceName) ||
            (entryExecutionMode ?: 0) and JavaScriptSource.EXECUTION_MODE_QUICKJS != 0

    internal fun requestsExternalBunRuntime(
        projectType: String?,
        configuredEntryName: String?,
        sourceName: String?,
        entryExecutionMode: Int?,
    ): Boolean =
        projectType?.trim()?.equals("bun", ignoreCase = true) == true ||
            JavaScriptSource.isBunFileName(configuredEntryName) ||
            JavaScriptSource.isBunFileName(sourceName) ||
            (entryExecutionMode ?: 0) and JavaScriptSource.EXECUTION_MODE_BUN != 0

    internal fun requiresNodeRuntime(
        selectedLibraries: List<String>,
        projectType: String?,
        hasNodeConfig: Boolean,
        configuredEntryName: String?,
        sourceName: String?,
        entryExecutionMode: Int?,
    ): Boolean {
        val runtime = ApkBuildLibrary.NODEJS_RUNTIME
        return selectedLibraries.any { selected ->
            selected.trim().equals(runtime.label, ignoreCase = true) ||
                runtime.aliases.any { selected.trim().equals(it, ignoreCase = true) }
        } || projectType?.trim()?.equals("node", ignoreCase = true) == true || hasNodeConfig ||
            JavaScriptSource.isNodeJsFileName(configuredEntryName) ||
            JavaScriptSource.isNodeJsFileName(sourceName) ||
            (entryExecutionMode ?: 0) and JavaScriptSource.EXECUTION_MODE_NODEJS != 0
    }

    internal fun resolveProjectEntry(source: File, configuredEntryName: String): File? {
        if (!source.isDirectory) {
            return source.canonicalFile
        }
        val root = source.canonicalFile
        val entry = File(root, configuredEntryName).canonicalFile
        val rootPrefix = "${root.path}${File.separator}"
        if (entry.path != root.path && !entry.path.startsWith(rootPrefix)) {
            throw IOException("Project entry escapes the project source root: $configuredEntryName")
        }
        return entry
    }

    private fun writeProjectArchive(
        archiveFile: File,
        source: File,
        projectConfig: ProjectConfig,
        remoteConfig: ProjectConfig,
        typeScriptPlan: TypeScriptApkBuildPlan?,
        typeScriptStagingEncryption: TypeScriptStagingEncryption?,
        cancelSignal: AtomicBoolean?,
    ): ArchivedProjectSource {
        val useDirectorySource = source.isDirectory || typeScriptPlan?.forceDirectorySource == true
        var sourcePathInArchive = SOURCE_ROOT_ENTRY
        writeZipFile(archiveFile, Deflater.BEST_SPEED) { zip ->
            ensureNotCancelled(cancelSignal)
            if (useDirectorySource) {
                if (source.isDirectory) {
                    addDirectoryTree(
                        zip = zip,
                        root = source.canonicalFile,
                        entryRoot = SOURCE_ROOT_ENTRY,
                        excludedDirs = projectConfig.excludedDirs.mapNotNull {
                            runCatching { it.canonicalFile }.getOrNull()
                        },
                        typeScriptPlan = typeScriptPlan,
                        cancelSignal = cancelSignal,
                    )
                } else {
                    addDirectoryEntry(zip, "$SOURCE_ROOT_ENTRY/")
                }
                typeScriptPlan?.entries?.forEach { entry ->
                    ensureNotCancelled(cancelSignal)
                    val stagedBytes = if (
                        entry.relativePath in
                        typeScriptStagingEncryption?.encryptedPaths.orEmpty()
                    ) {
                        TypeScriptBuildStagingCipher.encrypt(
                            entry.bytes,
                            requireNotNull(typeScriptStagingEncryption).key,
                        )
                    } else {
                        null
                    }
                    try {
                        addBytes(
                            zip,
                            stagedBytes ?: entry.bytes,
                            "$SOURCE_ROOT_ENTRY/${entry.relativePath}",
                        )
                    } finally {
                        stagedBytes?.fill(0)
                    }
                }
            } else {
                sourcePathInArchive = "$SOURCE_ROOT_ENTRY/${sanitizeEntrySegment(source.name)}"
                addFile(zip, source, sourcePathInArchive, cancelSignal)
            }
            ensureNotCancelled(cancelSignal)
            if (writeIconEntryIfAvailable(zip, projectConfig)) {
                remoteConfig.setIconPath(REMOTE_ICON_ENTRY)
            }
        }
        return ArchivedProjectSource(
            path = sourcePathInArchive,
            kind = if (useDirectorySource) {
                ApkBuildRequestExtraKeys.SOURCE_KIND_DIRECTORY
            } else {
                ApkBuildRequestExtraKeys.SOURCE_KIND_FILE
            },
        )
    }

    private fun addDirectoryTree(
        zip: ZipOutputStream,
        root: File,
        entryRoot: String,
        excludedDirs: List<File>,
        typeScriptPlan: TypeScriptApkBuildPlan?,
        cancelSignal: AtomicBoolean?,
    ) {
        addDirectoryEntry(zip, "$entryRoot/")
        val generatedPaths = typeScriptPlan?.generatedPaths.orEmpty()
        val generatedFoldedPaths = generatedPaths.mapTo(hashSetOf()) { it.asciiCaseFold() }
        root.walkTopDown()
            .onEnter { dir -> !isExcluded(dir, excludedDirs) }
            .filter { it != root }
            .filterNot { isExcluded(it, excludedDirs) }
            .forEach { file ->
                ensureNotCancelled(cancelSignal)
                val relative = file.relativeTo(root).path.replace(File.separatorChar, '/')
                if (typeScriptPlan?.omitOriginal(relative) == true) {
                    return@forEach
                }
                if (file.isFile && relative.asciiCaseFold() in generatedFoldedPaths) {
                    throw IOException(
                        "TypeScript APK output collides with a retained project file: $relative",
                    )
                }
                val entryName = "$entryRoot/${sanitizeEntryPath(relative)}"
                if (file.isDirectory) {
                    addDirectoryEntry(zip, "$entryName/")
                } else if (file.isFile) {
                    addFile(zip, file, entryName, cancelSignal)
                }
            }
    }

    private fun writeIconEntryIfAvailable(
        zip: ZipOutputStream,
        projectConfig: ProjectConfig,
    ): Boolean {
        val bitmap = projectConfig.iconBitmapGetter?.call() ?: return false
        zip.putNextEntry(ZipEntry(REMOTE_ICON_ENTRY))
        val written = bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
        zip.closeEntry()
        if (!written) {
            throw IOException("Failed to encode project icon for remote build request.")
        }
        return true
    }

    private fun writeBuildInputsArchive(
        context: Context,
        cacheDir: File,
        projectConfig: ProjectConfig,
        cancelSignal: AtomicBoolean?,
        progressListener: ((ApkBuildProgress) -> Unit)?,
    ): File? {
        ensureNotCancelled(cancelSignal)
        notifyProgress(progressListener, "Resolving native libraries", projectConfig.abis.joinToString(", "))
        val requiredLibs = resolveHostNativeLibraries(projectConfig)
        val targetAbis = projectConfig.abis.distinct()
        notifyProgress(progressListener, "Resolving plugin artifacts", projectConfig.libs.joinToString(", "))
        val pluginArtifacts = resolveSelectedPluginArtifacts(context, projectConfig, cancelSignal, progressListener)
        if ((requiredLibs.isEmpty() && pluginArtifacts.isEmpty()) || targetAbis.isEmpty()) {
            return null
        }

        val host = installedCode(
            context,
            context.packageName,
            PackagedNativeLibraryPolicy.Source(
                label = HOST_SOURCE_LABEL,
                packageName = context.packageName,
                versionName = BuildConfig.VERSION_NAME,
                isHost = true,
            ),
        )
        val archiveFile = File(cacheDir, "native-libraries-${System.nanoTime()}.zip")
        try {
            NativeStaging(File(cacheDir, "native-staging-${System.nanoTime()}")).use { staging ->
                targetAbis.forEach { abi ->
                    requiredLibs.forEach { soName ->
                        ensureNotCancelled(cancelSignal)
                        stageNativeLibrary(staging, host, abi, soName, cancelSignal, progressListener)
                    }
                }
                pluginArtifacts.forEach { artifact ->
                    stagePluginNativeLibraries(context, staging, host, artifact, targetAbis, cancelSignal, progressListener)
                }
                stageHostCxxRuntimeForConsumers(staging, host, targetAbis, cancelSignal, progressListener)

                notifyProgress(progressListener, "Checking native libraries", null)
                val selected = try {
                    PackagedNativeLibraryPolicy.resolve(staging.candidates, staging::dynamicInfo)
                } catch (failure: PackagedNativeLibraryPolicy.Failure) {
                    throw IOException(describeNativeLibraryFailure(context, failure), failure)
                }

                var wroteAny = false
                writeZipFile(archiveFile, Deflater.BEST_SPEED) { zip ->
                    selected.forEach { library ->
                        ensureNotCancelled(cancelSignal)
                        notifyProgress(progressListener, "Copying native libraries", library.entryName)
                        addFile(zip, library.file, library.entryName, cancelSignal)
                        wroteAny = true
                    }
                    val writtenAssets = linkedSetOf<String>()
                    pluginArtifacts.forEach { artifact ->
                        ensureNotCancelled(cancelSignal)
                        if (copyPluginArtifactAssetsToZip(zip, writtenAssets, context, artifact, cancelSignal, progressListener)) {
                            wroteAny = true
                        }
                    }
                    bundledOpenCvRuntimeRecord(context, pluginArtifacts, selected)?.let { record ->
                        addBytes(zip, BundledOpenCvRuntime.encode(record), "assets/${BundledOpenCvRuntime.ASSET_PATH}")
                    }
                }
                return archiveFile.takeIf { wroteAny && it.length() > 0L } ?: run {
                    deletePreparedFile(archiveFile)
                    null
                }
            }
        } catch (error: Throwable) {
            deletePreparedFile(archiveFile)
            throw error
        }
    }

    private fun resolveHostNativeLibraries(projectConfig: ProjectConfig): List<String> {
        val selectedLibs = projectConfig.libs.toSet()
        return ApkBuildLibrary.entries
            .filter { lib -> lib == ApkBuildLibrary.TERMINAL_EMULATOR || (!lib.isPlugin && lib.label in selectedLibs) }
            .flatMap { it.libsToInclude }
            .distinct()
    }

    /** Native libraries of an installed package: APK entries first, then the extracted directory. */
    private class InstalledCode(
        val source: PackagedNativeLibraryPolicy.Source,
        val apkPaths: List<String>,
        val nativeLibraryDir: File?,
    )

    /** Staged copies of every candidate library; deleted once the archive is written. */
    private class NativeStaging(private val root: File) : Closeable {
        private var sequence = 0
        private val dynamicInfoCache = HashMap<File, ElfDynamicInfo?>()
        val candidates = mutableListOf<PackagedNativeLibraryPolicy.Candidate>()

        init {
            if (!root.isDirectory && !root.mkdirs()) {
                throw IOException("Failed to create native library staging directory: ${root.path}")
            }
        }

        fun newFile(): File = File(root, "${sequence++}.so")

        /** Unreadable or non-ELF files yield null and are not checked against the C++ runtime. */
        fun dynamicInfo(file: File): ElfDynamicInfo? = dynamicInfoCache.getOrPut(file) {
            runCatching { ElfDynamicInfo.read(file) }.getOrNull()
        }

        fun hasCandidate(abi: String, name: String, source: PackagedNativeLibraryPolicy.Source? = null): Boolean =
            candidates.any { it.abi == abi && it.name == name && (source == null || it.source == source) }

        override fun close() {
            root.deleteRecursively()
        }
    }

    private fun installedCode(
        context: Context,
        packageName: String,
        source: PackagedNativeLibraryPolicy.Source,
    ): InstalledCode {
        val appInfo = getApplicationInfoCompat(context.packageManager, packageName)
        return InstalledCode(
            source = source,
            apkPaths = buildList {
                add(appInfo.sourceDir)
                appInfo.splitSourceDirs?.forEach { add(it) }
            }.distinct(),
            nativeLibraryDir = File(appInfo.nativeLibraryDir.orEmpty()).takeIf { it.isDirectory },
        )
    }

    /** Copies [soName] for [abi] from [code] into the staging directory; returns false when absent. */
    private fun stageNativeLibrary(
        staging: NativeStaging,
        code: InstalledCode,
        abi: String,
        soName: String,
        cancelSignal: AtomicBoolean?,
        progressListener: ((ApkBuildProgress) -> Unit)?,
    ): Boolean {
        if (staging.hasCandidate(abi, soName, code.source)) {
            return true
        }
        val abiCandidates = abiCandidates(abi)
        val target = staging.newFile()
        val sha256 = code.apkPaths.firstNotNullOfOrNull { apkPath ->
            ensureNotCancelled(cancelSignal)
            try {
                ZipFile(apkPath).use { apk ->
                    abiCandidates.firstNotNullOfOrNull { candidate -> findNativeLibraryEntry(apk, candidate, soName) }
                        ?.let { entry -> apk.getInputStream(entry).use { input -> copyAndHash(input, target, cancelSignal) } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
        } ?: code.nativeLibraryDir
            ?.takeIf { nativeDirMatchesAbi(it, abiCandidates) }
            ?.resolve(soName)
            ?.takeIf { it.isFile }
            ?.let { file -> FileInputStream(file).use { input -> copyAndHash(input, target, cancelSignal) } }
        if (sha256 == null) {
            target.delete()
            return false
        }
        notifyProgress(progressListener, "Staging native libraries", "${code.source.label}: lib/$abi/$soName")
        staging.candidates += PackagedNativeLibraryPolicy.Candidate(abi, soName, code.source, sha256, target)
        return true
    }

    private fun stagePluginNativeLibraries(
        context: Context,
        staging: NativeStaging,
        host: InstalledCode,
        artifact: SelectedPluginArtifact,
        targetAbis: List<String>,
        cancelSignal: AtomicBoolean?,
        progressListener: ((ApkBuildProgress) -> Unit)?,
    ) {
        val packageName = artifact.serviceInfo.packageName
        val plugin = installedCode(
            context,
            packageName,
            PackagedNativeLibraryPolicy.Source(
                label = artifact.library.label,
                packageName = packageName,
                versionName = getPackageVersionName(context.packageManager, packageName),
            ),
        )
        val variant = artifact.variant
        val missingNativeLibraries = mutableListOf<String>()
        targetAbis.forEach { abi ->
            ensureNotCancelled(cancelSignal)
            val stagedFromPlugin = mutableListOf<File>()
            (variant.libsToInclude + variant.optionalLibsToInclude).distinct().forEach { soName ->
                ensureNotCancelled(cancelSignal)
                val stagedPluginCopy = stageNativeLibrary(staging, plugin, abi, soName, cancelSignal, progressListener)
                if (stagedPluginCopy) {
                    stagedFromPlugin += staging.candidates.last { it.abi == abi && it.name == soName && it.source == plugin.source }.file
                }
                if (soName == PackagedNativeLibraryPolicy.CXX_SHARED) {
                    // The host runtime is always a candidate; the newest one is packaged (D5).
                    val stagedHostCopy = stageNativeLibrary(staging, host, abi, soName, cancelSignal, progressListener)
                    if (!stagedPluginCopy && !stagedHostCopy) {
                        missingNativeLibraries += "$abi/$soName"
                    }
                } else if (!stagedPluginCopy && soName in variant.libsToInclude) {
                    missingNativeLibraries += "$abi/$soName"
                }
            }
            variant.neededOnlyLibsToInclude.forEach { soName ->
                ensureNotCancelled(cancelSignal)
                val needed = stagedFromPlugin.any { file -> soName in staging.dynamicInfo(file)?.needed.orEmpty() }
                if (needed && !stageNativeLibrary(staging, plugin, abi, soName, cancelSignal, progressListener)) {
                    missingNativeLibraries += "$abi/$soName"
                }
            }
        }
        if (missingNativeLibraries.isNotEmpty()) {
            throw IOException(
                context.getString(
                    R.string.error_plugin_apk_does_not_contain_required_native_libraries,
                    missingNativeLibraries.joinToString(", "),
                )
            )
        }
    }

    /**
     * Packages the host C++ runtime for ABIs whose libraries need one but whose plugins do not
     * list it, so the runtime check covers every packaged library.
     */
    private fun stageHostCxxRuntimeForConsumers(
        staging: NativeStaging,
        host: InstalledCode,
        targetAbis: List<String>,
        cancelSignal: AtomicBoolean?,
        progressListener: ((ApkBuildProgress) -> Unit)?,
    ) {
        targetAbis.forEach { abi ->
            ensureNotCancelled(cancelSignal)
            if (staging.hasCandidate(abi, PackagedNativeLibraryPolicy.CXX_SHARED)) return@forEach
            val needsRuntime = staging.candidates.filter { it.abi == abi }.any { candidate ->
                PackagedNativeLibraryPolicy.CXX_SHARED in staging.dynamicInfo(candidate.file)?.needed.orEmpty()
            }
            if (needsRuntime) {
                stageNativeLibrary(staging, host, abi, PackagedNativeLibraryPolicy.CXX_SHARED, cancelSignal, progressListener)
            }
        }
    }

    private fun describeNativeLibraryFailure(context: Context, failure: PackagedNativeLibraryPolicy.Failure): String =
        when (failure) {
            is PackagedNativeLibraryPolicy.Failure.Conflict -> {
                val otherThanOpenCv = listOf(failure.first, failure.second)
                    .singleOrNull { it.packageName != OpenCvPluginContract.DEFAULT_PACKAGE_NAME }
                if (failure.entryName.endsWith("/${OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME}") && otherThanOpenCv != null) {
                    // D9: an older OCR plugin still ships the shared OpenCV runtime with different bytes.
                    context.getString(
                        R.string.error_apk_native_opencv_runtime_conflict,
                        failure.entryName,
                        otherThanOpenCv.description,
                    )
                } else {
                    context.getString(
                        R.string.error_apk_native_library_conflict,
                        failure.entryName,
                        failure.first.description,
                        failure.second.description,
                    )
                }
            }
            is PackagedNativeLibraryPolicy.Failure.CxxRuntimeTooOld -> context.getString(
                R.string.error_apk_native_cxx_runtime_too_old,
                failure.library.entryName,
                failure.library.sources.first().description,
                failure.runtime.sources.first().description,
                failure.missingSymbols.size,
                failure.missingSymbols.first(),
            )
        }

    /** P1.5: records the OpenCV plugin's runtime so packaged apps reject same-named copies. */
    private fun bundledOpenCvRuntimeRecord(
        context: Context,
        pluginArtifacts: List<SelectedPluginArtifact>,
        selected: List<PackagedNativeLibraryPolicy.Selected>,
    ): BundledOpenCvRuntime.Record? {
        val artifact = pluginArtifacts.firstOrNull { it.library == ApkBuildLibrary.OPENCV } ?: return null
        val packageName = artifact.serviceInfo.packageName
        val hashes = selected
            .filter { library ->
                library.name == OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME &&
                        library.sources.any { it.packageName == packageName }
            }
            .associate { it.abi to it.sha256 }
            .takeIf { it.isNotEmpty() }
            ?: return null
        val packageInfo = runCatching { getPackageInfoCompat(context.packageManager, packageName) }.getOrNull()
        return BundledOpenCvRuntime.Record(
            library = OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME,
            packageName = packageName,
            versionName = packageInfo?.versionName,
            versionCode = packageInfo?.let(PackageInfoCompat::getLongVersionCode),
            sha256ByAbi = hashes,
        )
    }

    private fun copyPluginArtifactAssetsToZip(
        zip: ZipOutputStream,
        writtenEntries: MutableSet<String>,
        context: Context,
        artifact: SelectedPluginArtifact,
        cancelSignal: AtomicBoolean?,
        progressListener: ((ApkBuildProgress) -> Unit)?,
    ): Boolean {
        val pluginApkPaths = installedCode(
            context,
            artifact.serviceInfo.packageName,
            PackagedNativeLibraryPolicy.Source(artifact.library.label, artifact.serviceInfo.packageName),
        ).apkPaths
        var wroteAny = false
        val (requiredAssets, optionalAssets) = artifact.variant.assetsToInclude
        val missingRequiredAssets = mutableListOf<String>()
        requiredAssets.forEach { prefix ->
            ensureNotCancelled(cancelSignal)
            if (copyPluginAssetsByPrefixToZip(zip, writtenEntries, pluginApkPaths, prefix, cancelSignal, progressListener)) {
                wroteAny = true
            } else {
                missingRequiredAssets += prefix
            }
        }
        if (missingRequiredAssets.isNotEmpty()) {
            throw IOException(
                context.getString(
                    R.string.error_plugin_apk_does_not_contain_required_assets_for_variant,
                    artifact.variant.variant,
                    missingRequiredAssets.joinToString(", "),
                )
            )
        }

        optionalAssets.forEach { prefix ->
            ensureNotCancelled(cancelSignal)
            if (copyPluginAssetsByPrefixToZip(zip, writtenEntries, pluginApkPaths, prefix, cancelSignal, progressListener)) {
                wroteAny = true
            }
        }

        return wroteAny
    }

    private fun copyPluginAssetsByPrefixToZip(
        zip: ZipOutputStream,
        writtenEntries: MutableSet<String>,
        apkPaths: List<String>,
        assetPrefix: String,
        cancelSignal: AtomicBoolean?,
        progressListener: ((ApkBuildProgress) -> Unit)?,
    ): Boolean {
        val zipPrefix = toAssetZipPrefix(assetPrefix)
        var copiedAny = false
        notifyProgress(progressListener, "Copying plugin assets", zipPrefix)
        apkPaths.forEach { apkPath ->
            ensureNotCancelled(cancelSignal)
            try {
                ZipFile(apkPath).use { apk ->
                    val entries = apk.entries()
                    while (entries.hasMoreElements()) {
                        ensureNotCancelled(cancelSignal)
                        val entry = entries.nextElement()
                        val name = entry.name.replace('\\', '/')
                        if (entry.isDirectory || (name != zipPrefix && !name.startsWith("$zipPrefix/"))) {
                            continue
                        }
                        if (name in writtenEntries) {
                            copiedAny = true
                            continue
                        }
                        zip.putNextEntry(ZipEntry(name))
                        apk.getInputStream(entry).use { input -> copyStreamWithCancel(input, zip, cancelSignal) }
                        zip.closeEntry()
                        writtenEntries += name
                        copiedAny = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Ignore and try next APK path.
            }
        }
        return copiedAny
    }

    private fun resolveSelectedPluginArtifacts(
        context: Context,
        projectConfig: ProjectConfig,
        cancelSignal: AtomicBoolean?,
        progressListener: ((ApkBuildProgress) -> Unit)?,
    ): List<SelectedPluginArtifact> {
        val selectedLabels = projectConfig.libs.toSet()
        return ApkBuildLibrary.entries.mapNotNull { lib ->
            ensureNotCancelled(cancelSignal)
            if (!lib.isPlugin || lib.label !in selectedLabels) {
                return@mapNotNull null
            }
            notifyProgress(progressListener, "Resolving plugin artifacts", lib.label)
            val (serviceInfo, variant) = selectPluginServiceOrThrow(context, lib, lib.requirePlugin(), cancelSignal, progressListener)
            notifyProgress(progressListener, "Selected plugin", "${lib.label}: ${serviceInfo.packageName} (${variant.variant.orEmpty()})")
            // Every plugin keeps its own payload; same-named libraries are resolved by content (P1.1).
            SelectedPluginArtifact(lib, serviceInfo, variant)
        }
    }

    private fun selectPluginServiceOrThrow(
        context: Context,
        lib: ApkBuildLibrary,
        plugin: ApkPluginLibrary,
        cancelSignal: AtomicBoolean?,
        progressListener: ((ApkBuildProgress) -> Unit)?,
    ): Pair<ServiceInfo, ApkPluginLibraryVariant> {
        ensureNotCancelled(cancelSignal)
        val enabledServices = filterPackagingServices(
            context,
            lib,
            queryServices(context, plugin.action),
        )
            .filter { service -> PluginEnableStore.isEnabled(context, service.packageName, defaultEnabled = true) }

        if (enabledServices.isEmpty()) {
            throw IOException(context.getString(R.string.error_no_enabled_plugin_for_module_label, lib.label))
        }

        selectPluginVariant(context, enabledServices, plugin, cancelSignal, progressListener)?.let { return it }

        if (tryWakePluginServices(context, enabledServices, cancelSignal)) {
            PLUGIN_WAKE_RETRY_DELAYS_MS.forEach { delayMs ->
                ensureNotCancelled(cancelSignal)
                Thread.sleep(delayMs)
                ensureNotCancelled(cancelSignal)
                val retryServices = filterPackagingServices(
                    context,
                    lib,
                    queryServices(context, plugin.action),
                )
                    .filter { service -> PluginEnableStore.isEnabled(context, service.packageName, defaultEnabled = true) }
                selectPluginVariant(context, retryServices, plugin, cancelSignal, progressListener)?.let { return it }
            }
        }

        if (lib == ApkBuildLibrary.PADDLE_OCR) {
            // D10: packaged applications only run the embedded PP-OCRv3 (Paddle Lite) engine.
            throw IOException(context.getString(R.string.error_apk_paddle_ocr_requires_pp_ocrv3_plugin))
        }
        val variantList = plugin.variants.joinToString("/") { it.variant.orEmpty() }
        throw IOException(context.getString(R.string.error_no_available_enabled_plugin_variants_found, lib.label, variantList))
    }

    private fun filterPackagingServices(
        context: Context,
        lib: ApkBuildLibrary,
        services: List<ServiceInfo>,
    ): List<ServiceInfo> = when (lib) {
        ApkBuildLibrary.IMAGE_QUANT -> ImageQuantPluginHost.filterEligiblePackagingServices(context, services)
        ApkBuildLibrary.OPENCV -> OpenCvPluginHost.filterEligiblePackagingServices(context, services)
        else -> services
    }

    private fun selectPluginVariant(
        context: Context,
        services: List<ServiceInfo>,
        plugin: ApkPluginLibrary,
        cancelSignal: AtomicBoolean?,
        progressListener: ((ApkBuildProgress) -> Unit)?,
    ): Pair<ServiceInfo, ApkPluginLibraryVariant>? {
        val candidates = services.mapNotNull { service ->
            ensureNotCancelled(cancelSignal)
            notifyProgress(progressListener, "Querying plugin info", service.packageName)
            val info = try {
                queryPluginInfoBlocking(context, service, plugin, cancelSignal)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
                ?: return@mapNotNull null
            resolvePluginVariant(plugin, info, service)?.let { variant -> service to variant }
        }
        return ApkBuildLibraryCatalog.selectBestPluginVariantCandidate(candidates, plugin.variants)
    }

    private fun resolvePluginVariant(
        plugin: ApkPluginLibrary,
        info: PluginInfo,
        serviceInfo: ServiceInfo,
    ): ApkPluginLibraryVariant? {
        if (!isPluginFamilyMatch(plugin, info, serviceInfo)) {
            return null
        }
        val hints = listOfNotNull(
            info.variant,
            info.id,
            info.name,
            serviceInfo.packageName,
            serviceInfo.name,
        ).filter { it.isNotBlank() }

        return plugin.variants.firstOrNull { variant ->
            val expected = variant.variant ?: return@firstOrNull false
            hints.any { hint -> isPluginVariantMatch(hint, expected) }
        }
    }

    private fun isPluginFamilyMatch(plugin: ApkPluginLibrary, info: PluginInfo, serviceInfo: ServiceInfo): Boolean {
        val expectedEngine = plugin.engine ?: return true
        if (info.engine == expectedEngine) {
            return true
        }
        val expected = normalizePluginVariantHint(expectedEngine)
        return listOfNotNull(
            info.id,
            info.name,
            serviceInfo.packageName,
            serviceInfo.name,
        ).any { hint ->
            normalizePluginVariantHint(hint).contains(expected)
        }
    }

    private fun isPluginVariantMatch(hint: String, expected: String): Boolean {
        val normalizedHint = normalizePluginVariantHint(hint)
        val normalizedExpected = normalizePluginVariantHint(expected)
        return normalizedHint == normalizedExpected || normalizedHint.endsWith(normalizedExpected)
    }

    private fun normalizePluginVariantHint(text: String): String {
        return text.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun queryPluginInfoBlocking(
        context: Context,
        serviceInfo: ServiceInfo,
        plugin: ApkPluginLibrary,
        cancelSignal: AtomicBoolean?,
    ): PluginInfo {
        ensureNotCancelled(cancelSignal)
        val latch = CountDownLatch(1)
        var pluginInfo: PluginInfo? = null
        var error: Throwable? = null

        val intent = Intent(plugin.action).apply {
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                try {
                    pluginInfo = plugin.onServiceConnected(binder)
                } catch (t: Throwable) {
                    error = t
                } finally {
                    latch.countDown()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }

        val bound = runCatching {
            PluginAutoWake.bindServiceWaking(context, intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound) {
            throw IOException("Failed to bind plugin service: ${serviceInfo.packageName}/${serviceInfo.name}")
        }

        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PLUGIN_INFO_QUERY_TIMEOUT_SECONDS)
            while (!latch.await(250L, TimeUnit.MILLISECONDS)) {
                ensureNotCancelled(cancelSignal)
                if (System.nanoTime() >= deadline) {
                    throw IOException("Timeout while querying plugin info: ${serviceInfo.packageName}/${serviceInfo.name}")
                }
            }
            ensureNotCancelled(cancelSignal)
            error?.let { throw it }
            return pluginInfo ?: throw IOException("Plugin returned empty info: ${serviceInfo.packageName}/${serviceInfo.name}")
        } finally {
            runCatching { context.unbindService(connection) }
        }
    }

    private fun queryServices(context: Context, action: String): List<ServiceInfo> {
        val intent = Intent(action).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(intent, 0)
        }
        return resolveList.mapNotNull { it.serviceInfo }
    }

    private fun tryWakePluginServices(
        context: Context,
        services: List<ServiceInfo>,
        cancelSignal: AtomicBoolean?,
    ): Boolean {
        var didWake = false
        services.map { it.packageName }.distinct().forEach { packageName ->
            ensureNotCancelled(cancelSignal)
            didWake = PluginWakeManager.tryAutoWakeIfNeeded(context, packageName) || didWake
        }
        return didWake
    }

    private fun findNativeLibraryEntry(apk: ZipFile, abi: String, soName: String): ZipEntry? {
        NATIVE_LIBRARY_ENTRY_ROOTS.forEach { root ->
            apk.getEntry("$root/$abi/$soName")?.let { return it }
        }
        return null
    }

    private fun writeZipFile(file: File, writer: (ZipOutputStream) -> Unit) {
        writeZipFile(file, Deflater.DEFAULT_COMPRESSION, writer)
    }

    private fun writeZipFile(
        file: File,
        compressionLevel: Int,
        writer: (ZipOutputStream) -> Unit,
    ) {
        FileOutputStream(file, false).use { fileOutput ->
            val bufferedOutput = fileOutput.buffered(BUFFER_SIZE)
            ZipOutputStream(bufferedOutput).use { zip ->
                zip.setLevel(compressionLevel)
                writer(zip)
                zip.finish()
                zip.flush()
                bufferedOutput.flush()
                syncBestEffort(fileOutput)
            }
        }
    }

    private fun notifyProgress(
        progressListener: ((ApkBuildProgress) -> Unit)?,
        title: String,
        detail: String?,
    ) {
        progressListener?.invoke(
            ApkBuildProgress(
                step = ApkBuildProgress.STEP_PREPARE,
                title = title,
                detail = detail,
            )
        )
    }

    private fun syncBestEffort(fileOutput: FileOutputStream) {
        try {
            fileOutput.fd.sync()
        } catch (_: SyncFailedException) {
            // The archive is a local temp file and is read/hashed immediately after close.
        }
    }

    private fun nativeDirMatchesAbi(nativeLibraryDir: File, abiCandidates: List<String>): Boolean {
        val path = nativeLibraryDir.path.replace('\\', '/')
        return abiCandidates.any { abi -> path.endsWith("/$abi") }
    }

    private fun abiCandidates(abi: String): List<String> {
        return buildList {
            add(abi)
            ABI_ALIASES[abi]?.let(::add)
        }.distinct()
    }

    private fun getPackageInfoCompat(pm: PackageManager, packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }

    private fun getPackageVersionName(pm: PackageManager, packageName: String): String? =
        runCatching { getPackageInfoCompat(pm, packageName).versionName }.getOrNull()

    /** Copies [input] to [target] and returns its SHA-256. */
    private fun copyAndHash(input: InputStream, target: File, cancelSignal: AtomicBoolean?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileOutputStream(target, false).use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                ensureNotCancelled(cancelSignal)
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                output.write(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun getApplicationInfoCompat(pm: PackageManager, packageName: String): ApplicationInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
    }

    private fun addDirectoryEntry(zip: ZipOutputStream, entryName: String) {
        val safeName = sanitizeEntryPath(entryName).let { if (it.endsWith('/')) it else "$it/" }
        zip.putNextEntry(ZipEntry(safeName))
        zip.closeEntry()
    }

    private fun addFile(
        zip: ZipOutputStream,
        file: File,
        entryName: String,
        cancelSignal: AtomicBoolean?,
    ) {
        zip.putNextEntry(ZipEntry(sanitizeEntryPath(entryName)))
        FileInputStream(file).use { input ->
            copyStreamWithCancel(input, zip, cancelSignal)
        }
        zip.closeEntry()
    }

    private fun addBytes(
        zip: ZipOutputStream,
        bytes: ByteArray,
        entryName: String,
    ) {
        zip.putNextEntry(ZipEntry(sanitizeEntryPath(entryName)))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun copyStreamWithCancel(
        input: InputStream,
        output: OutputStream,
        cancelSignal: AtomicBoolean?,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            ensureNotCancelled(cancelSignal)
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            output.write(buffer, 0, read)
        }
    }

    private fun ensureNotCancelled(cancelSignal: AtomicBoolean?) {
        if (cancelSignal?.get() == true || Thread.currentThread().isInterrupted) {
            throw CancellationException("Build aborted")
        }
    }

    private fun isExcluded(file: File, excludedDirs: List<File>): Boolean {
        if (!file.isDirectory || excludedDirs.isEmpty()) {
            return false
        }
        val path = runCatching { file.canonicalFile.path }.getOrDefault(file.absolutePath)
        return excludedDirs.any { excluded ->
            val excludedPath = excluded.path
            path == excludedPath || path.startsWith("$excludedPath${File.separator}")
        }
    }

    private fun sanitizeEntrySegment(value: String): String {
        return value.replace('\\', '/')
            .substringAfterLast('/')
            .takeIf { it.isNotBlank() && it != "." && it != ".." }
            ?: throw IOException("Invalid archive entry name: $value")
    }

    private fun sanitizeEntryPath(value: String): String {
        val normalized = value.replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." }
        require(normalized.none { it == ".." }) {
            "Invalid archive entry path: $value"
        }
        return normalized.joinToString("/")
    }

    private fun toAssetZipPrefix(path: String): String {
        val normalized = normalizeAssetPath(path)
        return if (normalized.isEmpty()) "assets/" else "assets/$normalized"
    }

    private fun normalizeAssetPath(path: String): String {
        var out = path.trim().replace('\\', '/')
        while (out.startsWith("/")) {
            out = out.removePrefix("/")
        }
        if (out.startsWith("assets/")) {
            out = out.removePrefix("assets/")
        }
        while (out.endsWith("/")) {
            out = out.removeSuffix("/")
        }
        return out
    }

    private fun sha256(
        file: File,
        cancelSignal: AtomicBoolean?,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                ensureNotCancelled(cancelSignal)
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun zipUncompressedSizeBytes(
        file: File,
        label: String,
        cancelSignal: AtomicBoolean?,
    ): Long {
        var total = 0L
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                ensureNotCancelled(cancelSignal)
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val size = entry.size
                if (size < 0L) {
                    throw IOException("Remote build $label contains an entry with unknown size.")
                }
                total = try {
                    Math.addExact(total, size)
                } catch (_: ArithmeticException) {
                    throw IOException("Remote build $label uncompressed size is too large.")
                }
            }
        }
        return total
    }

    private fun deletePreparedFile(file: File) {
        runCatching {
            if (file.exists()) {
                PFiles.deleteRecursively(file)
            }
        }
    }

    private val ABI_ALIASES = mapOf(
        "arm64-v8a" to "arm64",
        "armeabi-v7a" to "arm",
    )

    private fun String.asciiCaseFold(): String = buildString(length) {
        this@asciiCaseFold.forEach { character ->
            append(if (character in 'A'..'Z') character + 32 else character)
        }
    }

    private fun clearTypeScriptStagingKey(extras: Bundle?) {
        extras
            ?.getByteArray(ApkBuildRequestExtraKeys.TYPESCRIPT_STAGING_ENCRYPTION_KEY)
            ?.fill(0)
        extras?.remove(ApkBuildRequestExtraKeys.TYPESCRIPT_STAGING_ENCRYPTION_KEY)
    }
}
