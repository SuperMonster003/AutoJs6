package org.autojs.autojs.theme.library

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.theme.intelligent.DateCondition
import org.autojs.autojs.theme.intelligent.DateOverride
import org.autojs.autojs.theme.intelligent.GroupOrder
import org.autojs.autojs.theme.intelligent.IntelligentConfig
import org.autojs.autojs.theme.intelligent.IntelligentGroup
import org.autojs.autojs.theme.intelligent.IntervalAnchor
import org.autojs.autojs.theme.intelligent.IntervalUnit
import org.autojs.autojs.theme.intelligent.RotationTrigger
import org.autojs.autojs.theme.intelligent.SortKey
import org.autojs.autojs.theme.intelligent.StateColors
import org.autojs.autojs.theme.intelligent.StateSource
import org.autojs.autojs.theme.library.ColorLibraryJson.ColorLibraryJsonException
import org.autojs.autojs.theme.library.ColorLibraryJson.IntelligentLibrary
import org.autojs.autojs.theme.library.ColorLibraryJson.PlainLibrary
import org.autojs.autojs.theme.library.ColorLibraryJson.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

class ColorLibraryJsonTest {

    private val zone: ZoneId = ZoneOffset.ofHours(8)

    // 2026-10-01T09:00:00+08:00 and 2026-10-06T15:52:00+08:00.
    private val created = 1_790_816_400_000L
    private val exported = 1_791_273_120_000L

    private fun argb(rgb: Long) = (0xFF000000 or rgb).toInt()

    private val plain = PlainLibrary(
        "我的品牌色",
        listOf(
            ColorLibraryJson.Color(argb(0x00695C), "主色", created, created),
            ColorLibraryJson.Color(argb(0x37474F)),
        ),
    )

    // Group ids are database ids (10, 20, 30); the override targets the third group.
    private val intelligent = IntelligentLibrary(
        "暖冷交替",
        IntelligentConfig(
            stateSource = StateSource.NightMode,
            rotation = listOf(RotationTrigger.StateChange, RotationTrigger.Interval(1, IntervalUnit.HOUR, IntervalAnchor.NEXT_HOUR)),
            order = GroupOrder.Sequential(SortKey.HUE, "day", descending = false),
            overrides = listOf(DateOverride(DateCondition.DateRange(12, 24, 12, 26), 30L)),
        ),
        listOf(
            IntelligentGroup(10L, "橙", position = 0, createdTime = created, modifiedTime = created, states = dayNight(0xFFB74D, 0xE65100)),
            IntelligentGroup(20L, "青", position = 1, states = dayNight(0x4DD0E1, 0x006064)),
            IntelligentGroup(
                30L, "圣诞", enabled = false, position = 2,
                states = mapOf("day" to StateColors(argb(0xC62828), accent = argb(0xFFD54F)), "night" to StateColors(argb(0x1B5E20), dark = argb(0x003300))),
            ),
        ),
    )

    private fun dayNight(day: Long, night: Long) = mapOf("day" to StateColors(argb(day)), "night" to StateColors(argb(night)))

    private fun file(libraries: String, schemaVersion: Any = 1, format: String = ColorLibraryJson.FORMAT) =
        """{"format":"$format","schemaVersion":$schemaVersion,"libraries":$libraries}"""

    private fun assertRejected(reason: Reason, text: String, detail: String? = null) {
        try {
            ColorLibraryJson.decode(text)
            fail("expected $reason")
        } catch (e: ColorLibraryJsonException) {
            assertEquals(reason, e.reason)
            detail?.let { assertEquals(it, e.detail) }
        }
    }

    @Test
    fun librariesRoundTripWithGroupsRenumberedByIndex() {
        val text = ColorLibraryJson.encode(listOf(plain, intelligent), exported, zone, ColorLibraryJson.AppInfo("6.8.0", 5319))
        val document = ColorLibraryJson.decode(text)

        assertEquals(exported, document.exportedAt)
        assertEquals(ColorLibraryJson.AppInfo("6.8.0", 5319), document.app)
        assertEquals(0, document.skippedLibraries)
        assertEquals(listOf(0, 0), document.libraries.map { it.droppedValues })
        assertEquals(plain, document.libraries[0].library)

        val decoded = document.libraries[1].library as IntelligentLibrary
        // Groups come back with their file index as id; the override follows its group.
        val renumbered = intelligent.groups.mapIndexed { index, group -> group.copy(id = index.toLong(), position = index) }
        assertEquals(renumbered, decoded.groups)
        assertEquals(intelligent.config.copy(overrides = listOf(DateOverride(DateCondition.DateRange(12, 24, 12, 26), 2L))), decoded.config)
        assertEquals(intelligent.name, decoded.name)
    }

    @Test
    fun encodedFileFollowsAppendixB() {
        val root = JsonParser.parseString(ColorLibraryJson.encode(listOf(plain, intelligent), exported, zone)).asJsonObject
        assertEquals("autojs6.color-library", root["format"].asString)
        assertEquals(1, root["schemaVersion"].asInt)
        assertEquals("2026-10-06T15:52:00+08:00", root["exportedAt"].asString)
        assertFalse(root.has("app"))
        val libraries = root["libraries"].asJsonArray
        val first = libraries[0].asJsonObject
        assertEquals("plain", first["type"].asString)
        val colors = first["colors"].asJsonArray
        assertEquals("""{"name":"主色","color":"#00695C","createdAt":"2026-10-01T09:00:00+08:00","modifiedAt":"2026-10-01T09:00:00+08:00"}""", colors[0].toString())
        assertEquals("""{"color":"#37474F"}""", colors[1].toString())
        val second = libraries[1].asJsonObject
        assertEquals("intelligent", second["type"].asString)
        assertEquals(2, second["config"].asJsonObject["overrides"].asJsonArray[0].asJsonObject["group"].asInt)
        val christmas = second["groups"].asJsonArray[2].asJsonObject
        assertFalse(christmas["enabled"].asBoolean)
        assertEquals("""{"primary":"#C62828","accent":"#FFD54F"}""", christmas["states"].asJsonObject["day"].toString())
    }

    @Test
    fun unknownFieldsAreIgnoredAndUnknownLibraryTypesSkipped() {
        val document = ColorLibraryJson.decode(
            """{"format":"autojs6.color-library","schemaVersion":1,"future":{"x":1},"libraries":[
                {"type":"plain","name":"A","colors":[{"color":"#112233","tag":"x"}],"extra":true},
                {"type":"gradient","name":"B"},
                "not an object"]}"""
        )
        assertEquals(2, document.skippedLibraries)
        assertEquals(PlainLibrary("A", listOf(ColorLibraryJson.Color(argb(0x112233)))), document.libraries.single().library)
        assertEquals(null, document.exportedAt)
    }

    @Test
    fun filesThatAreNotImportableAreRejected() {
        assertRejected(Reason.NEWER_SCHEMA, file("[]", schemaVersion = 99), detail = "99")
        assertRejected(Reason.NOT_A_COLOR_LIBRARY, file("[]", format = "autojs6.something-else"))
        assertRejected(Reason.NOT_A_COLOR_LIBRARY, file("[]", schemaVersion = "\"1\""))
        assertRejected(Reason.NOT_A_COLOR_LIBRARY, """{"format":"autojs6.color-library","schemaVersion":1}""")
        assertRejected(Reason.MALFORMED, "{not json")
        assertRejected(Reason.MALFORMED, "[1, 2]")
        assertRejected(Reason.NO_LIBRARIES, file("[]"))
        assertRejected(Reason.NO_LIBRARIES, file("""[{"type":"unknown"}]"""))
    }

    @Test
    fun invalidColorValuesAreDroppedAndCounted() {
        val document = ColorLibraryJson.decode(
            file(
                """[
                {"type":"plain","name":"P","colors":[{"color":"#12345"},{"color":"red"},{"color":123},{"name":"x"},{"color":"#80FF0000"},{"color":"#00ff00"}]},
                {"type":"intelligent","name":"I","config":{"overrides":[{"when":{"type":"dateRange","from":"01-01","to":"01-02"},"group":5}]},
                 "groups":[{"states":{"day":{"primary":"#ZZZZZZ"},"night":{"primary":"#000000","accent":"blue","dark":"#111111"}}}, 7]}
                ]"""
            )
        )
        val (plainLibrary, intelligentLibrary) = document.libraries
        assertEquals(4, plainLibrary.droppedValues)
        // #80FF0000 is accepted and made opaque.
        assertEquals(listOf(argb(0xFF0000), argb(0x00FF00)), (plainLibrary.library as PlainLibrary).colors.map { it.color })
        // Invalid day primary, invalid accent, the non-object group, and the override to a missing group.
        assertEquals(4, intelligentLibrary.droppedValues)
        val library = intelligentLibrary.library as IntelligentLibrary
        assertEquals(mapOf("night" to StateColors(argb(0x000000), dark = argb(0x111111))), library.groups.single().states)
        assertTrue(library.config.overrides.isEmpty())
    }

    @Test
    fun limitsAreEnforcedOnBothSides() {
        val tooManyLibraries = (0..ColorLibraryJson.MAX_LIBRARIES).joinToString(",", "[", "]") { """{"type":"plain","name":"L$it"}""" }
        assertRejected(Reason.TOO_MANY_LIBRARIES, file(tooManyLibraries))
        val tooManyColors = (0..ColorLibraryJson.MAX_ITEMS_PER_LIBRARY).joinToString(",", "[", "]") { """{"color":"#000000"}""" }
        assertRejected(Reason.TOO_MANY_ITEMS, file("""[{"type":"plain","name":"Big","colors":$tooManyColors}]"""), detail = "Big")
        assertRejected(Reason.TOO_LARGE, file("""[{"type":"plain","name":"${"x".repeat(ColorLibraryJson.MAX_FILE_BYTES)}"}]"""))

        val big = PlainLibrary("Big", List(ColorLibraryJson.MAX_ITEMS_PER_LIBRARY + 1) { ColorLibraryJson.Color(argb(0)) })
        try {
            ColorLibraryJson.encode(listOf(big), exported, zone)
            fail("expected TOO_MANY_ITEMS")
        } catch (e: ColorLibraryJsonException) {
            assertEquals(Reason.TOO_MANY_ITEMS, e.reason)
        }
        // The limits themselves are allowed.
        val atLimit = PlainLibrary("Edge", List(ColorLibraryJson.MAX_ITEMS_PER_LIBRARY) { ColorLibraryJson.Color(argb(it.toLong())) })
        val decoded = ColorLibraryJson.decode(ColorLibraryJson.encode(listOf(atLimit), exported, zone))
        assertEquals(ColorLibraryJson.MAX_ITEMS_PER_LIBRARY, decoded.libraries.single().library.itemCount)
    }

    @Test
    fun namesAreNormalizedWithAFallback() {
        val document = ColorLibraryJson.decode(
            file("""[{"type":"plain","name":"  Ocean \t  Blues "},{"type":"plain","name":"   "},{"type":"intelligent"}]"""),
            fallbackName = "Imported",
        )
        assertEquals(listOf("Ocean Blues", "Imported", "Imported"), document.libraries.map { it.library.name })
        // A library without groups or config imports as an empty default library.
        assertEquals(IntelligentLibrary("Imported", IntelligentConfig(), emptyList()), document.libraries[2].library)
    }

    @Test
    fun unsupportedConfigurationSurvivesARoundTrip() {
        val config = JsonObject().apply {
            add("stateSource", JsonObject().apply { addProperty("type", "battery"); addProperty("levels", 3) })
            add("rotation", JsonArray().apply { add(JsonObject().apply { addProperty("type", "daily"); addProperty("at", "08:00") }) })
        }
        val text = file("""[{"type":"intelligent","name":"Future","config":$config,"groups":[{"states":{"default":{"primary":"#123456"}}}]}]""")
        val library = ColorLibraryJson.decode(text).libraries.single().library as IntelligentLibrary
        assertTrue(library.config.hasUnsupportedItems)
        assertTrue(library.config.stateSource is StateSource.Unsupported)

        val again = JsonParser.parseString(ColorLibraryJson.encode(listOf(library), exported, zone)).asJsonObject
        val writtenConfig = again["libraries"].asJsonArray[0].asJsonObject["config"].asJsonObject
        assertEquals(config["stateSource"], writtenConfig["stateSource"])
        assertEquals(config["rotation"], writtenConfig["rotation"])
    }

    @Test
    fun colorsAreFormattedAndParsedStrictly() {
        assertEquals("#00695C", ColorLibraryJson.formatColor(argb(0x00695C)))
        assertEquals("#00695C", ColorLibraryJson.formatColor(0x0000695C))
        assertEquals(argb(0xABCDEF), ColorLibraryJson.parseColor("#abcdef"))
        listOf(null, "", "abcdef", "#abc", "#abcdeg", " #abcdef", "#abcdef ").forEach {
            assertEquals("input: $it", null, ColorLibraryJson.parseColor(it))
        }
    }

}
