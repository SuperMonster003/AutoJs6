package org.autojs.autojs.core.accessibility.codegen.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageRejectionReason.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.Collections
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Reads the five-entry AJCG package without Android or an ML runtime. Input streams remain owned
 * by the caller. A bounded disk snapshot allows central/local ZIP validation without retaining an
 * untrusted archive in memory; every handle is closed and the temporary file is removed on exit.
 * zh-CN: 无 Android 或 ML 运行时依赖的五条目模型包读取器. 输入流由调用者持有; 有界磁盘副本用于完整 ZIP 校验,
 * 无需将不可信压缩包留在内存. 退出时关闭所有内部句柄并删除临时文件.
 */
class ModelPackageReader(
    private val hostVersionCode: Int = Int.MAX_VALUE,
    private val temporaryDirectory: File? = null,
) {
    fun read(file: File): ModelPackageResult = try {
        FileInputStream(file).use(::read)
    } catch (e: Exception) {
        Rejected(IO_ERROR, safeDetail(e))
    }

    fun read(input: InputStream): ModelPackageResult {
        var temporary: File? = null
        return try {
            // Android callers pass context.cacheDir; no platform type enters this reader.
            // zh-CN: Android 调用者传入 context.cacheDir, 本读取器不引入平台类型.
            enforce(temporaryDirectory == null || temporaryDirectory.isDirectory && temporaryDirectory.canWrite(), IO_ERROR, "Temporary directory must already exist and be writable")
            temporary = File.createTempFile("autojs-codegen-model-", ".zip", temporaryDirectory)
            temporary.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) {
                        val byte = input.read()
                        if (byte < 0) break
                        copied++
                        enforce(copied <= MAX_PACKAGE_BYTES, PACKAGE_TOO_LARGE, "Compressed package exceeds 100 MiB")
                        output.write(byte)
                    } else {
                        copied += read
                        enforce(copied <= MAX_PACKAGE_BYTES, PACKAGE_TOO_LARGE, "Compressed package exceeds 100 MiB")
                        output.write(buffer, 0, read)
                    }
                }
            }
            val files = readArchive(temporary)
            val manifest = parseManifest(strictJson(files.getValue("manifest.json")))
            val features = parseFeatures(strictJson(files.getValue("features.json")), manifest)
            enforce(manifest.minHostVersionCode <= hostVersionCode, HOST_TOO_OLD, "Package requires a newer host")
            val binary = files.getValue("model.bin")
            enforce(binary.size.toLong() == manifest.model.size, MODEL_METADATA_MISMATCH, "model.bin size differs from manifest")
            enforce(sha256(binary) == manifest.model.sha256, MODEL_HASH_MISMATCH, "model.bin SHA-256 differs from manifest")
            ModelPackage(manifest, features, parseModel(binary, manifest.model))
        } catch (e: PackageFailure) {
            Rejected(e.reason, e.message.orEmpty())
        } catch (e: IOException) {
            Rejected(IO_ERROR, safeDetail(e))
        } catch (e: Exception) {
            Rejected(INVALID_MANIFEST, safeDetail(e))
        } finally {
            runCatching { temporary?.delete() }
        }
    }

    private data class Entry(val name: String, val flags: Int, val method: Int, val crc: Long, val compressed: Long, val size: Long, val offset: Long, var dataOffset: Long = 0)

    private fun readArchive(file: File): Map<String, ByteArray> = try {
        RandomAccessFile(file, "r").use { archive ->
            val entries = directory(archive)
            val retained = HashMap<String, ByteArray>()
            var total = 0L
            for (entry in entries) {
                archive.seek(entry.dataOffset)
                val slice = object : InputStream() {
                    private var remaining = entry.compressed
                    override fun read(): Int {
                        if (remaining == 0L) return -1
                        val byte = archive.read()
                        enforce(byte >= 0, INVALID_ZIP, "Truncated entry")
                        remaining--
                        return byte
                    }
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (length == 0) return 0
                        if (remaining == 0L) return -1
                        val read = archive.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
                        enforce(read > 0, INVALID_ZIP, "Truncated entry")
                        remaining -= read
                        return read
                    }
                }
                val inflater = if (entry.method == 8) Inflater(true) else null
                val stream = if (inflater == null) slice else InflaterInputStream(slice, inflater, BUFFER_SIZE)
                val crc = CRC32()
                var count = 0L
                fun account(buffer: ByteArray, offset: Int, length: Int) {
                    count += length
                    total += length
                    enforce(count <= entryLimit(entry.name), ENTRY_TOO_LARGE, "Entry exceeds its size limit: ${entry.name}")
                    enforce(total <= MAX_PACKAGE_BYTES, PACKAGE_TOO_LARGE, "Uncompressed package exceeds 100 MiB")
                    enforce(count <= entry.compressed * MAX_RATIO, COMPRESSION_RATIO, "Entry compression ratio exceeds 200")
                    crc.update(buffer, offset, length)
                }
                val checked = object : FilterInputStream(stream) {
                    override fun read(): Int {
                        val byte = `in`.read()
                        if (byte >= 0) account(byteArrayOf(byte.toByte()), 0, 1)
                        return byte
                    }
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        val read = `in`.read(buffer, offset, length)
                        if (read > 0) account(buffer, offset, read)
                        return read
                    }
                }
                fun verifyEntry() {
                    enforce(count == entry.size, INVALID_ZIP, "Entry uncompressed size differs from ZIP directory")
                    enforce(crc.value == entry.crc, ZIP_CRC_MISMATCH, "Entry CRC differs from ZIP directory")
                    if (inflater != null) enforce(inflater.finished() && inflater.bytesRead == entry.compressed, INVALID_ZIP, "Deflate stream does not exactly fill the entry")
                }
                try {
                    checked.use {
                        if (entry.name in RETAINED) {
                            val bytes = ByteArrayOutputStream(BUFFER_SIZE)
                            it.copyTo(bytes, BUFFER_SIZE)
                            retained[entry.name] = bytes.toByteArray()
                            verifyEntry()
                        } else {
                            // Card/license are validated as UTF-8 incrementally, never retained as large strings.
                            // zh-CN: 模型说明与许可证增量校验 UTF-8, 不保留为大字符串.
                            InputStreamReader(it, utf8Decoder()).use { reader ->
                                val chars = CharArray(BUFFER_SIZE)
                                var nonblank = false
                                while (true) {
                                    val read = reader.read(chars)
                                    if (read < 0) break
                                    for (i in 0 until read) if (!chars[i].isWhitespace()) nonblank = true
                                }
                                enforce(nonblank, INVALID_ENTRY, "Empty text entry: ${entry.name}")
                                verifyEntry()
                            }
                        }
                    }
                } finally {
                    inflater?.end()
                }
            }
            retained
        }
    } catch (e: PackageFailure) {
        throw e
    } catch (e: Exception) {
        fail(INVALID_ZIP, safeDetail(e))
    }

    private fun directory(archive: RandomAccessFile): List<Entry> {
        val length = archive.length()
        enforce(length >= 22, INVALID_ZIP, "Missing ZIP end record")
        val tailSize = minOf(length, 65_557L).toInt()
        archive.seek(length - tailSize)
        val tail = ByteArray(tailSize).also(archive::readFully)
        val end = (tailSize - 22 downTo 0).firstOrNull { tail.u32(it) == 0x06054b50L && it + 22 + tail.u16(it + 20) == tailSize }
            ?: fail(INVALID_ZIP, "Missing or truncated ZIP end record")
        enforce(tail.u16(end + 4) == 0 && tail.u16(end + 6) == 0, UNSUPPORTED_ZIP, "Multi-disk ZIP is unsupported")
        val count = tail.u16(end + 10)
        enforce(tail.u16(end + 8) == count && count != 0xffff, UNSUPPORTED_ZIP, "ZIP64 or split directory is unsupported")
        enforce(count <= ENTRY_NAMES.size, INVALID_ENTRY, "Too many ZIP entries")
        val directorySize = tail.u32(end + 12)
        val directoryOffset = tail.u32(end + 16)
        val endOffset = length - tailSize + end
        enforce(directoryOffset + directorySize == endOffset, INVALID_ZIP, "ZIP directory boundary mismatch")
        val entries = ArrayList<Entry>(count)
        val names = HashSet<String>()
        archive.seek(directoryOffset)
        repeat(count) {
            val header = ByteArray(46).also(archive::readFully)
            enforce(header.u32(0) == 0x02014b50L, INVALID_ZIP, "Invalid ZIP central header")
            val flags = header.u16(8)
            val method = header.u16(10)
            enforce(header.u16(6) <= 20 && (flags and 0xf7f1) == 0 && method in setOf(0, 8), UNSUPPORTED_ZIP, "Encrypted, ZIP64, or unsupported compression")
            enforce(header.u16(34) == 0, UNSUPPORTED_ZIP, "Entry uses another disk")
            val size = header.u32(24)
            val compressed = header.u32(20)
            val offset = header.u32(42)
            enforce(size != 0xffffffffL && compressed != 0xffffffffL && offset != 0xffffffffL, UNSUPPORTED_ZIP, "ZIP64 is unsupported")
            val nameBytes = ByteArray(header.u16(28)).also(archive::readFully)
            enforce(nameBytes.all { it.toInt() in 0..127 }, INVALID_ENTRY, "Entry names must be fixed ASCII names")
            val name = nameBytes.toString(Charsets.US_ASCII)
            enforce(name in ENTRY_NAMES, INVALID_ENTRY, "Unexpected entry name")
            enforce(names.add(name), DUPLICATE_ENTRY, "Duplicate entry: $name")
            val attributes = header.u32(38)
            val kind = (attributes ushr 16).toInt() and 0xf000
            enforce(attributes and 0x10L == 0L && kind in setOf(0, 0x8000), INVALID_ENTRY, "Directory or symbolic link entry")
            enforce(size <= entryLimit(name), ENTRY_TOO_LARGE, "Entry exceeds its size limit: $name")
            enforce(size <= compressed * MAX_RATIO, COMPRESSION_RATIO, "Entry compression ratio exceeds 200")
            extra(ByteArray(header.u16(30)).also(archive::readFully))
            archive.seek(archive.filePointer + header.u16(32))
            enforce(archive.filePointer <= endOffset, INVALID_ZIP, "Central entry exceeds directory")
            entries += Entry(name, flags, method, header.u32(16), compressed, size, offset)
        }
        enforce(archive.filePointer == endOffset, INVALID_ZIP, "Extra or missing central directory bytes")
        enforce(names == ENTRY_NAMES, MISSING_ENTRY, "All five package entries are required")
        enforce(entries.sumOf { it.size } <= MAX_PACKAGE_BYTES, PACKAGE_TOO_LARGE, "Uncompressed package exceeds 100 MiB")
        val ordered = entries.sortedBy { it.offset }
        var expected = 0L
        for (entry in ordered) {
            enforce(entry.offset == expected, INVALID_ZIP, "Local entry gap, overlap, or unlisted data")
            archive.seek(entry.offset)
            val header = ByteArray(30).also(archive::readFully)
            enforce(header.u32(0) == 0x04034b50L && header.u16(4) <= 20 && header.u16(6) == entry.flags && header.u16(8) == entry.method, INVALID_ZIP, "Local header differs from directory")
            val name = ByteArray(header.u16(26)).also(archive::readFully).toString(Charsets.US_ASCII)
            enforce(name == entry.name, INVALID_ZIP, "Local entry name differs from directory")
            extra(ByteArray(header.u16(28)).also(archive::readFully))
            val descriptor = entry.flags and 8 != 0
            for ((actual, wanted) in listOf(header.u32(14) to entry.crc, header.u32(18) to entry.compressed, header.u32(22) to entry.size)) {
                enforce(actual == wanted || descriptor && actual == 0L, INVALID_ZIP, "Local sizes or CRC differ from directory")
            }
            entry.dataOffset = archive.filePointer
            expected = entry.dataOffset + entry.compressed
            enforce(expected <= directoryOffset, INVALID_ZIP, "Entry data exceeds local area")
            if (entry.method == 0) enforce(entry.compressed == entry.size, INVALID_ZIP, "Stored entry sizes disagree")
            if (descriptor) {
                archive.seek(expected)
                val first = ByteArray(4).also(archive::readFully).u32(0)
                val crc = if (first == 0x08074b50L) ByteArray(4).also(archive::readFully).u32(0) else first
                val sizes = ByteArray(8).also(archive::readFully)
                enforce(crc == entry.crc && sizes.u32(0) == entry.compressed && sizes.u32(4) == entry.size, INVALID_ZIP, "Invalid ZIP data descriptor")
                expected = archive.filePointer
            }
        }
        enforce(expected == directoryOffset, INVALID_ZIP, "Unlisted bytes before central directory")
        return ordered
    }

    private fun extra(bytes: ByteArray) {
        var at = 0
        while (at < bytes.size) {
            enforce(at + 4 <= bytes.size, INVALID_ZIP, "Truncated ZIP extra field")
            enforce(bytes.u16(at) != 1, UNSUPPORTED_ZIP, "ZIP64 extra field is unsupported")
            at += 4 + bytes.u16(at + 2)
            enforce(at <= bytes.size, INVALID_ZIP, "Truncated ZIP extra field value")
        }
    }

    private fun strictJson(bytes: ByteArray): JsonObject = try {
        val source = utf8Decoder().decode(ByteBuffer.wrap(bytes)).toString()
        enforce(!source.startsWith('\uFEFF'), INVALID_JSON, "JSON must not have a UTF-8 BOM")
        JsonReader(StringReader(source)).use { reader ->
            reader.strictness = Strictness.STRICT
            var nodes = 0
            fun value(depth: Int): JsonElement {
                enforce(depth <= 32 && ++nodes <= 20_000, INVALID_JSON, "JSON nesting or node limit exceeded")
                return when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        reader.beginObject()
                        val result = JsonObject()
                        while (reader.hasNext()) {
                            val key = reader.nextName()
                            enforce(key.length <= 128 && !result.has(key), INVALID_JSON, "Duplicate or oversized JSON key")
                            result.add(key, value(depth + 1))
                        }
                        reader.endObject()
                        result
                    }
                    JsonToken.BEGIN_ARRAY -> {
                        reader.beginArray()
                        val result = JsonArray()
                        while (reader.hasNext()) result.add(value(depth + 1))
                        reader.endArray()
                        result
                    }
                    JsonToken.STRING -> {
                        val text = reader.nextString()
                        enforce(text.length <= 1024, INVALID_JSON, "Oversized JSON string")
                        utf8Decoder().charset().newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text))
                        JsonPrimitive(text)
                    }
                    JsonToken.NUMBER -> {
                        val number = reader.nextString()
                        enforce(number.length <= 128, INVALID_JSON, "Oversized JSON number")
                        JsonPrimitive(JsonNumber(number))
                    }
                    JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
                    JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
                    else -> fail(INVALID_JSON, "Unexpected JSON token")
                }
            }
            val document = value(0)
            enforce(document.isJsonObject && reader.peek() == JsonToken.END_DOCUMENT, INVALID_JSON, "Expected one JSON object")
            document.asJsonObject
        }
    } catch (e: PackageFailure) { throw e } catch (e: Exception) { fail(INVALID_JSON, safeDetail(e)) }

    private fun parseManifest(json: JsonObject): ModelManifest {
        json.fields("format", "modelId", "displayName", "versionCode", "versionName", "kind", "algorithm", "featureSchemaVersion", "featureNamesHash", "model", "dataset", "metrics", "training", "minHostVersionCode", "license", "publisher")
        enforce(json.integer("format") == 1, INVALID_MANIFEST, "Unsupported manifest format")
        val id = json.text("modelId")
        enforce(MODEL_ID.matches(id), INVALID_MANIFEST, "Invalid modelId")
        val names = json.obj("displayName")
        enforce(names.size() in 1..128 && names.has("en"), INVALID_MANIFEST, "displayName requires en")
        val displayNames = names.keySet().associateWith { key ->
            enforce(LANGUAGE.matches(key), INVALID_MANIFEST, "Invalid displayName language tag")
            names.text(key, 256)
        }
        enforce(json.text("kind") == "ranker" && json.text("algorithm") == "catboost-oblivious", INVALID_MANIFEST, "Unsupported model kind or algorithm")
        val schema = json.integer("featureSchemaVersion")
        enforce(schema in FeatureSchema.SUPPORTED_SCHEMA, INCOMPATIBLE_SCHEMA, "Unsupported feature schema")
        val hash = json.text("featureNamesHash")
        enforce(hash == FeatureSchema.namesHash(schema), FEATURE_MISMATCH, "Feature names hash differs from host")
        val model = json.obj("model").apply { fields("file", "size", "sha256", "trees", "depth", "features") }
        enforce(model.text("file") == "model.bin", INVALID_MANIFEST, "Model file must be model.bin")
        val modelHash = model.text("sha256")
        enforce(HEX64.matches(modelHash), INVALID_MANIFEST, "Invalid model SHA-256")
        val metadata = ModelBinaryMetadata("model.bin", model.integer("size", 32, MAX_MODEL_BYTES).toLong(), modelHash,
            model.integer("trees", 1, 4096), model.integer("depth", 1, 8), model.integer("features", 1, 65535))
        enforce(metadata.features == FeatureSchema.names(schema).size, FEATURE_MISMATCH, "Feature count differs from host")
        val dataset = json.obj("dataset").apply { fields("datasetId", "groups", "createdAt") }
        val metrics = json.obj("metrics").apply { fields("test", "realTest") }
        val training = json.obj("training").apply { fields("catboostVersion", "pythonVersion", "hostCommit", "trainedAt") }
        enforce(HEX40.matches(training.text("hostCommit")), INVALID_MANIFEST, "Invalid training hostCommit")
        return ModelManifest(1, id, Collections.unmodifiableMap(displayNames), json.integer("versionCode"), json.text("versionName", 256),
            "ranker", "catboost-oblivious", schema, hash, metadata,
            ModelDatasetMetadata(dataset.text("datasetId"), dataset.integer("groups"), dataset.timestamp("createdAt")),
            ModelMetrics(parseMetrics(metrics.obj("test")), parseMetrics(metrics.obj("realTest"))),
            ModelTrainingMetadata(training.text("catboostVersion"), training.text("pythonVersion"), training.text("hostCommit"), training.timestamp("trainedAt")),
            json.integer("minHostVersionCode"), json.text("license", 256), json.text("publisher", 256))
    }

    private fun parseMetrics(json: JsonObject): ModelMetricSummary {
        json.fields("top1Survival", "ruleTop1Survival", "regret", "groups")
        val groups = json.integer("groups", 0)
        fun metric(key: String): Double? {
            val element = json.get(key)
            if (groups == 0) {
                enforce(element.isJsonNull, INVALID_MANIFEST, "Empty metric split must use null values")
                return null
            }
            enforce(element.isJsonPrimitive && element.asJsonPrimitive.isNumber, INVALID_MANIFEST, "Metric must be a number")
            val value = element.asDouble
            enforce(value.isFinite() && value in 0.0..1.0, INVALID_MANIFEST, "Metric must be finite in [0, 1]")
            return value
        }
        return ModelMetricSummary(metric("top1Survival"), metric("ruleTop1Survival"), metric("regret"), groups)
    }

    private fun parseFeatures(json: JsonObject, manifest: ModelManifest): ModelFeatures {
        json.fields("format", "featureSchemaVersion", "names", "namesHash")
        enforce(json.integer("format") == 1, FEATURE_MISMATCH, "Unsupported features format")
        val schema = json.integer("featureSchemaVersion")
        enforce(schema in FeatureSchema.SUPPORTED_SCHEMA, INCOMPATIBLE_SCHEMA, "Unsupported features schema")
        enforce(schema == manifest.featureSchemaVersion, FEATURE_MISMATCH, "Feature schemas disagree")
        val namesElement = json.get("names")
        enforce(namesElement.isJsonArray, FEATURE_MISMATCH, "Feature names must be an array")
        val names = namesElement.asJsonArray.map {
            enforce(it.isJsonPrimitive && it.asJsonPrimitive.isString, FEATURE_MISMATCH, "Feature names must be strings")
            it.asString
        }
        val hash = json.text("namesHash")
        enforce(names == FeatureSchema.names(schema) && hash == FeatureSchema.namesHash(schema), FEATURE_MISMATCH, "Feature names/order/hash differs from host")
        return ModelFeatures(1, schema, Collections.unmodifiableList(names), hash)
    }

    private fun parseModel(bytes: ByteArray, metadata: ModelBinaryMetadata): ObliviousTreeModel {
        enforce(bytes.size in 32..MAX_MODEL_BYTES, INVALID_MODEL, "Invalid binary model length")
        val end = bytes.size - 4
        val crc = CRC32().apply { update(bytes, 0, end) }.value
        enforce(crc == bytes.u32(end), MODEL_CRC_MISMATCH, "Binary model CRC mismatch")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        enforce(buffer.int == 0x47434a41 && (buffer.short.toInt() and 0xffff) == 1, INVALID_MODEL, "Unsupported model magic or format")
        val features = buffer.short.toInt() and 0xffff
        val trees = buffer.int.toLong() and 0xffffffffL
        enforce(features == metadata.features && trees == metadata.trees.toLong(), MODEL_METADATA_MISMATCH, "Model header differs from manifest")
        enforce(trees in 1..4096, INVALID_MODEL, "Invalid model tree count")
        val bias = buffer.double
        val scale = buffer.double
        enforce(bias.isFinite() && scale.isFinite(), INVALID_MODEL, "Non-finite model affine value")
        val depths = ByteArray(trees.toInt())
        val splitOffsets = IntArray(trees.toInt())
        val leafOffsets = IntArray(trees.toInt())
        var splits = 0
        var leaves = 0
        for (tree in depths.indices) {
            enforce(buffer.position() < end, INVALID_MODEL, "Truncated tree header")
            val depth = buffer.get().toInt() and 0xff
            enforce(depth in 1..8, INVALID_MODEL, "Tree depth must be 1..8")
            depths[tree] = depth.toByte()
            splitOffsets[tree] = splits
            leafOffsets[tree] = leaves
            splits += depth
            leaves += 1 shl depth
            val next = buffer.position() + depth * 6 + (1 shl depth) * 4
            enforce(next <= end, INVALID_MODEL, "Truncated tree body")
            buffer.position(next)
        }
        enforce(buffer.position() == end, INVALID_MODEL, "Extra binary model bytes")
        enforce(depths.max().toInt() == metadata.depth, MODEL_METADATA_MISMATCH, "Model depth differs from manifest")
        val indices = IntArray(splits)
        val thresholds = FloatArray(splits)
        val values = FloatArray(leaves)
        buffer.position(28)
        for (tree in depths.indices) {
            buffer.get()
            repeat(depths[tree].toInt()) { split ->
                val offset = splitOffsets[tree] + split
                indices[offset] = buffer.short.toInt() and 0xffff
                thresholds[offset] = buffer.float
                enforce(indices[offset] < features && thresholds[offset].isFinite(), INVALID_MODEL, "Invalid split feature or threshold")
            }
            repeat(1 shl depths[tree].toInt()) { leaf ->
                val offset = leafOffsets[tree] + leaf
                values[offset] = buffer.float
                enforce(values[offset].isFinite(), INVALID_MODEL, "Non-finite leaf value")
            }
        }
        return ObliviousTreeModel(features, depths, splitOffsets, leafOffsets, indices, thresholds, values, bias, scale)
    }

    private fun JsonObject.fields(vararg keys: String) = enforce(keySet() == keys.toSet(), INVALID_MANIFEST, "Missing or unknown JSON fields")
    private fun JsonObject.obj(key: String): JsonObject {
        val element = get(key)
        enforce(element?.isJsonObject == true, INVALID_MANIFEST, "Expected object: $key")
        return element.asJsonObject
    }
    private fun JsonObject.text(key: String, maximum: Int = 1024): String {
        val element = get(key)
        enforce(element?.isJsonPrimitive == true && element.asJsonPrimitive.isString, INVALID_MANIFEST, "Expected string: $key")
        val value = element.asString
        enforce(value.isNotBlank() && value.length <= maximum && value.none(Char::isISOControl), INVALID_MANIFEST, "Invalid string: $key")
        return value
    }
    private fun JsonObject.integer(key: String, minimum: Int = 1, maximum: Int = Int.MAX_VALUE): Int {
        val element = get(key)
        enforce(element?.isJsonPrimitive == true && element.asJsonPrimitive.isNumber, INVALID_MANIFEST, "Expected integer: $key")
        val value = element.asString.takeIf { INTEGER.matches(it) }?.toIntOrNull()
        enforce(value != null && value in minimum..maximum, INVALID_MANIFEST, "Integer out of range: $key")
        return value!!
    }
    private fun JsonObject.timestamp(key: String): String = text(key, 64).also {
        try { OffsetDateTime.parse(it) } catch (_: Exception) { fail(INVALID_MANIFEST, "Invalid ISO-8601 timestamp: $key") }
    }

    companion object {
        const val MAX_PACKAGE_BYTES = 100L * 1024 * 1024
        const val MAX_MODEL_BYTES = 32 + 4096 * (1 + 8 * 6 + 256 * 4)
        private const val BUFFER_SIZE = 8192
        private const val MAX_RATIO = 200L
        private val ENTRY_NAMES = setOf("manifest.json", "model.bin", "features.json", "MODEL_CARD.md", "LICENSE")
        private val RETAINED = setOf("manifest.json", "model.bin", "features.json")
        private val MODEL_ID = Regex("[a-z0-9][a-z0-9._-]{1,63}")
        private val LANGUAGE = Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")
        private val HEX64 = Regex("[0-9a-f]{64}")
        private val HEX40 = Regex("[0-9a-f]{40}")
        private val INTEGER = Regex("0|[1-9][0-9]{0,9}")
        private fun entryLimit(name: String): Long = when (name) {
            "model.bin" -> MAX_MODEL_BYTES.toLong()
            "features.json" -> 256L * 1024
            else -> 1024L * 1024
        }
        private fun utf8Decoder() = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        private fun ByteArray.u16(at: Int): Int = (this[at].toInt() and 0xff) or ((this[at + 1].toInt() and 0xff) shl 8)
        private fun ByteArray.u32(at: Int): Long = u16(at).toLong() or (u16(at + 2).toLong() shl 16)
        private fun safeDetail(error: Exception): String = (error.message ?: error.javaClass.simpleName).take(256)
        private fun enforce(condition: Boolean, reason: ModelPackageRejectionReason, detail: String) { if (!condition) fail(reason, detail) }
        private fun fail(reason: ModelPackageRejectionReason, detail: String): Nothing = throw PackageFailure(reason, detail)
        private class PackageFailure(val reason: ModelPackageRejectionReason, detail: String) : IllegalArgumentException(detail)
        // Preserve number spelling so integer fields cannot accept 1.0 or 1e0 via coercion.
        // zh-CN: 保留数值原始拼写, 防止整数字段经强制转换接受 1.0 或 1e0.
        private class JsonNumber(private val raw: String) : Number() {
            override fun toByte(): Byte = raw.toInt().toByte()
            override fun toShort(): Short = raw.toInt().toShort()
            override fun toInt(): Int = raw.toInt()
            override fun toLong(): Long = raw.toLong()
            override fun toFloat(): Float = raw.toFloat()
            override fun toDouble(): Double = raw.toDouble()
            override fun toString(): String = raw
        }
    }
}
