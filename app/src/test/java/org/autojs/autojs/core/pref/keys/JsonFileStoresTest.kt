package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.SettingsRegistry
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P5.1: the JSON files under `filesDir` / `noBackupFilesDir` declared in [JsonFileStores] keep
 * their paths (R1), are excluded JSON file stores without declared keys (appendix A.2), are registered in
 * [SettingsStores] and are what [SettingsRegistry.fileStores] lists; the store layer refuses to open them, since a
 * `JsonFileStore` would replace their owners' formats.
 *
 * zh-CN: ROADMAP P5.1: [JsonFileStores] 声明的 `filesDir` / `noBackupFilesDir` 下 JSON 文件路径不变 (R1), 都是不含键声明的
 * 排除存储 (附录 A.2), 已登记到 [SettingsStores], 即 [SettingsRegistry.fileStores]; 存储层拒绝打开它们, 因为
 * `JsonFileStore` 会替换读写方自己的格式.
 */
class JsonFileStoresTest {

    @Test
    fun jsonFilesKeepTheirPaths() {
        val paths = listOf(
            "../no_backup/ace_fonts/catalog-v1.json", "../no_backup/ace_fonts/catalog-v1.sig.json",
            "../no_backup/ace_fonts/fonts/*/installed.json", "../no_backup/codegen_models*/registry.json",
            "plugin_center/autojs6_installed_plugin_metadata_v1.json", "plugin_center/autojs6_plugin_index_v11.json",
            "runtime-web-downloads/*.json",
            "nodejs/checkpoints/*.json", "nodejs-work-manager/tasks/*.json", "../no_backup/python-runtime-preview/host-capabilities-*.json",
            "privileged-edit-sessions/*/session.json", "privileged-file-operations/*.json", "privileged-edit-backups/*.json",
            "node-doctor/node-doctor-report.json",
        )
        assertEquals(paths, SettingsRegistry.fileStores.map { it.name })
        SettingsRegistry.fileStores.forEach {
            assertEquals(StoreId.Backend.JSON_FILE, it.backend)
            assertTrue("$it is not excluded", it.excluded)
        }
    }

    @Test
    fun declaredJsonFilesAreRegisteredWithoutKeys() {
        val stores = SettingsRegistry.fileStores
        assertTrue(SettingsStores.registeredIds.containsAll(stores))
        val keyed = (SettingsRegistry.keys.map { it.store } + SettingsRegistry.families.map { it.template.store }).toSet()
        stores.forEach { assertTrue("$it holds declared keys", it !in keyed) }
        assertTrue(SettingsRegistry.stores.none { it in stores })
    }

    @Test
    fun theStoreLayerDoesNotOpenThem() {
        SettingsRegistry.fileStores.forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { SettingsStores.AndroidProvider.open(id) }
        }
    }
}
