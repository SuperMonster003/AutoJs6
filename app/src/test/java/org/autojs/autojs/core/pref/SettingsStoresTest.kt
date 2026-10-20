package org.autojs.autojs.core.pref

import org.autojs.autojs.core.pref.store.SettingsStore
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId
import org.autojs.autojs.core.pref.store.get
import org.autojs.autojs.core.pref.store.getOrNull
import org.autojs.autojs.core.pref.store.set
import org.autojs.autojs.core.pref.store.storedOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [SettingsStores] with an in-memory provider and the typed key access of [SettingsStore] (docs/settings/ROADMAP.md
 * P1.2, T3): one store per id, registrations, change events for T20, and reads / writes through declared keys.
 *
 * zh-CN: 以内存提供方测试 [SettingsStores] 与 [SettingsStore] 的类型化键读写 (ROADMAP P1.2, T3): 每个 id 一个存储, 登记,
 * 供 T20 使用的变化事件, 以及经键声明的读写.
 */
class SettingsStoresTest {

    private val opened = mutableListOf<StoreId>()

    @Before
    fun installInMemoryProvider() {
        SettingsStores.install { id -> opened.add(id); InMemorySettingsStore(id.name) }
    }

    @After
    fun resetStores() {
        SettingsStores.reset()
    }

    @Test
    fun eachIdIsOpenedOnce() {
        val default = SettingsStores.default
        assertSame(default, SettingsStores.default)
        assertSame(default, SettingsStores[StoreId.DEFAULT])
        val named = SettingsStores[StoreId("editor_symbols_config")]
        assertSame(named, SettingsStores[StoreId("editor_symbols_config")])
        assertEquals(listOf(StoreId.DEFAULT, StoreId("editor_symbols_config")), opened)
    }

    @Test
    fun providerMustOpenTheRequestedId() {
        SettingsStores.install { InMemorySettingsStore("other") }
        assertThrows(IllegalArgumentException::class.java) { SettingsStores[StoreId("wanted")] }
    }

    @Test
    fun registrationsKeepOrderAndAllowRepeats() {
        val id = StoreId("aj6_test_registered_store")
        assertSame(id, SettingsStores.register(id))
        SettingsStores.register(id)
        val ids = SettingsStores.registeredIds.toList()
        assertEquals(StoreId.DEFAULT, ids.first())
        assertEquals(1, ids.count { it == id })
    }

    @Test
    fun changeEventsComeFromStoresOpenedBeforeAndAfterListening() {
        val before = SettingsStores.default
        val events = mutableListOf<Pair<String, String?>>()
        val registration = SettingsStores.addChangeListener { store, name -> events.add(store.name to name) }
        val after = SettingsStores[StoreId("plugin_center_sort_state")]
        before.put("key_a", 1)
        before.put("key_a", 1)
        after.put("sort", "title")
        after.clear()
        registration.close()
        before.put("key_b", true)
        assertEquals(listOf("default" to "key_a", "plugin_center_sort_state" to "sort", "plugin_center_sort_state" to null), events)
    }

    @Test
    fun typedKeyAccessUsesDefaultsUntilAValueIsStored() {
        val store = SettingsStores.default
        val flag = SettingKey.Bool(name = "key_flag", category = SettingCategory.RUNNING, default = SettingDefault.of(true))
        assertEquals(true, store[flag])
        assertNull(store.storedOrNull(flag))
        store[flag] = false
        assertEquals(false, store[flag])
        assertEquals(false, store.getBoolean("key_flag", true))
        store[flag] = null
        assertTrue(!store.contains("key_flag"))

        val size = SettingKey.Int(name = "key_size", category = SettingCategory.EDITOR_APPEARANCE, default = SettingDefault.computed("R.integer.size") { 14 })
        assertEquals(14, store[size])
        store.put("key_size", 18)
        assertEquals(18, store[size])

        val history = StateKey.json(name = "key_history", type = SettingType.Json(SettingsJson.STRING_LIST, htmlSafe = true), default = SettingDefault.of(emptyList()))
        store[history] = listOf("/sdcard/a", "/sdcard/b")
        assertEquals("[\"/sdcard/a\",\"/sdcard/b\"]", store.getString("key_history", null))
        assertEquals(listOf("/sdcard/a", "/sdcard/b"), store[history])
        store.put("key_history", "[broken")
        assertEquals(emptyList<String>(), store[history])
    }

    @Test
    fun invalidEnumValuesFallBackToTheDefault() {
        val store = SettingsStores.default
        val mode = SettingKey.Enum(
            name = "key_mode", category = SettingCategory.APPEARANCE,
            enumType = SettingType.Enum.ofKeys { listOf("key_mode_a", "key_mode_b") }, default = SettingDefault.of("key_mode_a"),
        )
        store.put("key_mode", "key_mode_b")
        assertEquals("key_mode_b", store[mode])
        store.put("key_mode", "key_mode_removed")
        assertNull(store.storedOrNull(mode))
        assertEquals("key_mode_a", store[mode])
        assertThrows(IllegalArgumentException::class.java) { store[mode] = "key_mode_removed" }
    }

    @Test
    fun typeMismatchesAndForeignKeysAreErrors() {
        val store = SettingsStores.default
        val count = StateKey.long(name = "key_count", default = SettingDefault.of(0L))
        store.put("key_count", 1)
        // Same as SharedPreferences.getLong on an int value.
        assertThrows(ClassCastException::class.java) { store[count] }
        val noDefault = StateKey.str(name = "key_none", default = SettingDefault.None)
        assertNull(store.getOrNull(noDefault))
        assertThrows(IllegalStateException::class.java) { store[noDefault] }
        val foreign = SettingKey.Str(name = "token", category = SettingCategory.CREDENTIALS, default = SettingDefault.of(""), sensitive = true, store = StoreId("github_auth"))
        assertThrows(IllegalArgumentException::class.java) { store[foreign] }
        assertEquals("", SettingsStores[StoreId("github_auth")][foreign])
    }
}
