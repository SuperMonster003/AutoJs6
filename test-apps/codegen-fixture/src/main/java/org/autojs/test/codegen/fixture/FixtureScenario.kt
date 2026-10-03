package org.autojs.test.codegen.fixture

import java.security.MessageDigest
import java.util.Collections
import java.util.Random

/** Pure, deterministic scenario data. Truth keys are never used as UI text or resource IDs. */
data class FixtureScenario(
    val screen: String,
    val seed: Long,
    val mutations: List<String>,
    val rows: List<Row>,
    val chinese: Boolean,
    val textRevision: Int,
    val stripIds: Boolean,
    val renameIds: Boolean,
    val wrap: Boolean,
    val sibling: Boolean,
    val appliedMutations: List<String>,
    val skippedMutations: List<String>,
) {
    data class Row(val key: String?, val slot: Int)

    val targetKeys: List<String> get() = rows.mapNotNull { it.key }.sorted()

    val defaultStateId: String get() {
        val input = listOf(screen, seed.toString(), mutations.joinToString(",")).joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return "s-" + digest.take(12).joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun dynamicNumber(slot: Int): Int = Math.floorMod(seed + slot * 17L + textRevision * 113L, 1000L).toInt()

    companion object {
        val SCREENS = listOf("login", "list", "settings", "grid", "tabs", "dialog", "nested-scroll", "no-id")
        val MUTATIONS = setOf("shuffle", "insert", "delete", "retext", "locale", "strip-ids", "rename-ids", "wrap", "sibling")
        private val KEYS = mapOf(
            "login" to listOf("email", "password", "remember", "sign-in", "help", "guest"),
            "list" to listOf("mouse", "keyboard", "camera", "speaker", "headphones", "charger"),
            "settings" to listOf("wifi", "bluetooth", "notifications", "sync", "location", "dark-mode"),
            "grid" to listOf("photos", "music", "videos", "books", "downloads", "documents"),
            "tabs" to listOf("overview", "activity", "files", "open", "share", "archive"),
            "dialog" to listOf("name", "remember", "allow", "deny", "details", "later"),
            "nested-scroll" to listOf("news", "messages", "events", "tasks", "notes", "history"),
            "no-id" to listOf("search", "filter", "sort", "favorite", "export", "refresh"),
        )

        fun create(screen: String, seed: Long, mutations: List<String>): FixtureScenario {
            require(screen in SCREENS) { "Unknown screen: $screen" }
            require(mutations.size <= 32) { "Too many mutations" }
            require(mutations.all { it in MUTATIONS }) { "Unknown mutation" }
            val rows = KEYS.getValue(screen).mapIndexed { index, key -> Row("$screen.$key", index) }.toMutableList()
            // Untagged stock/help rows can be removed without destroying any training target.
            rows.add(1, Row(null, 6))
            rows.add(Row(null, 7))
            var chinese = false
            var revision = 0
            var strip = screen == "no-id"
            var rename = false
            var wrap = false
            var sibling = false
            val applied = mutableListOf<String>()
            val skipped = mutableListOf<String>()
            mutations.forEachIndexed { index, mutation ->
                val random = Random(seed xor (screen.hashCode().toLong() shl 32) xor (index + 1L) * 104729L)
                var changed = true
                when (mutation) {
                    "shuffle" -> {
                        val previous = rows.toList()
                        Collections.shuffle(rows, random)
                        if (rows == previous) Collections.rotate(rows, 1)
                    }
                    "insert" -> rows.add(random.nextInt(rows.size + 1), Row(null, 8 + index))
                    "delete" -> {
                        val removable = rows.indexOfFirst { it.key == null }
                        if (removable < 0) changed = false else rows.removeAt(removable)
                    }
                    "retext" -> revision++
                    "locale" -> chinese = !chinese
                    "strip-ids" -> { changed = !strip; strip = true; rename = false }
                    "rename-ids" -> { changed = !strip && !rename; rename = !strip }
                    "wrap" -> { changed = !wrap; wrap = true }
                    "sibling" -> { changed = !sibling; sibling = true }
                }
                if (changed) applied += mutation else skipped += mutation
            }
            return FixtureScenario(screen, seed, mutations.toList(), rows.toList(), chinese, revision,
                strip, rename, wrap, sibling, applied.toList(), skipped.toList())
        }

        fun safeStateId(value: String): String {
            require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,95}"))) { "Invalid stateId" }
            return value
        }
    }
}
