package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.SettingsRegistry
import org.autojs.autojs.core.pref.SettingsTestResources
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P3: the named stores declared in [NamedStores] keep their file names (R1), are registered in
 * [SettingsStores] and are what [SettingsRegistry.stores] lists; the permanent exclusions of appendix A.2 (P3.5) are
 * marked excluded and hold no declared keys.
 *
 * zh-CN: ROADMAP P3: [NamedStores] 声明的命名存储文件名不变 (R1), 已登记到 [SettingsStores], 即 [SettingsRegistry.stores];
 * 附录 A.2 的永久排除项 (P3.5) 标记为排除, 不含任何键声明.
 */
class NamedStoresTest {

    @Test
    fun permanentExclusionsKeepTheirNames() {
        val excluded = listOf(
            "device_id_prefs", "autojs.localstorage.*", "autojs.node.storage.*.*.*", "lua-runtime-storage-v1-*",
            "privileged_edit_backups", "runtime_task_intents", "ScriptWidgets", "explorer_playback_history",
        )
        assertEquals(excluded, SettingsRegistry.stores.filter { it.excluded }.map { it.name })
        SettingsRegistry.stores.forEach { assertEquals(StoreId.Backend.SHARED_PREFERENCES, it.backend) }
    }

    @Test
    fun theIgnoredUpdatesFileIsNamedByItsResource() {
        assertEquals(SettingsTestResources.value("string", "key_ignored_updates"), NamedStores.IGNORED_UPDATES.name)
    }

    @Test
    fun declaredStoresAreRegistered() {
        val stores = SettingsRegistry.stores
        assertEquals(stores.size, stores.map { it.name }.toSet().size)
        assertTrue(SettingsStores.registeredIds.containsAll(stores))
        val keyed = (SettingsRegistry.keys.map { it.store } + SettingsRegistry.families.map { it.template.store }).toSet()
        stores.filter { it.excluded }.forEach { assertTrue("$it holds declared keys", it !in keyed) }
        (keyed - StoreId.DEFAULT).forEach { assertTrue("$it is not declared in NamedStores", it in stores) }
    }
}
