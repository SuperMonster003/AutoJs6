package org.autojs.autojs.core.plugin.jvm

import org.autojs.plugin.jvmsource.api.JvmJavaSourceLayout
import org.autojs.plugin.jvmsource.api.JvmJavaSourceLayoutPolicy

internal object JvmSourceR1JavaPolicy {
    const val ENTRY_SIMPLE_NAME = JvmJavaSourceLayoutPolicy.DEFAULT_ENTRY_SIMPLE_NAME

    /**
     * Any well-formed physical `.java` file name is admitted. The logical compilation-unit name is
     * derived from the explicitly selected entry simple name (`Main` by default), while an optional
     * package declaration supplies the request's fully-qualified prefix.
     */
    fun requireSupportedFileName(fileName: String) {
        require(fileName.length in 6..255 && fileName.endsWith(".java", ignoreCase = true)) {
            "JVM source execution requires a .java source file"
        }
        require('/' !in fileName && '\\' !in fileName && '\u0000' !in fileName) {
            "JVM source file name must be a single safe path segment"
        }
    }

    fun requireEntrySimpleName(entrySimpleName: String): String =
        JvmJavaSourceLayoutPolicy.requireEntrySimpleName(entrySimpleName)

    fun inspect(
        source: ByteArray,
        entrySimpleName: String = ENTRY_SIMPLE_NAME,
    ): JvmJavaSourceLayout = JvmJavaSourceLayoutPolicy.inspect(source, entrySimpleName).layout
}
