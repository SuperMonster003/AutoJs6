package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.autojs.autojs.core.plugin.center.PluginCenterFragment.Sort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSortStoreTest {

    private val context = TestContext()
    private val stores = listOf(
        Store(
            "key_\$_plugin_center_sort_state",
            { PluginSortStore.getSort(it) }, PluginSortStore::setSort,
            PluginSortStore::getSecondarySort, PluginSortStore::setSecondarySort,
            { c, s -> PluginSortStore.getAscending(c, s) }, PluginSortStore::setAscending,
            PluginSortStore::clearAllSortMemory,
        ),
        Store(
            "key_\$_plugin_center_sort_state_official",
            { PluginSortStoreOfficial.getSort(it) }, PluginSortStoreOfficial::setSort,
            PluginSortStoreOfficial::getSecondarySort, PluginSortStoreOfficial::setSecondarySort,
            { c, s -> PluginSortStoreOfficial.getAscending(c, s) }, PluginSortStoreOfficial::setAscending,
            PluginSortStoreOfficial::clearAllSortMemory,
        ),
        Store(
            "key_\$_plugin_center_sort_state_third_party",
            { PluginSortStoreThirdParty.getSort(it) }, PluginSortStoreThirdParty::setSort,
            PluginSortStoreThirdParty::getSecondarySort, PluginSortStoreThirdParty::setSecondarySort,
            { c, s -> PluginSortStoreThirdParty.getAscending(c, s) }, PluginSortStoreThirdParty::setAscending,
            PluginSortStoreThirdParty::clearAllSortMemory,
        ),
    )

    @Test
    fun allLibrariesDefaultToStatusThenLastUpdateDescending() {
        stores.forEach(::assertDefaults)
    }

    @Test
    fun customFieldsAndDirectionsAreRememberedAndResetForEachLibrary() {
        stores.forEach { store ->
            store.setSort(context, Sort.TITLE_ASC)
            store.setSecondary(context, Sort.TITLE_ASC, Sort.PACKAGE_SIZE_DESC)
            store.setAscending(context, Sort.TITLE_ASC, false)
            store.setAscending(context, Sort.PACKAGE_SIZE_DESC, true)

            assertEquals(Sort.TITLE_ASC, store.getSort(context))
            assertEquals(Sort.PACKAGE_SIZE_DESC, store.getSecondary(context, Sort.TITLE_ASC))
            assertFalse(store.getAscending(context, Sort.TITLE_ASC))
            assertTrue(store.getAscending(context, Sort.PACKAGE_SIZE_DESC))
            stores.filter { it != store }.forEach(::assertDefaults)

            store.clear(context)
            assertDefaults(store)
            assertEquals(Sort.LAST_UPDATE_DESC, store.getSecondary(context, Sort.TITLE_ASC))
            assertTrue(store.getAscending(context, Sort.TITLE_ASC))
            assertFalse(store.getAscending(context, Sort.PACKAGE_SIZE_DESC))
        }
    }

    @Test
    fun existingPrimaryAndGroupingSecondaryPreferencesRemainReadable() {
        stores.forEach { store ->
            context.preferences.values[store.key] = 3 // MECHANISM, saved before status was added.
            context.preferences.values[store.key + "_mechanism_secondary"] = 6 // PACKAGE_NAME_ASC.
            context.preferences.values[store.key + "_source_secondary"] = 7 // AUTHOR_ASC.

            assertEquals(Sort.MECHANISM, store.getSort(context))
            assertEquals(Sort.PACKAGE_NAME_ASC, store.getSecondary(context, Sort.MECHANISM))
            assertEquals(Sort.AUTHOR_ASC, store.getSecondary(context, Sort.SOURCE))

            store.clear(context)
            assertEquals(Sort.LAST_UPDATE_DESC, store.getSecondary(context, Sort.MECHANISM))
            assertEquals(Sort.LAST_UPDATE_DESC, store.getSecondary(context, Sort.SOURCE))
        }
    }

    @Test
    fun invalidOrDuplicateSecondaryFieldsFallBackToADifferentValidField() {
        stores.forEach { store ->
            context.preferences.values[store.key] = Int.MAX_VALUE
            context.preferences.values[store.key + "_secondary_STATUS"] = Sort.STATUS.ordinal
            context.preferences.values[store.key + "_mechanism_secondary"] = Int.MAX_VALUE

            assertDefaults(store)
            assertEquals(Sort.LAST_UPDATE_DESC, store.getSecondary(context, Sort.MECHANISM))
            store.setSecondary(context, Sort.LAST_UPDATE_DESC, Sort.LAST_UPDATE_DESC)
            assertEquals(Sort.TITLE_ASC, store.getSecondary(context, Sort.LAST_UPDATE_DESC))
        }
    }

    private fun assertDefaults(store: Store) {
        assertEquals(Sort.STATUS, store.getSort(context))
        assertEquals(Sort.LAST_UPDATE_DESC, store.getSecondary(context, Sort.STATUS))
        assertTrue(store.getAscending(context, Sort.STATUS))
        assertFalse(store.getAscending(context, Sort.LAST_UPDATE_DESC))
    }

    private data class Store(
        val key: String,
        val getSort: (Context) -> Sort,
        val setSort: (Context, Sort) -> Unit,
        val getSecondary: (Context, Sort) -> Sort,
        val setSecondary: (Context, Sort, Sort) -> Unit,
        val getAscending: (Context, Sort) -> Boolean,
        val setAscending: (Context, Sort, Boolean) -> Unit,
        val clear: (Context) -> Unit,
    )

    private class TestContext : ContextWrapper(null) {
        val preferences = MemoryPreferences()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = preferences
    }

    private class MemoryPreferences : SharedPreferences {
        val values = mutableMapOf<String, Any>()
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun getString(key: String?, defValue: String?): String? = error("Not used")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = error("Not used")
        override fun getLong(key: String?, defValue: Long): Long = error("Not used")
        override fun getFloat(key: String?, defValue: Float): Float = error("Not used")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            override fun putInt(key: String?, value: Int) = apply { pending[requireNotNull(key)] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { pending[requireNotNull(key)] = value }
            override fun remove(key: String?) = apply { pending[requireNotNull(key)] = null }
            override fun apply() {
                pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            }
            override fun commit(): Boolean { apply(); return true }
            override fun clear(): SharedPreferences.Editor = error("Not used")
            override fun putString(key: String?, value: String?): SharedPreferences.Editor = error("Not used")
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = error("Not used")
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = error("Not used")
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = error("Not used")
        }
    }
}
