package org.autojs.autojs.project

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import java.io.File

/** Declarative script registration. Only the parser's validated snapshots may be executed. */
data class AgentManifest(
    // Keep absence distinct from an invalid explicit empty ID when ProjectConfig is saved.
    @SerializedName("id") val id: String? = null,
    @SerializedName("description") val description: String = "",
    @SerializedName("parameters") val parameters: JsonObject = emptyParameters(),
    @SerializedName("result") val result: JsonObject? = null,
    @SerializedName("risk") val risk: String = "normal",
    @SerializedName("confirm") val confirm: String = "never",
    @SerializedName("timeoutMs") val timeoutMs: Long = 60_000L,
    @SerializedName("examples") val examples: List<String> = emptyList(),
    @SerializedName("tags") val tags: List<String> = emptyList(),
) {
    fun toJsonObject(): JsonObject = JsonObject().apply {
        id?.let { addProperty("id", it) }
        addProperty("description", description)
        add("parameters", parameters.deepCopy())
        result?.let { add("result", it.deepCopy()) }
        addProperty("risk", risk)
        addProperty("confirm", confirm)
        addProperty("timeoutMs", timeoutMs)
        add("examples", JsonArray().apply { examples.forEach { add(it) } })
        add("tags", JsonArray().apply { tags.forEach { add(it) } })
    }

    companion object {
        @JvmStatic
        fun emptyParameters() = JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject())
            add("required", JsonArray())
            addProperty("additionalProperties", false)
        }
    }
}

/** Public catalog record; path is the canonical project directory or standalone script file. */
data class AgentScriptEntry(
    val path: String,
    val kind: String,
    val manifest: AgentManifest,
    val updatedAt: Long,
) {
    val id: String get() = requireNotNull(manifest.id) { "A catalog entry requires a normalized manifest ID." }
    val description get() = manifest.description
    val parameters get() = manifest.parameters.deepCopy()
    val result get() = manifest.result?.deepCopy()
    val risk get() = manifest.risk
    val confirm get() = manifest.confirm
    val timeoutMs: Long get() = manifest.timeoutMs
    val examples get() = manifest.examples
    val tags get() = manifest.tags

    fun toJsonObject(): JsonObject = manifest.toJsonObject().apply {
        addProperty("path", path)
        addProperty("kind", kind)
        addProperty("updatedAt", updatedAt)
    }
}

/** Revalidated launch, retaining project-directory identity for the project launch factory. */
data class AgentScriptInvocation(
    val entry: AgentScriptEntry,
    val scriptFile: File,
    val launchPath: String,
    val arguments: JsonObject,
)

/** Diagnostics intentionally contain neither filesystem paths nor manifest/argument contents. */
class AgentManifestException(
    val code: String,
    message: String,
) : IllegalArgumentException(message)
