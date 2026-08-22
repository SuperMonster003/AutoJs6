package org.autojs.autojs.script

import java.util.Locale

/** Source languages admitted by the JVM source execution pipeline. */
enum class JvmSourceKind(val canonicalExtension: String) {
    JAVA("java"),
    KOTLIN("kt"),
    ;

    companion object {

        private val unsupportedExtensions = setOf("kts", "class", "jar", "dex")

        @JvmStatic
        fun fromFileName(fileNameOrPath: String?): JvmSourceKind? {
            return when (extensionOf(fileNameOrPath)?.lowercase(Locale.ROOT)) {
                JAVA.canonicalExtension -> JAVA
                KOTLIN.canonicalExtension -> KOTLIN
                else -> null
            }
        }

        @JvmStatic
        fun isUnsupportedJvmFileName(fileNameOrPath: String?): Boolean {
            return extensionOf(fileNameOrPath)?.lowercase(Locale.ROOT) in unsupportedExtensions
        }

        @JvmStatic
        fun isJvmSourceOrUnsupportedArtifact(fileNameOrPath: String?): Boolean {
            return fromFileName(fileNameOrPath) != null || isUnsupportedJvmFileName(fileNameOrPath)
        }

        /** Prevents JVM-looking input from falling back to a JavaScript entry point. */
        @JvmStatic
        fun requireNoJavaScriptFallback(fileNameOrPath: String?) {
            if (isJvmSourceOrUnsupportedArtifact(fileNameOrPath)) {
                throw UnsupportedJvmSourceException(fileNameOrPath ?: "<unknown>")
            }
        }

        /** Returns the spelling used by the file, including its original case. */
        @JvmStatic
        fun recognizedExtension(fileNameOrPath: String?): String? {
            val extension = extensionOf(fileNameOrPath) ?: return null
            return extension.takeIf {
                fromFileName(fileNameOrPath) != null ||
                    it.lowercase(Locale.ROOT) in unsupportedExtensions
            }
        }

        /** True when canonicalization would change a request into another JVM-routing class. */
        internal fun hasCanonicalJvmRoutingMismatch(
            requestedFileNameOrPath: String?,
            canonicalFileNameOrPath: String?,
        ): Boolean {
            val canonicalJvmClass = recognizedExtension(canonicalFileNameOrPath)
                ?.lowercase(Locale.ROOT)
                ?: return false
            val requestedJvmClass = recognizedExtension(requestedFileNameOrPath)
                ?.lowercase(Locale.ROOT)
            return requestedJvmClass != canonicalJvmClass
        }

        private fun extensionOf(fileNameOrPath: String?): String? {
            val fileName = fileNameOrPath
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.takeIf(String::isNotBlank)
                ?: return null
            val separator = fileName.lastIndexOf('.')
            return fileName
                .takeIf { separator >= 0 && separator < it.lastIndex }
                ?.substring(separator + 1)
        }
    }
}

class UnsupportedJvmSourceException(fileNameOrPath: String) : IllegalArgumentException(
    "Unsupported JVM source or artifact '$fileNameOrPath'; routing admits only .java and .kt source files.",
)

class UnsafeJvmSourcePathException(path: String, reason: String) : IllegalArgumentException(
    "Unsafe JVM source path '$path': $reason",
)
