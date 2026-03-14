package org.autojs.autojs.apkbuilder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.content.pm.PackageManager.GET_SHARED_LIBRARY_FILES
import android.content.pm.ServiceInfo
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.mcal.apksigner.ApkSigner
import com.reandroid.arsc.chunk.TableBlock
import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.apkbuilder.keystore.AESUtils
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.engine.encryption.AdvancedEncryptionStandard
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.project.BuildInfo
import org.autojs.autojs.project.LaunchConfig
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.project.ProjectConfig.CONFIG_FILE_NAME
import org.autojs.autojs.script.EncryptedScriptFileHeader.writeHeader
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.util.FileUtils.TYPE.JAVASCRIPT
import org.autojs.autojs.util.MD5Utils
import org.autojs.autojs6.R
import org.autojs.plugin.paddle.ocr.api.IOcrPlugin
import pxb.android.StringItem
import pxb.android.axml.AxmlWriter
import zhao.arsceditor.ResDecoder.ARSCDecoder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Created by Stardust on Oct 24, 2017.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Jan 17, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 9, 2026.
 * Modified by SuperMonster003 as of Mar 13, 2026.
 */
open class ApkBuilder(apkInputStream: InputStream?, private val outApkFile: File, private val buildPath: String) {

    private var mProgressCallback: ProgressCallback? = null
    private var mArscPackageName: String? = null
    private var mManifestEditor: ManifestEditor? = null
    private var mInitVector: String? = null
    private var mKey: String? = null
    private var mCancelSignal: AtomicBoolean? = null
    private var mPendingProjectConfigFile: File? = null
    private var mPendingProjectConfigJson: String? = null
    private var mBundledProjectConfigJson: String? = null

    private lateinit var mProjectConfig: ProjectConfig

    private val mApkPackager = ApkPackager(apkInputStream, buildPath)

    private val mAssetManager: AssetManager by lazy { globalContext.assets }

    private var mLibsIncludes = Lib.defaultLibsToInclude.toMutableList()
    private var mAssetsFileIncludes = Lib.defaultAssetFilesToInclude.map(::normalizeAssetPath).toMutableList()
    private var mAssetsDirExcludes = Lib.defaultAssetDirsToExclude.map(::normalizeAssetPath).toMutableList()

    private var mSplashThemeId: Int = 0
    private var mNoSplashThemeId: Int = 0

    private val mManifestFile
        get() = File(buildPath, "AndroidManifest.xml")

    private val mResourcesArscFile
        get() = File(buildPath, "resources.arsc")

    init {
        PFiles.ensureDir(outApkFile.path)
    }

    fun setProgressCallback(callback: ProgressCallback?) = also {
        mProgressCallback = callback
    }

    fun setCancelSignal(cancelSignal: AtomicBoolean?) = also {
        mCancelSignal = cancelSignal
        mApkPackager.setCancelSignal(cancelSignal)
    }

    private fun getAssetsRoot(): File =
        File(buildPath, "assets")

    // Throw early when cancellation is requested to avoid partial outputs.
    // zh-CN: 当收到取消请求时尽早抛出, 以避免产生部分输出.
    private fun ensureNotCancelled() {
        if (mCancelSignal?.get() == true) {
            throw CancellationException("Build aborted")
        }
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException("Build aborted")
        }
    }

    // Copy streams with cancellation checks to keep abort responsive during large IO.
    // zh-CN: 在大 IO 过程中加入取消检查, 保持中止响应.
    private fun copyStreamWithCancel(input: InputStream, output: OutputStream, bufferSize: Int = 16 * 1024) {
        val buffer = ByteArray(bufferSize)
        var len: Int
        while (input.read(buffer).also { len = it } > 0) {
            ensureNotCancelled()
            output.write(buffer, 0, len)
        }
    }

    private fun toAssetZipPrefix(path: String): String {
        val normalized = normalizeAssetPath(path)
        return if (normalized.isEmpty()) "assets/" else "assets/$normalized"
    }

    private fun isAssetDirExcluded(assetPath: String): Boolean {
        val normalized = normalizeAssetPath(assetPath)
        return mAssetsDirExcludes.any { excluded ->
            normalized == excluded || normalized.startsWith("$excluded/")
        }
    }

    private fun includeLibraryContributions(lib: Lib) {
        mLibsIncludes += lib.libsToInclude.toSet()
        mAssetsFileIncludes += lib.assetFilesToInclude.map(::normalizeAssetPath).toSet()
        mAssetsDirExcludes -= lib.assetDirsToInclude.map(::normalizeAssetPath).toSet()
    }

    private fun pruneTemplateAssetsByPolicy(context: Context) {
        val assetsRoot = getAssetsRoot()
        if (!assetsRoot.exists() || !assetsRoot.isDirectory) {
            return
        }

        val optionalAssetFiles = Lib.entries
            .flatMap { it.assetFilesToInclude }
            .map(::normalizeAssetPath)
            .toSet()

        assetsRoot.listFiles()?.forEach { child ->
            ensureNotCancelled()
            val normalizedName = normalizeAssetPath(child.name)
            if (normalizedName.isEmpty()) {
                return@forEach
            }
            if (child.isDirectory) {
                if (!isAssetDirExcluded(normalizedName)) {
                    return@forEach
                }
                notifyStepProgress(
                    ProgressStep.BUILD,
                    context.getString(R.string.text_pruning),
                    child.path,
                )
                PFiles.deleteRecursively(child)
                return@forEach
            }
            if (optionalAssetFiles.contains(normalizedName) && !mAssetsFileIncludes.contains(normalizedName)) {
                notifyStepProgress(
                    ProgressStep.BUILD,
                    context.getString(R.string.text_pruning),
                    child.path,
                )
                child.delete()
            }
        }
    }

    private fun notifyStepChanged(step: ProgressStep) {
        mProgressCallback?.let { callback ->
            GlobalAppContext.post {
                when (step) {
                    ProgressStep.PREPARE -> callback.onPrepare(this)
                    ProgressStep.BUILD -> callback.onBuild(this)
                    ProgressStep.SIGN -> callback.onSign(this)
                    ProgressStep.CLEAN -> callback.onClean(this)
                }
            }
        }
    }

    private fun notifyStepProgress(step: ProgressStep, title: String, detail: String?) {
        mProgressCallback?.let { callback ->
            GlobalAppContext.post {
                callback.onStepProgress(
                    builder = this,
                    title = title,
                    detail = detail?.takeIf { it.isNotBlank() },
                )
            }
        }
    }

    @Throws(IOException::class)
    fun prepare(context: Context) = also {
        ensureNotCancelled()
        notifyStepChanged(ProgressStep.PREPARE)
        notifyStepProgress(
            ProgressStep.PREPARE,
            context.getString(R.string.text_preparing_workspace),
            buildPath,
        )
        File(buildPath).mkdirs()
        notifyStepProgress(
            ProgressStep.PREPARE,
            context.getString(R.string.text_extracting_template_apk),
            buildPath,
        )
        mApkPackager.unzip()
        ensureNotCancelled()
        notifyStepProgress(
            ProgressStep.PREPARE,
            context.getString(R.string.text_prepare_completed),
            buildPath,
        )
    }

    @Throws(IOException::class)
    fun setScriptFile(context: Context, path: String?) = also {
        ensureNotCancelled()
        path?.let {
            when {
                PFiles.isDir(it) -> {
                    notifyStepProgress(
                        ProgressStep.BUILD,
                        context.getString(R.string.text_copying_project_directory),
                        it,
                    )
                    copyDir(context, it, "assets/project/")
                    writeBundledProjectConfigIfNeeded(context)
                }
                else -> {
                    notifyStepProgress(
                        ProgressStep.BUILD,
                        context.getString(R.string.text_copying_script_file),
                        it,
                    )
                    replaceFile(context, it, "assets/project/main.js")
                    writeBundledProjectConfigIfNeeded(context)
                }
            }
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_source_processing_completed),
                it,
            )
        }
    }

    @Throws(IOException::class)
    @Suppress("SameParameterValue")
    private fun copyDir(context: Context, srcPath: String, relativeDestPath: String) {
        copyDir(context, File(srcPath), relativeDestPath)
    }

    @Throws(IOException::class)
    fun copyDir(context: Context, srcFile: File, relativeDestPath: String) {
        ensureNotCancelled()
        val destDirFile = File(buildPath, relativeDestPath).apply { mkdir() }
        notifyStepProgress(
            ProgressStep.BUILD,
            context.getString(R.string.text_copying_directory),
            "${srcFile.path} -> ${destDirFile.path}",
        )
        srcFile.listFiles()?.forEach { srcChildFile ->
            ensureNotCancelled()
            if (srcChildFile.isFile) {
                if (srcChildFile.name.endsWith(JAVASCRIPT.extensionWithDot)) {
                    encryptToDir(context, srcChildFile, destDirFile)
                } else {
                    val destFile = File(destDirFile, srcChildFile.name)
                    notifyStepProgress(
                        ProgressStep.BUILD,
                        context.getString(R.string.text_copying_file),
                        "${srcChildFile.path} -> ${destFile.path}",
                    )
                    srcChildFile.copyTo(destFile, true)
                }
            } else {
                if (!mProjectConfig.excludedDirs.contains(srcChildFile)) {
                    copyDir(context, srcChildFile, PFiles.join(relativeDestPath, srcChildFile.name + File.separator))
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun encrypt(context: Context, srcFile: File, destFile: File) {
        ensureNotCancelled()
        notifyStepProgress(
            ProgressStep.BUILD,
            context.getString(R.string.text_encrypting_script),
            "${srcFile.path} -> ${destFile.path}",
        )
        destFile.outputStream().use { os ->
            writeHeader(os, JavaScriptFileSource(srcFile).executionMode.toShort())
            AdvancedEncryptionStandard(mKey!!.toByteArray(), mInitVector!!)
                .encrypt(PFiles.readBytes(srcFile.path))
                .let { bytes -> os.write(bytes) }
        }
    }

    private fun encryptToDir(context: Context, srcFile: File, destDirFile: File) {
        val destFile = File(destDirFile, srcFile.name)
        encrypt(context, srcFile, destFile)
    }

    @Throws(IOException::class)
    fun replaceFile(context: Context, srcPath: String, relativeDestPath: String) = replaceFile(context, File(srcPath), relativeDestPath)

    @Throws(IOException::class)
    fun replaceFile(context: Context, srcFile: File, relativeDestPath: String) = also {
        ensureNotCancelled()
        val destFile = File(buildPath, relativeDestPath)
        if (destFile.name.endsWith(JAVASCRIPT.extensionWithDot)) {
            encrypt(context, srcFile, destFile)
        } else {
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_replacing_file),
                "${srcFile.path} -> ${destFile.path}",
            )
            srcFile.copyTo(destFile, true)
        }
    }

    @Throws(IOException::class)
    fun withConfig(context: Context, config: ProjectConfig) = also {
        notifyStepChanged(ProgressStep.BUILD)
        notifyStepProgress(
            ProgressStep.BUILD,
            context.getString(R.string.text_processing),
            context.getString(R.string.text_preparing_build_config),
        )
        config.also { mProjectConfig = it }.run {
            ensureNotCancelled()
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_reading_splash_resources),
                mResourcesArscFile.path,
            )
            retrieveSplashThemeResources(launchConfig)
            ensureNotCancelled()
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_configuring_manifest),
                mManifestFile.path,
            )
            prepareManifestConfiguration(this)
            ensureNotCancelled()
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_configuring_package_name),
                packageName,
            )
            setArscPackageName(packageName)
            ensureNotCancelled()
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_processing),
                context.getString(R.string.text_updating_project_config),
            )
            updateProjectConfig(this)
            ensureNotCancelled()
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_processing),
                getAssetsRoot().path,
            )
            pruneTemplateAssetsByPolicy(context)
            ensureNotCancelled()
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_copying_assets_to),
                getAssetsRoot().path,
            )
            copyAssetsRecursively(context, "", getAssetsRoot())
            ensureNotCancelled()
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_processing),
                context.getString(R.string.text_copying_native_libraries),
            )
            copyLibrariesByConfig(context, this)
            ensureNotCancelled()
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_processing_source),
                sourcePath,
            )
            setScriptFile(context, sourcePath)
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_processing),
                context.getString(R.string.text_applying_binary_resources),
            )
        }
    }

    private fun prepareManifestConfiguration(config: ProjectConfig) {
        mManifestEditor = editManifest()
            .setAppName(config.name)
            .setVersionName(config.versionName)
            .setVersionCode(config.versionCode)
            .setPackageName(config.packageName)
    }

    private fun retrieveSplashThemeResources(launchConfig: LaunchConfig) {
        if (launchConfig.isSplashVisible) {
            // @Hint by SuperMonster003 on Jan 23, 2024.
            //   ! Members `mSplashThemeId` and `mNoSplashThemeId` will keep their default values.
            //   ! zh-CN: 成员变量 `mSplashThemeId` 及 `mNoSplashThemeId` 将保持其默认值.
            return
        }
        try {
            val tableBlock = TableBlock.load(mResourcesArscFile)
            val packageName = "${GlobalAppContext.get().packageName}.inrt"
            val packageBlock = tableBlock.getOrCreatePackage(0x7f, packageName).also {
                tableBlock.currentPackage = it
            }
            packageBlock.getEntry("", "style", "AppTheme.Splash")?.let {
                mSplashThemeId = it.resourceId
            }
            packageBlock.getEntry("", "style", "AppTheme.SevereTransparent")?.let {
                mNoSplashThemeId = it.resourceId
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Throws(FileNotFoundException::class)
    fun editManifest(): ManifestEditor = ManifestEditorWithAuthorities(FileInputStream(mManifestFile)).also { mManifestEditor = it }

    private fun writeBundledProjectConfigIfNeeded(context: Context) {
        val json = mBundledProjectConfigJson ?: return
        val configFile = File(buildPath, "assets/project/$CONFIG_FILE_NAME")
        configFile.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
        notifyStepProgress(
            ProgressStep.BUILD,
            context.getString(R.string.text_writing_project_config),
            configFile.path,
        )
        configFile.writeText(json)
    }

    private fun updateProjectConfig(config: ProjectConfig) {
        ensureNotCancelled()

        val projectConfig = run {
            if (PFiles.isDir(config.sourcePath)) {
                // @Hint by SuperMonster003 on Jan 23, 2025.
                //  ! Project directory packaging.
                //  ! zh-CN: 打包项目目录.
                ProjectConfig.fromProjectDir(config.sourcePath)?.let { sourceProjectConfig ->
                    sourceProjectConfig
                        .setName(config.name)
                        .setPackageName(config.packageName)
                        .setVersionName(config.versionName)
                        .setVersionCode(config.versionCode)
                        .setAbis(ArrayList(config.abis))
                        .setLibs(ArrayList(config.libs))
                        .setPermissions(ArrayList(config.permissions))
                        .setSignatureScheme(config.signatureScheme)
                    sourceProjectConfig.launchConfig = config.launchConfig
                    val nextBuildInfo = BuildInfo.generate(sourceProjectConfig.buildInfo.buildNumber + 1)
                    sourceProjectConfig.buildInfo.apply {
                        buildId = nextBuildInfo.buildId
                        buildNumber = nextBuildInfo.buildNumber
                        buildTime = nextBuildInfo.buildTime
                    }
                    mPendingProjectConfigFile = File(ProjectConfig.configFileOfDir(config.sourcePath))
                    mPendingProjectConfigJson = sourceProjectConfig.toJson(true)
                    return@run sourceProjectConfig
                }
            }
            // @Hint by SuperMonster003 on Jan 23, 2025.
            //  ! Single file packaging.
            //  ! zh-CN: 打包单独文件.
            return@run ProjectConfig().also { newProjectConfig ->
                newProjectConfig
                    .setName(config.name)
                    .setPackageName(config.packageName)
                    .setVersionName(config.versionName)
                    .setVersionCode(config.versionCode)
                    .setAbis(ArrayList(config.abis))
                    .setLibs(ArrayList(config.libs))
                    .setPermissions(ArrayList(config.permissions))
                    .setSignatureScheme(config.signatureScheme)
                newProjectConfig.launchConfig = config.launchConfig
                newProjectConfig.setBuildInfo(BuildInfo.generate(newProjectConfig.versionCode.toLong()))
                File(buildPath, "assets/project/$CONFIG_FILE_NAME").also { file ->
                    file.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
                }.writeText(newProjectConfig.toJson(true))
            }

        }

        mKey = MD5Utils.md5(projectConfig.run { packageName + versionName + mainScriptFileName })
        mInitVector = MD5Utils.md5(projectConfig.run { buildInfo.buildId + name }).take(16)
        mBundledProjectConfigJson = projectConfig.toJson(true)
        Lib.entries.forEach { entry ->
            if (config.libs.contains(entry.label)) {
                includeLibraryContributions(entry)
            }
        }
    }

    // Commit project config changes only after a successful build.
    // zh-CN: 仅在构建成功后提交项目配置变更.
    fun commitProjectConfigIfNeeded(context: Context) = also {
        val pendingFile = mPendingProjectConfigFile
        val pendingJson = mPendingProjectConfigJson
        if (pendingFile == null || pendingJson == null) {
            notifyStepProgress(
                ProgressStep.SIGN,
                context.getString(R.string.text_processing),
                context.getString(R.string.text_sign_stage_completed),
            )
            return@also
        }
        ensureNotCancelled()
        notifyStepProgress(
            ProgressStep.SIGN,
            context.getString(R.string.text_writing_project_config),
            pendingFile.path,
        )
        pendingFile.writeText(pendingJson)
        mPendingProjectConfigFile = null
        mPendingProjectConfigJson = null
        notifyStepProgress(
            ProgressStep.SIGN,
            context.getString(R.string.text_processing),
            context.getString(R.string.text_sign_stage_completed),
        )
    }

    @Throws(Exception::class)
    fun build(context: Context) = also {
        ensureNotCancelled()
        notifyStepProgress(
            ProgressStep.BUILD,
            context.getString(R.string.text_processing),
            context.getString(R.string.text_building_resources),
        )
        mProjectConfig.iconBitmapGetter?.let { callable ->
            runCatching {
                ensureNotCancelled()
                val tableBlock = TableBlock.load(mResourcesArscFile)
                val packageName = "${GlobalAppContext.get().packageName}.inrt"
                val packageBlock = tableBlock.getOrCreatePackage(0x7f, packageName).also {
                    tableBlock.currentPackage = it
                }
                val appIcon = packageBlock.getOrCreate("", ICON_RES_DIR, ICON_NAME)
                val appIconPath = appIcon.resValue.decodeValue()
                Log.d(TAG, "Icon path: $appIconPath")
                val file = File(buildPath, appIconPath).also {
                    if (!it.exists()) {
                        File(it.parent!!).mkdirs()
                        it.createNewFile()
                    }
                }
                notifyStepProgress(
                    ProgressStep.BUILD,
                    context.getString(R.string.text_writing_app_icon),
                    file.path,
                )
                callable.call()?.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(file))
            }.onFailure { throw RuntimeException(it) }
        }
        mManifestEditor?.let {
            ensureNotCancelled()
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_writing_manifest),
                mManifestFile.path,
            )
            it.commit()
            it.writeTo(FileOutputStream(mManifestFile))
        }
        mArscPackageName?.let {
            ensureNotCancelled()
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_writing_resources_arsc),
                mResourcesArscFile.path,
            )
            buildArsc()
        }
        notifyStepProgress(
            ProgressStep.BUILD,
            context.getString(R.string.text_processing),
            context.getString(R.string.text_build_completed),
        )
    }

    private fun copyAssetsRecursively(context: Context, assetPath: String, targetFile: File) {
        ensureNotCancelled()
        if (targetFile.isFile && targetFile.exists()) return
        val list = mAssetManager.list(assetPath) ?: return
        if (list.isEmpty()) /* asset is a file */ {
            if (!assetPath.contains('/')) /* assets root dir */ {
                if (!mAssetsFileIncludes.contains(normalizeAssetPath(assetPath))) {
                    return
                }
            }
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_copying_asset),
                "assets/$assetPath -> ${targetFile.path}",
            )
            mAssetManager.open(assetPath).use { input ->
                FileOutputStream(targetFile.absolutePath).use { output ->
                    copyStreamWithCancel(input, output)
                    output.flush()
                }
            }
        } else /* asset is folder */ {
            if (isAssetDirExcluded(assetPath)) {
                return
            }
            val displayPath = if (assetPath.isEmpty()) "/" else "/$assetPath"
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_preparing_assets_dir),
                displayPath,
            )
            targetFile.delete()
            targetFile.mkdir()
            list.forEach {
                ensureNotCancelled()
                val sourcePath = if (assetPath.isEmpty()) it else "$assetPath/$it"
                copyAssetsRecursively(context, sourcePath, File(targetFile, it))
            }
        }
    }

    @Throws(Exception::class)
    fun sign(context: Context) = also {
        ensureNotCancelled()
        notifyStepChanged(ProgressStep.SIGN)
        val workspaceDir = File(buildPath)
        outApkFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val unsignedApkFile = outApkFile
        val tmpOutputApk = File(outApkFile.parentFile ?: workspaceDir, "${outApkFile.name}.signed.tmp")
        if (tmpOutputApk.exists()) {
            tmpOutputApk.delete()
        }
        notifyStepProgress(
            ProgressStep.SIGN,
            context.getString(R.string.text_creating_unsigned_apk),
            unsignedApkFile.path,
        )
        try {
            BufferedOutputStream(FileOutputStream(unsignedApkFile, false), 256 * 1024).use { fos ->
                TinySign.sign(workspaceDir, fos)
                fos.flush()
            }
        } catch (e: Exception) {
            if (tmpOutputApk.exists() && !tmpOutputApk.delete()) {
                Log.w(TAG, "Failed to delete temporary signed apk after unsigned apk creation failure: ${tmpOutputApk.path}")
            }
            if (e.hasNoSpaceLeft()) {
                throw IOException("No space left on device while creating unsigned APK: ${unsignedApkFile.path}", e)
            }
            throw e
        }
        notifyStepProgress(
            ProgressStep.SIGN,
            context.getString(R.string.text_unsigned_apk_created),
            unsignedApkFile.path,
        )

        val defaultKeyStoreFile = File(buildPath, "default_key_store.bks")
        if (mProjectConfig.keyStore == null) {

            // Replace FileUtils.copyInputStreamToFile(...).
            // zh-CN: 替换 FileUtils.copyInputStreamToFile(...).
            ensureNotCancelled()
            defaultKeyStoreFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
            notifyStepProgress(
                ProgressStep.SIGN,
                context.getString(R.string.text_preparing_keystore),
                defaultKeyStoreFile.path,
            )
            GlobalAppContext.get().assets.open("default_key_store.bks").use { input ->
                FileOutputStream(defaultKeyStoreFile, false).use { output ->
                    copyStreamWithCancel(input, output)
                    output.fd.sync()
                }
            }
        }

        ensureNotCancelled()
        val signer = ApkSigner(unsignedApkFile, tmpOutputApk).apply {
            useDefaultSignatureVersion = false
            v1SigningEnabled = "V1" in mProjectConfig.signatureScheme
            v2SigningEnabled = "V2" in mProjectConfig.signatureScheme
            v3SigningEnabled = "V3" in mProjectConfig.signatureScheme
            v4SigningEnabled = "V4" in mProjectConfig.signatureScheme
        }

        var keyStoreFile = defaultKeyStoreFile
        var password = "AutoJs6"
        var alias = "AutoJs6"
        var aliasPassword = "AutoJs6"

        mProjectConfig.keyStore?.let {
            keyStoreFile = File(it.absolutePath)
            password = AESUtils.decrypt(it.password)
            alias = it.alias
            aliasPassword = AESUtils.decrypt(it.aliasPassword)
        }
        notifyStepProgress(
            ProgressStep.SIGN,
            context.getString(R.string.text_using_keystore),
            keyStoreFile.path,
        )

        // Re-sign using ApkSigner.
        // zh-CN: 使用 ApkSigner 重新签名.
        ensureNotCancelled()
        notifyStepProgress(
            ProgressStep.SIGN,
            context.getString(R.string.text_processing),
            context.getString(R.string.text_re_signing_apk),
        )
        try {
            if (!signer.signRelease(keyStoreFile, password, alias, aliasPassword)) {
                throw RuntimeException("Failed to re-sign using ApkSigner")
            }
        } catch (e: Exception) {
            if (tmpOutputApk.exists() && !tmpOutputApk.delete()) {
                Log.w(TAG, "Failed to delete temporary signed apk after re-sign failure: ${tmpOutputApk.path}")
            }
            if (e.hasNoSpaceLeft()) {
                throw IOException("No space left on device while re-signing APK: ${tmpOutputApk.path}", e)
            }
            throw e
        }

        try {
            ensureNotCancelled()
            notifyStepProgress(
                ProgressStep.SIGN,
                context.getString(R.string.text_writing_signed_apk),
                outApkFile.path,
            )
            if (outApkFile.exists() && !outApkFile.delete()) {
                throw IOException("Failed to delete unsigned apk before replace: ${outApkFile.path}")
            }
            if (!tmpOutputApk.renameTo(outApkFile)) {
                copyFileWithLargeBuffer(tmpOutputApk, outApkFile)
            }
        } catch (e: Exception) {
            if (e.hasNoSpaceLeft()) {
                throw IOException("No space left on device while writing signed APK: ${outApkFile.path}", e)
            }
            throw RuntimeException(e)
        } finally {
            if (tmpOutputApk.exists() && !tmpOutputApk.delete()) {
                Log.w(TAG, "Failed to delete temporary signed apk: ${tmpOutputApk.path}")
            }
        }
        notifyStepProgress(
            ProgressStep.SIGN,
            context.getString(R.string.text_sign_completed),
            outApkFile.path,
        )
    }

    fun cleanWorkspace(context: Context) = also {
        notifyStepChanged(ProgressStep.CLEAN)
        val workspace = File(buildPath)
        val totalTargets = countDeleteTargets(workspace).coerceAtLeast(1)
        val deletedTargets = intArrayOf(0)
        notifyStepProgress(
            ProgressStep.CLEAN,
            context.getString(R.string.text_cleaning_workspace),
            workspace.path,
        )
        deleteWithProgress(context, workspace, totalTargets, deletedTargets)
        notifyStepProgress(
            ProgressStep.CLEAN,
            context.getString(R.string.text_clean_completed),
            workspace.path,
        )
    }

    fun finish() = also {
        mProgressCallback?.let { callback -> GlobalAppContext.post { callback.onFinished(this) } }
    }

    @Throws(IOException::class)
    fun setArscPackageName(packageName: String?) = also { mArscPackageName = packageName }

    @Throws(IOException::class)
    private fun buildArsc() {
        val oldArsc = File(buildPath, "resources.arsc")
        val newArsc = File(buildPath, "resources.arsc.new")
        BufferedInputStream(FileInputStream(oldArsc), 256 * 1024).use { input ->
            BufferedOutputStream(FileOutputStream(newArsc, false), 256 * 1024).use { output ->
                val decoder = ARSCDecoder(input, null, false)
                decoder.CloneArsc(output, mArscPackageName, true)
                output.flush()
            }
        }
        oldArsc.delete()
        if (!newArsc.renameTo(oldArsc)) {
            copyFileWithLargeBuffer(newArsc, oldArsc)
            newArsc.delete()
        }
    }

    private fun copyFileWithLargeBuffer(source: File, target: File, bufferSize: Int = 256 * 1024) {
        FileInputStream(source).use { input ->
            FileOutputStream(target, false).use { output ->
                val buffer = ByteArray(bufferSize)
                var len: Int
                while (input.read(buffer).also { len = it } > 0) {
                    ensureNotCancelled()
                    output.write(buffer, 0, len)
                }
            }
        }
    }

    private fun Throwable.hasNoSpaceLeft(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            val message = current.message.orEmpty()
            if (message.contains("ENOSPC", ignoreCase = true) || message.contains("No space left on device", ignoreCase = true)) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun countDeleteTargets(file: File): Int {
        if (!file.exists()) {
            return 0
        }
        return if (file.isDirectory) {
            1 + (file.listFiles()?.sumOf(::countDeleteTargets) ?: 0)
        } else {
            1
        }
    }

    private fun deleteWithProgress(context: Context, file: File, totalTargets: Int, deletedTargets: IntArray) {
        ensureNotCancelled()
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteWithProgress(context, child, totalTargets, deletedTargets)
            }
        }
        notifyStepProgress(
            ProgressStep.CLEAN,
            context.getString(R.string.text_deleting),
            file.path,
        )
        file.delete()
        deletedTargets[0] += 1
    }

    enum class ProgressStep {
        PREPARE,
        BUILD,
        SIGN,
        CLEAN,
    }

    interface ProgressCallback {
        fun onPrepare(builder: ApkBuilder)
        fun onBuild(builder: ApkBuilder)
        fun onSign(builder: ApkBuilder)
        fun onClean(builder: ApkBuilder)
        fun onStepProgress(builder: ApkBuilder, title: String, detail: String?)
        fun onFinished(builder: ApkBuilder)
    }

    private inner class ManifestEditorWithAuthorities(manifestInputStream: InputStream?) : ManifestEditor(manifestInputStream) {

        override fun shouldIgnoreComponentNode(nodeName: String?, componentClassName: String?): Boolean =
            when (componentClassName) {
                // Disable static shortcuts in packaged apps.
                // Will publish dynamic explicit shortcuts at runtime.
                // zh-CN:
                // 在打包应用中禁用静态快捷方式.
                // 将在运行时发布动态显式快捷方式.
                "android.app.shortcuts" -> nodeName == "meta-data"

                "org.autojs.autojs.external.open.EditIntentActivity",
                "org.autojs.autojs.external.open.RunIntentActivity",
                "org.autojs.autojs.external.open.ImportIntentActivity",
                "org.autojs.autojs.external.tile.LayoutBoundsTile",
                "org.autojs.autojs.external.tile.LayoutHierarchyTile",
                    -> true

                else -> false
            }

        override fun onAttr(attr: AxmlWriter.Attr) {
            attr.apply {

                // @Reference to aiselp (https://github.com/aiselp) by SuperMonster003 on Jan 18, 2025.
                //  ! https://github.com/aiselp/AutoX/blob/5b3303926082d591a166b1845702357406811aaf/app/src/main/java/org/autojs/autojs/build/ApkBuilder.kt#L175-L188
                if (!mProjectConfig.launchConfig.isSplashVisible && mSplashThemeId != 0 && value == mSplashThemeId) {
                    value = mNoSplashThemeId
                }

                when {
                    name.data == "authorities" -> (value as? StringItem)?.apply {
                        // @Reference to aiselp (https://github.com/aiselp) by SuperMonster003 on Apr 12, 2025.
                        //  ! https://github.com/aiselp/AutoX/commit/d085ccd41aafcf74d503bdf5ac08d021567945b2#diff-97c191813b60e8f000917ca5fe93dced97e688bdd48b25d212d03152d5f1678cR432
                        data = data.replace(INRT_APP_ID, mProjectConfig.packageName)
                    }
                    else -> super.onAttr(this)
                }
            }
        }

        override fun isPermissionRequired(permissionName: String): Boolean {
            return mProjectConfig.permissions.contains(permissionName)
        }
    }

    private fun copyLibrariesByConfig(context: Context, config: ProjectConfig) {
        ensureNotCancelled()

        // @Hint by SuperMonster003 on Dec 11, 2023.
        //  ! The list contains only abi names not matching the canonical name itself.
        //  ! zh-CN: 这个列表仅含有不匹配规范名称本身的 abi 名称.
        val potentialAbiAliasList = mapOf(
            "arm64-v8a" to "arm64",
            "armeabi-v7a" to "arm",
        )

        // Try extracting native libraries from installed plugin APKs if needed.
        // zh-CN: 如有需要, 尝试从已安装插件 APK 中解压 native 库文件.
        notifyStepProgress(
            ProgressStep.BUILD,
            context.getString(R.string.text_processing),
            context.getString(R.string.text_resolving_plugin_native_libraries),
        )
        ensureAndExtractPluginLibrariesIfNeeded(context, config, potentialAbiAliasList)

        config.abis.forEach { abiCanonicalName ->
            ensureNotCancelled()
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_copying_libraries_for_abi),
                abiCanonicalName,
            )
            copyLibrariesByAbi(context, abiCanonicalName, abiCanonicalName)
            potentialAbiAliasList[abiCanonicalName]?.let { abiAliasName ->
                copyLibrariesByAbi(context, abiAliasName, abiCanonicalName)
            }
        }
    }

    private fun ensureAndExtractPluginLibrariesIfNeeded(
        context: Context,
        config: ProjectConfig,
        potentialAbiAliasList: Map<String, String>,
    ) {
        ensureNotCancelled()
        Lib.entries.mapNotNull {
            if (it.isPlugin && config.libs.contains(it.label)) it.toPluginPair() else null
        }.forEach { (lib, plugin) ->
            ensureNotCancelled()
            // Select plugin service by variant.
            // zh-CN: 通过 variant 选择插件服务.
            val (serviceInfo, selectedVariant) = selectPluginServiceOrThrow(
                lib = lib,
                action = plugin.action,
            )
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_selected_plugin),
                "${lib.label} (${selectedVariant.variant}) from ${serviceInfo.packageName}",
            )

            Log.i(TAG, "Selected ${lib.label} plugin: variant=${selectedVariant.variant}, pkg=${serviceInfo.packageName}")

            // Extract libraries from installed plugin APK (variant-aware).
            // zh-CN: 从已安装插件 APK 中解压 so 文件, 并按变体裁剪.
            extractLibrariesFromPluginApkOrThrow(
                context = context,
                config = config,
                requiredLibNames = selectedVariant.libsToInclude,
                serviceInfo = serviceInfo,
                potentialAbiAliasList = potentialAbiAliasList,
            )
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_extracted_plugin_libraries),
                lib.label,
            )

            // Extract assets (models/labels) from installed plugin APK (variant-aware).
            // zh-CN: 从已安装插件 APK 中解压 assets 资源 (models/labels), 并按变体裁剪.
            extractAssetsFromPluginApkOrThrow(
                context = context,
                serviceInfo = serviceInfo,
                pluginLibVariant = selectedVariant,
            )
            notifyStepProgress(
                ProgressStep.BUILD,
                context.getString(R.string.text_extracted_plugin_assets),
                lib.label,
            )
        }
    }

    private fun selectPluginServiceOrThrow(
        lib: Lib,
        action: String,
    ): Pair<ServiceInfo, PluginLibVariant> {
        ensureNotCancelled()
        val pm = globalContext.packageManager
        val moduleLabel = lib.label
        val pluginPair = lib.toPluginPair()
        val plugin = pluginPair.second
        val services = findServicesByAction(pm, action)
        if (services.isEmpty()) {
            throw IllegalStateException(
                globalContext.getString(R.string.error_missing_required_plugin_for_module_label, moduleLabel)
            )
        }

        // Only consider enabled plugins in packaging phase.
        // zh-CN: 打包阶段仅考虑已启用的插件.
        val enabledServices = services.filter { si ->
            PluginEnableStore.isEnabled(globalContext, si.packageName, defaultEnabled = true)
        }

        if (enabledServices.isEmpty()) {
            throw IllegalStateException(
                globalContext.getString(R.string.error_no_enabled_plugin_for_module_label, moduleLabel)
            )
        }

        val infos = enabledServices.mapNotNull { si ->
            val info = runCatching { queryPluginInfoBlocking(globalContext, si, pluginPair) }.getOrNull()
            info?.let { si to it }
        }

        val variantList = plugin.variants.map { it.variant?.lowercase() }

        infos.filter { (_, pi) ->
            pi.variant?.lowercase() in variantList
        }.maxByOrNull { (_, pi) -> pi.variant?.replace(Regex("\\D"), "")?.toIntOrNull() ?: 0 }?.let {
            return it
        }

        infos.firstOrNull()?.let { return it }

        throw IllegalStateException(
            globalContext.getString(R.string.error_no_available_enabled_plugin_variants_found, moduleLabel, variantList.joinToString("/"))
        )
    }

    private fun queryPluginInfoBlocking(context: Context, serviceInfo: ServiceInfo, pluginPair: Pair<Lib, PluginLib>): PluginLibVariant {
        ensureNotCancelled()
        // Bind service and call getInfo() synchronously (packaging-time only).
        // zh-CN: 同步绑定服务并调用 getInfo() (仅打包阶段使用).
        val latch = CountDownLatch(1)
        var selectedVariant: String? = null
        var error: Throwable? = null

        val (lib, plugin) = pluginPair

        val intent = Intent(plugin.action).apply {
            component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        }

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                try {
                    selectedVariant = plugin.onServiceConnected(binder)
                } catch (t: Throwable) {
                    error = t
                } finally {
                    latch.countDown()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                // Ignored.
            }
        }

        val bound = runCatching {
            context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)

        if (!bound) {
            throw IllegalStateException(
                globalContext.getString(R.string.error_failed_to_bind_plugin_service, lib.label)
            )
        }

        try {
            val ok = latch.await(15, TimeUnit.SECONDS)
            if (!ok) {
                throw IllegalStateException(
                    globalContext.getString(R.string.error_timeout_while_querying_plugin_info, lib.label)
                )
            }
            error?.let { throw it }
            selectedVariant ?: throw IllegalStateException(
                globalContext.getString(R.string.error_plugin_returned_empty_info, lib.label)
            )
            return plugin.variants.firstOrNull {
                it.variant.equals(selectedVariant, ignoreCase = true)
            } ?: throw IllegalStateException(
                globalContext.getString(R.string.error_plugin_returned_invalid_variant, lib.label, selectedVariant)
            )
        } finally {
            runCatching { context.unbindService(conn) }
        }
    }

    private fun extractAssetsFromPluginApkOrThrow(
        context: Context,
        serviceInfo: ServiceInfo,
        pluginLibVariant: PluginLibVariant,
    ) {
        ensureNotCancelled()
        val pm = globalContext.packageManager

        val appInfo = getApplicationInfoCompat(pm, serviceInfo.packageName)
        val apkPaths = buildList {
            add(appInfo.sourceDir)
            appInfo.splitSourceDirs?.forEach { add(it) }
        }.distinct()

        val (requiredPrefixes, optionalPrefixes) = pluginLibVariant.assetsToInclude

        // Extract required prefixes.
        // zh-CN: 解压必需前缀.
        val missingRequired = mutableListOf<String>()
        requiredPrefixes.forEach { prefix ->
            ensureNotCancelled()
            val ok = extractAssetsByPrefixFromApks(
                context = context,
                apkPaths = apkPaths,
                assetPrefix = prefix,
            )
            if (!ok) missingRequired += prefix
        }

        if (missingRequired.isNotEmpty()) {
            val detail = missingRequired.joinToString(", ")
            throw IllegalStateException(
                globalContext.getString(R.string.error_plugin_apk_does_not_contain_required_assets_for_variant, pluginLibVariant.variant, detail)
            )
        }

        // Extract optional prefixes (best-effort).
        // zh-CN: 解压可选前缀 (尽力而为).
        optionalPrefixes.forEach { prefix ->
            ensureNotCancelled()
            extractAssetsByPrefixFromApks(
                context = context,
                apkPaths = apkPaths,
                assetPrefix = prefix,
            )
        }
    }

    private fun extractAssetsByPrefixFromApks(
        context: Context,
        apkPaths: List<String>,
        assetPrefix: String,
    ): Boolean {
        ensureNotCancelled()
        var extractedAny = false
        val zipPrefix = toAssetZipPrefix(assetPrefix)
        apkPaths.forEach { apkPath ->
            ensureNotCancelled()
            runCatching {
                ZipFile(apkPath).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        ensureNotCancelled()
                        val entry: ZipEntry = entries.nextElement()
                        val name = entry.name
                        if (name != zipPrefix && !name.startsWith("$zipPrefix/")) continue
                        if (entry.isDirectory) continue

                        val relative = name.removePrefix("assets/")
                        val outFile = File(buildPath, "assets/$relative").apply {
                            parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
                        }
                        notifyStepProgress(
                            ProgressStep.BUILD,
                            context.getString(R.string.text_extracting_plugin_asset),
                            "$apkPath!/$name -> ${outFile.path}",
                        )

                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile, false).use { output ->
                                copyStreamWithCancel(input, output)
                                output.fd.sync()
                            }
                        }

                        extractedAny = true
                    }
                }
            }.onFailure {
                // Ignore and try next apk path.
                // zh-CN: 忽略异常并尝试下一个 apk 路径.
                it.printStackTrace()
            }
        }
        return extractedAny
    }

    private fun extractLibrariesFromPluginApkOrThrow(
        context: Context,
        config: ProjectConfig,
        requiredLibNames: List<String>,
        serviceInfo: ServiceInfo,
        potentialAbiAliasList: Map<String, String>,
    ) {
        ensureNotCancelled()
        val pm = globalContext.packageManager

        val appInfo = getApplicationInfoCompat(pm, serviceInfo.packageName)
        val apkPaths = buildList {
            add(appInfo.sourceDir)
            appInfo.splitSourceDirs?.forEach { add(it) }
        }.distinct()

        val missingPairs = mutableListOf<Pair<String, String>>() // (abi, soName)

        config.abis.forEach { abiCanonicalName ->
            ensureNotCancelled()
            val abiCandidates = buildList {
                add(abiCanonicalName)
                potentialAbiAliasList[abiCanonicalName]?.let { add(it) }
            }.distinct()

            requiredLibNames.forEach { soName ->
                ensureNotCancelled()
                val ok = extractFirstMatchedSoFromApks(
                    context = context,
                    apkPaths = apkPaths,
                    abiCandidates = abiCandidates,
                    soName = soName,
                    abiDestName = abiCanonicalName,
                )
                if (!ok) missingPairs += abiCanonicalName to soName
            }
        }

        if (missingPairs.isNotEmpty()) {
            val detail = missingPairs.joinToString(", ") { (abi, so) -> "$abi/$so" }
            throw IllegalStateException(
                globalContext.getString(R.string.error_plugin_apk_does_not_contain_required_native_libraries, detail)
            )
        }
    }

    private fun findServicesByAction(pm: PackageManager, action: String): List<ServiceInfo> {
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(Intent(action), PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(Intent(action), 0)
        }
        return resolveList.mapNotNull { it.serviceInfo }
    }

    private fun extractFirstMatchedSoFromApks(
        context: Context,
        apkPaths: List<String>,
        abiCandidates: List<String>,
        soName: String,
        abiDestName: String,
    ): Boolean {
        ensureNotCancelled()
        apkPaths.forEach { apkPath ->
            ensureNotCancelled()
            runCatching {
                ZipFile(apkPath).use { zip ->
                    abiCandidates.forEach { abiInApk ->
                        ensureNotCancelled()
                        val entryName = "lib/$abiInApk/$soName"
                        val entry = zip.getEntry(entryName) ?: return@forEach
                        val outFile = File(buildPath, "lib/$abiDestName/$soName").apply {
                            parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
                        }
                        notifyStepProgress(
                            ProgressStep.BUILD,
                            context.getString(R.string.text_extracting_plugin_so),
                            "$apkPath!/$entryName -> ${outFile.path}",
                        )
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile, false).use { output ->
                                copyStreamWithCancel(input, output)
                                output.fd.sync()
                            }
                        }
                        Log.i(TAG, "Extracted so from plugin apk: $apkPath!/$entryName -> ${outFile.path}")
                        return true
                    }
                }
            }.onFailure {
                // Ignore and try next apk path.
                // zh-CN: 忽略异常并尝试下一个 apk 路径.
                it.printStackTrace()
            }
        }
        return false
    }

    private fun getApplicationInfoCompat(pm: PackageManager, packageName: String): ApplicationInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
    }

    private fun copyLibrariesByAbi(context: Context, abiSrcName: String, abiDestName: String) {
        ensureNotCancelled()

        // @Reference to LZX284 (https://github.com/LZX284) by SuperMonster003 on Dec 11, 2023.
        //  ! http://pr.autojs6.com/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61
        val srcLibDir = File(appApkFile.parent, LIBRARY_DIR).path

        mLibsIncludes.distinct().forEach { libName ->
            ensureNotCancelled()
            runCatching {
                val srcFile = File(srcLibDir, "$abiSrcName/$libName")
                val destFile = File(buildPath, "lib/$abiDestName/$libName")
                srcFile.takeIf { it.exists() }?.let {
                    notifyStepProgress(
                        ProgressStep.BUILD,
                        context.getString(R.string.text_copying_library),
                        "${srcFile.path} -> ${destFile.path}",
                    )
                    it.copyTo(destFile, overwrite = true)
                }
            }.onFailure { it.printStackTrace() }
        }
    }

    @Suppress("SpellCheckingInspection")
    enum class Lib(
        @JvmField val label: String,
        @JvmField val aliases: List<String> = emptyList(),
        @JvmField val enumerable: Boolean = true,
        internal val plugin: PluginLib? = null,
        internal val libsToInclude: List<String> = emptyList(),
        internal val assetFilesToInclude: List<String> = emptyList(),
        // Select, then include into packaging APK. (选择后, 会被打包进 APK 中.)
        internal val assetDirsToInclude: List<String> = emptyList(),
    ) {

        TERMINAL_EMULATOR(
            label = "Terminal Emulator",
            enumerable = false,
            libsToInclude = listOf(
                "libjackpal-androidterm5.so",
                "libjackpal-termexec2.so",
            ),
        ),

        OPENCV(
            label = "OpenCV",
            aliases = listOf("cv"),
            libsToInclude = listOf(
                "libc++_shared.so",
                "libopencv_java4.so",
            ),
        ),

        MLKIT_OCR(
            label = "MLKit OCR",
            aliases = listOf("mlkit", "mlkitocr", "mlkit-ocr", "mlkit_ocr"),
            libsToInclude = listOf(
                "libmlkit_google_ocr_pipeline.so",
            ),
            assetDirsToInclude = listOf(
                "assets/mlkit-google-ocr-models",
            ),
        ),

        PADDLE_OCR(
            label = "Paddle OCR",
            aliases = listOf("paddle", "paddleocr", "paddle-ocr", "paddle_ocr"),
            libsToInclude = emptyList(),
            assetDirsToInclude = emptyList(),
            plugin = PluginLib(
                action = "org.autojs.plugin.PADDLE_OCR",
                onServiceConnected = { binder: IBinder ->
                    IOcrPlugin.Stub.asInterface(binder).info.variant
                },
                variants = listOf(
                    PluginLibVariant(
                        variant = "v3",
                        assetsToInclude = listOf(
                            "assets/labels/ppocr_keys_v1.txt",
                            "assets/models/ocr_v3_for_cpu/",
                        ) to listOf(
                            "assets/models/ocr_v3_for_cpu(slim)/",
                        ),
                        libsToInclude = listOf(
                            "libc++_shared.so",
                            "libpaddle_light_api_shared.so",
                            "libNative.so",
                            "libopencv_java4.so",
                        ),
                    ),
                    PluginLibVariant(
                        variant = "v5",
                        assetsToInclude = listOf(
                            "assets/labels/ppocr_keys_ocrv5.txt",
                            "assets/models/pp-ocrv5-arm/",
                            "assets/models/pp-ocrv5-arm-int8/",
                            "assets/models/pp-ocrv5-arm-opencl/",
                            "assets/models/pp-ocrv5-arm-opencl-int8/",
                        ) to emptyList(),
                        libsToInclude = listOf(
                            "libc++_shared.so",
                            "libpaddle_light_api_shared.so",
                            "libNative.so",
                            "libopencv_java4.so",
                            "libopencl_probe.so",
                        ),
                    ),
                ),
            ),
        ),

        RAPID_OCR(
            label = "Rapid OCR",
            aliases = listOf("rapid", "rapidocr", "rapid-ocr", "rapid_ocr"),
            libsToInclude = listOf(
                "libRapidOcr.so",
                "libonnxruntime.so",
            ),
            assetDirsToInclude = listOf(
                "assets/labels",
            ),
        ),

        OPENCC(
            label = "OpenCC",
            aliases = listOf("cc"),
            libsToInclude = listOf(
                "libChineseConverter.so",
            ),
            assetDirsToInclude = listOf(
                "assets/openccdata",
            ),
        ),

        PINYIN(
            label = "Pinyin",
            aliases = listOf("pin"),
            assetFilesToInclude = listOf(
                "assets/dict-chinese-words.db.gzip",
                "assets/dict-chinese-phrases.db.gzip",
                "assets/dict-chinese-chars.db.gzip",
                "assets/prob_emit.txt",
            ),
        ),

        MLKIT_BARCODE(
            label = "MLKit Barcode",
            aliases = listOf("barcode", "mlkit-barcode", "mlkit_barcode"),
            libsToInclude = listOf(
                "libbarhopper_v3.so",
            ),
            assetDirsToInclude = listOf(
                "assets/mlkit_barcode_models",
            ),
        ),

        MEDIA_INFO(
            label = "MediaInfo",
            aliases = listOf("mediainfo", "media-info", "media_info"),
            libsToInclude = listOf(
                "libmediainfo.so",
            ),
        ),

        IMAGE_QUANT(
            label = "Image Quantization",
            aliases = listOf("imagequant", "image-quant", "image-quantization", "image_quant", "image_quantization"),
            libsToInclude = listOf(
                "libpng.so",
                "libpng16d.so",
                "libpngquant_bridge.so",
            ),
        ),

        ;

        val isPlugin: Boolean
            get() = plugin != null

        fun toPluginPair(): Pair<Lib, PluginLib> = this to plugin!!

        fun ensureLibFiles(moduleName: String = label) {
            if (!isInrt) return
            val nativeLibraryDir = File(globalContext.applicationInfo.nativeLibraryDir)
            val primaryNativeLibraries = nativeLibraryDir.list()?.toList() ?: emptyList()
            Log.d(TAG, "Native libraries: [ ${primaryNativeLibraries.joinToString(", ")} ]")
            if (!primaryNativeLibraries.containsAll(libsToInclude)) {
                throw Exception(globalContext.getString(R.string.error_module_does_not_work_due_to_the_lack_of_necessary_library_files, moduleName))
            }
        }

        companion object {

            val defaultLibsToInclude = listOf(
                TERMINAL_EMULATOR,
            ).flatMap { it.libsToInclude }

            val defaultAssetFilesToInclude = listOf(
                "init.js", "roboto_medium.ttf",
            ).map(::normalizeAssetPath)

            val defaultAssetDirsToExclude = listOf(
                "doc", "docs", "editor", "indices", "js-beautify", "sample", "stored-locales", "models",
            ).plus(entries.flatMap { it.assetDirsToInclude })
                .map(::normalizeAssetPath)
                .distinct()
        }
    }

    data class PluginLib(
        val action: String,
        val onServiceConnected: (IBinder) -> String,
        val variants: List<PluginLibVariant>,
    )

    data class PluginLibVariant(
        val variant: String? = null,
        /** <Required List> to <Optional List>. */
        val assetsToInclude: Pair<List<String>, List<String>> = emptyList<String>() to emptyList(),
        val libsToInclude: List<String> = emptyList(),
    )

    companion object {

        const val ICON_NAME = "ic_launcher"
        const val ICON_RES_DIR = "mipmap"
        const val LIBRARY_DIR = "lib"

        const val TEMPLATE_APK_NAME = "template.apk"
        const val INRT_APP_ID = "org.autojs.autojs6.inrt"

        private val TAG = ApkBuilder::class.java.simpleName

        private val globalContext: Context by lazy { GlobalAppContext.get() }

        val appApkFile by lazy {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                File(globalContext.packageManager.getApplicationInfo(globalContext.packageName, ApplicationInfoFlags.of(GET_SHARED_LIBRARY_FILES.toLong())).sourceDir)
            } else {
                File(globalContext.packageManager.getApplicationInfo(globalContext.packageName, 0).sourceDir)
            }
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
    }
}
