package org.autojs.autojs.apkbuilder

import android.content.Context
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.content.pm.PackageManager.GET_SHARED_LIBRARY_FILES
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.mcal.apksigner.ApkSigner
import com.reandroid.arsc.chunk.TableBlock
import org.apache.commons.io.FileUtils
import org.autojs.autojs.apkbuilder.keystore.AESUtils
import org.autojs.autojs.app.GlobalAppContext
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
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import pxb.android.StringItem
import pxb.android.axml.AxmlWriter
import zhao.arsceditor.ResDecoder.ARSCDecoder
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Created by Stardust on Oct 24, 2017.
 * Modified by SuperMonster003 as of Jul 8, 2022.
 */
open class ApkBuilder(apkInputStream: InputStream?, private val outApkFile: File, private val buildPath: String) {

    private var mProgressCallback: ProgressCallback? = null
    private var mArscPackageName: String? = null
    private var mManifestEditor: ManifestEditor? = null
    private var mInitVector: String? = null
    private var mKey: String? = null

    private lateinit var mProjectConfig: ProjectConfig

    private val mApkPackager = ApkPackager(apkInputStream, buildPath)

    private val mAssetManager: AssetManager by lazy { globalContext.assets }

    private var mLibsIncludes = Libs.defaultLibsToInclude.toMutableList()
    private var mAssetsFileIncludes = Libs.defaultAssetFilesToInclude.toMutableList()
    private var mAssetsDirExcludes = Libs.defaultAssetDirsToExclude.toMutableList()

    private var mSplashThemeId: Int = 0
    private var mNoSplashThemeId: Int = 0

    private val mManifestFile
        get() = File(buildPath, "AndroidManifest.xml")

    private val mResourcesArscFile
        get() = File(buildPath, "resources.arsc")

    init {
        PFiles.ensureDir(outApkFile.path)
    }

    fun setProgressCallback(callback: ProgressCallback?) = also { mProgressCallback = callback }

    @Throws(IOException::class)
    fun prepare() = also {
        mProgressCallback?.let { callback -> GlobalAppContext.post { callback.onPrepare(this) } }
        File(buildPath).mkdirs()
        mApkPackager.unzip()
    }

    @Throws(IOException::class)
    fun setScriptFile(path: String?) = also {
        path?.let {
            when {
                PFiles.isDir(it) -> copyDir(it, "assets/project/")
                else -> replaceFile(it, "assets/project/main.js")
            }
        }
    }

    @Throws(IOException::class)
    @Suppress("SameParameterValue")
    private fun copyDir(srcPath: String, relativeDestPath: String) {
        copyDir(File(srcPath), relativeDestPath)
    }

    @Throws(IOException::class)
    fun copyDir(srcFile: File, relativeDestPath: String) {
        val destDirFile = File(buildPath, relativeDestPath).apply { mkdir() }
        srcFile.listFiles()?.forEach { srcChildFile ->
            if (srcChildFile.isFile) {
                if (srcChildFile.name.endsWith(JAVASCRIPT.extensionWithDot)) {
                    encryptToDir(srcChildFile, destDirFile)
                } else {
                    srcChildFile.copyTo(File(destDirFile, srcChildFile.name), true)
                }
            } else {
                if (!mProjectConfig.excludedDirs.contains(srcChildFile)) {
                    copyDir(srcChildFile, PFiles.join(relativeDestPath, srcChildFile.name + File.separator))
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun encrypt(srcFile: File, destFile: File) {
        destFile.outputStream().use { os ->
            writeHeader(os, JavaScriptFileSource(srcFile).executionMode.toShort())
            AdvancedEncryptionStandard(mKey!!.toByteArray(), mInitVector!!)
                .encrypt(PFiles.readBytes(srcFile.path))
                .let { bytes -> os.write(bytes) }
        }
    }

    private fun encryptToDir(srcFile: File, destDirFile: File) {
        val destFile = File(destDirFile, srcFile.name)
        encrypt(srcFile, destFile)
    }

    @Throws(IOException::class)
    fun replaceFile(srcPath: String, relativeDestPath: String) = replaceFile(File(srcPath), relativeDestPath)

    @Throws(IOException::class)
    fun replaceFile(srcFile: File, relativeDestPath: String) = also {
        val destFile = File(buildPath, relativeDestPath)
        if (destFile.name.endsWith(JAVASCRIPT.extensionWithDot)) {
            encrypt(srcFile, destFile)
        } else {
            srcFile.copyTo(destFile, true)
        }
    }

    @Throws(IOException::class)
    fun withConfig(config: ProjectConfig) = also {
        config.also { mProjectConfig = it }.run {
            retrieveSplashThemeResources(launchConfig)
            prepareManifestConfiguration(this)
            setArscPackageName(packageName)
            updateProjectConfig(this)
            copyAssetsRecursively("", File(buildPath, "assets"))
            copyLibrariesByConfig(this)
            setScriptFile(sourcePath)
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

    private fun updateProjectConfig(config: ProjectConfig) {

        // 这里为什么要有这样的一个方法? (
        //     会不会是因为有些配置需要写入到文件中, 这些配置包括自增的版本号, 用户的选择或键入值等等
        // )
        // 参数 config 只是获取了一部分的字段用于设置新的 projectConfig, 为什么不直接使用全部的字段? (
        //     因为有些不需要更新. 如果我们需要将所有设置开放到 Activity 页面中, 那么其实所有配置都是需要更新的
        // )
        // 像 abis, libs, signatureScheme 等信息就全部丢失了. (
        //     所以需要添加到这个方法中
        // )

        val projectConfig = run {
            if (PFiles.isDir(config.sourcePath)) {
                // @Hint by SuperMonster003 on Jan 23, 2025.
                //  ! Project directory packaging.
                //  ! zh-CN: 打包项目目录.
                ProjectConfig.fromProjectDir(config.sourcePath)?.let { sourceProjectConfig ->
                    sourceProjectConfig
                        .setBuildInfo(BuildInfo.generate(sourceProjectConfig.buildInfo.buildNumber + 1))
                    File(ProjectConfig.configFileOfDir(config.sourcePath)).writeText(sourceProjectConfig.toJson())
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
                    .setBuildInfo(BuildInfo.generate(newProjectConfig.versionCode.toLong()))
                File(buildPath, "assets/project/$CONFIG_FILE_NAME").also { file ->
                    file.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
                }.writeText(newProjectConfig.toJson())
            }

        }

        mKey = MD5Utils.md5(projectConfig.run { packageName + versionName + mainScriptFileName })
        mInitVector = MD5Utils.md5(projectConfig.run { buildInfo.buildId + name }).take(16)
        Libs.entries.forEach { entry ->
            if (config.libs.contains(entry.label)) {
                mLibsIncludes += entry.libsToInclude.toSet()
                mAssetsFileIncludes += entry.assetFilesToInclude.toSet()
                mAssetsDirExcludes -= entry.assetDirsToExclude.toSet()
            }
        }
    }

    @Throws(Exception::class)
    fun build() = also {
        mProgressCallback?.let { callback -> GlobalAppContext.post { callback.onBuild(this) } }
        mProjectConfig.iconBitmapGetter?.let { callable ->
            runCatching {
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
                callable.call()?.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(file))
            }.onFailure { throw RuntimeException(it) }
        }
        mManifestEditor?.let {
            it.commit()
            it.writeTo(FileOutputStream(mManifestFile))
        }
        mArscPackageName?.let { buildArsc() }
    }

    private fun copyAssetsRecursively(assetPath: String, targetFile: File) {
        if (targetFile.isFile && targetFile.exists()) return
        val list = mAssetManager.list(assetPath) ?: return
        if (list.isEmpty()) /* asset is a file */ {
            if (!assetPath.contains(File.separatorChar)) /* assets root dir */ {
                if (!mAssetsFileIncludes.contains(assetPath)) {
                    return
                }
            }
            mAssetManager.open(assetPath).use { input ->
                FileOutputStream(targetFile.absolutePath).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
        } else /* asset is folder */ {
            if (mAssetsDirExcludes.any { assetPath.matches(Regex("$it(/[^/]+)*")) }) {
                return
            }
            targetFile.delete()
            targetFile.mkdir()
            list.forEach {
                val sourcePath = if (assetPath.isEmpty()) it else "$assetPath/$it"
                copyAssetsRecursively(sourcePath, File(targetFile, it))
            }
        }
    }

    @Throws(Exception::class)
    fun sign() = also {
        mProgressCallback?.let { callback -> GlobalAppContext.post { callback.onSign(this) } }
        val fos = FileOutputStream(outApkFile)
        TinySign.sign(File(buildPath), fos)
        fos.close()

        val defaultKeyStoreFile = File(buildPath, "default_key_store.bks")
        val tmpOutputApk = File(buildPath, "temp.apk")
        FileUtils.copyInputStreamToFile(GlobalAppContext.get().assets.open("default_key_store.bks"), defaultKeyStoreFile)

        val signer = ApkSigner(outApkFile, tmpOutputApk).apply {
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

        // 使用 ApkSigner 重新签名
        if (!signer.signRelease(keyStoreFile, password, alias, aliasPassword)) {
            throw java.lang.RuntimeException("Failed to re-sign using ApkSigner")
        }

        try {
            FileUtils.copyFile(tmpOutputApk, outApkFile)
        } catch (e: java.lang.Exception) {
            throw java.lang.RuntimeException(e)
        }
    }

    fun cleanWorkspace() = also {
        mProgressCallback?.let { callback -> GlobalAppContext.post { callback.onClean(this) } }
        delete(File(buildPath))
    }

    @Throws(IOException::class)
    fun setArscPackageName(packageName: String?) = also { mArscPackageName = packageName }

    @Throws(IOException::class)
    private fun buildArsc() {
        val oldArsc = File(buildPath, "resources.arsc")
        val newArsc = File(buildPath, "resources.arsc.new")
        val decoder = ARSCDecoder(BufferedInputStream(FileInputStream(oldArsc)), null, false)
        decoder.CloneArsc(FileOutputStream(newArsc), mArscPackageName, true)
        oldArsc.delete()
        newArsc.renameTo(oldArsc)
    }

    private fun delete(file: File) {
        file.apply { if (isDirectory) listFiles()?.forEach { delete(it) } }.also { it.delete() }
    }

    interface ProgressCallback {

        fun onPrepare(builder: ApkBuilder)
        fun onBuild(builder: ApkBuilder)
        fun onSign(builder: ApkBuilder)
        fun onClean(builder: ApkBuilder)

    }

    private inner class ManifestEditorWithAuthorities(manifestInputStream: InputStream?) : ManifestEditor(manifestInputStream) {
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

    private fun copyLibrariesByConfig(config: ProjectConfig) {

        // @Hint by SuperMonster003 on Dec 11, 2023.
        //  ! The list contains only abi names not matching the canonical name itself.
        //  ! zh-CN: 这个列表仅含有不匹配规范名称本身的 abi 名称.
        val potentialAbiAliasList = mapOf(
            "arm64-v8a" to "arm64",
            "armeabi-v7a" to "arm",
        )

        config.abis.forEach { abiCanonicalName ->
            copyLibrariesByAbi(abiCanonicalName, abiCanonicalName)
            potentialAbiAliasList[abiCanonicalName]?.let { abiAliasName ->
                copyLibrariesByAbi(abiAliasName, abiCanonicalName)
            }
        }
    }

    private fun copyLibrariesByAbi(abiSrcName: String, abiDestName: String) {

        // @Reference to LZX284 (https://github.com/LZX284) by SuperMonster003 on Dec 11, 2023.
        //  ! http://pr.autojs6.com/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61
        val srcLibDir = File(appApkFile.parent, LIBRARY_DIR).path

        mLibsIncludes.distinct().forEach { libName ->
            runCatching {
                File(srcLibDir, "$abiSrcName/$libName").takeIf { it.exists() }?.copyTo(
                    File(buildPath, "lib/$abiDestName/$libName"),
                    overwrite = true
                )
            }.onFailure { it.printStackTrace() }
        }
    }

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

    }

    @Suppress("SpellCheckingInspection")
    enum class Libs(
        @JvmField val label: String,
        @JvmField val aliases: List<String> = emptyList(),
        @JvmField val enumerable: Boolean = true,
        internal val libsToInclude: List<String> = emptyList(),
        internal val assetFilesToInclude: List<String> = emptyList(),
        internal val assetDirsToExclude: List<String> = emptyList(),
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
            assetDirsToExclude = listOf(
                "mlkit-google-ocr-models",
            ),
        ),

        PADDLE_OCR(
            label = "Paddle OCR",
            aliases = listOf("paddle", "paddleocr", "paddle-ocr", "paddle_ocr"),
            libsToInclude = listOf(
                "libc++_shared.so",
                "libpaddle_light_api_shared.so",
                "libNative.so",
                "libhiai.so",
                "libhiai_ir.so",
                "libhiai_ir_build.so",
            ),
            assetDirsToExclude = listOf(
                "models",
            )
        ),

        RAPID_OCR(
            label = "Rapid OCR",
            aliases = listOf("rapid", "rapidocr", "rapid-ocr", "rapid_ocr"),
            libsToInclude = listOf(
                "libRapidOcr.so",
            ),
            assetDirsToExclude = listOf(
                "labels",
            ),
        ),

        OPENCC(
            label = "OpenCC",
            aliases = listOf("cc"),
            libsToInclude = listOf(
                "libChineseConverter.so",
            ),
            assetDirsToExclude = listOf(
                "openccdata",
            ),
        ),

        PINYIN(
            label = "Pinyin",
            aliases = listOf("pin"),
            assetFilesToInclude = listOf(
                "dict-chinese-words.db.gzip",
                "dict-chinese-phrases.db.gzip",
                "dict-chinese-chars.db.gzip",
                "prob_emit.txt",
            ),
        ),

        MLKIT_BARCODE(
            label = "MLKit Barcode",
            aliases = listOf("barcode", "mlkit-barcode", "mlkit_barcode"),
            assetDirsToExclude = listOf(
                "mlkit_barcode_models",
            ),
        );

        fun ensureLibFiles(moduleName: String = label) {
            if (!BuildConfig.isInrt) return
            val nativeLibraryDir = File(globalContext.applicationInfo.nativeLibraryDir)
            val primaryNativeLibraries = nativeLibraryDir.list()?.toList() ?: emptyList()
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
            )

            val defaultAssetDirsToExclude = listOf(
                "doc", "docs", "editor", "indices", "js-beautify", "sample", "stored-locales",
            ) + Libs.entries.flatMap { it.assetDirsToExclude }

        }

    }

}