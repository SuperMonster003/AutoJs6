package org.autojs.autojs.script

import org.autojs.autojs.pio.PFiles
import java.io.File

/** A local `.py` file. The runtime receives its bytes through the plugin transport in R3-P2. */
open class PythonFileSource internal constructor(
    val file: File,
    internal val semanticFile: File,
) : PythonScriptSource(PFiles.getNameWithoutExtension(file.name)) {

    constructor(file: File) : this(file, file)

    constructor(path: String) : this(File(path))

    override fun toString(): String {
        return originPath ?: originUri ?: file.toString()
    }
}
