package org.autojs.autojs.apkbuilder.template

import org.autojs.autojs.core.plugin.nativealign.NativeLibraryAlignment
import java.io.File
import java.util.zip.ZipFile

internal object NativeBuildAlignment {
    data class Library(val path: String, val minLoadAlign: Long?) {
        val needsWarning: Boolean get() = path.split('/').getOrNull(1) in setOf("arm64-v8a", "x86_64") &&
            (minLoadAlign == null || minLoadAlign < 16384)
    }

    fun inspect(archive: File?): List<Library> = archive?.let {
        ZipFile(it).use { zip ->
            zip.entries().asSequence().filter { entry -> !entry.isDirectory && entry.name.startsWith("lib/") && entry.name.endsWith(".so") }
                .map { entry -> Library(entry.name, runCatching {
                    zip.getInputStream(entry).use { input -> NativeLibraryAlignment.read(input).minLoadAlign }
                }.getOrNull()) }.sortedBy(Library::path).toList()
        }
    } ?: emptyList()

    fun writeMetadata(file: File, libraries: List<Library>) {
        val data = mapOf("schemaVersion" to 1, "pageSize" to 16384,
            "strictAbis" to listOf("arm64-v8a", "x86_64"), "libraries" to libraries)
        file.writeText(com.google.gson.GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(data) + "\n")
    }
}
