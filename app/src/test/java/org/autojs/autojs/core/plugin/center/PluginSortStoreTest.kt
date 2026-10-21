package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.ContextWrapper
import org.autojs.autojs.core.plugin.center.PluginCenterFragment.Sort
import org.autojs.autojs.core.pref.InMemorySettingsStore
import org.autojs.autojs.core.pref.keys.NamedStores
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The three sort stores share the named store `plugin_center_sort_state` through the declared keys of
 * `PluginCenterKeys` (docs/settings/ROADMAP.md P3.1); the stores of the settings layer are in memory here.
 */
class PluginSortStoreTest {

    private val context: Context = ContextWrapper(null)
    private val stores = LinkedHashMap<StoreId, InMemorySettingsStore>()

    /** The shared sort store file. */
    private val sortState: InMemorySettingsStore
        get() = SettingsStores[NamedStores.PLUGIN_CENTER_SORT_STATE] as InMemorySettingsStore

    @Before
    fun installStores() {
        SettingsStores.install { id -> stores.getOrPut(id) { InMemorySettingsStore(id.name) } }
    }

    @After
    fun resetStores() {
        SettingsStores.reset()
    }
    private val sortStores = listOf(
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
        sortStores.forEach(::assertDefaults)
    }

    @Test
    fun customFieldsAndDirectionsAreRememberedAndResetForEachLibrary() {
        sortStores.forEach { store ->
            store.setSort(context, Sort.TITLE_ASC)
            store.setSecondary(context, Sort.TITLE_ASC, Sort.PACKAGE_SIZE_DESC)
            store.setAscending(context, Sort.TITLE_ASC, false)
            store.setAscending(context, Sort.PACKAGE_SIZE_DESC, true)

            assertEquals(Sort.TITLE_ASC, store.getSort(context))
            assertEquals(Sort.PACKAGE_SIZE_DESC, store.getSecondary(context, Sort.TITLE_ASC))
            assertFalse(store.getAscending(context, Sort.TITLE_ASC))
            assertTrue(store.getAscending(context, Sort.PACKAGE_SIZE_DESC))
            sortStores.filter { it != store }.forEach(::assertDefaults)

            store.clear(context)
            assertDefaults(store)
            assertEquals(Sort.LAST_UPDATE_DESC, store.getSecondary(context, Sort.TITLE_ASC))
            assertTrue(store.getAscending(context, Sort.TITLE_ASC))
            assertFalse(store.getAscending(context, Sort.PACKAGE_SIZE_DESC))
        }
    }

    @Test
    fun existingPrimaryAndGroupingSecondaryPreferencesRemainReadable() {
        sortStores.forEach { store ->
            sortState.put(store.key, 3) // MECHANISM, saved before status was added.
            sortState.put(store.key + "_mechanism_secondary", 6) // PACKAGE_NAME_ASC.
            sortState.put(store.key + "_source_secondary", 7) // AUTHOR_ASC.

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
        sortStores.forEach { store ->
            sortState.put(store.key, Int.MAX_VALUE)
            sortState.put(store.key + "_secondary_STATUS", Sort.STATUS.ordinal)
            sortState.put(store.key + "_mechanism_secondary", Int.MAX_VALUE)

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
}
