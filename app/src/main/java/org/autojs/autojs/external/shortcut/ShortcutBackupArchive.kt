package org.autojs.autojs.external.shortcut

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A bounded, versioned archive. ZIP names are never used as filesystem paths. */
internal object ShortcutBackupArchive {
    const val MAX_ENTRIES = 1000
    private const val MAX_ICON = 1024 * 1024
    private const val MAX_MANIFEST = 4 * 1024 * 1024
    private const val MAX_TOTAL = 64 * 1024 * 1024
    private val targets = setOf("script", "settings", "docs", "log", "plugins")
    private val pngSignature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

    fun write(state: ShortcutBackupState, output: OutputStream) {
        require(state.entries.size <= MAX_ENTRIES)
        require(state.entries.map { it.id }.toSet().size == state.entries.size)
        val root = JsonObject().apply {
            addProperty("format", "autojs6-shortcuts")
            addProperty("version", 2)
            addProperty("markerToken", state.markerToken)
            addProperty("launcher", state.launcher)
            addProperty("activity", state.activity)
            add("shortcuts", JsonArray().apply {
                state.entries.forEachIndexed { index, item ->
                    validate(item)
                    add(JsonObject().apply {
                        addProperty("id", item.id)
                        addProperty("shortLabel", item.shortLabel)
                        addProperty("longLabel", item.longLabel)
                        addProperty("target", item.target)
                        addProperty("path", item.path)
                        addProperty("sourceKind", item.sourceKind)
                        addProperty("projectRoot", item.projectRoot)
                        addProperty("icon", if (item.icon == null) null else "icons/$index.png")
                        addProperty("status", item.status.name)
                        addProperty("notified", item.notified)
                        addProperty("requestToken", item.requestToken)
                        addProperty("iconNoticeShown", item.iconNoticeShown)
                    })
                }
            })
        }
        val manifest = root.toString().toByteArray(Charsets.UTF_8)
        require(manifest.size <= MAX_MANIFEST)
        require(manifest.size.toLong() + state.entries.sumOf { it.icon?.size?.toLong() ?: 0 } <= MAX_TOTAL)
        // Release the deflater without closing AtomicFile's stream before its fsync.
        ZipOutputStream(object : FilterOutputStream(output) {
            override fun write(bytes: ByteArray, offset: Int, length: Int) = out.write(bytes, offset, length)
            override fun close() = flush()
        }).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("manifest.json", manifest)
            state.entries.forEachIndexed { index, item -> item.icon?.let { put("icons/$index.png", it) } }
        }
    }

    fun read(input: InputStream): ShortcutBackupState {
        val files = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && (entry.name == "manifest.json" || entry.name.matches(Regex("icons/[0-9]+\\.png")))) {
                    "Unexpected archive entry"
                }
                require(entry.name !in files && files.size <= MAX_ENTRIES) { "Duplicate or excessive archive entries" }
                val limit = if (entry.name == "manifest.json") MAX_MANIFEST else MAX_ICON
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    total += count
                    require(bytes.size() + count <= limit && total <= MAX_TOTAL) { "Shortcut backup is too large" }
                    bytes.write(buffer, 0, count)
                }
                files[entry.name] = bytes.toByteArray()
                zip.closeEntry()
            }
        }
        val root = JsonParser.parseString(requireNotNull(files.remove("manifest.json")) { "Missing manifest" }.toString(Charsets.UTF_8)).asJsonObject
        val version = root.get("version")?.asString
        require(root.text("format") == "autojs6-shortcuts" && version in setOf("1", "2")) { "Unsupported shortcut backup format" }
        val items = root.getAsJsonArray("shortcuts") ?: error("Missing shortcuts")
        require(items.size() <= MAX_ENTRIES)
        val entries = items.map { element ->
            val item = element.asJsonObject
            require(item.has("icon")) { "Missing icon declaration" }
            val iconName = item.text("icon")
            require(version == "2" || iconName != null) { "Version 1 requires an icon" }
            ShortcutBackupEntry(
                id = requireNotNull(item.text("id")),
                shortLabel = requireNotNull(item.text("shortLabel")),
                longLabel = requireNotNull(item.text("longLabel")),
                target = requireNotNull(item.text("target")),
                path = item.text("path"),
                sourceKind = item.text("sourceKind"),
                projectRoot = item.text("projectRoot"),
                icon = iconName?.let { requireNotNull(files.remove(it)) { "Missing or shared icon" } },
                status = ShortcutBackupEntry.Status.valueOf(requireNotNull(item.text("status"))),
                notified = item.get("notified")?.asBoolean ?: false,
                requestToken = item.text("requestToken"),
                iconNoticeShown = item.get("iconNoticeShown")?.asBoolean ?: false,
            ).also(::validate)
        }
        require(files.isEmpty() && entries.map { it.id }.toSet().size == entries.size) { "Unexpected files or duplicate shortcut IDs" }
        return ShortcutBackupState(entries, root.text("markerToken"), root.text("launcher"), root.text("activity"))
    }

    private fun JsonObject.text(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.let {
        require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "Invalid $key" }
        it.asString.also { value -> require(value.length <= 8192 && '\u0000' !in value) }
    }

    internal fun validate(item: ShortcutBackupEntry) {
        // Older AutoJs6 IDs include a class name, the entire label and a timestamp.
        require(item.id.isNotBlank() && item.id.length <= 1024)
        require(item.shortLabel.isNotBlank() && item.shortLabel.length <= 256)
        require(item.longLabel.isNotBlank() && item.longLabel.length <= 1024)
        require(item.target in targets)
        if (item.target == "script") {
            require(item.path?.startsWith('/') == true && item.path.length <= 8192)
        } else {
            require(item.path == null && item.sourceKind == null && item.projectRoot == null)
        }
        require(item.projectRoot == null || item.projectRoot.startsWith('/'))
        val icon = item.icon ?: return
        require(icon.size in 24..MAX_ICON && icon.take(8).toByteArray().contentEquals(pngSignature)) { "Invalid PNG icon" }
        val dimensions = ByteBuffer.wrap(icon, 16, 8)
        require(dimensions.int in 1..512 && dimensions.int in 1..512) { "Icon dimensions exceed the limit" }
    }
}
