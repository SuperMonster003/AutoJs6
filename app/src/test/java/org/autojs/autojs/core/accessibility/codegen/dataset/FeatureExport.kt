package org.autojs.autojs.core.accessibility.codegen.dataset

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import java.io.File

/** Shared wire encoding for offline JVM feature producers; Python never recomputes vectors. */
internal object FeatureExport {
    fun schemaJson() = JsonObject().apply {
        addProperty("format", 1)
        addProperty("featureSchemaVersion", FeatureSchema.featureSchemaVersion)
        add("names", JsonArray().apply { FeatureSchema.names.forEach(::add) })
        addProperty("namesHash", FeatureSchema.namesHash)
    }

    fun csvRow(values: List<String>): String = values.joinToString(",") { value ->
        if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value
    } + "\n"

    fun requirePrivate(file: File, privateRoot: File? = System.getenv("CODEGEN_PRIVATE_DIR")?.let(::File)) {
        require(privateRoot != null && privateRoot.isAbsolute) { "Set CODEGEN_PRIVATE_DIR for real application data" }
        val root = privateRoot.canonicalFile
        val path = file.canonicalFile
        require(path.toPath().startsWith(root.toPath())) { "Real application data must stay under CODEGEN_PRIVATE_DIR" }
        generateSequence(path) { it.parentFile }.forEach { directory ->
            require(!File(directory, ".git").exists()) { "Real application data must stay outside Git worktrees" }
        }
    }
}
