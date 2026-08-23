package org.autojs.autojs.core.plugin.typescript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun mapsNodeV8FramesWithRealColumnsAndKeepsUnrelatedFrames() {
        val generatedSource = "autojs-typescript-entry.generated.mjs"
        val originalSource = "node task.mts"
        val binding = TypeScriptSourceMapBinding.parse(
            sourceMapText =
                "{\"version\":3,\"file\":\"main.mjs\",\"sourceRoot\":\"\"," +
                    "\"sources\":[\"../sources/node task.mts\"],\"names\":[]," +
                    "\"mappings\":\"AAAA;AAEI\"}",
            generatedSourceName = generatedSource,
            expectedGeneratedFileName = "main.mjs",
            originalSourceName = originalSource,
        )
        val formatted = requireNotNull(
            TypeScriptNodeStackTraceFormatter.format(
                binding = binding,
                stack = buildString {
                    appendLine("Error: node-typescript-map-marker")
                    appendLine("    at fail (file:///runtime/$generatedSource:2:7)")
                    append("    at untouched (bootstrap.mjs:4:9)")
                },
            ),
        )

        assertTrue(formatted.contains("fail ($originalSource:3:5)"))
        assertTrue(formatted.contains("untouched (bootstrap.mjs:4:9)"))
        assertFalse(formatted.contains(generatedSource))

        val flattened = requireNotNull(
            TypeScriptNodeStackTraceFormatter.format(
                binding = binding,
                stack = "Error: flattened     at fail ($generatedSource:2:7)     " +
                    "at untouched (bootstrap.mjs:4:9)",
            ),
        )
        assertTrue(flattened.contains("fail ($originalSource:3:5)"))
        assertTrue(flattened.contains("untouched (bootstrap.mjs:4:9)"))
        assertTrue(flattened.contains('\n'))
    }

    @Test
    fun mapsMultipleNodeModulesAndComposesEntryBootstrapLineOffset() {
        fun projectBinding(
            generated: String,
            generatedFile: String,
            original: String,
            lineOffset: Int = 0,
        ) = TypeScriptSourceMapBinding.parse(
            sourceMapText =
                "{\"version\":3,\"file\":\"$generatedFile\",\"sourceRoot\":\"\"," +
                    "\"sources\":[\"../sources/${original.substringAfterLast('/')}\"]," +
                    "\"names\":[],\"mappings\":\"AAAA;AACA\"}",
            generatedSourceName = generated,
            expectedGeneratedFileName = generatedFile,
            originalSourceName = original,
            generatedLineOffset = lineOffset,
        )

        val bindings = TypeScriptSourceMapBindings.of(
            listOf(
                projectBinding("/runtime/main.js", "main.js", "/project/main.ts", 3),
                projectBinding("/runtime/lib/value.js", "value.js", "/project/lib/value.ts"),
            ),
        )
        val formatted = requireNotNull(
            TypeScriptNodeStackTraceFormatter.format(
                bindings = bindings,
                stack = buildString {
                    appendLine("Error: multi-module")
                    appendLine("    at entry (file:///runtime/main.js:4:1)")
                    append("    at imported (/runtime/lib/value.js:2:1)")
                },
            ),
        )

        assertTrue(formatted.contains("entry (/project/main.ts:1:1)"))
        assertTrue(formatted.contains("imported (/project/lib/value.ts:2:1)"))
        assertFalse(formatted.contains("/runtime/main.js"))
        assertFalse(formatted.contains("/runtime/lib/value.js"))
    }

    @Test
    fun leavesAmbiguousGeneratedBasenamesUnmapped() {
        fun sharedBinding(directory: String, original: String) = TypeScriptSourceMapBinding.parse(
            sourceMapText =
                "{\"version\":3,\"file\":\"shared.js\",\"sourceRoot\":\"\"," +
                    "\"sources\":[\"$original\"],\"names\":[],\"mappings\":\"AAAA\"}",
            generatedSourceName = "/runtime/$directory/shared.js",
            expectedGeneratedFileName = "shared.js",
            originalSourceName = original,
        )
        val bindings = TypeScriptSourceMapBindings.of(
            listOf(sharedBinding("a", "a.ts"), sharedBinding("b", "b.ts")),
        )

        val mappedByRelativeSuffix = TypeScriptNodeStackTraceFormatter.format(
            bindings = bindings,
            stack = "Error: workspace     at fail (/plugin/workspace/sandbox/a/shared.js:1:1)",
        )
        assertNotNull(mappedByRelativeSuffix)
        assertTrue(requireNotNull(mappedByRelativeSuffix).contains("fail (a.ts:1:1)"))

        assertNull(
            TypeScriptNodeStackTraceFormatter.format(
                bindings = bindings,
                stack = "Error: ambiguous     at fail (shared.js:1:1)",
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
