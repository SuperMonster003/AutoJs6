package org.autojs.autojs.apkbuilder.template

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.google.gson.GsonBuilder
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginWakeManager
import org.autojs.autojs.core.plugin.imagequant.ImageQuantPluginHost
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
    private val NATIVE_LIBRARY_ENTRY_ROOTS = listOf("lib", "libs")
    private val PLUGIN_HOST_PREFERRED_NATIVE_LIB_NAMES = setOf("libc++_shared.so")
    private val PLUGIN_WAKE_RETRY_DELAYS_MS = longArrayOf(300L, 800L, 1500L)

    private data class SelectedPluginArtifact(
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
            if (JavaScriptSource.isBunFileName(entry.name)) {
                mode = mode or JavaScriptSource.EXECUTION_MODE_BUN
            } else if (JavaScriptSource.isNodeJsFileName(entry.name)) {
                mode = mode or JavaScriptSource.EXECUTION_MODE_NODEJS
            }
            JavaScriptSource.validateExclusiveEngineMode(mode)
            mode
        }
        if ((entryExecutionMode ?: 0) and JavaScriptSource.EXECUTION_MODE_BUN != 0 || requestsExternalBunRuntime(
                projectType = projectConfig.projectType,
                configuredEntryName = projectConfig.mainScriptFileName,
                sourceName = source.name,
                entryExecutionMode = entryExecutionMode,
            )
        ) {
            throw IOException(
                "Bun Runtime is provided by an external AutoJs6 plugin and cannot be embedded " +
                    "in a packaged APK. Run Bun scripts from the AutoJs6 host with the Bun Runtime plugin installed."
            )
        }
        if (!requestsEmbeddedNodePackaging(
                selectedLibraries = projectConfig.libs,
                projectType = projectConfig.projectType,
                hasNodeConfig = projectConfig.nodeConfig != null,
                configuredEntryName = projectConfig.mainScriptFileName,
                sourceName = source.name,
                entryExecutionMode = entryExecutionMode,
            )
        ) {
            return
        }
        throw IOException(
            "Embedded Node.js packaging was retired because the runtime is owned by the external plugin. " +
                    "Remove the Embedded Node.js library from this packaged project; run Node.js scripts " +
                    "from the AutoJs6 host with the runtime plugin installed."
        )
    }

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

    internal fun requestsEmbeddedNodePackaging(
        selectedLibraries: List<String>,
        projectType: String?,
        hasNodeConfig: Boolean,
        configuredEntryName: String?,
        sourceName: String?,
        entryExecutionMode: Int?,
    ): Boolean {
        val embeddedNode = ApkBuildLibrary.EMBEDDED_NODE_JS
        val selectedAsLibrary = selectedLibraries.any { selected ->
            val normalized = selected.trim()
            normalized.equals(embeddedNode.label, ignoreCase = true) ||
                    embeddedNode.aliases.any { alias -> normalized.equals(alias, ignoreCase = true) }
        }
        return selectedAsLibrary ||
                projectType?.trim()?.equals("node", ignoreCase = true) == true ||
                hasNodeConfig ||
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

        val appInfo = getApplicationInfoCompat(context.packageManager, context.packageName)
        val apkPaths = buildList {
            add(appInfo.sourceDir)
            appInfo.splitSourceDirs?.forEach { add(it) }
        }.distinct()
        val nativeLibraryDir = File(appInfo.nativeLibraryDir.orEmpty()).takeIf { it.isDirectory }
        val archiveFile = File(cacheDir, "native-libraries-${System.nanoTime()}.zip")
        var wroteAny = false
        val writtenEntries = linkedSetOf<String>()

        try {
            writeZipFile(archiveFile, Deflater.BEST_SPEED) { zip ->
                targetAbis.forEach { abi ->
                    ensureNotCancelled(cancelSignal)
                    val abiCandidates = abiCandidates(abi)
                    requiredLibs.forEach { soName ->
                        ensureNotCancelled(cancelSignal)
                        val entryName = "lib/$abi/$soName"
                        if (copyFirstMatchedSoToZip(zip, writtenEntries, entryName, apkPaths, nativeLibraryDir, abiCandidates, soName, cancelSignal, progressListener)) {
                            wroteAny = true
                        }
                    }
                }
                pluginArtifacts.forEach { artifact ->
                    ensureNotCancelled(cancelSignal)
                    if (copyPluginArtifactToZip(zip, writtenEntries, context, targetAbis, apkPaths, nativeLibraryDir, artifact, cancelSignal, progressListener)) {
                        wroteAny = true
                    }
                }
            }
        } catch (error: Throwable) {
            deletePreparedFile(archiveFile)
            throw error
        }

        return archiveFile.takeIf { wroteAny && it.length() > 0L } ?: run {
            deletePreparedFile(archiveFile)
            null
        }
    }

    private fun resolveHostNativeLibraries(projectConfig: ProjectConfig): List<String> {
        val selectedLibs = projectConfig.libs.toSet()
        return ApkBuildLibrary.entries
            .filter { lib -> lib == ApkBuildLibrary.TERMINAL_EMULATOR || (!lib.isPlugin && lib.label in selectedLibs) }
            .flatMap { it.libsToInclude }
            .distinct()
    }

    private fun copyFirstMatchedSoToZip(
        zip: ZipOutputStream,
        writtenEntries: MutableSet<String>,
        entryName: String,
        apkPaths: List<String>,
        nativeLibraryDir: File?,
        abiCandidates: List<String>,
        soName: String,
        cancelSignal: AtomicBoolean?,
        progressListener: ((ApkBuildProgress) -> Unit)?,
    ): Boolean {
        if (entryName in writtenEntries) {
            return true
        }
        apkPaths.forEach { apkPath ->
            ensureNotCancelled(cancelSignal)
            val copied = try {
                ZipFile(apkPath).use { apk ->
                    abiCandidates.firstNotNullOfOrNull { abi ->
                        ensureNotCancelled(cancelSignal)
                        findNativeLibraryEntry(apk, abi, soName)
                    }?.let { entry ->
                        notifyProgress(progressListener, "Copying native libraries", entryName)
                        zip.putNextEntry(ZipEntry(entryName))
                        apk.getInputStream(entry).use { input -> copyStreamWithCancel(input, zip, cancelSignal) }
                        zip.closeEntry()
                        writtenEntries += entryName
                        true
                    } ?: false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                false
            }
            if (copied) {
                return true
            }
        }

        val nativeFile = nativeLibraryDir
            ?.takeIf { nativeDirMatchesAbi(it, abiCandidates) }
            ?.resolve(soName)
            ?.takeIf { it.isFile }
            ?: return false

        notifyProgress(progressListener, "Copying native libraries", entryName)
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(nativeFile).use { input -> copyStreamWithCancel(input, zip, cancelSignal) }
        zip.closeEntry()
        writtenEntries += entryName
        return true
    }

    private fun copyPluginArtifactToZip(
        zip: ZipOutputStream,
        writtenEntries: MutableSet<String>,
        context: Context,
        targetAbis: List<String>,
        hostApkPaths: List<String>,
        hostNativeLibraryDir: File?,
        artifact: SelectedPluginArtifact,
        cancelSignal: AtomicBoolean?,
        progressListener: ((ApkBuildProgress) -> Unit)?,
    ): Boolean {
        val appInfo = getApplicationInfoCompat(context.packageManager, artifact.serviceInfo.packageName)
        val pluginApkPaths = buildList {
            add(appInfo.sourceDir)
            appInfo.splitSourceDirs?.forEach { add(it) }
        }.distinct()
        val pluginNativeLibraryDir = File(appInfo.nativeLibraryDir.orEmpty()).takeIf { it.isDirectory }
        var wroteAny = false

        val missingNativeLibraries = mutableListOf<Pair<String, String>>()
        targetAbis.forEach { abi ->
            ensureNotCancelled(cancelSignal)
            val abiCandidates = abiCandidates(abi)
            (artifact.variant.libsToInclude + artifact.variant.optionalLibsToInclude).distinct().forEach { soName ->
                ensureNotCancelled(cancelSignal)
                val entryName = "lib/$abi/$soName"
                // Keep local and remote builds on the template's single libc++ runtime. Use a
                // plugin copy only if the template does not provide the requested ABI.
                val copied = if (soName in PLUGIN_HOST_PREFERRED_NATIVE_LIB_NAMES) {
                    copyFirstMatchedSoToZip(
                        zip = zip,
                        writtenEntries = writtenEntries,
                        entryName = entryName,
                        apkPaths = hostApkPaths,
                        nativeLibraryDir = hostNativeLibraryDir,
                        abiCandidates = abiCandidates,
                        soName = soName,
                        cancelSignal = cancelSignal,
                        progressListener = progressListener,
                    ) || copyFirstMatchedSoToZip(
                        zip = zip,
                        writtenEntries = writtenEntries,
                        entryName = entryName,
                        apkPaths = pluginApkPaths,
                        nativeLibraryDir = pluginNativeLibraryDir,
                        abiCandidates = abiCandidates,
                        soName = soName,
                        cancelSignal = cancelSignal,
                        progressListener = progressListener,
                    )
                } else {
                    copyFirstMatchedSoToZip(
                        zip = zip,
                        writtenEntries = writtenEntries,
                        entryName = entryName,
                        apkPaths = pluginApkPaths,
                        nativeLibraryDir = pluginNativeLibraryDir,
                        abiCandidates = abiCandidates,
                        soName = soName,
                        cancelSignal = cancelSignal,
                        progressListener = progressListener,
                    )
                }
                if (copied) {
                    wroteAny = true
                } else if (soName in artifact.variant.libsToInclude) {
                    missingNativeLibraries += abi to soName
                }
            }
        }

        if (missingNativeLibraries.isNotEmpty()) {
            val detail = missingNativeLibraries.joinToString(", ") { (abi, soName) -> "$abi/$soName" }
            throw IOException(context.getString(R.string.error_plugin_apk_does_not_contain_required_native_libraries, detail))
        }

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
            val packagedVariant = if (
                lib != ApkBuildLibrary.OPENCV &&
                ApkBuildLibrary.OPENCV.label in selectedLabels
            ) {
                // The dedicated OpenCV plugin is the single owner of this JNI runtime.
                variant.copy(
                    libsToInclude = variant.libsToInclude.filterNot {
                        it == OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME
                    },
                    optionalLibsToInclude = variant.optionalLibsToInclude.filterNot {
                        it == OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME
                    },
                )
            } else {
                variant
            }
            SelectedPluginArtifact(serviceInfo, packagedVariant)
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
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
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
