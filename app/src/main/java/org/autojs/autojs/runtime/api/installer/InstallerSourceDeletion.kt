package org.autojs.autojs.runtime.api.installer

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.installer.InstallerJson
import org.autojs.plugin.installer.api.InstallerContract

/** Only successful items and explicitly requested deletion can reach the owner's delete method. */
internal object InstallerSourceDeletion {

    interface Source {
        val item: Int
        val isOwnedFile: Boolean
        /** Captured when opening the source; aliases of the same owned path share this key. */
        val ownershipKey: String?
        fun deleteIfUnchanged(): Boolean
    }

    fun apply(completion: InstallerJson.Completion, sources: List<Source>, requested: Boolean): InstallerJson.Completion {
        if (!requested) return completion
        val retainedKeys = sources.filter { completion.results.getOrNull(it.item)?.ok != true }
            .mapNotNull { it.ownershipKey }.toSet()
        val deletedKeys = mutableMapOf<String, Boolean>()
        val results = completion.results.mapIndexed { index, result ->
            val itemSources = sources.filter { it.item == index }
            val notes = result.notes.toMutableList()
            var deleted = result.ok && itemSources.isNotEmpty()
            if (result.ok) {
                var contentUri = false
                var retainedFile = false
                var sharedWithFailure = false
                itemSources.forEach { source ->
                    if (!source.isOwnedFile) {
                        contentUri = true
                        deleted = false
                    } else if (source.ownershipKey != null && source.ownershipKey in retainedKeys) {
                        sharedWithFailure = true
                        deleted = false
                    } else {
                        val key = source.ownershipKey
                        val removed = if (key == null) runCatching { source.deleteIfUnchanged() }.getOrDefault(false)
                        else deletedKeys.getOrPut(key) { runCatching { source.deleteIfUnchanged() }.getOrDefault(false) }
                        if (!removed) {
                            retainedFile = true
                            deleted = false
                        }
                    }
                }
                if (contentUri) notes += "Content URI sources are retained; only their owner can delete them."
                if (sharedWithFailure) notes += "Source files shared with unsuccessful or unprocessed items were retained."
                if (retainedFile) notes += "Some source files could not be deleted safely and were retained."
            }
            val enriched = JsonParser.parseString(result.json).asJsonObject.apply {
                addProperty(InstallerContract.FIELD_SOURCE_DELETED, deleted)
                if (notes.isNotEmpty()) add(InstallerContract.FIELD_NOTES, JsonArray().apply { notes.distinct().forEach(::add) })
            }.toString()
            // Host cleanup metadata must not turn a valid near-limit success document into an
            // oversized wire document. ScriptValues overlays these typed fields after parsing
            // the original JSON, so all plugin fields and host notes remain available to JS.
            result.copy(sourceDeleted = deleted, notes = notes.distinct(), json = withinBudget(enriched, result.json))
        }
        val root = JsonParser.parseString(completion.json).asJsonObject
        val json = if (root.has(InstallerContract.FIELD_RESULTS)) {
            root.add(InstallerContract.FIELD_RESULTS, JsonArray().apply {
                results.forEach { add(JsonParser.parseString(it.json)) }
            })
            root.toString()
        } else results.single().json
        return InstallerJson.Completion(results, withinBudget(json, completion.json))
    }

    private fun withinBudget(enriched: String, original: String): String =
        if (enriched.toByteArray(Charsets.UTF_8).size <= InstallerContract.MAX_JSON_BYTES) enriched else original
}
