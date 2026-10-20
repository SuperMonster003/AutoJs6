package org.autojs.autojs.core.pref

import org.autojs.autojs.core.pref.SettingDescriptorFixtures.cache
import org.autojs.autojs.core.pref.SettingDescriptorFixtures.excluded
import org.autojs.autojs.core.pref.SettingDescriptorFixtures.setting
import org.autojs.autojs.core.pref.SettingDescriptorFixtures.spec
import org.autojs.autojs.core.pref.SettingDescriptorFixtures.state
import org.autojs.autojs.core.pref.SettingDescriptorFixtures.valueConstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingDescriptorFixturesTest {

    @Test
    fun settingCarriesTheT4Fields() {
        val row = setting("key_\$_night_mode", "string", "appearance", "key_\$_night_mode_follow_system", "APPLY")
        assertEquals(
            SettingDescriptorSpec(
                name = "key_\$_night_mode",
                type = "string",
                store = "default",
                category = "appearance",
                defaultText = "key_\$_night_mode_follow_system",
                effect = "APPLY",
                kind = "Setting",
                deployment = null,
            ),
            row,
        )
    }

    @Test
    fun onlySettingsCarryADeploymentKind() {
        val rows = listOf(
            setting("key_\$_working_directory", "string", "explorer", deployment = "CREATE_DIRECTORY"),
            setting("key_\$_ace_editor_font", "string", "editor_appearance", "iosevka", deployment = "DOWNLOAD_EDITOR_FONT"),
            setting("key_\$_editor_text_size", "int", "editor_appearance", "14"),
        )
        assertEquals(listOf("CREATE_DIRECTORY", "DOWNLOAD_EDITOR_FONT"), SettingDescriptorFixtures.deploymentKinds(rows).toList())
        assertThrows(IllegalArgumentException::class.java) {
            setting("k", "string", "explorer", deployment = "UNZIP_PROJECT")
        }
        assertThrows(IllegalArgumentException::class.java) {
            spec("k", "string", "default", "other", null, "NONE", "State", "CREATE_DIRECTORY")
        }
        assertThrows(IllegalArgumentException::class.java) {
            spec("k", "string", "default", null, null, "NONE", "Cache", "DOWNLOAD_EDITOR_FONT")
        }
    }

    @Test
    fun stateIsAlwaysInOtherWithoutEffect() {
        val row = state("key_\$_last_updates_checked", "long", "-1")
        assertEquals("other", row.category)
        assertEquals("NONE", row.effect)
        assertEquals("State", row.kind)
        assertThrows(IllegalArgumentException::class.java) {
            spec("key_\$_x", "bool", "default", "appearance", null, "NONE", "State")
        }
    }

    @Test
    fun nonSettingKindsHaveNoCategory() {
        assertNull(cache("key_root_manager_apps_cache", "string").category)
        assertNull(valueConstant("key_\$_night_mode_follow_system").category)
        assertNull(excluded("key_\$_pref_bundle_default_item").category)
        assertThrows(IllegalArgumentException::class.java) {
            spec("key_\$_x", "string", "default", "general", null, "NONE", "Excluded")
        }
        assertThrows(IllegalArgumentException::class.java) {
            spec("key_\$_x", "string", "default", null, null, "RESTART", "Cache")
        }
    }

    @Test
    fun unknownValuesAndMissingCategoryAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { setting("k", "double", "general") }
        assertThrows(IllegalArgumentException::class.java) { setting("k", "bool", "general", effect = "LATER") }
        assertThrows(IllegalArgumentException::class.java) { spec("k", "bool", "default", null, null, "NONE", "Setting") }
        assertThrows(IllegalArgumentException::class.java) { setting("k", "bool", "other") }
        assertThrows(IllegalArgumentException::class.java) { spec("k", "bool", "default", "general", null, "NONE", "Pref") }
        assertThrows(IllegalArgumentException::class.java) { setting(" ", "bool", "general") }
        assertThrows(IllegalArgumentException::class.java) { setting("k", "bool", "general", store = "") }
    }

    @Test
    fun rowsGroupByCategoryInFirstSeenOrder() {
        val rows = listOf(
            setting("a", "bool", "editor_gutter"),
            valueConstant("v"),
            setting("b", "int", "appearance"),
            state("c", "long"),
            setting("d", "string", "editor_gutter", store = "editor_symbols_config"),
        )
        val grouped = SettingDescriptorFixtures.byCategory(rows)
        assertEquals(listOf("editor_gutter", "appearance", "other"), grouped.keys.toList())
        assertEquals(listOf("a", "d"), grouped.getValue("editor_gutter").map { it.name })
    }

    @Test
    fun duplicateNamesAreDetectedPerStore() {
        val rows = listOf(
            setting("a", "bool", "general"),
            state("a", "bool"),
            setting("a", "bool", "general", store = "other_store"),
            setting("b", "bool", "general"),
        )
        assertEquals(setOf("a"), SettingDescriptorFixtures.duplicateNames(rows))
        assertTrue(SettingDescriptorFixtures.duplicateNames(rows.drop(1)).isEmpty())
    }

    @Test
    fun storeWithDefaultsParsesPrimitiveDefaults() {
        val rows = listOf(
            setting("b", "bool", "general", "true"),
            setting("i", "int", "general", "14"),
            setting("l", "long", "history", "2147483648"),
            setting("f", "float", "general", "0.5"),
            setting("s", "string", "general", "key_\$_x"),
            setting("set", "stringSet", "developer", "[]"),
            setting("none", "bool", "general"),
            setting("elsewhere", "bool", "general", "true", store = "editor_symbols_config"),
        )
        val store = SettingDescriptorFixtures.storeWithDefaults(rows)
        assertEquals(
            mapOf("b" to true, "i" to 14, "l" to 2147483648L, "f" to 0.5f, "s" to "key_\$_x"),
            store.all(),
        )
        assertEquals(1, store.commitCount)
        assertEquals(mapOf("elsewhere" to true), SettingDescriptorFixtures.storeWithDefaults(rows, "editor_symbols_config").all())
    }
}
