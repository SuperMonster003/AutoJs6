package org.autojs.autojs.core.plugin.jvm

import org.autojs.plugin.jvmsource.api.JvmJavaSourceLayout
import org.autojs.plugin.jvmsource.api.JvmJavaSourceLayoutPolicy

internal object JvmSourceR1JavaPolicy {
    const val ENTRY_SIMPLE_NAME = JvmJavaSourceLayoutPolicy.DEFAULT_ENTRY_SIMPLE_NAME

    /**
     * Roadmap v2 M1: any well-formed `.java` file name is admitted. The compilation unit remains
     * normalized to the entry simple name (`Main.java`), while an optional package declaration is
     * reflected in the request's fully-qualified entry class name. Free file-to-class mapping is
     * deferred until the provider protocol can evolve independently (post plugin extraction).
     */
    fun requireSupportedFileName(fileName: String) {
        require(fileName.length in 6..255 && fileName.endsWith(".java", ignoreCase = true)) {
            "JVM source execution requires a .java source file"
        }
        require('/' !in fileName && '\\' !in fileName && '\u0000' !in fileName) {
            "JVM source file name must be a single safe path segment"
        }
    }

    fun inspect(source: ByteArray): JvmJavaSourceLayout =
        JvmJavaSourceLayoutPolicy.inspect(source, ENTRY_SIMPLE_NAME).layout
}
