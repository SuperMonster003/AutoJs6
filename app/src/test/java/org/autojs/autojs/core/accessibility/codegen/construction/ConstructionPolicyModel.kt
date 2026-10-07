package org.autojs.autojs.core.accessibility.codegen.construction

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.autojs.autojs.core.accessibility.codegen.model.ObliviousTreeModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Offline prototype only: this reader is not an installable model-package format or an APK asset. */
class ConstructionPolicyModel private constructor(val model: ObliviousTreeModel) : ConstructionPolicy {
    /** Completion-value prediction, without clipping or summing scores from different states. */
    override fun score(features: FloatArray): Double = model.score(features)

    companion object {
        const val MAX_FILE_BYTES = 32 * 1024 * 1024
        const val SCORE_SEMANTICS = "observed-rollout-survival-v1"

        private const val MAX_TREES = 4096
        private const val MAX_DEPTH = 8
        private const val MAX_SPLITS = MAX_TREES * MAX_DEPTH
        private const val MAX_LEAVES = MAX_TREES * (1 shl MAX_DEPTH)

        fun read(file: File): ConstructionPolicyModel {
            require(file.length() in 1..MAX_FILE_BYTES.toLong()) { "Construction policy file size is outside the prototype limit" }
            val bytes = file.inputStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size().toLong() + count <= MAX_FILE_BYTES) { "Construction policy exceeds the prototype file limit" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return read(bytes)
        }

        fun read(bytes: ByteArray): ConstructionPolicyModel {
            require(bytes.size in 1..MAX_FILE_BYTES) { "Construction policy file size is outside the prototype limit" }
            try {
                val text = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
                JsonReader(StringReader(text)).use { reader ->
                    reader.isLenient = false
                    val document = readDocument(reader)
                    require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing construction policy content" }
                    return ConstructionPolicyModel(document.toModel())
                }
            } catch (error: IOException) {
                throw IllegalArgumentException("Invalid construction policy JSON", error)
            }
        }

        private data class Document(
            val format: Int,
            val role: String,
            val grammarVersion: Int,
            val featureSchemaVersion: Int,
            val featureNamesHash: String,
            val featureCount: Int,
            val scoreSemantics: String,
            val model: ModelFields,
        ) {
            fun toModel(): ObliviousTreeModel {
                require(format == 1) { "Unsupported construction policy format" }
                require(role == "construction-policy") { "Model does not declare the construction-policy role" }
                require(grammarVersion == ConstructionFeatures.grammarVersion) { "Construction grammar version differs" }
                require(featureSchemaVersion == ConstructionFeatures.version) { "Construction feature schema version differs" }
                require(featureNamesHash == ConstructionFeatures.namesHash) { "Construction feature names hash differs" }
                require(featureCount == ConstructionFeatures.names.size) { "Construction feature count differs" }
                require(scoreSemantics == SCORE_SEMANTICS) { "Construction policy score semantics differ" }
                return ObliviousTreeModel(featureCount, model.depths, model.splitOffsets, model.leafOffsets,
                    model.featureIndices, model.thresholds, model.leafValues, model.bias, model.scale)
            }
        }

        private data class ModelFields(
            val depths: ByteArray,
            val splitOffsets: IntArray,
            val leafOffsets: IntArray,
            val featureIndices: IntArray,
            val thresholds: FloatArray,
            val leafValues: FloatArray,
            val bias: Double,
            val scale: Double,
        )

        private fun readDocument(reader: JsonReader): Document {
            var format: Int? = null
            var role: String? = null
            var grammarVersion: Int? = null
            var featureSchemaVersion: Int? = null
            var featureNamesHash: String? = null
            var featureCount: Int? = null
            var scoreSemantics: String? = null
            var model: ModelFields? = null
            readObject(reader) { name ->
                when (name) {
                    "format" -> format = readInt(reader)
                    "role" -> role = readString(reader)
                    "grammarVersion" -> grammarVersion = readInt(reader)
                    "featureSchemaVersion" -> featureSchemaVersion = readInt(reader)
                    "featureNamesHash" -> featureNamesHash = readString(reader)
                    "featureCount" -> featureCount = readInt(reader)
                    "scoreSemantics" -> scoreSemantics = readString(reader)
                    "model" -> model = readModel(reader)
                    else -> throw IllegalArgumentException("Unknown construction policy field: $name")
                }
            }
            return Document(requireNotNull(format), requireNotNull(role), requireNotNull(grammarVersion),
                requireNotNull(featureSchemaVersion), requireNotNull(featureNamesHash), requireNotNull(featureCount),
                requireNotNull(scoreSemantics), requireNotNull(model))
        }

        private fun readModel(reader: JsonReader): ModelFields {
            var depths: ByteArray? = null
            var splitOffsets: IntArray? = null
            var leafOffsets: IntArray? = null
            var featureIndices: IntArray? = null
            var thresholds: FloatArray? = null
            var leafValues: FloatArray? = null
            var bias: Double? = null
            var scale: Double? = null
            readObject(reader) { name ->
                when (name) {
                    "depths" -> depths = readInts(reader, MAX_TREES).map {
                        require(it in 1..MAX_DEPTH) { "Construction tree depth must be between 1 and $MAX_DEPTH" }
                        it.toByte()
                    }.toByteArray()
                    "splitOffsets" -> splitOffsets = readInts(reader, MAX_TREES)
                    "leafOffsets" -> leafOffsets = readInts(reader, MAX_TREES)
                    "featureIndices" -> featureIndices = readInts(reader, MAX_SPLITS)
                    "thresholds" -> thresholds = readFloats(reader, MAX_SPLITS)
                    "leafValues" -> leafValues = readFloats(reader, MAX_LEAVES)
                    "bias" -> bias = readDouble(reader)
                    "scale" -> scale = readDouble(reader)
                    else -> throw IllegalArgumentException("Unknown construction tree field: $name")
                }
            }
            return ModelFields(requireNotNull(depths), requireNotNull(splitOffsets), requireNotNull(leafOffsets),
                requireNotNull(featureIndices), requireNotNull(thresholds), requireNotNull(leafValues),
                requireNotNull(bias), requireNotNull(scale))
        }

        private fun readObject(reader: JsonReader, field: (String) -> Unit) {
            require(reader.peek() == JsonToken.BEGIN_OBJECT) { "Expected an object at ${reader.path}" }
            reader.beginObject()
            val seen = HashSet<String>()
            while (reader.hasNext()) {
                val name = reader.nextName()
                require(seen.add(name)) { "Duplicate construction policy field: $name" }
                field(name)
            }
            reader.endObject()
        }

        private fun readInts(reader: JsonReader, limit: Int): IntArray {
            require(reader.peek() == JsonToken.BEGIN_ARRAY) { "Expected an integer array at ${reader.path}" }
            reader.beginArray()
            val values = ArrayList<Int>()
            while (reader.hasNext()) {
                require(values.size < limit) { "Construction integer array exceeds its limit" }
                values.add(readInt(reader))
            }
            reader.endArray()
            return values.toIntArray()
        }

        private fun readFloats(reader: JsonReader, limit: Int): FloatArray {
            require(reader.peek() == JsonToken.BEGIN_ARRAY) { "Expected a float array at ${reader.path}" }
            reader.beginArray()
            val values = ArrayList<Float>()
            while (reader.hasNext()) {
                require(values.size < limit) { "Construction float array exceeds its limit" }
                val number = readDouble(reader).toFloat()
                require(number.isFinite()) { "Construction float payload must be finite" }
                values.add(number)
            }
            reader.endArray()
            return values.toFloatArray()
        }

        private fun readInt(reader: JsonReader): Int {
            require(reader.peek() == JsonToken.NUMBER) { "Expected an integer at ${reader.path}" }
            return requireNotNull(reader.nextString().toIntOrNull()) { "Invalid construction policy integer" }
        }

        private fun readDouble(reader: JsonReader): Double {
            require(reader.peek() == JsonToken.NUMBER) { "Expected a number at ${reader.path}" }
            return requireNotNull(reader.nextString().toDoubleOrNull()) { "Invalid construction policy number" }.also {
                require(it.isFinite()) { "Construction numeric payload must be finite" }
            }
        }

        private fun readString(reader: JsonReader): String {
            require(reader.peek() == JsonToken.STRING) { "Expected a string at ${reader.path}" }
            return reader.nextString()
        }
    }
}
