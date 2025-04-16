package org.autojs.autojs.runtime.api

import android.content.Context
import org.autojs.autojs.pio.PFileInterface
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.pio.PFiles.getElegantPath
import org.autojs.autojs.pio.PFiles.read
import org.autojs.autojs.pio.UncheckedIOException
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.tool.Func1
import org.autojs.autojs.util.EnvironmentUtils.externalStoragePath
import org.autojs.autojs6.R
import java.io.File
import java.io.File.separator
import java.io.IOException
import java.lang.IllegalArgumentException

/**
 * Created by Stardust on Jan 23, 2018.
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on Apr 15, 2025.
 */
class Files(private val scriptRuntime: ScriptRuntime) {

    val context: Context
        get() = scriptRuntime.uiHandler.applicationContext

    val sdcardPath: String
        get() = externalStoragePath

    // FIXME by Stardust on Oct 16, 2018.
    //  ! Is not correct in sub-directory?
    //  ! zh-CN (translated by SuperMonster003 on Jul 29, 2024):
    //  ! 子目录的处理不够准确吗?
    fun path(relativePath: String?): String? {
        relativePath ?: return null
        val cwd = cwd() ?: return null
        if (relativePath.startsWith(separator)) {
            return relativePath
        }
        var file = File(cwd)
        relativePath.split(separator).forEach { path ->
            when {
                path == ".." -> {
                    file = file.getParentFile() ?: return null
                }
                path != "." && path.isNotBlank() -> {
                    file = File(file, path)
                }
            }
        }
        return when (relativePath.endsWith(separator)) {
            true -> file.path + separator
            else -> file.path
        }
    }

    @Throws(IllegalArgumentException::class)
    fun nonNullPath(relativePath: String): String {
        return path(relativePath) ?: throw IllegalArgumentException(context.getString(R.string.error_resolved_path_for_a_relative_path_cannot_be_null))
    }

    fun cwd(): String? = scriptRuntime.engines.myEngine().cwd()

    @JvmOverloads
    fun open(path: String? = null, mode: String? = null, encoding: String? = null, bufferSize: Int? = null): PFileInterface {
        return PFiles.open(path(path), mode, encoding, bufferSize)
    }

    fun create(path: String?): Boolean {
        return PFiles.create(path(path) ?: return false)
    }

    fun createIfNotExists(path: String?): Boolean {
        return PFiles.createIfNotExists(path(path) ?: return false)
    }

    fun createWithDirs(path: String?): Boolean {
        return PFiles.createWithDirs(path(path) ?: return false)
    }

    fun exists(path: String?): Boolean {
        return PFiles.exists(path(path))
    }

    fun ensureDir(path: String?): Boolean {
        return PFiles.ensureDir(path(path) ?: return false)
    }

    @JvmOverloads
    fun read(path: String?, encoding: String? = PFiles.DEFAULT_ENCODING): String {
        return PFiles.read(path(path), encoding)
    }

    @JvmOverloads
    fun readAssets(fileName: String?, encoding: String? = PFiles.DEFAULT_ENCODING): String {
        val niceFileName = ensureFileNameNotNull(fileName, ::readAssets.name)
        try {
            return read(context.assets.open(niceFileName), encoding)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    fun readBytes(path: String?): ByteArray {
        return PFiles.readBytes(path(path))
    }

    @JvmOverloads
    fun write(path: String?, text: String, encoding: String? = PFiles.DEFAULT_ENCODING) {
        PFiles.write(path(path), text, encoding)
    }

    @JvmOverloads
    fun append(path: String?, text: String, encoding: String? = PFiles.DEFAULT_ENCODING) {
        PFiles.append(ensurePathNotNull(path, ::append.name), text, encoding)
    }

    fun appendBytes(path: String?, bytes: ByteArray?) {
        PFiles.appendBytes(ensurePathNotNull(path, ::appendBytes.name), bytes)
    }

    fun writeBytes(path: String?, bytes: ByteArray?) {
        PFiles.writeBytes(ensurePathNotNull(path, ::writeBytes.name), bytes)
    }

    fun copy(pathFrom: String?, pathTo: String?): Boolean = PFiles.copy(
        ensurePathNotNull(pathFrom, ::copy.name, "pathFrom"),
        ensurePathNotNull(pathTo, ::copy.name, "pathTo"),
    )

    fun renameWithoutExtension(path: String?, newName: String): Boolean {
        return PFiles.renameWithoutExtension(ensurePathNotNull(path, ::renameWithoutExtension.name), newName)
    }

    fun rename(path: String?, newName: String): Boolean {
        return PFiles.rename(ensurePathNotNull(path, ::rename.name), newName)
    }

    fun move(path: String?, newPath: String): Boolean {
        return PFiles.move(ensurePathNotNull(path, ::move.name), newPath)
    }

    fun getExtension(fileName: String?): String {
        return PFiles.getExtension(ensureFileNameNotNull(fileName, ::getExtension.name))
    }

    fun getName(filePath: String?): String {
        return PFiles.getName(ensurePathNotNull(
            pathToCheck = filePath,
            funcName = ::getName.name,
            pathArgName = "filePath",
            shouldWrapWithPathMethod = false,
        ))
    }

    fun getNameWithoutExtension(filePath: String?): String {
        return PFiles.getNameWithoutExtension(ensurePathNotNull(
            pathToCheck = filePath,
            funcName = ::getNameWithoutExtension.name,
            pathArgName = "filePath",
            shouldWrapWithPathMethod = false,
        ))
    }

    fun remove(path: String?): Boolean {
        return PFiles.remove(path(path))
    }

    fun removeDir(path: String?): Boolean {
        return PFiles.removeDir(path(path))
    }

    fun listDir(path: String?): Array<String> {
        return PFiles.listDir(path(path))
    }

    fun listDir(path: String?, filter: Func1<String, Boolean?>): Array<String> {
        return PFiles.listDir(path(path), filter)
    }

    fun isFile(path: String?): Boolean {
        return PFiles.isFile(path(path))
    }

    fun isDir(path: String?): Boolean {
        return PFiles.isDir(path(path))
    }

    fun isEmptyDir(path: String?): Boolean {
        return PFiles.isEmptyDir(path(path))
    }

    fun getHumanReadableSize(bytes: Long): String {
        return PFiles.getHumanReadableSize(bytes)
    }

    fun getSimplifiedPath(path: String?): String {
        return getElegantPath(ensurePathNotNull(path, ::getSimplifiedPath.name, shouldWrapWithPathMethod = false))
    }

    private fun ensureFileNameNotNull(fileNameToCheck: String?, funcName: String, fileNameArgName: String = "fileName"): String {
        fileNameToCheck ?: throw WrappedIllegalArgumentException(
            context.getString(
                R.string.error_argument_name_for_class_name_and_member_func_name_cannot_be_nullish,
                fileNameArgName, Files::class.java.simpleName, funcName,
            )
        )
        return fileNameToCheck
    }

    private fun ensurePathNotNull(pathToCheck: String?, funcName: String, pathArgName: String = "path", shouldWrapWithPathMethod: Boolean = true): String {
        val path = if (shouldWrapWithPathMethod) path(pathToCheck) else pathToCheck
        path ?: throw WrappedIllegalArgumentException(
            context.getString(
                R.string.error_argument_name_for_class_name_and_member_func_name_cannot_be_nullish,
                pathArgName, Files::class.java.simpleName, funcName,
            )
        )
        return path
    }

    companion object {
        fun join(parent: String?, vararg child: String?): String {
            return PFiles.join(parent, *child)
        }
    }
}
