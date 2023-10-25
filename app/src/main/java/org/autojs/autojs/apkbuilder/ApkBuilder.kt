package org.autojs.autojs.apkbuilder

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.reandroid.arsc.chunk.TableBlock
import org.autojs.autojs.apkbuilder.util.StreamUtils
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.engine.encryption.AdvancedEncryptionStandard
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.project.BuildInfo
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.script.EncryptedScriptFileHeader.writeHeader
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.util.MD5Utils
import pxb.android.StringItem
import pxb.android.axml.AxmlWriter
import zhao.arsceditor.ResDecoder.ARSCDecoder
import java.io.*
import java.util.concurrent.Callable

/**
 * Created by Stardust on 2017/10/24.
 * Modified by SuperMonster003 as of Jul 8, 2022.
 */
open class ApkBuilder(apkInputStream: InputStream?, private val mOutApkFile: File, private val mWorkspacePath: String) {

    private var mProgressCallback: ProgressCallback? = null
    private val mApkPackager = ApkPackager(apkInputStream, mWorkspacePath)
    private var mArscPackageName: String? = null
    private var mManifestEditor: ManifestEditor? = null
    private var mInitVector: String? = null
    private var mKey: String? = null

    private val mAssetManager: AssetManager by lazy { GlobalAppContext.get().assets }

    private val mManifestFile
        get() = File(mWorkspacePath, "AndroidManifest.xml")

    private val mResourcesArscFile
        get() = File(mWorkspacePath, "resources.arsc")

    private lateinit var mAppConfig: AppConfig

    init {
        PFiles.ensureDir(mOutApkFile.path)
    }

    fun setProgressCallback(callback: ProgressCallback?) = also { mProgressCallback = callback }

    @Throws(IOException::class)
    fun prepare() = also {
        mProgressCallback?.let { callback -> GlobalAppContext.post { callback.onPrepare(this) } }
        File(mWorkspacePath).mkdirs()
        mApkPackager.unzip()
        copyAssetsRecursively("", File(mWorkspacePath, "assets"))
    }

    @Throws(IOException::class)
    fun setScriptFile(path: String?) = also {
        path?.let {
            when {
                PFiles.isDir(it) -> copyDir("assets/project/", it)
                else -> replaceFile("assets/project/main.js", it)
            }
        }
    }

    @Throws(IOException::class)
    fun copyDir(relativePath: String, path: String) {
        val fromDir = File(path)
        val toDir = File(mWorkspacePath, relativePath).apply { mkdir() }
        fromDir.listFiles()?.forEach { child ->
            if (child.isFile) {
                if (child.name.endsWith(".js")) {
                    encrypt(toDir, child)
                } else {
                    StreamUtils.write(FileInputStream(child), FileOutputStream(File(toDir, child.name)))
                }
            } else {
                if (!mAppConfig.ignoredDirs.contains(child)) {
                    copyDir(PFiles.join(relativePath, child.name + "/"), child.path)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun encrypt(toDir: File, file: File) = encrypt(FileOutputStream(File(toDir, file.name)), file)

    @Throws(IOException::class)
    private fun encrypt(fos: FileOutputStream, file: File) {
        try {
            writeHeader(fos, JavaScriptFileSource(file).executionMode.toShort())
            AdvancedEncryptionStandard(mKey!!.toByteArray(), mInitVector!!)
                .encrypt(PFiles.readBytes(file.path))
                .let { bytes -> fos.apply { write(bytes) }.apply { close() } }
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    fun replaceFile(relativePath: String, newFilePath: String) = also {
        if (newFilePath.endsWith(".js")) {
            encrypt(FileOutputStream(File(mWorkspacePath, relativePath)), File(newFilePath))
        } else {
            StreamUtils.write(FileInputStream(newFilePath), FileOutputStream(File(mWorkspacePath, relativePath)))
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
            setScriptFile(sourcePath)
        }
    }

    @Throws(FileNotFoundException::class)
    fun editManifest(): ManifestEditor = ManifestEditorWithAuthorities(FileInputStream(mManifestFile)).also { mManifestEditor = it }

    private fun updateProjectConfig(appConfig: AppConfig) {
        let {
            if (PFiles.isDir(appConfig.sourcePath)) {
                ProjectConfig.fromProjectDir(appConfig.sourcePath).also {
                    val buildNumber = it.buildInfo.buildNumber
                    it.buildInfo = BuildInfo.generate(buildNumber + 1)
                    PFiles.write(ProjectConfig.configFileOfDir(appConfig.sourcePath), it.toJson())
                }
            } else {
                ProjectConfig()
                    .setMainScriptFile("main.js")
                    .setName(appConfig.appName)
                    .setPackageName(appConfig.packageName)
                    .setVersionName(appConfig.versionName)
                    .setVersionCode(appConfig.versionCode)
                    .also {
                        it.buildInfo = BuildInfo.generate(appConfig.versionCode.toLong())
                        PFiles.write(File(mWorkspacePath, "assets/project/project.json").path, it.toJson())
                    }
            }
        }.run {
            mKey = MD5Utils.md5(packageName + versionName + mainScriptFile)
            mInitVector = MD5Utils.md5(buildInfo.buildId + name).substring(0, 16)
        }
    }

    @Throws(IOException::class)
    fun build() = also {
        mProgressCallback?.let { callback -> GlobalAppContext.post { callback.onBuild(this) } }
        mAppConfig.icon?.let { callable ->
            try {
                val tableBlock = TableBlock.load(mResourcesArscFile)
                val packageName = "${GlobalAppContext.get().packageName}.inrt"
                val packageBlock = tableBlock.getOrCreatePackage(0x7f, packageName).also {
                    tableBlock.currentPackage = it
                }
                val appIcon = packageBlock.getOrCreate("", ICON_RES_DIR, ICON_NAME)
                val appIconPath = appIcon.resValue.decodeValue()
                Log.d(TAG, "Icon path: $appIconPath")
                val file = File(mWorkspacePath, appIconPath).also {
                    if (!it.exists()) {
                        File(it.parent!!).mkdirs()
                        it.createNewFile()
                    }
                }
                callable.call()?.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(file))
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
        mManifestEditor?.apply { commit() }?.run { writeTo(FileOutputStream(mManifestFile)) }
        mArscPackageName?.let { buildArsc() }
    }

    private fun copyAssetsRecursively(assetPath: String, targetFile: File) {
        if (targetFile.isFile && targetFile.exists()) return
        val list = mAssetManager.list(assetPath) ?: return
        if (list.isEmpty()) /* asset is file */ {
            mAssetManager.open(assetPath).use { input ->
                FileOutputStream(targetFile.absolutePath).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
        } else /* asset is folder */ {
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
        val fos = FileOutputStream(mOutApkFile)
        TinySign.sign(File(mWorkspacePath), fos)
        fos.close()
    }

    fun cleanWorkspace() = also {
        mProgressCallback?.let { callback -> GlobalAppContext.post { callback.onClean(this) } }
        delete(File(mWorkspacePath))
    }

    @Throws(IOException::class)
    fun setArscPackageName(packageName: String?) = also { mArscPackageName = packageName }

    @Throws(IOException::class)
    private fun buildArsc() {
        val oldArsc = File(mWorkspacePath, "resources.arsc")
        val newArsc = File(mWorkspacePath, "resources.arsc.new")
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

        fun ignoreDir(dir: File) = also { ignoredDirs.add(dir) }

        fun setAppName(appName: String?) = also { appName?.let { this.appName = it } }

        fun setVersionName(versionName: String?) = also { versionName?.let { this.versionName = it } }

        fun setVersionCode(versionCode: Int?) = also { versionCode?.let { this.versionCode = it } }

        fun setSourcePath(sourcePath: String?) = also { sourcePath?.let { this.sourcePath = it } }

        fun setPackageName(packageName: String?) = also { packageName?.let { this.packageName = it } }

        fun setIcon(icon: Callable<Bitmap>?) = also { icon?.let { this.icon = it } }

        fun setIcon(iconPath: String?) = also { iconPath?.let { this.icon = Callable { BitmapFactory.decodeFile(it) } } }

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
    }

    companion object {

        private val TAG = ApkBuilder::class.java.simpleName

        const val ICON_NAME = "ic_launcher"
        const val ICON_RES_DIR = "mipmap"

    }

}