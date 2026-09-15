package org.autojs.autojs.script

import java.io.File

/** A local single-file TypeScript source that must be compiled before Rhino execution. */
class TypeScriptFileSource @JvmOverloads constructor(
    file: File,
    name: String? = null,
) : JavaScriptFileSource(name ?: file.nameWithoutExtension, file) {

    private val hasCustomName = name != null

    var semanticFileName: String? = null

    val compilationSourceName: String
        get() = sequenceOf(
            semanticFileName,
            originPath?.let(::File)?.name,
            file.name,
        ).filterNotNull()
            .firstOrNull(JavaScriptSource::isRhinoTypeScriptFileName)
            ?: DEFAULT_SOURCE_NAME

    val compilationSourceIdentity: String
        get() = originPath?.takeIf(String::isNotBlank)
            ?: originUri?.takeIf(String::isNotBlank)
            ?: runCatching { file.canonicalPath }.getOrElse { file.absolutePath }

    override fun toString(): String {
        originPath?.let { return it }
        originUri?.let { return it }
        if (!hasCustomName) return file.toString()
        return prefix.orEmpty() + this.name + "." + compilationSourceName.substringAfterLast('.')
    }

    private companion object {
        const val DEFAULT_SOURCE_NAME = "main.ts"
    }
}
