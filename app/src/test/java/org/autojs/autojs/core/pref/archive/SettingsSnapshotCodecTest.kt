package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.SettingsJson.SettingsJsonException
import org.autojs.autojs.core.pref.TypedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P6.1: the category files of an archive ([SettingsSnapshotCodec], T6, appendix B).
 *
 * zh-CN: ROADMAP P6.1: 归档中分类文件的编解码 (T6, 附录 B).
 */
class SettingsSnapshotCodecTest {

    @Test
    fun theSevenTypesRoundTrip() {
        val category = SettingsSnapshot.Category(
            "editor_general",
            linkedMapOf(
                "default" to linkedMapOf(
                    "a_bool" to TypedValue("bool", true),
                    "b_int" to TypedValue("int", -7),
                    "c_long" to TypedValue("long", 5L),
                    "d_long" to TypedValue("long", 3_000_000_000L),
                    "e_float" to TypedValue("float", 0.1f),
                    "f_float" to TypedValue("float", 2f),
                    // A text that holds JSON stays a string: only a declaration makes it json (T6).
                    "g_string" to TypedValue("string", "{\"not\": \"a document\"}"),
                    "h_set" to TypedValue("stringSet", linkedSetOf("a", "b")),
                    "i_json" to TypedValue("json", """{"a":[1,2.5,"x"],"b":null,"c":{"d":true}}"""),
                ),
                "plugin_center_recent" to linkedMapOf("x" to TypedValue("stringSet", emptySet<String>())),
            ),
        )
        val text = SettingsSnapshotCodec.categoryText(category)
        val decoded = SettingsSnapshotCodec.decodeCategoryText(text)
        assertEquals(category, decoded.category)
        assertEquals(emptyList<String>(), decoded.rejected)
        assertEquals(listOf("default", "plugin_center_recent"), decoded.category.stores.keys.toList())
        assertEquals(category.stores.getValue("default").keys.toList(), decoded.category.stores.getValue("default").keys.toList())
        val values = decoded.category.stores.getValue("default")
        // A long that fits an Int and a float without a fraction keep their types.
        assertTrue(values.getValue("c_long").value is Long)
        assertTrue(values.getValue("f_float").value is Float)
        assertEquals(10, decoded.category.entries)

        // The file is indented by 2; a stringSet is an array and a json value the document itself (appendix B).
        assertTrue(text, text.startsWith("{\n  \"id\": \"editor_general\",\n  \"stores\": {\n    \"default\": {\n"))
        val root = SettingsJson.parse(text) as Map<*, *>
        assertEquals(listOf("id", "stores"), root.keys.toList())
        val entries = (root["stores"] as Map<*, *>)["default"] as Map<*, *>
        assertEquals(mapOf("t" to "long", "v" to 5), entries["c_long"])
        assertEquals(mapOf("t" to "long", "v" to 3_000_000_000L), entries["d_long"])
        assertEquals(mapOf("t" to "stringSet", "v" to listOf("a", "b")), entries["h_set"])
        assertEquals(
            mapOf("t" to "json", "v" to mapOf("a" to listOf(1, 2.5, "x"), "b" to null, "c" to mapOf("d" to true))),
            entries["i_json"],
        )
    }

    @Test
    fun jsonValuesReadBackAsCompactText() {
        val category = SettingsSnapshot.Category(
            "general",
            mapOf("default" to mapOf("list" to TypedValue("json", "[ \"b\" , \"a\" ]"), "map" to TypedValue("json", "{ \"z\" : 1, \"a\" : [ ] }"))),
        )
        val values = SettingsSnapshotCodec.decodeCategoryText(SettingsSnapshotCodec.categoryText(category)).category.stores.getValue("default")
        assertEquals(TypedValue("json", "[\"b\",\"a\"]"), values["list"])
        assertEquals(TypedValue("json", "{\"z\":1,\"a\":[]}"), values["map"])
    }

    @Test
    fun entriesThatDoNotFitTheirTagsAreLeftOut() {
        val text = """
            {
              "id": "general",
              "future": 1,
              "stores": {
                "default": {
                  "a_ok": {"t": "int", "v": 1, "future": true},
                  "b_unknown": {"t": "double", "v": 0.5},
                  "c_int": {"t": "int", "v": 3000000000},
                  "d_long": {"t": "long", "v": 1.5},
                  "e_bool": {"t": "bool", "v": "true"},
                  "f_float": {"t": "float", "v": 1e39},
                  "g_set": {"t": "stringSet", "v": ["a", 1]},
                  "h_untagged": {"v": 1},
                  "i_json": {"t": "json"},
                  "j_string": {"t": "string", "v": null},
                  "k_entry": 1,
                  "l_json_null": {"t": "json", "v": null}
                },
                "broken": [1]
              }
            }
        """.trimIndent()
        val decoded = SettingsSnapshotCodec.decodeCategoryText(text)
        assertEquals(
            mapOf("default" to mapOf("a_ok" to TypedValue("int", 1), "l_json_null" to TypedValue("json", "null"))),
            decoded.category.stores,
        )
        assertEquals(
            listOf(
                "default:b_unknown", "default:c_int", "default:d_long", "default:e_bool", "default:f_float", "default:g_set",
                "default:h_untagged", "default:i_json", "default:j_string", "default:k_entry", "broken",
            ),
            decoded.rejected.map { it.substringBefore(" (") },
        )
        assertEquals("default:b_unknown (unknown type tag \"double\")", decoded.rejected.first())
        assertEquals("broken (not an object of entries)", decoded.rejected.last())
    }

    @Test
    fun filesThatAreNoCategoryFilesAreRejected() {
        listOf(
            "[]", "{}", "{", """{"id": "General", "stores": {}}""", """{"id": 1, "stores": {}}""", """{"id": "general"}""",
            """{"id": "general", "stores": []}""",
        ).forEach { text ->
            assertThrows(text, SettingsJsonException::class.java) { SettingsSnapshotCodec.decodeCategoryText(text) }
        }
        // The category of a newer version is a category file all the same (D9).
        val future = SettingsSnapshotCodec.decodeCategoryText("""{"id": "future_category", "stores": {}}""")
        assertEquals(SettingsSnapshot.Category("future_category", emptyMap()), future.category)
    }

    @Test
    fun aSnapshotListsACategoryOnce() {
        val general = SettingsSnapshot.Category("general", emptyMap())
        assertThrows(IllegalArgumentException::class.java) { SettingsSnapshot(listOf(general, general)) }
        assertThrows(IllegalArgumentException::class.java) { SettingsSnapshot.Category("General", emptyMap()) }
        assertEquals(general, SettingsSnapshot(listOf(general)).category("general"))
    }
}
