package org.autojs.autojs.core.plugin.typescript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.ScriptStackElement

class TypeScriptSourceMapTest {

    @Test
    fun decodesSourceMapV3LinesAndGeneratedColumns() {
        val binding = binding("AAAA,UAAK;AACL;AACA")

        assertEquals(
            TypeScriptOriginalSourcePosition(ORIGINAL_SOURCE, 1, 1),
            binding.originalPositionFor(GENERATED_SOURCE, 1),
        )
        assertEquals(
            TypeScriptOriginalSourcePosition(ORIGINAL_SOURCE, 1, 6),
            binding.originalPositionFor(GENERATED_SOURCE, 1, 10),
        )
        assertEquals(
            TypeScriptOriginalSourcePosition(ORIGINAL_SOURCE, 3, 1),
            binding.originalPositionFor(GENERATED_SOURCE, 3),
        )
        assertNull(binding.originalPositionFor(GENERATED_SOURCE, 0))
        assertNull(binding.originalPositionFor("/different/main.ts.generated.js", 1))
    }

    @Test
    fun rejectsUnpairedOrMalformedSourceMaps() {
        assertThrows(IllegalArgumentException::class.java) {
            binding("AAAA", file = "other.js")
        }
        assertThrows(IllegalArgumentException::class.java) {
            binding("D")
        }
        assertThrows(IllegalArgumentException::class.java) {
            binding("g")
        }
        assertThrows(IllegalArgumentException::class.java) {
            binding("AAAAAA")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TypeScriptSourceMapBinding.parse(
                sourceMapText = sourceMapJson("AAAA", sourcesContent = "null"),
                generatedSourceName = GENERATED_SOURCE,
                expectedGeneratedFileName = GENERATED_FILE,
                originalSourceName = ORIGINAL_SOURCE,
            )
        }
    }

    @Test
    fun formatsMappedRhinoFramesWithoutInventingAColumn() {
        val binding = binding("AAAA;AAEA;AACA;AAEA")
        val formatted = requireNotNull(
            TypeScriptRuntimeStackTraceFormatter.format(
                binding = binding,
                details = "Error: typescript-runtime-map-marker",
                frames = listOf(
                    ScriptStackElement(GENERATED_SOURCE, "failAtTypeScriptLine", 2),
                    ScriptStackElement("bootstrap.js", "bootstrap", 9),
                ),
            ),
        )

        assertTrue(formatted.startsWith("Error: typescript-runtime-map-marker"))
        assertTrue(formatted.contains("failAtTypeScriptLine ($ORIGINAL_SOURCE:3)"))
        assertTrue(formatted.contains("bootstrap (bootstrap.js:9:0)"))
        assertFalse(formatted.contains(GENERATED_SOURCE))
        assertFalse(formatted.contains("$ORIGINAL_SOURCE:3:"))
    }

    @Test
    fun doesNotRewriteAnUnrelatedRhinoStack() {
        assertNull(
            TypeScriptRuntimeStackTraceFormatter.format(
                binding = binding("AAAA"),
                details = "Error: unrelated",
                frames = listOf(ScriptStackElement("other.js", "other", 1)),
            ),
        )
    }

    private fun binding(
        mappings: String,
        file: String = GENERATED_FILE,
    ): TypeScriptSourceMapBinding = TypeScriptSourceMapBinding.parse(
        sourceMapText = sourceMapJson(mappings, file),
        generatedSourceName = GENERATED_SOURCE,
        expectedGeneratedFileName = GENERATED_FILE,
        originalSourceName = ORIGINAL_SOURCE,
    )

    private fun sourceMapJson(
        mappings: String,
        file: String = GENERATED_FILE,
        sourcesContent: String? = null,
    ): String = buildString {
        append("{\"version\":3,\"file\":\"")
        append(file)
        append("\",\"sourceRoot\":\"\",\"sources\":[\"../sources/")
        append(ORIGINAL_SOURCE)
        append("\"],\"names\":[],\"mappings\":\"")
        append(mappings)
        append('"')
        if (sourcesContent != null) {
            append(",\"sourcesContent\":")
            append(sourcesContent)
        }
        append('}')
    }

    private companion object {
        const val GENERATED_SOURCE = "/storage/emulated/0/Scripts/runtime map.ts.generated.js"
        const val GENERATED_FILE = "runtime map.js"
        const val ORIGINAL_SOURCE = "runtime map.ts"
    }
}
