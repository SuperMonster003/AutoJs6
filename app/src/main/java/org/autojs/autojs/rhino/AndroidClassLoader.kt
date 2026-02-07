package org.autojs.autojs.rhino

import android.os.Build
import android.util.Log
import com.android.dx.command.dexer.Main
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import dalvik.system.DexClassLoader
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import org.autojs.autojs.pio.PFiles.deleteFilesOfDir
import org.autojs.autojs.util.MD5Utils.md5
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.GeneratedClassLoader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import com.legacy.android.dx.command.dexer.Main as LegacyMain

/**
 * Created by Stardust on Apr 5, 2017.
 * Modified by SuperMonster003 as of Jul 5, 2023.
 * Transformed by SuperMonster003 on Jul 5, 2023.
 * Modified by LYS86 (https://github.com/LYS86) as of Dec 26, 2025.
 */
/**
 * Create a new instance with the given parent classloader and cache directory
 *
 * @param parent   the parent
 * @param cacheDir the cache directory
 */
class AndroidClassLoader(private val parent: ClassLoader, private val cacheDir: File) : ClassLoader(), GeneratedClassLoader {

    private val mDexClassLoaders = ConcurrentHashMap<String, DexClassLoader>()

    // Ensure per-jar conversion is thread-safe.
    // zh-CN: 确保对同一个 jar 的转换过程线程安全.
    private val conversionLocks = ConcurrentHashMap<String, Any>()

    init {
        if (cacheDir.exists()) {
            deleteFilesOfDir(cacheDir)
        } else {
            if (!cacheDir.mkdirs()) {
                Log.e(TAG, "dir.mkdirs() failed")
            }
        }
    }

    /**
     * Might be thrown in any Rhino method that loads bytecode if the loading failed
     */
    class FatalLoadingException internal constructor(t: Throwable?) : RuntimeException("Failed to define class", t)

    @Throws(FileNotFoundException::class)
    fun loadDex(file: File): DexClassLoader {
        val originalPath = file.path
        Log.d(TAG, "loadDex: file = $originalPath")

        if (!file.exists() || !file.canRead()) {
            throw FileNotFoundException(str(R.string.file_not_exist_or_readable_with_path, originalPath))
        }

        // @Hint by SuperMonster003 on Nov 30, 2023.
        //  ! Try to avoid the exception which looks like below (API Level 34+):
        //  # java.lang.SecurityException: Writable dex file '/data/user/0/org.autojs.autojs6/cache/classes/xxx.jar' is not allowed.
        //  ! zh-CN: 尝试避免如下异常 (API 级别 34+):
        //  # java.lang.SecurityException: 不允许可写的 dex 文件 '/data/user/0/org.autojs.autojs6/cache/classes/xxx.jar'.
        //  !
        // @Hint by SuperMonster003 on Jan 13, 2025.
        //  ! Copy the dex file to the cache path and set it as read-only.
        //  ! zh-CN: 将 dex 文件复制到缓存路径并设置只读.
        //  # file.setReadOnly()
        val safeDexFile = File(cacheDir, "safe_${file.name}")
        try {
            if (!safeDexFile.exists() || md5(file) != md5(safeDexFile)) {
                file.copyTo(safeDexFile, overwrite = true)
            }
            if (!safeDexFile.setReadOnly()) {
                Log.e(TAG, "Failed to set file as read-only: ${safeDexFile.path}")
            }
        } catch (e: IOException) {
            throw FatalLoadingException(e)
        }

        return DexClassLoader(safeDexFile.path, cacheDir.path, null, parent).also {
            mDexClassLoaders[safeDexFile.path] = it
        }
    }

    @Throws(IOException::class, FileNotFoundException::class)
    fun loadJar(jar: File): DexClassLoader {
        Log.d(TAG, "loadJar: jar = ${jar.path}")
        if (!jar.exists() || !jar.canRead()) {
            throw FileNotFoundException(str(R.string.file_not_exist_or_readable_with_path, jar.path))
        }

        val key = generateArtifactsCacheName(jar)
        val lock = conversionLocks.getOrPut(key) { Any() }

        return synchronized(lock) {
            runCatching {
                jarToDexDx(jar)
            }.recoverCatching { eDx ->
                Log.e(TAG, "Failed: jarToDexDx", eDx)
                jarToDexR8(jar)
            }.getOrElse { eR8 ->
                Log.e(TAG, "Failed: jarToDexR8", eR8)
                throw eR8
            }
        }
    }

    @Throws(IOException::class, FileNotFoundException::class)
    private fun jarToDexDx(jar: File): DexClassLoader {
        val cacheName = generateArtifactsCacheName(jar)
        val cacheFile = File(cacheDir, "$cacheName.jar")
        if (cacheFile.exists()) {
            return loadDex(cacheFile)
        }
        try {
            val classFile = generateTempFile(jar.path, false)
            val zipFile = ZipFile(classFile)
            val jarFile = ZipFile(jar)
            for (header in jarFile.fileHeaders) {
                if (!header.isDirectory) {
                    zipFile.addStream(jarFile.getInputStream(header), ZipParameters().apply {
                        fileNameInZip = header.fileName
                    })
                }
            }
            val classLoader = dexJar(classFile, cacheFile)
            if (!classFile.delete()) {
                Log.e(TAG, "classFile.delete() failed")
            }
            return classLoader
        } catch (e: ZipException) {
            throw IOException(e)
        }
    }

    /**
     * Try to load a class. This will search all defined classes, all loaded jars and the parent class loader.
     *
     * @param name    the name of the class to load
     * @param resolve ignored
     * @return the class
     * @throws ClassNotFoundException if the class could not be found in any of the locations
     */
    @Throws(ClassNotFoundException::class)
    public override fun loadClass(name: String, resolve: Boolean): Class<*> {
        findLoadedClass(name)?.let { return it }
        for (dex in mDexClassLoaders.values) try {
            dex.loadClass(name)?.let { return it }
        } catch (e: Exception) {
            e.printStackTrace()
            continue
        }
        return parent.loadClass(name)
    }

    /**
     * Does nothing
     *
     * @param aClass ignored
     */
    override fun linkClass(aClass: Class<*>?) {
        // doesn't make sense on android
    }

    /**
     * {@inheritDoc}
     */
    override fun defineClass(name: String, data: ByteArray): Class<*> {
        Log.d(TAG, "defineClass: name = $name data.length = ${data.size}")
        var classFile: File? = null
        return try {
            classFile = generateTempFile(name, false)
            ZipFile(classFile).addStream(ByteArrayInputStream(data), ZipParameters().apply {
                fileNameInZip = name.replace('.', '/') + ".class"
            })
            dexJar(classFile, null).loadClass(name)
        } catch (e: IOException) {
            throw FatalLoadingException(e)
        } catch (e: ClassNotFoundException) {
            throw FatalLoadingException(e)
        } finally {
            when {
                // Treat as a temporary file that has been generated.
                // zh-CN: 视为临时文件已生成.
                classFile != null -> {
                    if (!classFile.delete()) {
                        Log.e(TAG, "classFile.delete() failed")
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun dexJar(classFile: File, dexFile: File?): DexClassLoader {
        val niceDexFile = dexFile ?: generateTempFile("dex-" + classFile.path, true)
        when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            true -> Main.Arguments().apply {
                fileNames = arrayOf(classFile.path)
                outName = niceDexFile.path
                jarOutput = true
                Main.run(this)
            }
            else -> LegacyMain.Arguments().apply {
                fileNames = arrayOf(classFile.path)
                outName = niceDexFile.path
                jarOutput = true
                LegacyMain.run(this)
            }
        }
        val loader = loadDex(niceDexFile)
        when (dexFile) {
            // Treat as a temporary file that has been generated.
            // zh-CN: 视为临时文件已生成.
            null -> {
                if (!niceDexFile.delete()) {
                    Log.e(TAG, "dexFile.delete() failed")
                }
            }
        }
        return loader
    }

    @Throws(IOException::class)
    private fun generateTempFile(name: String, create: Boolean): File {
        val file = File(cacheDir, "${(name.hashCode() + System.currentTimeMillis())}.jar")
        if (create) {
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    Log.e(TAG, "file.createNewFile() failed")
                }
            }
        } else {
            if (file.exists() && !file.delete()) {
                Log.e(TAG, "file.delete() failed")
            }
        }
        return file
    }

    private fun generateArtifactsCacheName(jar: File) = md5("${jar.path}_${jar.lastModified()}")

    @Throws(IOException::class, FileNotFoundException::class)
    private fun jarToDexR8(jar: File): DexClassLoader {
        val cacheName = generateArtifactsCacheName(jar)

        val dexFile = File(cacheDir, "$cacheName.dex")
        val dexJarFile = File(cacheDir, "$cacheName.jar")

        if (dexJarFile.exists()) {
            return loadDex(dexJarFile)
        }
        if (dexFile.exists()) {
            return loadDex(dexFile)
        }

        val tmpOut = File(cacheDir, "${cacheName}_tmp_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}")
        if (!tmpOut.mkdirs()) {
            throw IOException("Failed to create temp output directory: ${tmpOut.path}")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val cmd = D8Command.builder()
                    .addProgramFiles(jar.toPath())
                    .setOutput(tmpOut.toPath(), OutputMode.DexIndexed)
                    .setMode(CompilationMode.RELEASE)
                    .build()
                D8.run(cmd)
            } else {
                val args = arrayOf(
                    "--output", tmpOut.absolutePath,
                    "--release",
                    jar.absolutePath
                )
                D8.main(args)
            }

            val dexFiles = tmpOut.listFiles { _, name ->
                Regex("classes\\d*\\.dex").matches(name)
            }?.sortedBy { it.name } ?: emptyList()

            if (dexFiles.isEmpty()) {
                throw IOException("D8 did not produce any classes*.dex")
            }

            return if (dexFiles.size == 1) {
                val classesDex = dexFiles.first()
                if (!classesDex.renameTo(dexFile)) {
                    dexFile.outputStream().use { out ->
                        classesDex.inputStream().use { it.copyTo(out) }
                    }
                }
                loadDex(dexFile)
            } else {
                // Multi-dex output: pack all classes*.dex into a single jar for DexClassLoader.
                // zh-CN: 多 dex 输出: 将所有 classes*.dex 打包到单个 jar 文件中供 DexClassLoader 使用.
                val zip = ZipFile(dexJarFile)
                dexFiles.forEach { f ->
                    zip.addStream(f.inputStream(), ZipParameters().apply {
                        fileNameInZip = f.name
                    })
                }
                loadDex(dexJarFile)
            }
        } finally {
            tmpOut.deleteRecursively()
        }
    }

    companion object {

        private val TAG = AndroidClassLoader::class.java.simpleName

    }

}