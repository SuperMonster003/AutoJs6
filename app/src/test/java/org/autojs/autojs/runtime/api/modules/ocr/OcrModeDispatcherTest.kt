package org.autojs.autojs.runtime.api.modules.ocr

import org.autojs.autojs.runtime.api.modules.ocr.Ocr.Companion.OcrMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * `ocr.recognizeText` / `ocr.detect` pick the engine implementation from the `OcrMode` property of the same
 * name (case-insensitive); the lookup must see exactly the enum's constructor properties.
 * zh-CN: `ocr.recognizeText` / `ocr.detect` 从同名 (不区分大小写) 的 `OcrMode` 属性中取引擎实现; 查找范围必须恰好是枚举的构造属性.
 */
class OcrModeDispatcherTest {

    @Test
    fun `resolves the constructor properties case-insensitively`() {
        assertEquals(
            listOf("value", "recognizeText", "detect", "engine"),
            listOf("VALUE", "recognizetext", "Detect", "engine").map { OcrMode.dispatcherProperty(it)?.name },
        )
        OcrMode.entries.forEach { mode ->
            assertSame(mode.name, mode.recognizeText, OcrMode.dispatcherProperty("recognizeText")!!.get(mode))
            assertSame(mode.name, mode.detect, OcrMode.dispatcherProperty("detect")!!.get(mode))
            assertEquals(mode.name, mode.value, OcrMode.dispatcherProperty("value")!!.get(mode))
        }
    }

    @Test
    fun `ignores enum constants, inherited members, synthetic fields and unknown names`() {
        listOf("MLKIT", "AUTO", "name", "ordinal", "\$VALUES", "\$ENTRIES", "entries", "missing").forEach {
            assertNull(it, OcrMode.dispatcherProperty(it))
        }
    }

}
