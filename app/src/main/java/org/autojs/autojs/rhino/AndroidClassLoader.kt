package org.autojs.autojs.rhino

import android.os.Build
import android.util.Log
import com.android.dx.command.dexer.Main
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
import com.legacy.android.dx.command.dexer.Main as LegacyMain

/**
 * Created by Stardust on Apr 5, 2017.
 * Modified by SuperMonster003 as of Jul 5, 2023.
 * Transformed by SuperMonster003 on Jul 5, 2023.
 */
/**
 * Create a new instance with the given parent classloader and cache directory
 *
 * @param parent   the parent
 * @param cacheDir the cache directory
 */
class AndroidClassLoader(private val parent: ClassLoader, private val cacheDir: File) : ClassLoader(), GeneratedClassLoader {

    private val mDexClassLoaders = HashMap<String, DexClassLoader>()

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
            throw FileNotFoundException(str(R.string.file_not_exist_or_readable, originalPath))
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

    @Throws(IOException::class)
    fun loadJar(file: File): DexClassLoader {
        val path = file.path
        Log.d(TAG, "loadJar: jar = $path")
        if (!file.exists() || !file.canRead()) {
            throw FileNotFoundException(str(R.string.file_not_exist_or_readable, path))
        }
        val dexFile = File(cacheDir, generateDexFileName(file))
        if (dexFile.exists()) {
            return loadDex(dexFile)
        }
        try {
            val classFile = generateTempFile(path, false)
            val zipFile = ZipFile(classFile)
            val jarFile = ZipFile(file)
            for (header in jarFile.fileHeaders) {
                if (!header.isDirectory) {
                    zipFile.addStream(jarFile.getInputStream(header), ZipParameters().apply {
                        fileNameInZip = header.fileName
                    })
                }
            }
            val classLoader = dexJar(classFile, dexFile)
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
            if (classFile != null) {
                if (!classFile.delete()) {
                    Log.e(TAG, "classFile.delete() failed")
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
        if (dexFile == null) /* is temporary file generated */ {
            if (!niceDexFile.delete()) {
                Log.e(TAG, "dexFile.delete() failed")
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

    private fun generateDexFileName(jar: File) = md5("${jar.path}_${jar.lastModified()}")

    companion object {

        private val TAG = AndroidClassLoader::class.java.simpleName

    }

}