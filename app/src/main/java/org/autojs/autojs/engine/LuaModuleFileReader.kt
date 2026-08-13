package org.autojs.autojs.engine

import android.system.Os
import android.system.OsConstants
import org.autojs.plugin.lua.runtime.api.LuaContractException
import org.autojs.plugin.lua.runtime.api.LuaContractViolation
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/** Injectable seam keeps host JVM tests Android-free while production binds checks to an fd. */
internal fun interface LuaModuleFileReader {
    fun read(file: File, moduleName: String, maximumBytes: Int): ByteArray
}

internal object LuaModuleFileReaders {
    fun runtime(vmName: String = System.getProperty("java.vm.name").orEmpty()): LuaModuleFileReader =
        if (vmName == ANDROID_VM_NAME) AndroidLuaModuleFileReader else PortableLuaModuleFileReader

    private const val ANDROID_VM_NAME = "Dalvik"
}

private object PortableLuaModuleFileReader : LuaModuleFileReader {
    override fun read(file: File, moduleName: String, maximumBytes: Int): ByteArray =
        file.inputStream().buffered().use { input ->
            readBounded(input, file.length(), moduleName, maximumBytes)
        }
}

private object AndroidLuaModuleFileReader : LuaModuleFileReader {
    override fun read(file: File, moduleName: String, maximumBytes: Int): ByteArray = try {
        val before = Os.lstat(file.path)
        if (!OsConstants.S_ISREG(before.st_mode)) {
            invalid(moduleName, "is not a regular file")
        }
        val descriptor = Os.open(
            file.path,
            OsConstants.O_RDONLY or O_CLOEXEC_LINUX or OsConstants.O_NOFOLLOW,
            0,
        )
        val input = try {
            FileInputStream(descriptor)
        } catch (error: Exception) {
            runCatching { Os.close(descriptor) }
            throw error
        }
        input.buffered().use { stream ->
            val opened = Os.fstat(input.fd)
            if (
                !OsConstants.S_ISREG(opened.st_mode) ||
                opened.st_dev != before.st_dev ||
                opened.st_ino != before.st_ino
            ) {
                invalid(moduleName, "changed while it was being opened")
            }
            if (opened.st_size > maximumBytes.toLong()) {
                invalid(moduleName, "exceeds $maximumBytes bytes")
            }
            val bytes = readBounded(stream, opened.st_size, moduleName, maximumBytes)
            val completed = Os.fstat(input.fd)
            if (
                !OsConstants.S_ISREG(completed.st_mode) ||
                completed.st_dev != opened.st_dev ||
                completed.st_ino != opened.st_ino ||
                completed.st_size != opened.st_size
            ) {
                invalid(moduleName, "changed while it was being read")
            }
            bytes
        }
    } catch (error: LuaContractException) {
        throw error
    } catch (error: Exception) {
        throw LuaContractException(
            LuaContractViolation.INVALID_VALUE,
            "Lua module '$moduleName' could not be safely read",
            error,
        )
    }
}

private fun readBounded(
    input: InputStream,
    expectedBytes: Long,
    moduleName: String,
    maximumBytes: Int,
): ByteArray {
    val output = ByteArrayOutputStream(
        expectedBytes.coerceIn(0L, maximumBytes.toLong()).toInt(),
    )
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        if (output.size() > maximumBytes - count) {
            invalid(moduleName, "exceeds $maximumBytes bytes")
        }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun invalid(moduleName: String, reason: String): Nothing =
    throw LuaContractException(
        LuaContractViolation.INVALID_VALUE,
        "Lua module '$moduleName' $reason",
    )

// Linux UAPI O_CLOEXEC. Android did not expose OsConstants.O_CLOEXEC until API 27,
// while AutoJs6 still supports API 24; passing the stable kernel flag keeps the fd private.
private const val O_CLOEXEC_LINUX = 0x80000
