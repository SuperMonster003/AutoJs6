package org.autojs.autojs.apkbuilder

import android.content.Context
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.content.pm.PackageManager.GET_SHARED_LIBRARY_FILES
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import com.mcal.apksigner.ApkSigner
import com.reandroid.arsc.chunk.TableBlock
import org.apache.commons.io.FileUtils.copyFile
import org.apache.commons.io.FileUtils.copyInputStreamToFile
import org.autojs.autojs.apkbuilder.keystore.AESUtils
import org.autojs.autojs.apkbuilder.keystore.KeyStore
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.engine.encryption.AdvancedEncryptionStandard
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.project.BuildInfo
import org.autojs.autojs.project.ProjectConfig
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
import java.util.concurrent.Callable

/**
 * Created by Stardust on Oct 24, 2017.
 * Modified by SuperMonster003 as of Jul 8, 2022.
 */
open class ApkBuilder(apkInputStream: InputStream?, private val outApkFile: File, private val workspacePath: String) {

    private var mProgressCallback: ProgressCallback? = null
    private var mArscPackageName: String? = null
    private var mManifestEditor: ManifestEditor? = null
    private var mInitVector: String? = null
    private var mKey: String? = null

    private lateinit var mAppConfig: AppConfig

    private val mApkPackager = ApkPackager(apkInputStream, workspacePath)

    private val mAssetManager: AssetManager by lazy { globalContext.assets }

    private var mLibsIncludes = Libs.defaultLibsToInclude.toMutableList()
    private var mAssetsFileIncludes = Libs.defaultAssetFilesToInclude.toMutableList()
    private var mAssetsDirExcludes = Libs.defaultAssetDirsToExclude.toMutableList()

    private val mManifestFile
        get() = File(workspacePath, "AndroidManifest.xml")

    private val mResourcesArscFile
        get() = File(workspacePath, "resources.arsc")

    init {
        PFiles.ensureDir(outApkFile.path)
    }

    fun setProgressCallback(callback: ProgressCallback?) = also { mProgressCallback = callback }

    @Throws(IOException::class)
    fun prepare() = also {
        mProgressCallback?.let { callback -> GlobalAppContext.post { callback.onPrepare(this) } }
        File(workspacePath).mkdirs()
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
        val destDirFile = File(workspacePath, relativeDestPath).apply { mkdir() }
        srcFile.listFiles()?.forEach { srcChildFile ->
            if (srcChildFile.isFile) {
                if (srcChildFile.name.endsWith(JAVASCRIPT.extensionWithDot)) {
                    encryptToDir(srcChildFile, destDirFile)
                } else {
                    srcChildFile.copyTo(File(destDirFile, srcChildFile.name), true)
                }
            } else {
                if (!mAppConfig.ignoredDirs.contains(srcChildFile)) {
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
        val destFile = File(workspacePath, relativeDestPath)
        if (destFile.name.endsWith(JAVASCRIPT.extensionWithDot)) {
            encrypt(srcFile, destFile)
        } else {
            srcFile.copyTo(destFile, true)
        }
    }

    @Throws(IOException::class)
    fun withConfig(config: AppConfig) = also {
        config.also { mAppConfig = it }.run {
            mManifestEditor = editManifest()
                .setAppName(appName)
                .setVersionName(versionName)
                .setVersionCode(versionCode)
                .setPackageName(packageName)

            setArscPackageName(packageName)
            updateProjectConfig(this)
            copyAssetsRecursively("", File(workspacePath, "assets"))
            copyLibrariesByConfig(this)
            setScriptFile(sourcePath)
        }
    }

    @Throws(FileNotFoundException::class)
    fun editManifest(): ManifestEditor = ManifestEditorWithAuthorities(FileInputStream(mManifestFile)).also { mManifestEditor = it }

    private fun updateProjectConfig(appConfig: AppConfig) {
        val projectConfig = when {
            !PFiles.isDir(appConfig.sourcePath) -> null
            else -> ProjectConfig.fromProjectDir(appConfig.sourcePath)?.also {
                val buildNumber = it.buildInfo.buildNumber
                it.buildInfo = BuildInfo.generate(buildNumber + 1)
                PFiles.write(ProjectConfig.configFileOfDir(appConfig.sourcePath), it.toJson())
            }
        } ?: ProjectConfig()
            .setMainScriptFile("main.js")
            .setName(appConfig.appName)
            .setPackageName(appConfig.packageName)
            .setVersionName(appConfig.versionName)
            .setVersionCode(appConfig.versionCode)
            .also { config ->
                config.buildInfo = BuildInfo.generate(appConfig.versionCode.toLong())
                File(workspacePath, "assets/project/${ProjectConfig.CONFIG_FILE_NAME}").also { file ->
                    file.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
                }.writeText(config.toJson())
            }

        projectConfig.run {
            mKey = MD5Utils.md5(packageName + versionName + mainScriptFile)
            mInitVector = MD5Utils.md5(buildInfo.buildId + name).substring(0, 16)
            Libs.entries.forEach { entry ->
                if (appConfig.libs.contains(entry.label)) {
                    mLibsIncludes += entry.libsToInclude.toSet()
                    mAssetsFileIncludes += entry.assetFilesToInclude.toSet()
                    mAssetsDirExcludes -= entry.assetDirsToExclude.toSet()
                }
            }
        }
    }

    @Throws(Exception::class)
    fun build() = also {
        mProgressCallback?.let { callback -> GlobalAppContext.post { callback.onBuild(this) } }
        mAppConfig.icon?.let { callable ->
            runCatching {
                val tableBlock = TableBlock.load(mResourcesArscFile)
                val packageName = "${GlobalAppContext.get().packageName}.inrt"
                val packageBlock = tableBlock.getOrCreatePackage(0x7f, packageName).also {
                    tableBlock.currentPackage = it
                }
                val appIcon = packageBlock.getOrCreate("", ICON_RES_DIR, ICON_NAME)
                val appIconPath = appIcon.resValue.decodeValue()
                Log.d(TAG, "Icon path: $appIconPath")
                val file = File(workspacePath, appIconPath).also {
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
        TinySign.sign(File(workspacePath), fos)
        fos.close()

        val defaultKeyStoreFile = File(workspacePath, "default_key_store.bks")
        val tmpOutputApk = File(workspacePath, "temp.apk")
        copyInputStreamToFile(GlobalAppContext.get().assets.open("default_key_store.bks"), defaultKeyStoreFile)

        val signer = ApkSigner(outApkFile, tmpOutputApk)
        signer.useDefaultSignatureVersion = false
        signer.v1SigningEnabled = mAppConfig.signatureSchemes.contains("V1")
        signer.v2SigningEnabled = mAppConfig.signatureSchemes.contains("V2")
        signer.v3SigningEnabled = mAppConfig.signatureSchemes.contains("V3")
        signer.v4SigningEnabled = mAppConfig.signatureSchemes.contains("V4")

        var keyStoreFile = defaultKeyStoreFile
        var password = "AutoJs6"
        var alias = "AutoJs6"
        var aliasPassword = "AutoJs6"

        mAppConfig.keyStore?.let {
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
            copyFile(tmpOutputApk, outApkFile)
        } catch (e: java.lang.Exception) {
            throw java.lang.RuntimeException(e)
        }
    }

    fun cleanWorkspace() = also {
        mProgressCallback?.let { callback -> GlobalAppContext.post { callback.onClean(this) } }
        delete(File(workspacePath))
    }

    @Throws(IOException::class)
    fun setArscPackageName(packageName: String?) = also { mArscPackageName = packageName }

    @Throws(IOException::class)
    private fun buildArsc() {
        val oldArsc = File(workspacePath, "resources.arsc")
        val newArsc = File(workspacePath, "resources.arsc.new")
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

    class AppConfig {

        var appName: String? = null
            private set
        var versionName: String? = null
            private set
        var versionCode = 0
            private set
        var sourcePath: String? = null
            private set
        var packageName: String? = null
            private set
        var ignoredDirs = ArrayList<File>()
        var icon: Callable<Bitmap>? = null
            private set
        var abis: List<String> = emptyList()
            private set
        var libs: List<String> = emptyList()
            private set
        var keyStore: KeyStore? = null
            private set
        var signatureSchemes: String = "V1 + V2"
            private set
        var permissions: List<String> = emptyList()
            private set

        fun ignoreDir(dir: File) = also { ignoredDirs.add(dir) }

        fun setAppName(appName: String?) = also { appName?.let { this.appName = it } }

        fun setVersionName(versionName: String?) = also { versionName?.let { this.versionName = it } }

        fun setVersionCode(versionCode: Int?) = also { versionCode?.let { this.versionCode = it } }

        fun setSourcePath(sourcePath: String?) = also { sourcePath?.let { this.sourcePath = it } }

        fun setPackageName(packageName: String?) = also { packageName?.let { this.packageName = it } }

        fun setIcon(icon: Callable<Bitmap>?) = also { icon?.let { this.icon = it } }

        fun setIcon(iconPath: String?) = also { iconPath?.let { this.icon = Callable { BitmapFactory.decodeFile(it) } } }

        fun setAbis(abis: List<String>) = also { this.abis = abis }

        fun setLibs(libs: List<String>) = also { this.libs = libs }

        fun setKeyStore(keyStore: KeyStore?) = also { this.keyStore = keyStore }

        fun setSignatureSchemes(signatureSchemes: String) = also { this.signatureSchemes = signatureSchemes }

        fun setPermissions(permissions: List<String>) = also { this.permissions = permissions }

        companion object {
            @JvmStatic
            fun fromProjectConfig(projectDir: String?, projectConfig: ProjectConfig) = AppConfig()
                .setAppName(projectConfig.name)
                .setPackageName(projectConfig.packageName)
                .ignoreDir(File(projectDir, projectConfig.buildDir))
                .setVersionCode(projectConfig.versionCode)
                .setVersionName(projectConfig.versionName)
                .setSourcePath(projectDir)
                .setIcon(projectConfig.icon?.let { File(projectDir, it).path })
        }
    }

    private inner class ManifestEditorWithAuthorities(manifestInputStream: InputStream?) : ManifestEditor(manifestInputStream) {
        override fun onAttr(attr: AxmlWriter.Attr) {
            attr.apply {
                if (name.data == "authorities" && value is StringItem) {
                    (value as StringItem).data = "${mAppConfig.packageName}.fileprovider"
                } else {
                    super.onAttr(this)
                }
            }
        }

        override fun isPermissionRequired(permissionName: String): Boolean {
            return mAppConfig.permissions.contains(permissionName)
        }
    }

    private fun copyLibrariesByConfig(config: AppConfig) {

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
        //  ! https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61
        val srcLibDir = File(appApkFile.parent, LIBRARY_DIR).path

        mLibsIncludes.distinct().forEach { libName ->
            runCatching {
                File(srcLibDir, "$abiSrcName/$libName").takeIf { it.exists() }?.copyTo(
                    File(workspacePath, "lib/$abiDestName/$libName"),
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