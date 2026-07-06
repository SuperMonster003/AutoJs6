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
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginWakeManager
import org.autojs.autojs.apkbuilder.ApkBuilder
import org.autojs.autojs.apkbuilder.keystore.AESUtils
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.engine.NodeBridgePermissionManifest
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.apkbuilder.template.ApkBuildRequest
import org.autojs.plugin.apkbuilder.template.ApkBuildRequestExtraKeys
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateProtocol
import org.autojs.plugin.common.api.PluginInfo
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.SyncFailedException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object RemoteApkBuildRequestPreparer {

    private const val ARCHIVE_FORMAT_VERSION = 1
    private const val SOURCE_ROOT_ENTRY = "project"
    private const val REMOTE_ICON_ENTRY = "__autojs6_remote__/icon.png"
    private const val BUFFER_SIZE = 256 * 1024
    private const val PLUGIN_INFO_QUERY_TIMEOUT_SECONDS = 60L
    private val NATIVE_LIBRARY_ENTRY_ROOTS = listOf("lib", "libs")
    private val PLUGIN_HOST_FALLBACK_NATIVE_LIB_NAMES = setOf("libc++_shared.so")
    private val PLUGIN_WAKE_RETRY_DELAYS_MS = longArrayOf(300L, 800L, 1500L)

    private data class SelectedPluginArtifact(
        val serviceInfo: ServiceInfo,
        val variant: ApkBuilder.PluginLibVariant,
    )

    data class Prepared(
        val request: ApkBuildRequest,
        val archiveFile: File,
        val nativeLibrariesArchiveFile: File?,
    ) : Closeable {
        override fun close() {
            runCatching { request.projectArchiveFd?.close() }
            runCatching { request.nativeLibrariesArchiveFd?.close() }
            runCatching { request.keyStoreFd?.close() }
        }

        fun cleanup() {
            close()
            if (archiveFile.exists()) {
                archiveFile.delete()
            }
            nativeLibrariesArchiveFile?.takeIf { it.exists() }?.delete()
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun prepare(
        context: Context,
        projectConfig: ProjectConfig,
        outputFileName: String,
    ): Prepared {
        val appContext = context.applicationContext
        val source = File(projectConfig.sourcePath ?: throw IOException("Project source path is not set."))
        if (!source.exists()) {
            throw IOException("Project source path does not exist: ${source.path}")
        }

        val cacheDir = File(appContext.cacheDir, "apk-remote-build-input").apply {
            mkdirs()
            listFiles()?.forEach { PFiles.deleteRecursively(it) }
        }
        val archiveFile = File(cacheDir, "project-${System.nanoTime()}.zip")
        val remoteConfig = cloneForRemoteRequest(projectConfig)
        applyHostSideConfigAdjustments(source, projectConfig, remoteConfig)
        val sourcePathInArchive = writeProjectArchive(archiveFile, source, projectConfig, remoteConfig)
        val nativeLibrariesArchiveFile = writeBuildInputsArchive(
            context = appContext,
            cacheDir = cacheDir,
            projectConfig = projectConfig,
        )

        val keyStore = projectConfig.keyStore
        val keyStoreFile = keyStore?.absolutePath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
        if (keyStoreFile != null && !keyStoreFile.isFile) {
            throw IOException("KeyStore file does not exist: ${keyStoreFile.path}")
        }

        val request = ApkBuildRequest(
            hostPackageName = appContext.packageName,
            hostVersionName = BuildConfig.VERSION_NAME,
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            requiredProtocolVersion = ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION,
            projectArchiveFd = ParcelFileDescriptor.open(archiveFile, ParcelFileDescriptor.MODE_READ_ONLY),
            projectArchiveSizeBytes = archiveFile.length(),
            projectArchiveSha256 = sha256(archiveFile),
            nativeLibrariesArchiveFd = nativeLibrariesArchiveFile?.let {
                ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY)
            },
            nativeLibrariesArchiveSizeBytes = nativeLibrariesArchiveFile?.length() ?: 0L,
            nativeLibrariesArchiveSha256 = nativeLibrariesArchiveFile?.let(::sha256),
            projectConfigJson = remoteConfig.toJson(false),
            keyStoreFd = keyStoreFile?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) },
            keyStoreSizeBytes = keyStoreFile?.length() ?: 0L,
            keyStoreSha256 = keyStoreFile?.let(::sha256),
            keyStorePassword = keyStore?.password?.takeIf { it.isNotBlank() }?.let(AESUtils::decrypt),
            keyAlias = keyStore?.alias,
            keyAliasPassword = keyStore?.aliasPassword?.takeIf { it.isNotBlank() }?.let(AESUtils::decrypt),
            outputFileName = outputFileName,
            allowRiskyBuild = true,
            extras = Bundle().apply {
                putInt(ApkBuildRequestExtraKeys.ARCHIVE_FORMAT_VERSION, ARCHIVE_FORMAT_VERSION)
                putString(
                    ApkBuildRequestExtraKeys.SOURCE_KIND,
                    if (source.isDirectory) {
                        ApkBuildRequestExtraKeys.SOURCE_KIND_DIRECTORY
                    } else {
                        ApkBuildRequestExtraKeys.SOURCE_KIND_FILE
                    },
                )
                putString(ApkBuildRequestExtraKeys.SOURCE_ROOT_PATH, SOURCE_ROOT_ENTRY)
                putString(ApkBuildRequestExtraKeys.SOURCE_PATH, sourcePathInArchive)
                if (remoteConfig.iconPath == REMOTE_ICON_ENTRY) {
                    putString(ApkBuildRequestExtraKeys.ICON_PATH, REMOTE_ICON_ENTRY)
                }
                putString(ApkBuildRequestExtraKeys.HOST_OUTPUT_FILE_NAME, outputFileName)
            },
        )

        return Prepared(request, archiveFile, nativeLibrariesArchiveFile)
    }

    private fun cloneForRemoteRequest(projectConfig: ProjectConfig): ProjectConfig {
        val json = projectConfig.toJson(false)
        return ProjectConfig.fromJson(json)
            ?.setIconPath(null)
            ?: throw IOException("Failed to clone project config for remote build request.")
    }

    private fun applyHostSideConfigAdjustments(
        source: File,
        projectConfig: ProjectConfig,
        remoteConfig: ProjectConfig,
    ) {
        applyNodeBridgeAndroidManifestPermissions(source, projectConfig, remoteConfig)
    }

    private fun applyNodeBridgeAndroidManifestPermissions(
        source: File,
        projectConfig: ProjectConfig,
        remoteConfig: ProjectConfig,
    ) {
        if (!source.isDirectory || !projectConfig.libs.contains(ApkBuilder.Lib.EMBEDDED_NODE_JS.label)) {
            return
        }
        val requiredPermissions = NodeBridgePermissionManifest.androidPermissionsForWorkingDirectory(source.path)
        if (requiredPermissions.isEmpty()) {
            return
        }
        val mergedPermissions = LinkedHashSet(remoteConfig.permissions)
        mergedPermissions.addAll(requiredPermissions)
        remoteConfig.setPermissions(ArrayList(mergedPermissions))
    }

    private fun writeProjectArchive(
        archiveFile: File,
        source: File,
        projectConfig: ProjectConfig,
        remoteConfig: ProjectConfig,
    ): String {
        var sourcePathInArchive = SOURCE_ROOT_ENTRY
        writeZipFile(archiveFile) { zip ->
            if (source.isDirectory) {
                addDirectoryTree(
                    zip = zip,
                    root = source.canonicalFile,
                    entryRoot = SOURCE_ROOT_ENTRY,
                    excludedDirs = projectConfig.excludedDirs.mapNotNull { runCatching { it.canonicalFile }.getOrNull() },
                )
            } else {
                sourcePathInArchive = "$SOURCE_ROOT_ENTRY/${sanitizeEntrySegment(source.name)}"
                addFile(zip, source, sourcePathInArchive)
            }
            if (writeIconEntryIfAvailable(zip, projectConfig)) {
                remoteConfig.setIconPath(REMOTE_ICON_ENTRY)
            }
        }
        return sourcePathInArchive
    }

    private fun addDirectoryTree(
        zip: ZipOutputStream,
        root: File,
        entryRoot: String,
        excludedDirs: List<File>,
    ) {
        addDirectoryEntry(zip, "$entryRoot/")
        root.walkTopDown()
            .onEnter { dir -> !isExcluded(dir, excludedDirs) }
            .filter { it != root }
            .filterNot { isExcluded(it, excludedDirs) }
            .forEach { file ->
                val relative = file.relativeTo(root).path.replace(File.separatorChar, '/')
                val entryName = "$entryRoot/${sanitizeEntryPath(relative)}"
                if (file.isDirectory) {
                    addDirectoryEntry(zip, "$entryName/")
                } else if (file.isFile) {
                    addFile(zip, file, entryName)
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
    ): File? {
        val requiredLibs = resolveHostNativeLibraries(projectConfig)
        val targetAbis = projectConfig.abis.distinct()
        val pluginArtifacts = resolveSelectedPluginArtifacts(context, projectConfig)
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

        writeZipFile(archiveFile) { zip ->
            targetAbis.forEach { abi ->
                val abiCandidates = abiCandidates(abi)
                requiredLibs.forEach { soName ->
                    val entryName = "lib/$abi/$soName"
                    if (copyFirstMatchedSoToZip(zip, writtenEntries, entryName, apkPaths, nativeLibraryDir, abiCandidates, soName)) {
                        wroteAny = true
                    }
                }
            }
            pluginArtifacts.forEach { artifact ->
                if (copyPluginArtifactToZip(zip, writtenEntries, context, targetAbis, apkPaths, nativeLibraryDir, artifact)) {
                    wroteAny = true
                }
            }
        }

        return archiveFile.takeIf { wroteAny && it.length() > 0L } ?: run {
            archiveFile.delete()
            null
        }
    }

    private fun resolveHostNativeLibraries(projectConfig: ProjectConfig): List<String> {
        val selectedLibs = projectConfig.libs.toSet()
        return ApkBuilder.Lib.entries
            .filter { lib -> lib == ApkBuilder.Lib.TERMINAL_EMULATOR || (!lib.isPlugin && lib.label in selectedLibs) }
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
    ): Boolean {
        if (entryName in writtenEntries) {
            return true
        }
        apkPaths.forEach { apkPath ->
            val copied = runCatching {
                ZipFile(apkPath).use { apk ->
                    abiCandidates.firstNotNullOfOrNull { abi ->
                        findNativeLibraryEntry(apk, abi, soName)
                    }?.let { entry ->
                        zip.putNextEntry(ZipEntry(entryName))
                        apk.getInputStream(entry).use { input -> input.copyTo(zip, BUFFER_SIZE) }
                        zip.closeEntry()
                        writtenEntries += entryName
                        true
                    } ?: false
                }
            }.getOrDefault(false)
            if (copied) {
                return true
            }
        }

        val nativeFile = nativeLibraryDir
            ?.takeIf { nativeDirMatchesAbi(it, abiCandidates) }
            ?.resolve(soName)
            ?.takeIf { it.isFile }
            ?: return false

        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(nativeFile).use { input -> input.copyTo(zip, BUFFER_SIZE) }
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
            val abiCandidates = abiCandidates(abi)
            artifact.variant.libsToInclude.forEach { soName ->
                val entryName = "lib/$abi/$soName"
                val copied = copyFirstMatchedSoToZip(
                    zip = zip,
                    writtenEntries = writtenEntries,
                    entryName = entryName,
                    apkPaths = pluginApkPaths,
                    nativeLibraryDir = pluginNativeLibraryDir,
                    abiCandidates = abiCandidates,
                    soName = soName,
                ) || (
                    soName in PLUGIN_HOST_FALLBACK_NATIVE_LIB_NAMES &&
                        copyFirstMatchedSoToZip(
                            zip = zip,
                            writtenEntries = writtenEntries,
                            entryName = entryName,
                            apkPaths = hostApkPaths,
                            nativeLibraryDir = hostNativeLibraryDir,
                            abiCandidates = abiCandidates,
                            soName = soName,
                        )
                    )
                if (copied) {
                    wroteAny = true
                } else {
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
            if (copyPluginAssetsByPrefixToZip(zip, writtenEntries, pluginApkPaths, prefix)) {
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
            if (copyPluginAssetsByPrefixToZip(zip, writtenEntries, pluginApkPaths, prefix)) {
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
    ): Boolean {
        val zipPrefix = toAssetZipPrefix(assetPrefix)
        var copiedAny = false
        apkPaths.forEach { apkPath ->
            runCatching {
                ZipFile(apkPath).use { apk ->
                    val entries = apk.entries()
                    while (entries.hasMoreElements()) {
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
                        apk.getInputStream(entry).use { input -> input.copyTo(zip, BUFFER_SIZE) }
                        zip.closeEntry()
                        writtenEntries += name
                        copiedAny = true
                    }
                }
            }
        }
        return copiedAny
    }

    private fun resolveSelectedPluginArtifacts(
        context: Context,
        projectConfig: ProjectConfig,
    ): List<SelectedPluginArtifact> {
        val selectedLabels = projectConfig.libs.toSet()
        return ApkBuilder.Lib.entries.mapNotNull { lib ->
            if (!lib.isPlugin || lib.label !in selectedLabels) {
                return@mapNotNull null
            }
            val (serviceInfo, variant) = selectPluginServiceOrThrow(context, lib, lib.toPluginPair().second)
            SelectedPluginArtifact(serviceInfo, variant)
        }
    }

    private fun selectPluginServiceOrThrow(
        context: Context,
        lib: ApkBuilder.Lib,
        plugin: ApkBuilder.PluginLib,
    ): Pair<ServiceInfo, ApkBuilder.PluginLibVariant> {
        val enabledServices = queryServices(context, plugin.action)
            .filter { service -> PluginEnableStore.isEnabled(context, service.packageName, defaultEnabled = true) }

        if (enabledServices.isEmpty()) {
            throw IOException(context.getString(R.string.error_no_enabled_plugin_for_module_label, lib.label))
        }

        selectPluginVariant(context, enabledServices, plugin)?.let { return it }

        if (tryWakePluginServices(context, enabledServices)) {
            PLUGIN_WAKE_RETRY_DELAYS_MS.forEach { delayMs ->
                Thread.sleep(delayMs)
                val retryServices = queryServices(context, plugin.action)
                    .filter { service -> PluginEnableStore.isEnabled(context, service.packageName, defaultEnabled = true) }
                selectPluginVariant(context, retryServices, plugin)?.let { return it }
            }
        }

        val variantList = plugin.variants.joinToString("/") { it.variant.orEmpty() }
        throw IOException(context.getString(R.string.error_no_available_enabled_plugin_variants_found, lib.label, variantList))
    }

    private fun selectPluginVariant(
        context: Context,
        services: List<ServiceInfo>,
        plugin: ApkBuilder.PluginLib,
    ): Pair<ServiceInfo, ApkBuilder.PluginLibVariant>? {
        val candidates = services.mapNotNull { service ->
            val info = runCatching { queryPluginInfoBlocking(context, service, plugin) }.getOrNull()
                ?: return@mapNotNull null
            resolvePluginVariant(plugin, info, service)?.let { variant -> service to variant }
        }
        return ApkBuilder.selectBestPluginVariantCandidate(candidates, plugin.variants)
    }

    private fun resolvePluginVariant(
        plugin: ApkBuilder.PluginLib,
        info: PluginInfo,
        serviceInfo: ServiceInfo,
    ): ApkBuilder.PluginLibVariant? {
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

    private fun isPluginFamilyMatch(plugin: ApkBuilder.PluginLib, info: PluginInfo, serviceInfo: ServiceInfo): Boolean {
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
        plugin: ApkBuilder.PluginLib,
    ): PluginInfo {
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
            if (!latch.await(PLUGIN_INFO_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IOException("Timeout while querying plugin info: ${serviceInfo.packageName}/${serviceInfo.name}")
            }
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

    private fun tryWakePluginServices(context: Context, services: List<ServiceInfo>): Boolean {
        var didWake = false
        services.map { it.packageName }.distinct().forEach { packageName ->
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
        FileOutputStream(file, false).use { fileOutput ->
            val bufferedOutput = fileOutput.buffered(BUFFER_SIZE)
            ZipOutputStream(bufferedOutput).use { zip ->
                writer(zip)
                zip.finish()
                zip.flush()
                bufferedOutput.flush()
                syncBestEffort(fileOutput)
            }
        }
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

    private fun addFile(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(sanitizeEntryPath(entryName)))
        FileInputStream(file).use { input ->
            input.copyTo(zip, BUFFER_SIZE)
        }
        zip.closeEntry()
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

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private val ABI_ALIASES = mapOf(
        "arm64-v8a" to "arm64",
        "armeabi-v7a" to "arm",
    )
}
