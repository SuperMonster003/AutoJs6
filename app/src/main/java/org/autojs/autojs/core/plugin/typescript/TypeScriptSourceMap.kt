package org.autojs.autojs.core.plugin.typescript

import java.net.URI

internal data class TypeScriptOriginalSourcePosition(
    val sourceName: String,
    val lineNumber: Int,
    val columnNumber: Int,
)

/** Execution-scoped pairing between Rhino's generated source name and its Source Map v3 output. */
internal class TypeScriptSourceMapBinding private constructor(
    val generatedSourceName: String,
    val generatedFileName: String,
    val originalSourceName: String,
    private val generatedLineOffset: Int,
    private val sourceMap: TypeScriptSourceMapV3,
) {

    fun originalPositionFor(
        frameSourceName: String,
        generatedLineNumber: Int,
        generatedColumnIndex: Int? = null,
    ): TypeScriptOriginalSourcePosition? {
        if (!matchesGeneratedSource(frameSourceName)) {
            return null
        }
        val sourceMapLineNumber = generatedLineNumber - generatedLineOffset
        if (sourceMapLineNumber <= 0) return null
        val position = sourceMap.originalPositionFor(
            generatedLineNumber = sourceMapLineNumber,
            generatedColumnIndex = generatedColumnIndex,
        ) ?: return null
        return TypeScriptOriginalSourcePosition(
            sourceName = originalSourceName,
            lineNumber = position.lineNumber,
            columnNumber = position.columnNumber,
        )
    }

    internal fun matchesGeneratedSource(frameSourceName: String): Boolean {
        val frame = normalizeGeneratedSourceIdentity(frameSourceName)
        val generated = normalizeGeneratedSourceIdentity(generatedSourceName)
        return frame == generated
    }

    internal fun hasGeneratedBasename(frameSourceName: String): Boolean =
        generatedBasename(frameSourceName) == generatedBasename(generatedSourceName)

    internal fun generatedSuffixSegmentCount(frameSourceName: String): Int {
        val frameSegments = normalizedGeneratedPathSegments(frameSourceName)
        val generatedSegments = normalizedGeneratedPathSegments(generatedSourceName)
        var count = 0
        while (
            count < frameSegments.size &&
            count < generatedSegments.size &&
            frameSegments[frameSegments.lastIndex - count] ==
            generatedSegments[generatedSegments.lastIndex - count]
        ) {
            count += 1
        }
        return count
    }

    companion object {

        fun parse(
            sourceMapText: String,
            generatedSourceName: String,
            expectedGeneratedFileName: String,
            originalSourceName: String,
            generatedLineOffset: Int = 0,
        ): TypeScriptSourceMapBinding {
            require(generatedSourceName.isNotBlank())
            require(expectedGeneratedFileName.isNotBlank())
            require(originalSourceName.isNotBlank())
            require(generatedLineOffset >= 0)
            val sourceMap = TypeScriptSourceMapV3.parse(
                sourceMapText = sourceMapText,
                expectedGeneratedFileName = expectedGeneratedFileName,
            )
            require(sourceMap.sources.size == 1) {
                "Each emitted TypeScript module must identify exactly one Source Map source."
            }
            require(
                generatedBasename(sourceMap.sources.single()) ==
                    generatedBasename(originalSourceName),
            ) {
                "The TypeScript Source Map does not identify the entry source."
            }
            return TypeScriptSourceMapBinding(
                generatedSourceName = generatedSourceName,
                generatedFileName = sourceMap.file,
                originalSourceName = originalSourceName,
                generatedLineOffset = generatedLineOffset,
                sourceMap = sourceMap,
            )
        }
    }
}

/** One execution's fail-closed mapping set for every emitted TypeScript module. */
internal class TypeScriptSourceMapBindings private constructor(
    val bindings: List<TypeScriptSourceMapBinding>,
) {

    init {
        require(bindings.isNotEmpty())
        require(bindings.map(TypeScriptSourceMapBinding::generatedSourceName).distinct().size == bindings.size)
    }

    fun originalPositionFor(
        frameSourceName: String,
        generatedLineNumber: Int,
        generatedColumnIndex: Int? = null,
    ): TypeScriptOriginalSourcePosition? {
        val exact = bindings.filter { binding -> binding.matchesGeneratedSource(frameSourceName) }
        if (exact.size == 1) {
            return exact.single().originalPositionFor(
                frameSourceName = exact.single().generatedSourceName,
                generatedLineNumber = generatedLineNumber,
                generatedColumnIndex = generatedColumnIndex,
            )
        }
        if (exact.isNotEmpty()) return null

        // The external Node plugin materializes the same project-relative paths below its own
        // private workspace root. Prefer the longest unique multi-segment suffix so projects may
        // contain repeated basenames without confusing two emitted modules.
        val suffixCandidates = bindings.mapNotNull { binding ->
            binding.generatedSuffixSegmentCount(frameSourceName)
                .takeIf { segmentCount -> segmentCount >= 2 }
                ?.let { segmentCount -> binding to segmentCount }
        }
        val strongestSuffixLength = suffixCandidates.maxOfOrNull { (_, length) -> length }
        val suffixMatches = suffixCandidates.filter { (_, length) -> length == strongestSuffixLength }
        if (suffixMatches.size == 1) {
            val binding = suffixMatches.single().first
            return binding.originalPositionFor(
                frameSourceName = binding.generatedSourceName,
                generatedLineNumber = generatedLineNumber,
                generatedColumnIndex = generatedColumnIndex,
            )
        }
        if (suffixMatches.isNotEmpty()) return null

        // Some Rhino/V8 builds retain only a generated basename. Map it only when
        // that basename is unique in this execution; ambiguity stays unmapped.
        val basenameMatches = bindings.filter { binding ->
            binding.hasGeneratedBasename(frameSourceName)
        }
        if (basenameMatches.size != 1) return null
        val binding = basenameMatches.single()
        return binding.originalPositionFor(
            frameSourceName = binding.generatedSourceName,
            generatedLineNumber = generatedLineNumber,
            generatedColumnIndex = generatedColumnIndex,
        )
    }

    companion object {
        fun of(bindings: List<TypeScriptSourceMapBinding>): TypeScriptSourceMapBindings =
            TypeScriptSourceMapBindings(bindings.toList())

        fun single(binding: TypeScriptSourceMapBinding): TypeScriptSourceMapBindings =
            TypeScriptSourceMapBindings(listOf(binding))
    }
}

private fun normalizeGeneratedSourceIdentity(value: String): String {
    val uriPath = runCatching {
        URI(value).takeIf { uri -> uri.scheme.equals("file", ignoreCase = true) }?.path
    }.getOrNull()
    return (uriPath ?: value).replace('\\', '/').removePrefix("file://")
}

private fun generatedBasename(value: String): String =
    normalizeGeneratedSourceIdentity(value).substringAfterLast('/')

private fun normalizedGeneratedPathSegments(value: String): List<String> =
    normalizeGeneratedSourceIdentity(value).trimEnd('/').split('/').filter(String::isNotEmpty)

private class TypeScriptSourceMapV3 private constructor(
    val file: String,
    val sources: List<String>,
    private val generatedLines: List<List<MappingSegment>>,
) {

    fun originalPositionFor(
        generatedLineNumber: Int,
        generatedColumnIndex: Int?,
    ): OriginalPosition? {
        if (generatedLineNumber <= 0 || generatedColumnIndex != null && generatedColumnIndex < 0) {
            return null
        }
        val segments = generatedLines.getOrNull(generatedLineNumber - 1) ?: return null
        val segment = if (generatedColumnIndex == null) {
            segments.firstOrNull()
        } else {
            segments.lastOrNull { it.generatedColumn <= generatedColumnIndex }
        } ?: return null
        return OriginalPosition(
            sourceIndex = segment.sourceIndex,
            lineNumber = segment.originalLineIndex + 1,
            columnNumber = segment.originalColumnIndex + 1,
        )
    }

    data class OriginalPosition(
        val sourceIndex: Int,
        val lineNumber: Int,
        val columnNumber: Int,
    )

    private data class MappingSegment(
        val generatedColumn: Int,
        val sourceIndex: Int,
        val originalLineIndex: Int,
        val originalColumnIndex: Int,
    )

    companion object {

        fun parse(
            sourceMapText: String,
            expectedGeneratedFileName: String,
        ): TypeScriptSourceMapV3 {
            val root = TypeScriptStrictJson.parseObject(
                TypeScriptUtf8.encode(sourceMapText, "Source Map v3"),
            )
            require(root.keys.all(ALLOWED_KEYS::contains)) {
                "The TypeScript Source Map contains unsupported fields."
            }
            require(root.requireCanonicalInt("version") == SOURCE_MAP_VERSION)
            val file = root.requireString("file")
            require(file == expectedGeneratedFileName) {
                "The TypeScript Source Map belongs to another generated module."
            }
            require(root.requireString("sourceRoot").isEmpty()) {
                "The TypeScript Source Map sourceRoot must be empty."
            }
            val sources = root.requireStringList("sources")
            require(sources.isNotEmpty() && sources.size <= MAX_SOURCES)
            require(sources.all(String::isNotBlank))
            val names = root.requireStringList("names")
            require(names.size <= MAX_NAMES)
            if ("sourcesContent" in root) {
                val value = root.getValue("sourcesContent")
                require(value is List<*> && value.size == sources.size)
                require(value.all { it == null || it is String })
            }
            val generatedLines = decodeMappings(
                mappings = root.requireString("mappings"),
                sourceCount = sources.size,
                nameCount = names.size,
            )
            return TypeScriptSourceMapV3(file, sources.toList(), generatedLines)
        }

        private fun decodeMappings(
            mappings: String,
            sourceCount: Int,
            nameCount: Int,
        ): List<List<MappingSegment>> {
            val lines = mappings.split(';')
            require(lines.size <= MAX_GENERATED_LINES)
            var previousSourceIndex = 0
            var previousOriginalLine = 0
            var previousOriginalColumn = 0
            var previousNameIndex = 0
            var segmentCount = 0
            return lines.map { encodedLine ->
                if (encodedLine.isEmpty()) {
                    return@map emptyList()
                }
                var generatedColumn = 0
                encodedLine.split(',').mapNotNull { encodedSegment ->
                    require(encodedSegment.isNotEmpty())
                    segmentCount++
                    require(segmentCount <= MAX_SEGMENTS)
                    val fields = decodeSegment(encodedSegment)
                    require(fields.size == 1 || fields.size == 4 || fields.size == 5)
                    require(fields[0] >= 0)
                    generatedColumn = Math.addExact(generatedColumn, fields[0])
                    if (fields.size == 1) {
                        return@mapNotNull null
                    }
                    previousSourceIndex = Math.addExact(previousSourceIndex, fields[1])
                    previousOriginalLine = Math.addExact(previousOriginalLine, fields[2])
                    previousOriginalColumn = Math.addExact(previousOriginalColumn, fields[3])
                    require(previousSourceIndex in 0 until sourceCount)
                    require(previousOriginalLine >= 0 && previousOriginalColumn >= 0)
                    if (fields.size == 5) {
                        previousNameIndex = Math.addExact(previousNameIndex, fields[4])
                        require(previousNameIndex in 0 until nameCount)
                    }
                    MappingSegment(
                        generatedColumn = generatedColumn,
                        sourceIndex = previousSourceIndex,
                        originalLineIndex = previousOriginalLine,
                        originalColumnIndex = previousOriginalColumn,
                    )
                }
            }
        }

        private fun decodeSegment(encoded: String): IntArray {
            val values = ArrayList<Int>(5)
            var offset = 0
            while (offset < encoded.length) {
                require(values.size < MAX_SEGMENT_FIELDS) {
                    "Source Map segment contains too many fields."
                }
                var value = 0L
                var shift = 0
                var continuation: Boolean
                do {
                    require(offset < encoded.length) { "Truncated Source Map VLQ value." }
                    val digit = base64Value(encoded[offset++])
                    continuation = digit and VLQ_CONTINUATION_BIT != 0
                    val payload = digit and VLQ_PAYLOAD_MASK
                    require(shift <= MAX_VLQ_SHIFT)
                    value = value or (payload.toLong() shl shift)
                    shift += VLQ_SHIFT
                } while (continuation)
                val negative = value and VLQ_SIGN_BIT != 0L
                val magnitude = value ushr 1
                require(magnitude <= Int.MAX_VALUE.toLong())
                require(!negative || magnitude != 0L) {
                    "Source Map VLQ negative zero is not canonical."
                }
                values += if (negative) -magnitude.toInt() else magnitude.toInt()
            }
            return values.toIntArray()
        }

        private fun base64Value(character: Char): Int = when (character) {
            in 'A'..'Z' -> character - 'A'
            in 'a'..'z' -> character - 'a' + 26
            in '0'..'9' -> character - '0' + 52
            '+' -> 62
            '/' -> 63
            else -> throw IllegalArgumentException("Invalid Source Map base64 digit.")
        }

        private fun Map<String, Any?>.requireCanonicalInt(key: String): Int {
            val token = getValue(key) as? TypeScriptStrictJson.NumberToken
                ?: error("Source Map field $key is not a number.")
            require(CANONICAL_NON_NEGATIVE_INTEGER.matches(token.source))
            return token.source.toInt()
        }

        private fun Map<String, Any?>.requireString(key: String): String =
            getValue(key) as? String ?: error("Source Map field $key is not a string.")

        private fun Map<String, Any?>.requireStringList(key: String): List<String> {
            val values = getValue(key) as? List<*>
                ?: error("Source Map field $key is not an array.")
            return values.map { value ->
                value as? String ?: error("Source Map field $key contains a non-string value.")
            }
        }

        private const val SOURCE_MAP_VERSION = 3
        private const val MAX_SOURCES = 4_096
        private const val MAX_NAMES = 262_144
        private const val MAX_GENERATED_LINES = 1_000_000
        private const val MAX_SEGMENTS = 1_000_000
        private const val MAX_SEGMENT_FIELDS = 5
        private const val VLQ_CONTINUATION_BIT = 32
        private const val VLQ_PAYLOAD_MASK = 31
        private const val VLQ_SIGN_BIT = 1L
        private const val VLQ_SHIFT = 5
        private const val MAX_VLQ_SHIFT = 30
        private val CANONICAL_NON_NEGATIVE_INTEGER = Regex("0|[1-9][0-9]*")
        private val ALLOWED_KEYS = setOf(
            "version",
            "file",
            "sourceRoot",
            "sources",
            "sourcesContent",
            "names",
            "mappings",
        )
    }
}
