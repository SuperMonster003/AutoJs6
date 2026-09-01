package org.autojs.autojs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.Charset

class JavaScriptFileSourceEncodingTest {

    @Test
    fun unicodeBomScriptsAreDecodedConsistentlyForDirectAndReaderAccess() {
        val script = "\"ui\";\ntoast(\"你好\");"
        val variants = listOf(
            EncodingVariant("UTF-8", intArrayOf(0xEF, 0xBB, 0xBF)),
            EncodingVariant("UTF-16BE", intArrayOf(0xFE, 0xFF)),
            EncodingVariant("UTF-16LE", intArrayOf(0xFF, 0xFE)),
            EncodingVariant("UTF-32BE", intArrayOf(0x00, 0x00, 0xFE, 0xFF)),
            EncodingVariant("UTF-32LE", intArrayOf(0xFF, 0xFE, 0x00, 0x00)),
        )

        variants.forEach { variant ->
            val file = File.createTempFile("autojs6-script-encoding-", ".js")
            try {
                file.writeBytes(variant.bom + script.toByteArray(variant.charset))
                val source = JavaScriptFileSource(file)

                assertEquals(variant.charset.name(), script, source.script)
                requireNotNull(source.scriptReader).use { reader ->
                    assertEquals(variant.charset.name(), script, reader.readText())
                }
                assertTrue(
                    variant.charset.name(),
                    source.executionMode and JavaScriptSource.EXECUTION_MODE_UI != 0,
                )
            } finally {
                file.delete()
            }
        }
    }

    private data class EncodingVariant(
        val charsetName: String,
        val bomBytes: IntArray,
    ) {
        val charset: Charset = Charset.forName(charsetName)
        val bom: ByteArray = bomBytes.map { it.toByte() }.toByteArray()
    }
}
