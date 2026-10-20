package org.autojs.autojs.core.pref

import com.google.gson.Gson
import org.autojs.autojs.core.pref.store.StoreId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Key declaration model of docs/settings/ROADMAP.md P1.1 (T1 / T2): value types and their tags, default resolution,
 * Enum candidate checks, Json round trips, category rules and the 31 categories of appendix A.1.
 *
 * zh-CN: ROADMAP P1.1 键声明模型的测试 (T1 / T2): 值类型与类型标签, 默认值解析, Enum 候选校验, Json 往返, 分类规则与附录 A.1
 * 的 31 个分类.
 */
class SettingKeyTest {

    private val originalResolver = SettingKeyNames.resolver

    @After
    fun restoreResolver() {
        SettingKeyNames.resolver = originalResolver
    }

    @Test
    fun typeTagsAreTheArchiveTags() {
        val tags = listOf(SettingType.Bool, SettingType.Int, SettingType.Long, SettingType.Float, SettingType.Str, SettingType.StrSet, SettingType.Json(SettingsJson.STRING_LIST)).map { it.tag }
        assertEquals(SettingDescriptorFixtures.TYPES, tags)
        assertEquals("string", SettingType.Enum.ofKeys { listOf("a") }.tag)
    }

    @Test
    fun storedFormsRoundTripAndRejectOtherTypes() {
        assertRoundTrip(SettingType.Bool, true)
        assertRoundTrip(SettingType.Int, -7)
        assertRoundTrip(SettingType.Long, 1L shl 40)
        assertRoundTrip(SettingType.Float, 1.25f)
        assertRoundTrip(SettingType.Str, "key_night_mode_follow_system")
        assertRoundTrip(SettingType.StrSet, linkedSetOf("b", "a"))
        // SharedPreferences types are strict: an Int is not a Long, a String is not a Boolean.
        assertNull(SettingType.Long.fromStored(1))
        assertNull(SettingType.Int.fromStored(1L))
        assertNull(SettingType.Float.fromStored(1.0))
        assertNull(SettingType.Bool.fromStored("true"))
        assertNull(SettingType.StrSet.fromStored(setOf(1)))
        assertNull(SettingType.Str.fromStored(setOf("a")))
    }

    @Test
    fun archiveFormsUseSettingsJsonNarrowing() {
        assertEquals(2, SettingType.Int.fromJson(2.0))
        assertNull(SettingType.Int.fromJson(2.5))
        assertNull(SettingType.Int.fromJson(3000000000L))
        assertEquals(3000000000L, SettingType.Long.fromJson(3000000000L))
        assertEquals(5L, SettingType.Long.fromJson(5))
        assertEquals(0.1f, SettingType.Float.fromJson(0.1))
        assertNull(SettingType.Float.fromJson(1.0E39))
        assertNull(SettingType.Bool.fromJson("true"))
        assertEquals(listOf("a", "b"), SettingType.StrSet.toJson(linkedSetOf("b", "a")))
        assertEquals(setOf("b", "a"), SettingType.StrSet.fromJson(listOf("b", "a")))
        assertNull(SettingType.StrSet.fromJson(listOf("a", null)))
        val float = 0.1f
        assertEquals(float, SettingType.Float.fromJson(SettingsJson.parse(SettingsJson.stringify(SettingType.Float.toJson(float)))))
    }

    @Test
    fun defaultsResolveAsDeclared() {
        val constant = SettingKey.Bool(name = "key_a", category = SettingCategory.RUNNING, default = SettingDefault.of(true))
        assertEquals(true, constant.defaultOrNull())
        assertEquals("true", constant.defaultText)

        var reads = 0
        val computed = SettingKey.Int(name = "key_b", category = SettingCategory.RUNNING, default = SettingDefault.computed("R.integer.b") { ++reads })
        assertEquals("R.integer.b", computed.defaultText)
        assertEquals(0, reads)
        assertEquals(1, computed.defaultOrNull())
        // Computed defaults are read on every call, like the resource reads they replace (R2).
        assertEquals(2, computed.defaultOrNull())

        val none = StateKey.str(name = "key_c", default = SettingDefault.None)
        assertNull(none.defaultOrNull())
        assertNull(none.defaultText)
    }

    @Test
    fun namesComeFromLiteralsOrFromTheResolverOnce() {
        val resolved = mutableListOf<Int>()
        SettingKeyNames.resolver = { resolved.add(it); "key_from_res_$it" }
        val byRes = SettingKey.Str(keyRes = 42, category = SettingCategory.GENERAL, default = SettingDefault.of("x"))
        assertTrue(resolved.isEmpty())
        assertEquals("key_from_res_42", byRes.name)
        assertEquals("key_from_res_42", byRes.name)
        assertEquals(listOf(42), resolved)
        assertEquals("key_\$_literal", SettingKey.Str(name = "key_\$_literal", category = SettingCategory.GENERAL, default = SettingDefault.of("x")).name)
        assertRejected { SettingKey.Str(category = SettingCategory.GENERAL, default = SettingDefault.of("x")) }
        assertRejected { SettingKey.Str(keyRes = 1, name = "key_x", category = SettingCategory.GENERAL, default = SettingDefault.of("x")) }
        assertRejected { SettingKey.Str(name = " ", category = SettingCategory.GENERAL, default = SettingDefault.of("x")) }
    }

    @Test
    fun enumValuesMustBeCandidates() {
        var resolved = 0
        val type = SettingType.Enum.ofKeys { resolved++; listOf("key_night_mode_follow_system", "key_night_mode_always_on", "key_night_mode_always_off") }
        val key = SettingKey.Enum(name = "key_night_mode", category = SettingCategory.APPEARANCE, enumType = type, default = SettingDefault.of("key_night_mode_follow_system"), effect = SettingEffect.APPLY)
        assertEquals(0, resolved)
        assertEquals("key_night_mode_follow_system", key.decode("key_night_mode_follow_system"))
        assertEquals(1, resolved)
        assertNull(key.decode("key_night_mode_unknown"))
        assertNull(key.decode(1))
        assertEquals("key_night_mode_always_on", key.encode("key_night_mode_always_on"))
        assertRejected { key.encode("key_night_mode_unknown") }
        assertTrue(key.isDefaultCandidate())
        assertEquals("string", key.type.tag)
        assertEquals(3, key.candidates.size)
        assertEquals(1, resolved)

        val badDefault = SettingKey.Enum(name = "key_x", category = SettingCategory.APPEARANCE, enumType = type, default = SettingDefault.of("key_other"))
        assertFalse(badDefault.isDefaultCandidate())
        assertRejected { SettingType.Enum.ofKeys { listOf("a", "a") }.candidates }
        assertRejected { SettingType.Enum.ofKeys { emptyList() }.candidates }
    }

    @Test
    fun enumOfKotlinEnumStoresKeyStrings() {
        val type = SettingType.Enum({ Mode.values().toList() }, Mode::key)
        val key = SettingKey.Enum(name = "key_root_mode", category = SettingCategory.RUNNING, enumType = type, default = SettingDefault.of(Mode.AUTO))
        assertEquals("auto_detect", key.encode(Mode.AUTO))
        assertSame(Mode.ROOT, key.decode("force_root"))
        assertEquals("auto_detect", key.defaultText)
        assertEquals("force_root", type.toJson(Mode.ROOT))
        assertSame(Mode.ROOT, type.fromJson("force_root"))
    }

    @Test
    fun jsonValuesRoundTripThroughStoredTextAndArchives() {
        val type = SettingType.Json(SettingsJson.STRING_LIST, htmlSafe = true)
        val key = StateKey.json(name = "key_working_directory_history", type = type, default = SettingDefault.of(emptyList()))
        val value = listOf("/sdcard/Scripts", "/sdcard/a=b")
        val stored = key.encode(value)
        assertEquals(Gson().toJson(value), stored)
        assertEquals(value, key.decode(stored))
        assertEquals(value, type.toJson(value))
        assertEquals(value, type.fromJson(SettingsJson.parse(SettingsJson.stringify(type.toJson(value)))))
        assertNull(key.decode("[not json"))
        assertNull(key.decode("{\"a\":1}"))
        assertNull(type.fromJson(mapOf("a" to 1)))
        assertEquals("[]", key.defaultText)

        val objectType = SettingType.Json(PointCodec)
        assertEquals("{\"x\":1,\"y\":-2}", objectType.toStored(Point(1, -2)))
        assertEquals(Point(1, -2), objectType.fromStored("{\"y\":-2,\"x\":1.0}"))
        assertEquals(mapOf("x" to 1, "y" to -2), objectType.toJson(Point(1, -2)))
    }

    @Test
    fun settingKeysFollowTheCategoryRules() {
        val ok = SettingKey.Str(name = "key_working_directory", category = SettingCategory.EXPLORER, default = SettingDefault.of("Scripts"), deployment = DeploymentKind.CREATE_DIRECTORY)
        assertEquals(SettingKind.SETTING, ok.kind)
        assertEquals(DeploymentKind.CREATE_DIRECTORY, ok.deployment)
        assertEquals(StoreId.DEFAULT, ok.store)
        val token = SettingKey.Str(name = "token", category = SettingCategory.CREDENTIALS, default = SettingDefault.of(""), sensitive = true, store = StoreId("github_auth"))
        assertTrue(token.sensitive)
        assertRejected { SettingKey.Bool(name = "key_x", category = SettingCategory.OTHER, default = SettingDefault.of(false)) }
        assertRejected { SettingKey.Bool(name = "key_x", category = SettingCategory.DATA_SHORTCUTS, default = SettingDefault.of(false)) }
        assertRejected { SettingKey.Bool(name = "key_x", category = SettingCategory.GENERAL, default = SettingDefault.of(false), sensitive = true) }
        assertRejected { SettingKey.Str(name = "token", category = SettingCategory.CREDENTIALS, default = SettingDefault.of("")) }
    }

    @Test
    fun stateAndCacheKeysHaveFixedPlaces() {
        val state = StateKey.int(name = "key_a11y_service_manager_control_level", default = SettingDefault.of(0))
        assertEquals(SettingKind.STATE, state.kind)
        assertSame(SettingCategory.OTHER, state.category)
        val cache = CacheKey.str(name = "key_root_manager_apps_cache", default = SettingDefault.None)
        assertEquals(SettingKind.CACHE, cache.kind)
        assertNull(cache.category)
        assertEquals(SettingType.Enum::class.java, StateKey(name = "key_s", type = SettingType.Enum.ofKeys { listOf("a") }, default = SettingDefault.of("a")).type.javaClass)
    }

    @Test
    fun categoriesFollowAppendixA() {
        val all = SettingCategory.ALL
        assertEquals(31, all.size)
        assertEquals((1..31).toList(), all.map { it.order })
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(listOf("developer", "credentials", "other"), all.filterNot { it.defaultSelected }.map { it.id })
        assertEquals(listOf("credentials"), all.filter { it.sensitive }.map { it.id })
        assertEquals(listOf("data_shortcuts", "data_color_library"), all.filter { it.isData }.map { it.id })
        assertSame(SettingCategory.A11Y, SettingCategory.ofId("a11y"))
        assertNull(SettingCategory.ofIdOrNull("nope"))
        assertRejected { SettingCategory.ofId("nope") }
        assertRejected { SettingCategory("Bad-Id", 1) }
        assertRejected { SettingCategory("secret", 1, defaultSelected = true, sensitive = true) }
    }

    @Test
    fun kindsUseTheClassificationLabels() {
        assertEquals(SettingDescriptorFixtures.KINDS, SettingKind.values().map { it.label })
        assertEquals(SettingKind.VALUE_CONSTANT, SettingKind.ofLabel("ValueConstant"))
        assertEquals(SettingDescriptorFixtures.EFFECTS, SettingEffect.values().map { it.name })
        assertEquals(SettingDescriptorFixtures.DEPLOYMENTS, DeploymentKind.values().map { it.name })
    }

    private fun <T : Any> assertRoundTrip(type: SettingType<T>, value: T) {
        assertEquals(value, type.fromStored(type.toStored(value)))
        assertEquals(value, type.fromJson(SettingsJson.parse(SettingsJson.stringify(type.toJson(value)))))
    }

    private fun assertRejected(block: () -> Any?) {
        try {
            block()
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    private enum class Mode(val key: String) {
        AUTO("auto_detect"),
        ROOT("force_root"),
        NON_ROOT("force_non_root"),
    }

    private data class Point(val x: Int, val y: Int)

    private object PointCodec : SettingsJson.Codec<Point> {
        override fun encode(value: Point): Any = linkedMapOf("x" to value.x, "y" to value.y)
        override fun decode(json: Any?): Point {
            val map = json as? Map<*, *> ?: throw SettingsJson.SettingsJsonException("Expected an object")
            return Point(SettingsJson.toInt(map["x"]) ?: throw SettingsJson.SettingsJsonException("x"), SettingsJson.toInt(map["y"]) ?: throw SettingsJson.SettingsJsonException("y"))
        }
    }
}
