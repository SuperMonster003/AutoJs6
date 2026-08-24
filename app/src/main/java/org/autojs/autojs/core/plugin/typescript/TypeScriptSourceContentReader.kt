package org.autojs.autojs.core.plugin.typescript

import java.io.File

/** Supplies one bounded source snapshot without granting the compiler plugin path access. */
internal fun interface TypeScriptSourceContentReader {

    fun read(file: File, maximumBytes: Long): ByteArray
}

internal object PlaintextTypeScriptSourceContentReader : TypeScriptSourceContentReader {

    override fun read(file: File, maximumBytes: Long): ByteArray {
        require(file.isFile && file.length() in 0..maximumBytes) {
            "TypeScript source exceeds its byte limit: ${file.path}"
        }
        return file.readBytes().also { bytes ->
            require(bytes.size.toLong() <= maximumBytes) {
                "TypeScript source exceeds its byte limit: ${file.path}"
            }
        }
    }
}
