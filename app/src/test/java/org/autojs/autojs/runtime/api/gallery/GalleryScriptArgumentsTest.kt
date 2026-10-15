package org.autojs.autojs.runtime.api.gallery

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.gallery.GalleryConvertOptions
import org.autojs.autojs.core.plugin.gallery.GalleryEncodeOptions
import org.autojs.autojs.core.plugin.gallery.GalleryPickerOptions
import org.autojs.autojs.core.plugin.gallery.GalleryPluginException
import org.autojs.plugin.gallery.api.GalleryContract
import org.autojs.plugin.gallery.api.GalleryErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The pure argument rules of the `gallery` script API (3-Maple Image roadmap M6, 10.1). */
class GalleryScriptArgumentsTest {

    private fun json(text: String): JsonElement = JsonParser.parseString(text)

    private fun refused(code: String = GalleryErrorCodes.INVALID_ARGUMENT, block: () -> Unit): String {
        try {
            block()
            fail("expected $code")
        } catch (e: GalleryPluginException) {
            assertEquals(code, e.code)
            return e.error.message
        }
        throw AssertionError()
    }

    @Test
    fun pickDefaultsToASingleImageAndNormalizesFilters() {
        assertEquals(GalleryPickerOptions(), GalleryScriptArguments.pick(null))
        assertEquals(
            GalleryPickerOptions(multiple = true, maxCount = 5, mimeTypes = listOf("image/png", "image/*"), title = "Choose"),
            GalleryScriptArguments.pick(json("""{"multiple":true,"maxCount":5,"mimeTypes":[" Image/PNG ","image/*","image/png"],"title":"  Choose "}""")),
        )
        assertEquals(listOf("image/jpeg"), GalleryScriptArguments.pick(json("""{"mimeTypes":"image/jpeg"}""")).mimeTypes)
        assertNull(GalleryScriptArguments.pick(json("""{"title":"   "}""")).title)
    }

    @Test
    fun pickRefusesUnknownKeysAndOutOfRangeValues() {
        assertTrue(refused { GalleryScriptArguments.pick(json("""{"multi":true}""")) }.contains("options.multi"))
        assertTrue(refused { GalleryScriptArguments.pick(json("""{"maxCount":3}""")) }.contains("multiple"))
        refused { GalleryScriptArguments.pick(json("""{"multiple":true,"maxCount":0}""")) }
        refused { GalleryScriptArguments.pick(json("""{"multiple":true,"maxCount":${GalleryContract.MAX_PICK_COUNT + 1}}""")) }
        refused { GalleryScriptArguments.pick(json("""{"multiple":true,"maxCount":2.5}""")) }
        refused { GalleryScriptArguments.pick(json("""{"multiple":"yes"}""")) }
        refused { GalleryScriptArguments.pick(json("""{"mimeTypes":["video/mp4"]}""")) }
        refused { GalleryScriptArguments.pick(json("""{"mimeTypes":[1]}""")) }
        val many = (1..GalleryContract.MAX_MIME_TYPES + 1).joinToString(",") { "\"image/x$it\"" }
        refused { GalleryScriptArguments.pick(json("""{"mimeTypes":[$many]}""")) }
        refused { GalleryScriptArguments.pick(json("""{"title":"${"t".repeat(GalleryContract.MAX_TITLE_LENGTH + 1)}"}""")) }
        refused { GalleryScriptArguments.pick(json("[]")) }
    }

    @Test
    fun viewTakesOnePathOrAnArrayAndAStartIndex() {
        val one = GalleryScriptArguments.view(json("\"a.jpg\""), null)
        assertEquals(listOf("a.jpg"), one.paths)
        assertEquals(0, one.index)
        val two = GalleryScriptArguments.view(json("""["a.jpg","/sdcard/b.png"]"""), json("""{"index":1}"""))
        assertEquals(listOf("a.jpg", "/sdcard/b.png"), two.paths)
        assertEquals(1, two.index)
        refused { GalleryScriptArguments.view(null, null) }
        refused { GalleryScriptArguments.view(json("[]"), null) }
        refused { GalleryScriptArguments.view(json("""["a.jpg"]"""), json("""{"index":1}""")) }
        refused { GalleryScriptArguments.view(json("""["a.jpg"]"""), json("""{"startIndex":0}""")) }
        assertTrue(refused { GalleryScriptArguments.view(json("""["content://media/1"]"""), null) }.contains("paths[0]"))
        val pages = (0..GalleryContract.MAX_VIEW_PAGES).joinToString(",") { "\"p$it.jpg\"" }
        refused(GalleryErrorCodes.LIMIT_EXCEEDED) { GalleryScriptArguments.view(json("[$pages]"), null) }
    }

    @Test
    fun editCollectsTheOutputAndTheEncoding() {
        val defaults = GalleryScriptArguments.edit(null)
        assertNull(defaults.output)
        assertFalse(defaults.overwrite)
        assertEquals(GalleryEncodeOptions(), defaults.encode)
        val given = GalleryScriptArguments.edit(json("""{"output":"out/a.png","overwrite":true,"format":"JPG","quality":80,"keepExif":true,"keepLocation":true}"""))
        assertEquals("out/a.png", given.output)
        assertTrue(given.overwrite)
        assertEquals(GalleryEncodeOptions(GalleryContract.FORMAT_JPEG, 80, keepExif = true, keepLocation = true), given.encode)
        refused { GalleryScriptArguments.edit(json("""{"format":"gif"}""")) }
        refused { GalleryScriptArguments.edit(json("""{"quality":0}""")) }
        refused { GalleryScriptArguments.edit(json("""{"output":"file:///sdcard/a.png"}""")) }
        refused { GalleryScriptArguments.edit(json("""{"output":" "}""")) }
        refused { GalleryScriptArguments.edit(json("""{"lossless":true}""")) }
    }

    @Test
    fun convertInfersTheSizeModeFromTheSizeOptions() {
        assertEquals(GalleryConvertOptions(), GalleryScriptArguments.convert(null).options)
        assertEquals(GalleryConvertOptions(), GalleryScriptArguments.convert(json("""{"sizeMode":"original"}""")).options)
        val percent = GalleryScriptArguments.convert(json("""{"percent":50}""")).options
        assertEquals(GalleryContract.SIZE_PERCENT, percent.sizeMode)
        assertEquals(50, percent.percent)
        val longEdge = GalleryScriptArguments.convert(json("""{"sizeMode":"longEdge","longEdge":1920}""")).options
        assertEquals(GalleryContract.SIZE_LONG_EDGE, longEdge.sizeMode)
        assertEquals(1920, longEdge.longEdge)
        val custom = GalleryScriptArguments.convert(json("""{"width":800}""")).options
        assertEquals(GalleryContract.SIZE_CUSTOM, custom.sizeMode)
        assertEquals(800, custom.width)
        assertNull(custom.height)
        assertTrue(custom.keepAspectRatio)
        val stretched = GalleryScriptArguments.convert(json("""{"width":800,"height":600,"keepAspectRatio":false}""")).options
        assertFalse(stretched.keepAspectRatio)

        refused { GalleryScriptArguments.convert(json("""{"percent":50,"longEdge":100}""")) }
        refused { GalleryScriptArguments.convert(json("""{"sizeMode":"percent","width":10}""")) }
        refused { GalleryScriptArguments.convert(json("""{"sizeMode":"percent"}""")) }
        refused { GalleryScriptArguments.convert(json("""{"percent":1001}""")) }
        refused { GalleryScriptArguments.convert(json("""{"longEdge":${GalleryContract.MAX_DIMENSION + 1}}""")) }
        refused { GalleryScriptArguments.convert(json("""{"sizeMode":"custom"}""")) }
        refused { GalleryScriptArguments.convert(json("""{"width":100,"keepAspectRatio":false}""")) }
        refused { GalleryScriptArguments.convert(json("""{"keepAspectRatio":true}""")) }
        refused { GalleryScriptArguments.convert(json("""{"sizeMode":"fit"}""")) }
    }

    @Test
    fun convertChecksTheEncodingCombinations() {
        val lossless = GalleryScriptArguments.convert(json("""{"format":"webp","lossless":true}""")).options
        assertTrue(lossless.lossless)
        assertEquals(GalleryContract.FORMAT_WEBP, lossless.encode.format)
        val target = GalleryScriptArguments.convert(json("""{"format":"jpeg","targetBytes":204800}""")).options
        assertEquals(204_800L, target.targetBytes)
        assertEquals(9_000_000_000L, GalleryScriptArguments.convert(json("""{"targetBytes":9000000000}""")).options.targetBytes)
        refused { GalleryScriptArguments.convert(json("""{"format":"png","lossless":true}""")) }
        refused { GalleryScriptArguments.convert(json("""{"format":"png","targetBytes":1000}""")) }
        refused { GalleryScriptArguments.convert(json("""{"format":"webp","lossless":true,"targetBytes":1000}""")) }
        refused { GalleryScriptArguments.convert(json("""{"quality":80,"targetBytes":1000}""")) }
        refused { GalleryScriptArguments.convert(json("""{"targetBytes":0}""")) }
        refused { GalleryScriptArguments.convert(json("""{"targetBytes":1.5}""")) }
    }

    @Test
    fun backgroundAcceptsArgbNumbersAndHexStrings() {
        fun background(value: String) = GalleryScriptArguments.convert(json("""{"background":$value}""")).options.background
        assertEquals(0xFFFFFFFF.toInt(), background("\"#fff\""))
        assertEquals(0xFF102030.toInt(), background("\"#102030\""))
        assertEquals(0x80102030.toInt(), background("\"#80102030\""))
        assertEquals(0xFF000000.toInt(), background("4278190080"))
        assertEquals(-1, background("-1"))
        assertNull(GalleryScriptArguments.convert(json("""{"background":null}""")).options.background)
        refused { background("\"white\"") }
        refused { background("\"#12345\"") }
        refused { background("4294967296") }
        refused { background("[255]") }
    }

    @Test
    fun closeFinishesTheViewerByDefault() {
        assertTrue(GalleryScriptArguments.close(null).finish)
        assertFalse(GalleryScriptArguments.close(json("""{"finish":false}""")).finish)
        refused { GalleryScriptArguments.close(json("""{"finish":1}""")) }
        refused { GalleryScriptArguments.close(json("""{"force":true}""")) }
    }
}
