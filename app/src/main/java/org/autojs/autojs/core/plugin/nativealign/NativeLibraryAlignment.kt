package org.autojs.autojs.core.plugin.nativealign

import java.io.File
import java.io.InputStream

/** Shared policy for plugin staging and APK build diagnostics; has no Android dependency. */
object NativeLibraryAlignment {
    fun read(input: InputStream): ElfLoadAlignment.Result {
        val prefix = ByteArray(ElfLoadAlignment.PREFIX_SIZE)
        var length = 0
        while (length < prefix.size) {
            val count = input.read(prefix, length, prefix.size - length)
            if (count < 0) break
            if (count == 0) {
                val value = input.read()
                if (value < 0) break
                prefix[length++] = value.toByte()
            } else length += count
        }
        return ElfLoadAlignment.parse(prefix.copyOf(length))
    }

    fun isCompatible(minAlign: Long, pageSize: Long): Boolean = minAlign >= pageSize

    fun requireCompatible(file: File, pageSize: Long): Long {
        val elf = file.inputStream().use(::read)
        if (!isCompatible(elf.minLoadAlign, pageSize) || elf.loads.any { it.offset % pageSize != it.address % pageSize }) {
            throw NativePageAlignmentException(file.name, elf.minLoadAlign, pageSize)
        }
        return elf.minLoadAlign
    }
}

class NativePageAlignmentException(val libraryName: String, val minLoadAlign: Long, val pageSize: Long) :
    IllegalArgumentException("$libraryName: PT_LOAD alignment $minLoadAlign is incompatible with page size $pageSize")
