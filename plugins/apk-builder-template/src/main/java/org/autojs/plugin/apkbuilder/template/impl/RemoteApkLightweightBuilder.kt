package org.autojs.plugin.apkbuilder.template.impl

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import com.reandroid.arsc.chunk.TableBlock
import com.mcal.apksigner.ApkSigner
import org.autojs.plugin.apkbuilder.template.ApkBuildProgress
import org.autojs.plugin.apkbuilder.template.ApkBuildRequest
import org.autojs.plugin.apkbuilder.template.ApkBuildRequestExtraKeys
import zhao.arsceditor.ResDecoder.ARSCDecoder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

class RemoteApkLightweightBuilder(
    context: Context,
    private val request: ApkBuildRequest,
    private val workspace: RemoteApkBuildWorkspace,
    private val cancelSignal: AtomicBoolean,
    private val progress: (ApkBuildProgress) -> Unit,
) {

    private val appContext = context.applicationContext

    data class Output(
        val apkFile: File,
        val outputFileName: String,
        val sha256: String,
        val updatedProjectConfigJson: String,
        val warnings: ArrayList<String>,
        val extras: Bundle,
    )

    fun build(): Output {
        val projectConfig = parseProjectConfig()
        val buildDir = File(workspace.root, "apk-workspace")
        val outputApk = File(workspace.root, sanitizeOutputFileName(request.outputFileName))

        ensureActive()
        progress(ApkBuildProgress(ApkBuildProgress.STEP_PREPARE, "Extracting template APK", buildDir.path))
        unzipTemplate(buildDir)

        ensureActive()
        progress(ApkBuildProgress(ApkBuildProgress.STEP_BUILD, "Copying remote build inputs", null))
        copyRemoteBuildInputs(buildDir, projectConfig)

        ensureActive()
        progress(ApkBuildProgress(ApkBuildProgress.STEP_BUILD, "Configuring AndroidManifest.xml", projectConfig.packageName))
        configureManifest(buildDir, projectConfig)

        ensureActive()
        progress(ApkBuildProgress(ApkBuildProgress.STEP_BUILD, "Pruning native libraries", null))
        pruneNativeLibrariesByAbiPolicy(buildDir, projectConfig.abis)

        ensureActive()
        progress(ApkBuildProgress(ApkBuildProgress.STEP_BUILD, "Copying project files", workspace.sourcePath.path))
        val updatedProjectConfigJson = updateProjectBuildInfo(projectConfig)
        copyProjectAssets(buildDir, projectConfig, updatedProjectConfigJson)

        ensureActive()
        progress(ApkBuildProgress(ApkBuildProgress.STEP_BUILD, "Replacing app icon", null))
        replaceAppIconIfProvided(buildDir)

        ensureActive()
        progress(ApkBuildProgress(ApkBuildProgress.STEP_BUILD, "Rewriting resources.arsc", projectConfig.packageName))
        rewriteResourcesArsc(buildDir, projectConfig.packageName)

        repackageAndSign(buildDir, outputApk, projectConfig)

        ensureActive()
        val outputSha256 = sha256(outputApk)
        return Output(
            apkFile = outputApk,
            outputFileName = outputApk.name,
            sha256 = outputSha256,
            updatedProjectConfigJson = updatedProjectConfigJson,
            warnings = arrayListOf(
                "Remote APK build currently uses a lightweight plugin-side builder.",
            ),
            extras = Bundle().apply {
                putString("builder", "lightweight")
                putString("workspace", workspace.root.path)
                putString("projectSource", workspace.sourcePath.path)
            },
        )
    }

    private fun parseProjectConfig(): RemoteProjectConfig {
        val json = JSONObject(request.projectConfigJson ?: throw IOException("Project config JSON is missing."))
        if (workspace.sourceKind == ApkBuildRequestExtraKeys.SOURCE_KIND_FILE) {
            json.put(KEY_MAIN, DEFAULT_MAIN_SCRIPT)
        }

        val name = json.optString(KEY_NAME).takeIf { it.isNotBlank() } ?: throw IOException("Project name is missing.")
        val packageName = json.optString(KEY_PACKAGE_NAME).takeIf { it.isNotBlank() }
            ?: throw IOException("Project packageName is missing.")
        val versionName = json.optString(KEY_VERSION_NAME).takeIf { it.isNotBlank() }
            ?: throw IOException("Project versionName is missing.")
        val versionCode = json.optInt(KEY_VERSION_CODE, -1).takeIf { it > 0 }
            ?: throw IOException("Project versionCode is missing.")
        val mainScript = json.optString(KEY_MAIN).takeIf { it.isNotBlank() } ?: DEFAULT_MAIN_SCRIPT
        val abis = parseStringArray(json, KEY_ABIS)
        val libs = parseStringArray(json, KEY_LIBS).orEmpty()
        val permissions = parseStringArray(json, KEY_PERMISSIONS)
        val signatureScheme = json.optString(KEY_SIGNATURE_SCHEME).takeIf { it.isNotBlank() } ?: DEFAULT_SIGNATURE_SCHEME
        val launchConfig = json.optJSONObject(KEY_LAUNCH_CONFIG)
        val splashVisible = launchConfig?.optBoolean(KEY_SPLASH_VISIBLE, true) ?: true

        if (!PACKAGE_NAME_PATTERN.matches(packageName)) {
            throw IOException("Invalid project packageName: $packageName")
        }

        return RemoteProjectConfig(
            json = json,
            name = name,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            mainScript = mainScript,
            abis = abis,
            libs = libs,
            permissions = permissions,
            signatureScheme = signatureScheme,
            splashVisible = splashVisible,
        )
    }

    private fun parseStringArray(json: JSONObject, key: String): List<String>? {
        if (!json.has(key) || json.isNull(key)) {
            return null
        }
        val array = json.optJSONArray(key) ?: return null
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun unzipTemplate(targetDir: File) {
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        if (!targetDir.mkdirs()) {
            throw IOException("Failed to create APK build directory: ${targetDir.path}")
        }
        ZipInputStream(appContext.assets.open(TEMPLATE_APK_ASSET).buffered(BUFFER_SIZE)).use { zip ->
            val canonicalRoot = targetDir.canonicalFile
            while (true) {
                val entry = zip.nextEntry ?: break
                extractZipEntry(zip, entry, canonicalRoot)
                zip.closeEntry()
            }
        }
    }

    private fun copyRemoteBuildInputs(buildDir: File, projectConfig: RemoteProjectConfig) {
        val requiredLibs = resolveRequiredHostNativeLibraries(projectConfig)
        val abis = projectConfig.abis.orEmpty().distinct()
        val archiveFile = workspace.nativeLibrariesArchiveFile

        if (archiveFile != null) {
            unzipBuildInputsArchive(archiveFile, buildDir)
        }

        if (requiredLibs.isEmpty() || abis.isEmpty()) {
            return
        }

        if (archiveFile == null) {
            throw RemoteApkBuildUnsupportedException(
                "Remote APK build requires native libraries, but the host did not provide a build input archive."
            )
        }

        val missing = buildList {
            abis.forEach { abi ->
                requiredLibs.forEach { soName ->
                    if (!File(buildDir, "lib/$abi/$soName").isFile) {
                        add("$abi/$soName")
                    }
                }
            }
        }
        if (missing.isNotEmpty()) {
            throw RemoteApkBuildUnsupportedException(
                "Remote APK build cannot find required host native libraries: ${missing.joinToString(", ")}"
            )
        }
    }

    private fun resolveRequiredHostNativeLibraries(projectConfig: RemoteProjectConfig): List<String> {
        val selectedLibs = projectConfig.libs.toSet()
        return (DEFAULT_NATIVE_LIBRARIES + selectedLibs.flatMap { BUILTIN_NATIVE_LIBRARIES_BY_LABEL[it].orEmpty() })
            .distinct()
    }

    private fun unzipBuildInputsArchive(zipFile: File, buildDir: File) {
        val canonicalRoot = buildDir.canonicalFile
        ZipInputStream(FileInputStream(zipFile).buffered(BUFFER_SIZE)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                extractBuildInputEntry(zip, entry, canonicalRoot)
                zip.closeEntry()
            }
        }
    }

    private fun extractBuildInputEntry(zip: ZipInputStream, entry: ZipEntry, root: File) {
        ensureActive()
        val name = entry.name.replace('\\', '/')
        if (name.isBlank() || name.startsWith('/') || name.split('/').any { it == ".." }) {
            throw IOException("Unsafe remote build input archive entry: ${entry.name}")
        }
        validateBuildInputEntry(name, entry)
        val out = File(root, name).canonicalFile
        if (!out.path.startsWith(root.path + File.separator) && out.path != root.path) {
            throw IOException("Remote build input archive entry escapes build directory: ${entry.name}")
        }
        if (entry.isDirectory) {
            if (!out.exists() && !out.mkdirs()) {
                throw IOException("Failed to create remote build input directory: ${out.path}")
            }
            return
        }
        out.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Failed to create remote build input parent directory: ${parent.path}")
            }
        }
        FileOutputStream(out, false).use { output ->
            zip.copyTo(output, BUFFER_SIZE)
            output.flush()
        }
    }

    private fun validateBuildInputEntry(name: String, entry: ZipEntry) {
        val segments = name.split('/')
        when (segments.firstOrNull()) {
            "lib" -> {
                if (entry.isDirectory) {
                    return
                }
                if (segments.size != 3 || !segments[2].endsWith(".so")) {
                    throw IOException("Unexpected native library archive entry: ${entry.name}")
                }
            }
            "assets" -> Unit
            else -> throw IOException("Unexpected remote build input archive entry: ${entry.name}")
        }
    }

    private fun extractZipEntry(zip: ZipInputStream, entry: ZipEntry, root: File) {
        ensureActive()
        val name = entry.name.replace('\\', '/')
        if (name.isBlank() || name.startsWith('/') || name.split('/').any { it == ".." }) {
            throw IOException("Unsafe template APK entry: ${entry.name}")
        }
        val out = File(root, name).canonicalFile
        if (!out.path.startsWith(root.path + File.separator) && out.path != root.path) {
            throw IOException("Template APK entry escapes build directory: ${entry.name}")
        }
        if (entry.isDirectory) {
            if (!out.exists() && !out.mkdirs()) {
                throw IOException("Failed to create APK directory: ${out.path}")
            }
            return
        }
        out.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Failed to create APK parent directory: ${parent.path}")
            }
        }
        FileOutputStream(out, false).use { output ->
            zip.copyTo(output, BUFFER_SIZE)
            output.flush()
        }
    }

    private fun configureManifest(buildDir: File, projectConfig: RemoteProjectConfig) {
        val manifestFile = File(buildDir, "AndroidManifest.xml")
        val editor = RemoteApkManifestEditor(FileInputStream(manifestFile))
            .setAppName(projectConfig.name)
            .setPackageName(projectConfig.packageName)
            .setVersionName(projectConfig.versionName)
            .setVersionCode(projectConfig.versionCode)
            .setPermissions(projectConfig.permissions)

        if (!projectConfig.splashVisible) {
            resolveSplashThemeReplacement(buildDir)?.let { (splashThemeId, noSplashThemeId) ->
                editor.setSplashThemeReplacement(splashThemeId, noSplashThemeId)
            }
        }
        if (projectConfig.libs.contains(LIB_EMBEDDED_NODE_JS)) {
            editor
                .addEmbeddedNodeScriptServiceIfMissing()
                .addEmbeddedNodeForegroundServicePermissionsIfMissing()
        }

        editor.commit().writeTo(FileOutputStream(manifestFile, false))
    }

    private fun resolveSplashThemeReplacement(buildDir: File): Pair<Int, Int>? {
        return runCatching {
            val tableBlock = TableBlock.load(File(buildDir, "resources.arsc"))
            val packageBlock = tableBlock.getOrCreatePackage(0x7f, BuildConfig.TEMPLATE_PACKAGE_NAME).also {
                tableBlock.currentPackage = it
            }
            val splashThemeId = packageBlock.getEntry("", "style", "AppTheme.Splash")?.resourceId ?: 0
            val noSplashThemeId = packageBlock.getEntry("", "style", "AppTheme.SevereTransparent")?.resourceId ?: 0
            if (splashThemeId != 0 && noSplashThemeId != 0) {
                splashThemeId to noSplashThemeId
            } else {
                null
            }
        }.onFailure {
            Log.w(TAG, "Failed to resolve remote splash theme replacement.", it)
        }.getOrNull()
    }

    private fun updateProjectBuildInfo(projectConfig: RemoteProjectConfig): String {
        val build = projectConfig.json.optJSONObject(KEY_BUILD) ?: JSONObject().also {
            projectConfig.json.put(KEY_BUILD, it)
        }
        val nextNumber = (build.optLong(KEY_BUILD_NUMBER, 0L).takeIf { it > 0L }
            ?: projectConfig.versionCode.toLong()) + 1L
        val buildTime = System.currentTimeMillis()
        build.put(KEY_BUILD_ID, generateBuildId(nextNumber, buildTime))
        build.put(KEY_BUILD_NUMBER, nextNumber)
        build.put(KEY_BUILD_TIME, buildTime)
        projectConfig.json.put(KEY_MAIN, projectConfig.mainScript)
        return projectConfig.json.toString(2)
    }

    private fun copyProjectAssets(
        buildDir: File,
        projectConfig: RemoteProjectConfig,
        projectConfigJson: String,
    ) {
        val projectAssetsDir = File(buildDir, "assets/project")
        if (projectAssetsDir.exists()) {
            projectAssetsDir.deleteRecursively()
        }
        if (!projectAssetsDir.mkdirs()) {
            throw IOException("Failed to create project assets directory: ${projectAssetsDir.path}")
        }
        val scriptEncryptor = createScriptEncryptor(projectConfig)

        when (workspace.sourceKind) {
            ApkBuildRequestExtraKeys.SOURCE_KIND_DIRECTORY -> copyDirectoryContents(workspace.sourcePath, projectAssetsDir, scriptEncryptor)
            ApkBuildRequestExtraKeys.SOURCE_KIND_FILE -> copyProjectFile(
                workspace.sourcePath,
                File(projectAssetsDir, DEFAULT_MAIN_SCRIPT),
                scriptEncryptor,
            )
            else -> throw IOException("Unsupported project source kind: ${workspace.sourceKind}")
        }

        File(projectAssetsDir, CONFIG_FILE_NAME).writeText(projectConfigJson, Charsets.UTF_8)
    }

    private fun createScriptEncryptor(projectConfig: RemoteProjectConfig): RemoteScriptEncryptor {
        val buildId = projectConfig.json
            .optJSONObject(KEY_BUILD)
            ?.optString(KEY_BUILD_ID)
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("Project build id is missing.")
        val key = md5(projectConfig.packageName + projectConfig.versionName + projectConfig.mainScript)
        val initVector = md5(buildId + projectConfig.name).take(16)
        return RemoteScriptEncryptor(key, initVector)
    }

    private fun pruneNativeLibrariesByAbiPolicy(buildDir: File, abis: List<String>?) {
        if (abis.isNullOrEmpty()) {
            return
        }
        val libRoot = File(buildDir, "lib")
        if (!libRoot.isDirectory) {
            return
        }
        val allowedAbis = abis.toSet()
        libRoot.listFiles()?.forEach { abiDir ->
            ensureActive()
            if (!abiDir.isDirectory || abiDir.name in allowedAbis) {
                return@forEach
            }
            if (!abiDir.deleteRecursively()) {
                throw IOException("Failed to delete unused ABI directory: ${abiDir.path}")
            }
        }
    }

    private fun replaceAppIconIfProvided(buildDir: File) {
        val iconFile = request.extras
            ?.getString(ApkBuildRequestExtraKeys.ICON_PATH)
            ?.takeIf { it.isNotBlank() }
            ?.let(::resolveProjectArchiveEntry)
            ?.takeIf { it.isFile }
            ?: return

        val resourcesArsc = File(buildDir, "resources.arsc")
        val tableBlock = TableBlock.load(resourcesArsc)
        val packageBlock = tableBlock.getOrCreatePackage(0x7f, BuildConfig.TEMPLATE_PACKAGE_NAME).also {
            tableBlock.currentPackage = it
        }
        val iconPath = packageBlock
            .getOrCreate("", ICON_RES_DIR, ICON_NAME)
            .resValue
            .decodeValue()
        val targetIconFile = File(buildDir, iconPath).also { file ->
            file.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    throw IOException("Failed to create icon parent directory: ${parent.path}")
                }
            }
        }
        val bitmap = BitmapFactory.decodeFile(iconFile.path)
            ?: throw IOException("Failed to decode remote project icon: ${iconFile.path}")
        FileOutputStream(targetIconFile, false).use { output ->
            if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
                throw IOException("Failed to write remote app icon: ${targetIconFile.path}")
            }
            output.flush()
        }
        Log.d(TAG, "Remote APK icon replaced: ${iconFile.path} -> ${targetIconFile.path}")
    }

    private fun resolveProjectArchiveEntry(relativePath: String): File {
        val normalized = relativePath.replace('\\', '/')
        if (normalized.isBlank() || normalized.startsWith('/') || normalized.split('/').any { it == ".." }) {
            throw IOException("Unsafe project archive path: $relativePath")
        }
        val canonicalRoot = workspace.projectRoot.canonicalFile
        val resolved = File(canonicalRoot, normalized).canonicalFile
        if (!resolved.path.startsWith(canonicalRoot.path + File.separator) && resolved.path != canonicalRoot.path) {
            throw IOException("Project archive path escapes workspace: $relativePath")
        }
        return resolved
    }

    private fun copyDirectoryContents(
        sourceDir: File,
        targetDir: File,
        scriptEncryptor: RemoteScriptEncryptor,
    ) {
        sourceDir.listFiles()?.forEach { child ->
            val target = File(targetDir, child.name)
            if (child.isDirectory) {
                if (!target.mkdirs() && !target.isDirectory) {
                    throw IOException("Failed to create project assets directory: ${target.path}")
                }
                copyDirectoryContents(child, target, scriptEncryptor)
            } else if (child.isFile) {
                copyProjectFile(child, target, scriptEncryptor)
            }
        }
    }

    private fun copyProjectFile(
        source: File,
        target: File,
        scriptEncryptor: RemoteScriptEncryptor,
    ) {
        ensureActive()
        if (scriptEncryptor.isJavaScriptFileName(target.name)) {
            scriptEncryptor.encryptFile(source, target)
        } else {
            copyFile(source, target)
        }
    }

    private fun copyFile(source: File, target: File) {
        ensureActive()
        target.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Failed to create parent directory: ${parent.path}")
            }
        }
        FileInputStream(source).use { input ->
            FileOutputStream(target, false).use { output ->
                input.copyTo(output, BUFFER_SIZE)
                output.flush()
            }
        }
    }

    private fun rewriteResourcesArsc(buildDir: File, packageName: String) {
        val oldArsc = File(buildDir, "resources.arsc")
        val newArsc = File(buildDir, "resources.arsc.new")
        BufferedInputStream(FileInputStream(oldArsc), BUFFER_SIZE).use { input ->
            BufferedOutputStream(FileOutputStream(newArsc, false), BUFFER_SIZE).use { output ->
                ARSCDecoder(input, null, false).CloneArsc(output, packageName, true)
            }
        }
        if (!oldArsc.delete()) {
            throw IOException("Failed to replace resources.arsc: ${oldArsc.path}")
        }
        if (!newArsc.renameTo(oldArsc)) {
            copyFile(newArsc, oldArsc)
            if (!newArsc.delete()) {
                throw IOException("Failed to delete temporary resources.arsc: ${newArsc.path}")
            }
        }
    }

    private fun repackageAndSign(buildDir: File, outputApk: File, projectConfig: RemoteProjectConfig) {
        val unsignedApk = File(workspace.root, "${outputApk.name}.unsigned.apk")
        if (unsignedApk.exists()) {
            unsignedApk.delete()
        }
        if (outputApk.exists()) {
            outputApk.delete()
        }
        progress(ApkBuildProgress(ApkBuildProgress.STEP_SIGN, "Creating unsigned APK", unsignedApk.path))
        packageUnsignedApk(buildDir, unsignedApk)
        progress(ApkBuildProgress(ApkBuildProgress.STEP_SIGN, "Unsigned APK created", unsignedApk.path))
        progress(ApkBuildProgress(ApkBuildProgress.STEP_SIGN, "Processing", "Re-signing APK"))
        signApk(unsignedApk, outputApk, projectConfig)
        progress(ApkBuildProgress(ApkBuildProgress.STEP_SIGN, "Sign completed", outputApk.path))
        unsignedApk.delete()
    }

    private fun packageUnsignedApk(buildDir: File, unsignedApk: File) {
        FileOutputStream(unsignedApk, false).use { output ->
            ZipOutputStream(BufferedOutputStream(output, BUFFER_SIZE)).use { zip ->
                addApkDirectoryToZip(buildDir, buildDir, zip)
                zip.finish()
                zip.flush()
            }
        }
    }

    private fun addApkDirectoryToZip(root: File, dir: File, zip: ZipOutputStream) {
        dir.listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name })
            ?.forEach { child ->
                ensureActive()
                val relativePath = child.relativeTo(root).path.replace(File.separatorChar, '/')
                if (shouldSkipUnsignedApkEntry(relativePath, child.isDirectory)) {
                    return@forEach
                }
                if (child.isDirectory) {
                    zip.putNextEntry(ZipEntry("$relativePath/"))
                    zip.closeEntry()
                    addApkDirectoryToZip(root, child, zip)
                } else {
                    zip.putNextEntry(ZipEntry(relativePath))
                    FileInputStream(child).use { input -> input.copyTo(zip, BUFFER_SIZE) }
                    zip.closeEntry()
                }
            }
    }

    private fun shouldSkipUnsignedApkEntry(path: String, isDirectory: Boolean): Boolean {
        if (!path.startsWith("META-INF/", ignoreCase = true)) {
            return false
        }
        if (path.startsWith("META-INF/services", ignoreCase = true)) {
            return false
        }
        if (isDirectory) {
            return path.equals("META-INF", ignoreCase = true)
                || path.equals("META-INF/", ignoreCase = true)
        }
        return true
    }

    private fun signApk(unsignedApk: File, outputApk: File, projectConfig: RemoteProjectConfig) {
        progress(ApkBuildProgress(ApkBuildProgress.STEP_SIGN, "Preparing keystore", null))
        val signing = resolveSigningConfig()
        progress(ApkBuildProgress(ApkBuildProgress.STEP_SIGN, "Using keystore", signing.keyStoreFile.path))
        val signer = ApkSigner(unsignedApk, outputApk).apply {
            useDefaultSignatureVersion = false
            v1SigningEnabled = "V1" in projectConfig.signatureScheme
            v2SigningEnabled = "V2" in projectConfig.signatureScheme
            v3SigningEnabled = "V3" in projectConfig.signatureScheme
            v4SigningEnabled = "V4" in projectConfig.signatureScheme
        }
        if (!signer.signRelease(signing.keyStoreFile, signing.password, signing.alias, signing.aliasPassword)) {
            throw IOException("Failed to sign remote APK with scheme: ${projectConfig.signatureScheme}")
        }
    }

    private fun resolveSigningConfig(): SigningConfig {
        val customKeyStore = workspace.keyStoreFile
        if (customKeyStore != null) {
            return SigningConfig(
                keyStoreFile = customKeyStore,
                password = request.keyStorePassword ?: throw IOException("Remote build keystore password is missing."),
                alias = request.keyAlias ?: throw IOException("Remote build keystore alias is missing."),
                aliasPassword = request.keyAliasPassword ?: request.keyStorePassword
                ?: throw IOException("Remote build key alias password is missing."),
            )
        }
        val defaultKeyStore = File(workspace.root, "default_key_store.bks")
        appContext.assets.open(DEFAULT_KEY_STORE_ASSET).use { input ->
            FileOutputStream(defaultKeyStore, false).use { output ->
                input.copyTo(output, BUFFER_SIZE)
                output.flush()
            }
        }
        return SigningConfig(
            keyStoreFile = defaultKeyStore,
            password = DEFAULT_KEY_STORE_PASSWORD,
            alias = DEFAULT_KEY_ALIAS,
            aliasPassword = DEFAULT_KEY_ALIAS_PASSWORD,
        )
    }

    private fun ensureActive() {
        if (cancelSignal.get()) {
            throw CancellationException("Remote APK build session was cancelled.")
        }
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException("Remote APK build thread was interrupted.")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5")
        digest.update(value.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun sanitizeOutputFileName(value: String?): String {
        val candidate = value?.replace('\\', '/')?.substringAfterLast('/')?.trim().orEmpty()
        val safeName = candidate.takeIf { it.isNotBlank() && it != "." && it != ".." } ?: DEFAULT_OUTPUT_APK
        return if (safeName.endsWith(".apk", ignoreCase = true)) safeName else "$safeName.apk"
    }

    private fun generateBuildId(buildNumber: Long, buildTime: Long): String {
        val crc32 = CRC32()
        crc32.update("$buildNumber$buildTime".toByteArray(Charsets.UTF_8))
        return "%08X-$buildNumber".format(crc32.value)
    }

    private data class RemoteProjectConfig(
        val json: JSONObject,
        val name: String,
        val packageName: String,
        val versionName: String,
        val versionCode: Int,
        val mainScript: String,
        val abis: List<String>?,
        val libs: List<String>,
        val permissions: List<String>?,
        val signatureScheme: String,
        val splashVisible: Boolean,
    )

    private data class SigningConfig(
        val keyStoreFile: File,
        val password: String,
        val alias: String,
        val aliasPassword: String,
    )

    companion object {
        private const val BUFFER_SIZE = 256 * 1024
        private const val TEMPLATE_APK_ASSET = ApkBuilderTemplateMetadata.TEMPLATE_APK_ASSET
        private const val DEFAULT_KEY_STORE_ASSET = ApkBuilderTemplateMetadata.DEFAULT_KEY_STORE_ASSET
        private const val CONFIG_FILE_NAME = "project.json"
        private const val DEFAULT_MAIN_SCRIPT = "main.js"
        private const val DEFAULT_OUTPUT_APK = "remote-build.apk"
        private const val DEFAULT_SIGNATURE_SCHEME = "V1 + V2"
        private const val DEFAULT_KEY_STORE_PASSWORD = "AutoJs6"
        private const val DEFAULT_KEY_ALIAS = "AutoJs6"
        private const val DEFAULT_KEY_ALIAS_PASSWORD = "AutoJs6"
        private const val ICON_RES_DIR = "mipmap"
        private const val ICON_NAME = "ic_launcher"
        private const val TAG = "RemoteApkBuilder"

        private const val KEY_NAME = "name"
        private const val KEY_PACKAGE_NAME = "packageName"
        private const val KEY_VERSION_NAME = "versionName"
        private const val KEY_VERSION_CODE = "versionCode"
        private const val KEY_MAIN = "main"
        private const val KEY_ABIS = "abis"
        private const val KEY_LIBS = "libs"
        private const val KEY_PERMISSIONS = "permissions"
        private const val KEY_SIGNATURE_SCHEME = "signatureScheme"
        private const val KEY_LAUNCH_CONFIG = "launchConfig"
        private const val KEY_SPLASH_VISIBLE = "splashVisible"
        private const val KEY_BUILD = "build"
        private const val KEY_BUILD_ID = "id"
        private const val KEY_BUILD_NUMBER = "number"
        private const val KEY_BUILD_TIME = "time"
        private const val LIB_EMBEDDED_NODE_JS = "Embedded Node.js"

        private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
        private val DEFAULT_NATIVE_LIBRARIES = listOf(
            "libjackpal-androidterm5.so",
            "libjackpal-termexec2.so",
        )
        private val BUILTIN_NATIVE_LIBRARIES_BY_LABEL = mapOf(
            "OpenCV" to listOf(
                "libc++_shared.so",
                "libopencv_java4.so",
            ),
            "Image Quantization" to listOf(
                "libpngquant_bridge.so",
            ),
        )
    }
}
