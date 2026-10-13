package org.autojs.plugin.typescript.parser

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScriptParserContractTest {

    @Test
    fun contractHashMatchesItsInput() {
        assertEquals(1, TypeScriptParserContract.VERSION)
        assertEquals(
            TypeScriptParserContract.HASH,
            MessageDigest.getInstance("SHA-256")
                .digest(TypeScriptParserContract.HASH_INPUT.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun parserTransactionsAreFrozenInOrder() {
        // Binder transaction codes follow the AIDL method order; reordering breaks every peer.
        val expected = listOf(
            "getContractVersion()->int",
            "getContractHash()->String",
            "getParserInfo()->Bundle",
            "parse(Bundle,ParcelFileDescriptor)->Bundle",
        )
        val described = TypeScriptParserContract.HASH_INPUT.split('|').drop(1).takeWhile { !it.startsWith("bundle:") }
        assertEquals(expected, described)
        val aidl = File("src/main/aidl/org/autojs/plugin/typescript/parser/ITypeScriptParserPlugin.aidl").readText()
        val declared = Regex("""^\s*(?:\w+)\s+(\w+)\(""", RegexOption.MULTILINE).findAll(aidl).map { it.groupValues[1] }.toList()
        assertEquals(listOf("getContractVersion", "getContractHash", "getParserInfo", "parse"), declared)
    }

    @Test
    fun theParserStaysSeparateFromTheCompilerService() {
        assertEquals("org.autojs.plugin.typescript.PARSER", TypeScriptParserContract.ACTION)
        assertTrue(TypeScriptParserContract.CAPABILITY_CONTRACT_VERSION.startsWith("typescriptParser"))
        assertTrue(TypeScriptParserContract.CAPABILITY_SERVICE_ACTION.startsWith("typescriptParser"))
        assertTrue(TypeScriptParserContract.CAPABILITY_TREE_SCHEMA_REVISION.startsWith("typescriptParser"))
        assertTrue(".tsx" in TypeScriptParserContract.SUPPORTED_EXTENSIONS)
        assertTrue(TypeScriptParserContract.ERROR_BUSY.startsWith("ERR_AUTOJS6_TYPESCRIPT_PARSER_"))
    }

    @Test
    fun limitsAreConsistent() {
        with(TypeScriptParserContract) {
            assertTrue(DEFAULT_TIMEOUT_MS in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS)
            // UTF-16 source text in the request parcel stays below half of the 1 MiB Binder buffer.
            assertTrue(MAX_SOURCE_CHARS * 2L < 512L * 1024L + 1)
            assertTrue(MAX_CONCURRENT_PARSES in 1..4)
            assertTrue(MAX_TREE_NODES > 0 && MAX_TREE_BYTES > 0 && MAX_DIAGNOSTICS > 0)
            assertEquals(3, TERMINAL_OUTCOMES.toSet().size)
        }
    }

    @Test
    fun treeMemberNamesAreDistinct() {
        val tree = with(TypeScriptParserContract.Tree) { listOf(SCHEMA_REVISION, COMPILER_VERSION, FILE_NAME, LANGUAGE_VARIANT, NODE_COUNT, DIAGNOSTICS, ROOT) }
        val node = with(TypeScriptParserContract.Node) { listOf(KIND, START, END, TEXT, DECLARATION_KIND, OPERATOR, TRIVIA_ONLY) }
        val diagnostic = with(TypeScriptParserContract.Diagnostic) { listOf(START, LENGTH, CODE, CATEGORY, MESSAGE) }
        for (names in listOf(tree, node, diagnostic)) assertEquals(names.size, names.toSet().size)
    }
}
