package org.autojs.autojs.engine

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class NodeScreenTextCodecTest {
    @Test fun capsItemsAndPreservesBoundsAndUnknownConfidence() {
        val line = NodeScreenTextCodec.Line("文字", NodeScreenTextCodec.Bounds(1, 2, 3, 4), Double.NaN)
        val values = JsonParser.parseString(NodeScreenTextCodec.encode(generateSequence { line })).asJsonArray
        assertEquals(400, values.size())
        assertTrue(values[0].asJsonObject["confidence"].isJsonNull)
        assertEquals(4, values[0].asJsonObject["bounds"].asJsonObject["bottom"].asInt)
    }

    @Test fun encodedUtf8IncludingEscapesStaysWithinBudget() {
        val line = NodeScreenTextCodec.Line("中\n\"\\".repeat(100), null, 0.9)
        val encoded = NodeScreenTextCodec.encode(generateSequence { line })
        assertTrue(encoded.toByteArray(Charsets.UTF_8).size <= 65536)
        assertTrue(JsonParser.parseString(encoded).asJsonArray.size() in 1..399)
    }

    @Test fun oversizedFirstItemDoesNotLeakPartialJson() {
        val line = NodeScreenTextCodec.Line("x".repeat(65537), null, null)
        assertEquals("[]", NodeScreenTextCodec.encode(sequenceOf(line)))
    }
}
