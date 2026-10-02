package org.autojs.autojs.core.plugin.installer

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.autojs.plugin.installer.api.InstallerContract

/** Only successful items and explicitly requested deletion can reach the owner's delete method. */
internal object InstallerSourceDeletion {

    interface Source {
        val item: Int
        val isOwnedFile: Boolean
        /** Captured when opening the source; aliases of the same owned path share this key. */
        val ownershipKey: String?
        /** Equality proves an underlying regular-file alias; separate from deletion path caching. */
        val retentionKey: String? get() = ownershipKey
        fun deleteIfUnchanged(): Boolean
    }

    fun apply(completion: InstallerJson.Completion, sources: List<Source>, requested: Boolean,
        explicitRequest: Boolean? = null): InstallerJson.Completion {
        if (!completion.sourceProfilesEnabled && !requested) return completion
        val decisions = completion.results.map { result ->
            result.ok && if (completion.sourceProfilesEnabled) explicitRequest != false && result.sourceDeleteRequested == true else requested
        }
        // Another item can retain an alias even after a successful install. A profile result
        // cannot turn one item's explicit/effective false into permission to delete its source.
        val retained = sources.filter { decisions.getOrNull(it.item) != true || !it.isOwnedFile }
        val retainedKeys = retained.mapNotNull { it.ownershipKey }.toSet()
        val retainedIdentities = retained.mapNotNull { it.retentionKey }.toSet()
        val deletedKeys = mutableMapOf<String, Boolean>()
        val results = completion.results.mapIndexed { index, result ->
            val itemSources = sources.filter { it.item == index }
            val notes = result.notes.toMutableList()
            val shouldDelete = decisions[index]
            var deleted = shouldDelete && itemSources.isNotEmpty()
            val missingDecision = completion.sourceProfilesEnabled && result.ok && result.sourceDeleteRequested == null
            val vetoed = completion.sourceProfilesEnabled && explicitRequest == false && result.sourceDeleteRequested == true
            if (missingDecision) notes += "The plugin did not provide a valid sourceDeleteRequested decision; source files were retained."
            if (vetoed) notes += "The explicit deleteSource:false request took precedence; source files were retained."
            if (shouldDelete) {
                var contentUri = false
                var retainedFile = false
                var sharedWithFailure = false
                itemSources.forEach { source ->
                    if (!source.isOwnedFile) {
                        contentUri = true
                        deleted = false
                    } else if ((source.ownershipKey != null && source.ownershipKey in retainedKeys) ||
                        (source.retentionKey != null && source.retentionKey in retainedIdentities)) {
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
                if (sharedWithFailure) notes += "Source files shared with unsuccessful, unprocessed or retained items were retained."
                if (retainedFile) notes += "Some source files could not be deleted safely and were retained."
            }
            val enriched = JsonParser.parseString(result.json).asJsonObject.apply {
                addProperty(InstallerContract.FIELD_SOURCE_DELETED, deleted)
                if (vetoed) addProperty(InstallerContract.FIELD_SOURCE_DELETE_REQUESTED, false)
                if (missingDecision) remove(InstallerContract.FIELD_SOURCE_DELETE_REQUESTED)
                if (notes.isNotEmpty()) add(InstallerContract.FIELD_NOTES, JsonArray().apply { notes.distinct().forEach(::add) })
            }.toString()
            // Host cleanup metadata must not turn a valid near-limit success document into an
            // oversized wire document. ScriptValues overlays these typed fields after parsing
            // the original JSON, so all plugin fields and host notes remain available to JS.
            result.copy(sourceDeleted = deleted, notes = notes.distinct(), json = withinBudget(enriched, result.json),
                sourceDeleteRequested = if (vetoed) false else result.sourceDeleteRequested)
        }
        val root = JsonParser.parseString(completion.json).asJsonObject
        val json = if (root.has(InstallerContract.FIELD_RESULTS)) {
            root.add(InstallerContract.FIELD_RESULTS, JsonArray().apply {
                results.forEach { add(JsonParser.parseString(it.json)) }
            })
            root.toString()
        } else results.single().json
        return completion.copy(results = results, json = withinBudget(json, completion.json))
    }

    private fun withinBudget(enriched: String, original: String): String =
        if (enriched.toByteArray(Charsets.UTF_8).size <= InstallerContract.MAX_JSON_BYTES) enriched else original
}
