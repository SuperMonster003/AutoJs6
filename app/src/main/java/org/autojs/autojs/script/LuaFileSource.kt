package org.autojs.autojs.script

import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.pio.UncheckedIOException
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.io.Reader

class LuaFileSource(val file: File) : LuaScriptSource(PFiles.getNameWithoutExtension(file.name)) {

    private var cachedScript: String? = null

    constructor(path: String) : this(File(path))

    override val script: String
        get() = cachedScript ?: PFiles.read(file, Charsets.UTF_8.name()).also { cachedScript = it }

    override val scriptReader: Reader
        get() = try {
            InputStreamReader(FileInputStream(file), Charsets.UTF_8)
        } catch (e: FileNotFoundException) {
            throw UncheckedIOException(e)
        }

    override fun toString(): String {
        return originPath ?: originUri ?: file.toString()
    }
}
