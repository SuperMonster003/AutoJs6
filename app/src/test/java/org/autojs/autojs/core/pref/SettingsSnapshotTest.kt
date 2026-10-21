package org.autojs.autojs.core.pref

import org.autojs.autojs.core.pref.archive.SettingsSnapshot
import org.autojs.autojs.core.pref.archive.SettingsSnapshotCodec
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.pref.keys.DialogStateKeys
import org.autojs.autojs.core.pref.keys.DomainKeysTestBase
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.core.pref.store.StoreId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P4.3: the run-time fallback of the `other` category ([OtherCategoryFallback]) and the typed
 * values of a snapshot ([TypedValue], T6); P6.1: the values that [SettingsSnapshot.capture] takes from the stores.
 *
 * zh-CN: ROADMAP P4.3: "其他" 分类的运行时兜底与快照的带类型值 (T6); P6.1: [SettingsSnapshot.capture] 从存储中取出的值.
 */
class SettingsSnapshotTest : DomainKeysTestBase() {

    @Test
    fun runtimeTypesAreTheTagsOfT6() {
        assertEquals(TypedValue("bool", true), TypedValue.ofRuntimeType(true))
        assertEquals(TypedValue("int", 3), TypedValue.ofRuntimeType(3))
        assertEquals(TypedValue("long", 3L), TypedValue.ofRuntimeType(3L))
        assertEquals(TypedValue("float", 0.5f), TypedValue.ofRuntimeType(0.5f))
        // A text is a string even when it holds JSON: only a declaration knows (T6).
        assertEquals(TypedValue("string", "[\"a\"]"), TypedValue.ofRuntimeType("[\"a\"]"))
        val set = TypedValue.ofRuntimeType(linkedSetOf("b", "a", "c"))!!
        assertEquals("stringSet", set.type)
        assertEquals(listOf("a", "b", "c"), (set.value as Set<*>).toList())
        assertEquals(TypedValue("stringSet", emptySet<String>()), TypedValue.ofRuntimeType(emptySet<String>()))
        // SharedPreferences holds none of these.
        assertNull(TypedValue.ofRuntimeType(null))
        assertNull(TypedValue.ofRuntimeType(0.5))
        assertNull(TypedValue.ofRuntimeType(setOf(1, 2)))
        assertNull(TypedValue.ofRuntimeType(listOf("a")))
    }

    @Test
    fun typedValuesMatchTheirTags() {
        listOf(SettingType.Bool, SettingType.Int, SettingType.Long, SettingType.Float, SettingType.Str, SettingType.StrSet).forEach {
            assertTrue(it.tag in listOf("bool", "int", "long", "float", "string", "stringSet"))
        }
        assertEquals("json", TypedValue.JSON_TAG)
        assertEquals("{}", TypedValue("json", "{}").value)
        assertThrows(IllegalArgumentException::class.java) { TypedValue("int", "1") }
        assertThrows(IllegalArgumentException::class.java) { TypedValue("long", 1) }
        assertThrows(IllegalArgumentException::class.java) { TypedValue("stringSet", setOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { TypedValue("double", 1.0) }
    }

    @Test
    fun unregisteredEntriesOfTheDefaultStoreGoToOther() {
        val rows = SettingsRegistry.describe()
        val excluded = rows.first { it.kind == SettingKind.EXCLUDED && it.store == StoreId.DEFAULT.name && !it.family }.name
        val valueConstantOnly = rows.filter { it.kind == SettingKind.VALUE_CONSTANT }.map { it.name }
            .first { name -> rows.none { it.name == name && it.kind != SettingKind.VALUE_CONSTANT } }
        // The stack trace key of NotAskAgainDialog (appendix C Q23) and keys of a newer version.
        val stackTraceKey = "0f343b0931126a20f133d67c2b018a3b"
        store.edit {
            put(AppearanceKeys.NIGHT_MODE.name, key("key_night_mode_follow_system"))
            put(MiscStateKeys.FIND_OR_REPLACE_USING_REGEX.name, true)
            put(MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.name, 4)
            put(DialogStateKeys.LIST_PREFERENCE_PROMPT.key("key_night_mode").name, false)
            put(DialogStateKeys.TIMED_TASK_EXACT_ALARM_PROMPT.name, false)
            put(excluded, "x")
            put(valueConstantOnly, "y")
            put(stackTraceKey, false)
            put("future_key_count", 7)
            put("future_key_since", 1_700_000_000_000L)
            put("future_key_ratio", 0.25f)
            put("future_key_tags", setOf("z", "a"))
        }
        val all = store.all()
        val result = OtherCategoryFallback.collect(all)
        assertEquals(
            linkedMapOf(
                stackTraceKey to TypedValue("bool", false),
                "future_key_count" to TypedValue("int", 7),
                "future_key_ratio" to TypedValue("float", 0.25f),
                "future_key_since" to TypedValue("long", 1_700_000_000_000L),
                "future_key_tags" to TypedValue("stringSet", linkedSetOf("a", "z")),
                valueConstantOnly to TypedValue("string", "y"),
            ).toSortedMap(),
            result.entries,
        )
        assertEquals(result.entries.keys.sorted(), result.entries.keys.toList())
        assertEquals(emptyList<String>(), result.untyped)
        // Declared keys (settings, states, the cache), family members and excluded names are known; a candidate value
        // that also names a state (P4.1) is known through the state.
        listOf(
            AppearanceKeys.NIGHT_MODE.name, MiscStateKeys.FIND_OR_REPLACE_USING_REGEX.name, MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.name,
            DialogStateKeys.LIST_PREFERENCE_PROMPT.key("key_night_mode").name, DialogStateKeys.TIMED_TASK_EXACT_ALARM_PROMPT.name, excluded,
        ).forEach { assertTrue(it, OtherCategoryFallback.isKnown(it, rows)) }
        // Every stored entry is either known or in the fallback.
        assertEquals(all.keys, all.keys.filter { OtherCategoryFallback.isKnown(it, rows) }.toSet() + result.entries.keys)
    }

    @Test
    fun valuesWithoutATagAreReportedNotExported() {
        val result = OtherCategoryFallback.collect(mapOf("b_double" to 0.5, "a_null" to null, "c_text" to "t"), rows = emptyList())
        assertEquals(mapOf("c_text" to TypedValue("string", "t")), result.entries)
        assertEquals(listOf("a_null", "b_double"), result.untyped)
    }

    @Test
    fun rowsCountOnlyForTheirStore() {
        val named = StoreId("plugin_center_recent")
        val rows = listOf(
            row("key_a", StoreId.DEFAULT, SettingKind.SETTING),
            row("key_b", named, SettingKind.STATE),
            row("prefix_", StoreId.DEFAULT, SettingKind.STATE, family = true),
            row("", named, SettingKind.STATE, family = true),
            row("key_c", StoreId.DEFAULT, SettingKind.VALUE_CONSTANT),
        )
        val all = mapOf("key_a" to 1, "key_b" to 2, "key_c" to "c", "prefix_1" to true, "prefix_" to false, "other" to "o")
        // The empty prefix of the named store knows nothing of the default store.
        assertEquals(setOf("key_b", "key_c", "other"), OtherCategoryFallback.collect(all, rows).entries.keys)
        assertEquals(emptyMap<String, TypedValue>(), OtherCategoryFallback.collect(mapOf("key_b" to 2, "x" to 1), rows, named).entries)
        assertFalse(OtherCategoryFallback.isKnown("", rows, named))
        assertTrue(OtherCategoryFallback.isKnown("prefix_", rows))
    }

    @Test
    fun aSnapshotHoldsTheStoredValuesOfTheSelectedCategories() {
        val named = StoreId("a_snapshot_test")
        val flag = SettingKey.Bool(category = SettingCategory.GENERAL, default = SettingDefault.of(false), name = "test_flag")
        val count = SettingKey.Int(category = SettingCategory.GENERAL, default = SettingDefault.of(1), name = "test_count")
        val unset = SettingKey.Long(category = SettingCategory.GENERAL, default = SettingDefault.of(1L), name = "test_unset")
        val list = SettingKey.Json(
            category = SettingCategory.GENERAL, jsonType = SettingType.Json(SettingsJson.STRING_LIST), default = SettingDefault.of(emptyList()), name = "test_list",
        )
        val malformed = SettingKey.Json(
            category = SettingCategory.GENERAL, jsonType = SettingType.Json(SettingsJson.STRING_LIST), default = SettingDefault.of(emptyList()), name = "test_malformed",
        )
        val early = SettingKey.Bool(category = SettingCategory.GENERAL, default = SettingDefault.of(false), store = named, name = "test_early")
        val ratio = SettingKey.Float(category = SettingCategory.EDITOR_GENERAL, default = SettingDefault.of(1f), store = named, name = "test_ratio")
        val token = SettingKey.Str(category = SettingCategory.CREDENTIALS, default = SettingDefault.None, sensitive = true, name = "test_token")
        val recent = KeyFamily("test_recent_") { name ->
            SettingKey.StrSet(category = SettingCategory.GENERAL, default = SettingDefault.of(emptySet()), name = name)
        }
        val keys = listOf(flag, count, unset, list, malformed, early, ratio, token)
        store.edit {
            put(flag.name, true)
            put(count.name, "3")
            put(list.name, "[\"a\",\"b\"]")
            put(malformed.name, "[\"a\"")
            put("test_recent_2", setOf("y"))
            put("test_recent_1", setOf("x"))
            put(token.name, "secret")
            put("test_unrelated", 1)
        }
        store(named).edit {
            put(early.name, true)
            put(ratio.name, 0.5f)
        }

        val selected = listOf(SettingCategory.EDITOR_LOG, SettingCategory.DATA_SHORTCUTS, SettingCategory.EDITOR_GENERAL, SettingCategory.GENERAL)
        val snapshot = SettingsSnapshot.capture(selected, keys, listOf(recent), rows = emptyList())
        // Page order, no data category (T19), and a category without stored values is listed empty (D8).
        assertEquals(listOf("general", "editor_general", "editor_log"), snapshot.categories.map { it.id })
        val general = snapshot.category("general")!!
        assertEquals(
            mapOf(
                "default" to mapOf(
                    // A value of another type keeps its own tag, a json declaration without a JSON document is a string (D9).
                    "test_count" to TypedValue("string", "3"),
                    "test_flag" to TypedValue("bool", true),
                    "test_list" to TypedValue("json", "[\"a\",\"b\"]"),
                    "test_malformed" to TypedValue("string", "[\"a\""),
                    "test_recent_1" to TypedValue("stringSet", setOf("x")),
                    "test_recent_2" to TypedValue("stringSet", setOf("y")),
                ),
                "a_snapshot_test" to mapOf("test_early" to TypedValue("bool", true)),
            ),
            general.stores,
        )
        // The default store first, then the others by name; keys by name.
        assertEquals(listOf("default", "a_snapshot_test"), general.stores.keys.toList())
        assertEquals(general.stores.getValue("default").keys.sorted(), general.stores.getValue("default").keys.toList())
        assertEquals(7, general.entries)
        assertEquals(mapOf("a_snapshot_test" to mapOf("test_ratio" to TypedValue("float", 0.5f))), snapshot.category("editor_general")!!.stores)
        assertEquals(SettingsSnapshot.Category("editor_log", emptyMap()), snapshot.category("editor_log"))
        assertNull(snapshot.category("credentials"))
        assertEquals(emptyList<String>(), snapshot.unexportable)

        // Credentials only when they are selected (D2).
        val credentials = SettingsSnapshot.capture(listOf(SettingCategory.CREDENTIALS), keys, emptyList(), emptyList())
        assertEquals(listOf("credentials"), credentials.categories.map { it.id })
        assertEquals(mapOf("default" to mapOf("test_token" to TypedValue("string", "secret"))), credentials.category("credentials")!!.stores)
    }

    @Test
    fun theOtherCategoryOfASnapshotKeepsTheRunTimeTags() {
        val stackTraceKey = "0f343b0931126a20f133d67c2b018a3b"
        val prompt = DialogStateKeys.LIST_PREFERENCE_PROMPT.key("key_night_mode").name
        store.edit {
            put(AppearanceKeys.NIGHT_MODE.name, key("key_night_mode_follow_system"))
            put(MiscStateKeys.FIND_OR_REPLACE_USING_REGEX.name, true)
            put(MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.name, 4)
            put(prompt, false)
            put(stackTraceKey, false)
            put("future_key_count", 7)
            put("future_key_since", 1_700_000_000_000L)
            put("future_key_ratio", 0.25f)
            put("future_key_nan", Float.NaN)
            put("future_key_tags", setOf("z", "a"))
            put("future_key_text", "{}")
        }
        val snapshot = SettingsSnapshot.capture(listOf(SettingCategory.OTHER))
        assertEquals(listOf("other"), snapshot.categories.map { it.id })
        val other = snapshot.category("other")!!
        // States, state families and the run-time fallback (P4.3); settings and the cache are not "other".
        assertEquals(
            mapOf(
                "default" to mapOf(
                    MiscStateKeys.FIND_OR_REPLACE_USING_REGEX.name to TypedValue("bool", true),
                    prompt to TypedValue("bool", false),
                    stackTraceKey to TypedValue("bool", false),
                    "future_key_count" to TypedValue("int", 7),
                    "future_key_ratio" to TypedValue("float", 0.25f),
                    "future_key_since" to TypedValue("long", 1_700_000_000_000L),
                    "future_key_tags" to TypedValue("stringSet", setOf("a", "z")),
                    // Without a declaration a text is a string, whatever it holds (T6).
                    "future_key_text" to TypedValue("string", "{}"),
                ),
            ),
            other.stores,
        )
        // A value that no JSON number holds is reported, not exported (P6.4).
        assertEquals(listOf("default:future_key_nan"), snapshot.unexportable)

        // The tags survive the category file.
        val text = SettingsSnapshotCodec.categoryText(other)
        assertEquals(SettingsSnapshotCodec.Decoded(other, emptyList()), SettingsSnapshotCodec.decodeCategoryText(text))
        val entries = ((SettingsJson.parse(text) as Map<*, *>)["stores"] as Map<*, *>)["default"] as Map<*, *>
        assertEquals(mapOf("t" to "long", "v" to 1_700_000_000_000L), entries["future_key_since"])
        assertEquals(mapOf("t" to "float", "v" to 0.25), entries["future_key_ratio"])
        assertEquals(mapOf("t" to "stringSet", "v" to listOf("a", "z")), entries["future_key_tags"])
    }

    private fun row(name: String, store: StoreId, kind: SettingKind, family: Boolean = false) = SettingDescriptor(
        name = name, type = "bool", store = store.name, category = if (kind == SettingKind.VALUE_CONSTANT) null else SettingCategory.OTHER.id,
        defaultText = null, effect = SettingEffect.NONE, kind = kind, family = family,
    )
}
