package org.autojs.autojs.core.ui.preview

import org.autojs.plugin.ui.preview.UiPreviewContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPreviewBoundsTest {

    private fun entry(index: Int, instance: Int = 0, id: String? = null, className: String = "JsTextView") =
        UiPreviewBoundsEntry(index, 10 * index, 20 * index, 10 * index + 100, 20 * index + 40, className, id, instance, 0)

    @Test
    fun entriesAreSortedByElementThenInstanceAndUseContractMembers() {
        val encoded = UiPreviewBounds.encode(listOf(entry(2, 1), entry(0, 0, "root", "JsLinearLayout"), entry(2, 0), entry(1)))
        assertEquals(4, encoded.count)
        assertFalse(encoded.truncated)
        assertEquals(
            "[{\"i\":0,\"l\":0,\"t\":0,\"r\":100,\"b\":40,\"c\":\"JsLinearLayout\",\"d\":\"root\",\"n\":0,\"s\":0}," +
                "{\"i\":1,\"l\":10,\"t\":20,\"r\":110,\"b\":60,\"c\":\"JsTextView\",\"n\":0,\"s\":0}," +
                "{\"i\":2,\"l\":20,\"t\":40,\"r\":120,\"b\":80,\"c\":\"JsTextView\",\"n\":0,\"s\":0}," +
                "{\"i\":2,\"l\":20,\"t\":40,\"r\":120,\"b\":80,\"c\":\"JsTextView\",\"n\":1,\"s\":0}]",
            encoded.json,
        )
        assertTrue(encoded.json.contains("\"${UiPreviewContract.Bounds.ELEMENT_INDEX}\":0"))
    }

    @Test
    fun entriesBeyondTheLimitAreDroppedAndFlagged() {
        val encoded = UiPreviewBounds.encode((0 until 10).map { entry(it) }, limit = 3)
        assertEquals(3, encoded.count)
        assertTrue(encoded.truncated)
        assertEquals(3, Regex("\"i\":").findAll(encoded.json).count())
        assertTrue(encoded.json.endsWith("\"n\":0,\"s\":0}]"))
        assertEquals("[]", UiPreviewBounds.encode(emptyList()).json)
    }

    @Test
    fun stringsAreEscapedAsJson() {
        assertEquals("\"a\\\"b\\\\c\\nd\\u0001\"", UiPreviewBounds.quote("a\"b\\c\nd\u0001"))
        val encoded = UiPreviewBounds.encode(listOf(entry(0, id = "quote\"d")))
        assertTrue(encoded.json.contains("\"d\":\"quote\\\"d\""))
    }

    @Test
    fun diagnosticsCarryCodeMessageElementAndAttribute() {
        val json = UiPreviewBounds.encodeDiagnostics(
            listOf(
                UiPreviewDiagnostic(UiPreviewContract.ERROR_BINDING_FAILED, "ReferenceError: \"name\" is not defined", 3, "text"),
                UiPreviewDiagnostic(UiPreviewContract.ERROR_BINDING_FAILED, "x", -1, null),
            ),
        )
        assertEquals(
            "[{\"code\":\"ERR_AUTOJS6_UI_PREVIEW_BINDING_FAILED\",\"message\":\"ReferenceError: \\\"name\\\" is not defined\",\"i\":3,\"a\":\"text\"}," +
                "{\"code\":\"ERR_AUTOJS6_UI_PREVIEW_BINDING_FAILED\",\"message\":\"x\",\"i\":-1}]",
            json,
        )
        assertEquals("[]", UiPreviewBounds.encodeDiagnostics(emptyList()))
        // Contract v3: an ignored attribute carries its outcome.
        assertEquals(
            "[{\"code\":\"ERR_AUTOJS6_UI_PREVIEW_ATTRIBUTE_IGNORED\",\"message\":\"m\",\"i\":2,\"a\":\"android:onClick\",\"o\":\"unsupported\"}]",
            UiPreviewBounds.encodeDiagnostics(
                listOf(UiPreviewDiagnostic(UiPreviewContract.ERROR_ATTRIBUTE_IGNORED, "m", 2, "android:onClick", UiPreviewContract.ATTRIBUTE_OUTCOME_UNSUPPORTED)),
            ),
        )
    }
}
