package org.autojs.plugin.apkbuilder.template.impl

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import com.reandroid.arsc.chunk.TableBlock
import org.autojs.plugin.apkbuilder.template.ApkBuildProgress
import org.autojs.plugin.apkbuilder.template.ApkBuildRequest
import org.autojs.plugin.apkbuilder.template.ApkBuildRequestExtraKeys
import pxb.android.tinysign.TinySign
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
        progress(ApkBuildProgress(ApkBuildProgress.STEP_BUILD, "Configuring AndroidManifest.xml", projectConfig.packageName))
        configureManifest(buildDir, projectConfig)

        ensureActive()
        progress(ApkBuildProgress(ApkBuildProgress.STEP_BUILD, "Copying project files", workspace.sourcePath.path))
        val updatedProjectConfigJson = updateProjectBuildInfo(projectConfig)
        copyProjectAssets(buildDir, updatedProjectConfigJson)

        ensureActive()
        progress(ApkBuildProgress(ApkBuildProgress.STEP_BUILD, "Replacing app icon", null))
        replaceAppIconIfProvided(buildDir)

        ensureActive()
        progress(ApkBuildProgress(ApkBuildProgress.STEP_BUILD, "Rewriting resources.arsc", projectConfig.packageName))
        rewriteResourcesArsc(buildDir, projectConfig.packageName)

        ensureActive()
        progress(ApkBuildProgress(ApkBuildProgress.STEP_SIGN, "Creating remote APK", outputApk.path))
        repackage(buildDir, outputApk)

        ensureActive()
        val outputSha256 = sha256(outputApk)
        return Output(
            apkFile = outputApk,
            outputFileName = outputApk.name,
            sha256 = outputSha256,
            updatedProjectConfigJson = updatedProjectConfigJson,
            warnings = arrayListOf(
                "Remote APK build currently uses a lightweight plugin-side builder.",
                "Native/plugin library injection and custom signing schemes are not implemented yet.",
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
        val permissions = parseStringArray(json, KEY_PERMISSIONS)

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
            permissions = permissions,
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
        RemoteApkManifestEditor(FileInputStream(manifestFile))
            .setAppName(projectConfig.name)
            .setPackageName(projectConfig.packageName)
            .setVersionName(projectConfig.versionName)
            .setVersionCode(projectConfig.versionCode)
            .setPermissions(projectConfig.permissions)
            .commit()
            .writeTo(FileOutputStream(manifestFile, false))
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

    private fun copyProjectAssets(buildDir: File, projectConfigJson: String) {
        val projectAssetsDir = File(buildDir, "assets/project")
        if (projectAssetsDir.exists()) {
            projectAssetsDir.deleteRecursively()
        }
        if (!projectAssetsDir.mkdirs()) {
            throw IOException("Failed to create project assets directory: ${projectAssetsDir.path}")
        }

        when (workspace.sourceKind) {
            ApkBuildRequestExtraKeys.SOURCE_KIND_DIRECTORY -> copyDirectoryContents(workspace.sourcePath, projectAssetsDir)
            ApkBuildRequestExtraKeys.SOURCE_KIND_FILE -> copyFile(workspace.sourcePath, File(projectAssetsDir, DEFAULT_MAIN_SCRIPT))
            else -> throw IOException("Unsupported project source kind: ${workspace.sourceKind}")
        }

        File(projectAssetsDir, CONFIG_FILE_NAME).writeText(projectConfigJson, Charsets.UTF_8)
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

    private fun copyDirectoryContents(sourceDir: File, targetDir: File) {
        sourceDir.listFiles()?.forEach { child ->
            val target = File(targetDir, child.name)
            if (child.isDirectory) {
                if (!target.mkdirs() && !target.isDirectory) {
                    throw IOException("Failed to create project assets directory: ${target.path}")
                }
                copyDirectoryContents(child, target)
            } else if (child.isFile) {
                copyFile(child, target)
            }
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

    private fun repackage(buildDir: File, outputApk: File) {
        if (outputApk.exists()) {
            outputApk.delete()
        }
        FileOutputStream(outputApk, false).use { output ->
            TinySign.sign(buildDir, output)
            output.flush()
        }
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
        val permissions: List<String>?,
    )

    companion object {
        private const val BUFFER_SIZE = 256 * 1024
        private const val TEMPLATE_APK_ASSET = "template/template.apk"
        private const val CONFIG_FILE_NAME = "project.json"
        private const val DEFAULT_MAIN_SCRIPT = "main.js"
        private const val DEFAULT_OUTPUT_APK = "remote-build.apk"
        private const val ICON_RES_DIR = "mipmap"
        private const val ICON_NAME = "ic_launcher"
        private const val TAG = "RemoteApkBuilder"

        private const val KEY_NAME = "name"
        private const val KEY_PACKAGE_NAME = "packageName"
        private const val KEY_VERSION_NAME = "versionName"
        private const val KEY_VERSION_CODE = "versionCode"
        private const val KEY_MAIN = "main"
        private const val KEY_PERMISSIONS = "permissions"
        private const val KEY_BUILD = "build"
        private const val KEY_BUILD_ID = "id"
        private const val KEY_BUILD_NUMBER = "number"
        private const val KEY_BUILD_TIME = "time"

        private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    }
}
