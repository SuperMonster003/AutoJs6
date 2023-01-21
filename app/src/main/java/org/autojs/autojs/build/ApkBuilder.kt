package org.autojs.autojs.build

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.stardust.autojs.apkbuilder.ApkPackager
import com.stardust.autojs.apkbuilder.ManifestEditor
import com.stardust.autojs.apkbuilder.util.StreamUtils
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

    interface ProgressCallback {
        fun onPrepare(builder: ApkBuilder?)
        fun onBuild(builder: ApkBuilder?)
        fun onSign(builder: ApkBuilder?)
        fun onClean(builder: ApkBuilder?)
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

    private var mProgressCallback: ProgressCallback? = null
    private val mApkPackager: ApkPackager
    private var mArscPackageName: String? = null
    private var mManifestEditor: ManifestEditor? = null
    private var mInitVector: String? = null
    private var mKey: String? = null

    private lateinit var mAppConfig: AppConfig

    init {
        mApkPackager = ApkPackager(apkInputStream, mWorkspacePath)
        PFiles.ensureDir(mOutApkFile.path)
    }

    fun setProgressCallback(callback: ProgressCallback?) = also { mProgressCallback = callback }

    @Throws(IOException::class)
    fun prepare() = also {
        mProgressCallback?.run { GlobalAppContext.post { onPrepare(this@ApkBuilder) } }
        File(mWorkspacePath).mkdirs()
        mApkPackager.unzip()
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
    fun editManifest(): ManifestEditor = ManifestEditorWithAuthorities(FileInputStream(manifestFile)).also { mManifestEditor = it }

    protected val manifestFile: File
        get() = File(mWorkspacePath, "AndroidManifest.xml")

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
        mProgressCallback?.run { GlobalAppContext.post { onBuild(this@ApkBuilder) } }
        mAppConfig.icon?.run {
            try {
                call()?.compress(
                    Bitmap.CompressFormat.PNG, 100,
                    FileOutputStream(File(mWorkspacePath, "res/mipmap/ic_launcher.png"))
                )
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
        mManifestEditor?.apply { commit() }?.run { writeTo(FileOutputStream(manifestFile)) }
        mArscPackageName?.let { buildArsc() }
    }

    @Throws(Exception::class)
    fun sign() = also {
        mProgressCallback?.run { GlobalAppContext.post { onSign(this@ApkBuilder) } }
        val fos = FileOutputStream(mOutApkFile)
        TinySign.sign(File(mWorkspacePath), fos)
        fos.close()
    }

    fun cleanWorkspace() = also {
        mProgressCallback?.run { GlobalAppContext.post { onClean(this@ApkBuilder) } }
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

}