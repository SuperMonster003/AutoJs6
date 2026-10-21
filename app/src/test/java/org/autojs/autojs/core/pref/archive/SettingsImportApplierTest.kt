package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingEffect
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.archive.SettingsEffects.Change
import org.autojs.autojs.core.pref.archive.SettingsEffects.EffectException
import org.autojs.autojs.core.pref.archive.SettingsEffects.Entry
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.pref.keys.ConsoleKeys
import org.autojs.autojs.core.pref.keys.DomainKeysTestBase
import org.autojs.autojs.core.pref.keys.GeneralKeys
import org.autojs.autojs.core.pref.keys.NamedStores
import org.autojs.autojs.core.pref.keys.PluginCenterKeys
import org.autojs.autojs.core.pref.store.StoreId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * docs/settings/ROADMAP.md P7.4: [SettingsImportApplier] commits each store of a plan once and reports the keys that
 * changed; [SettingsEffects] runs the entry of the changed `APPLY` keys once each (T8 / T12). The Android side
 * (`AndroidSettingsEffects`) is replaced by recording fakes; its evidence on devices is P7.5 (E3).
 *
 * zh-CN: ROADMAP P7.4: [SettingsImportApplier] 对计划中的每个存储提交一次并报告变化的键; [SettingsEffects] 对变化的 `APPLY` 键所属入口
 * 各执行一次 (T8 / T12). Android 部分 (`AndroidSettingsEffects`) 以记录调用的替身代替, 真机证据在 P7.5 (E3).
 */
class SettingsImportApplierTest : DomainKeysTestBase() {

    private val calls = ArrayList<Entry>()

    private val recording: (Entry) -> SettingsEffects.Action = { entry -> SettingsEffects.Action { calls.add(entry) } }

    @Test
    fun everyStoreIsCommittedOnce() {
        val enableStore = NamedStores.PLUGIN_CENTER_ENABLE_STATE
        val enabled = PluginCenterKeys.PLUGIN_ENABLED
        store.edit { put(AppearanceKeys.NIGHT_MODE_ENABLED.name, true) }
        store(enableStore).edit { put(enabled.nameOf("org.example.old"), false) }
        val before = store.commitCount to store(enableStore).commitCount
        val plan = ImportPlan(
            categories = listOf("appearance", "console", "plugin_enable_state"),
            puts = listOf(
                ImportPlan.Put("appearance", StoreId.DEFAULT, AppearanceKeys.THEME_COLOR_PRIMARY.name, 9, SettingEffect.APPLY),
                ImportPlan.Put("plugin_enable_state", enableStore, enabled.nameOf("org.example.new"), false, SettingEffect.NONE),
                ImportPlan.Put("console", StoreId.DEFAULT, ConsoleKeys.LOG_ACTIVITY_TIME_VISIBLE.name, false, SettingEffect.NONE),
            ),
            removes = listOf(
                ImportPlan.Remove("appearance", StoreId.DEFAULT, AppearanceKeys.NIGHT_MODE_ENABLED.name, SettingEffect.APPLY),
                ImportPlan.Remove("plugin_enable_state", enableStore, enabled.nameOf("org.example.old"), SettingEffect.NONE),
            ),
            skips = emptyList(),
            deployments = emptyList(),
        )
        val changes = SettingsImportApplier.write(plan)
        assertEquals(before.first + 1, store.commitCount)
        assertEquals(before.second + 1, store(enableStore).commitCount)
        assertEquals(mapOf(AppearanceKeys.THEME_COLOR_PRIMARY.name to 9, ConsoleKeys.LOG_ACTIVITY_TIME_VISIBLE.name to false), store.all())
        assertEquals(mapOf(enabled.nameOf("org.example.new") to false), store(enableStore).all())
        // Removes of a store come before its puts.
        assertEquals(
            listOf(
                Change(StoreId.DEFAULT, AppearanceKeys.NIGHT_MODE_ENABLED.name, SettingEffect.APPLY),
                Change(StoreId.DEFAULT, AppearanceKeys.THEME_COLOR_PRIMARY.name, SettingEffect.APPLY),
                Change(StoreId.DEFAULT, ConsoleKeys.LOG_ACTIVITY_TIME_VISIBLE.name, SettingEffect.NONE),
                Change(enableStore, enabled.nameOf("org.example.old"), SettingEffect.NONE),
                Change(enableStore, enabled.nameOf("org.example.new"), SettingEffect.NONE),
            ),
            changes,
        )
        // Writing the same plan again changes nothing, but still commits once per store.
        assertEquals(emptyList<Change>(), SettingsImportApplier.write(plan))
        assertEquals(before.first + 2, store.commitCount)
    }

    @Test
    fun theEntryOfChangedApplyKeysRunsOnceEach() {
        store.edit {
            put(AppearanceKeys.APP_LANGUAGE.name, key("key_app_language_en"))
            put(GeneralKeys.FOREGROUND_SERVICE.name, true)
        }
        val plan = plan(
            put(AppearanceKeys.THEME_COLOR_ACCENT, 1),
            put(AppearanceKeys.THEME_COLOR_PRIMARY, 2),
            put(AppearanceKeys.THEME_COLOR_PRIMARY_DARK, 3),
            put(AppearanceKeys.AUTO_NIGHT_MODE_ENABLED, false),
            // The same value: no change, no effect.
            put(AppearanceKeys.APP_LANGUAGE, key("key_app_language_en")),
            put(ConsoleKeys.LOG_ACTIVITY_TIME_VISIBLE, false),
            remove(GeneralKeys.FOREGROUND_SERVICE),
        )
        val result = SettingsImportApplier.apply(plan, recording)
        assertEquals(listOf(Entry.THEME_COLOR, Entry.NIGHT_MODE, Entry.FOREGROUND_SERVICE), calls)
        assertEquals(calls, result.effects.applied)
        assertEquals(calls, result.effects.succeeded)
        assertEquals(emptyMap<Entry, Throwable>(), result.effects.failures)
        assertFalse(result.effects.restartNeeded)
        assertEquals(6, result.changes.size)
    }

    @Test
    fun aFailingEntryDoesNotStopTheOthers() {
        val failure = EffectException(EffectException.Reason.NOTIFICATION_PERMISSION, "no permission")
        val actions: (Entry) -> SettingsEffects.Action = { entry ->
            SettingsEffects.Action {
                calls.add(entry)
                if (entry == Entry.FOREGROUND_SERVICE) throw failure
                if (entry == Entry.LANGUAGE) throw IllegalStateException("no locale")
            }
        }
        val plan = plan(
            put(GeneralKeys.FOREGROUND_SERVICE, true),
            put(AppearanceKeys.APP_LANGUAGE, key("key_app_language_en")),
            put(GeneralKeys.LAUNCHER_ICON, key("key_launcher_icon_adaptive")),
        )
        val report = SettingsImportApplier.apply(plan, actions).effects
        assertEquals(listOf(Entry.LANGUAGE, Entry.LAUNCHER_ICON, Entry.FOREGROUND_SERVICE), calls)
        assertEquals(listOf(Entry.LANGUAGE, Entry.FOREGROUND_SERVICE), report.failures.keys.toList())
        assertSame(failure, report.failures[Entry.FOREGROUND_SERVICE])
        assertEquals(listOf(Entry.LAUNCHER_ICON), report.succeeded)
        // The values stay written: the result page reports the failures (P7.5).
        assertEquals(true, store[GeneralKeys.FOREGROUND_SERVICE.name])
    }

    @Test
    fun aRestartKeyAsksForARestart() {
        val restart = SettingKey.Bool(name = "test_restart_key", category = SettingCategory.GENERAL, default = SettingDefault.of(false), effect = SettingEffect.RESTART)
        val apply = SettingKey.Bool(name = "test_apply_key", category = SettingCategory.GENERAL, default = SettingDefault.of(false), effect = SettingEffect.APPLY)
        val table = mapOf(Entry.TIMED_TASK_BACKEND to listOf(apply))
        val changes = listOf(Change(StoreId.DEFAULT, restart.name, SettingEffect.RESTART), Change(StoreId.DEFAULT, "unbound", SettingEffect.NONE))
        val report = SettingsEffects.run(changes, recording, table)
        assertTrue(report.restartNeeded)
        assertEquals(emptyList<Entry>(), report.applied)
        assertEquals(Entry.TIMED_TASK_BACKEND, SettingsEffects.entryOf(StoreId.DEFAULT, apply.name, table))
        assertNull(SettingsEffects.entryOf(StoreId("other_store"), apply.name, table))
        assertEquals(listOf(Entry.TIMED_TASK_BACKEND), SettingsEffects.run(listOf(Change(StoreId.DEFAULT, apply.name, SettingEffect.APPLY)), recording, table).applied)
    }

    @Test
    fun anArchiveImportsEndToEnd() {
        store.edit {
            put(AppearanceKeys.THEME_COLOR_PRIMARY.name, 5)
            put(AppearanceKeys.NIGHT_MODE_ENABLED.name, true)
            put(ConsoleKeys.LOG_ACTIVITY_TIME_VISIBLE.name, false)
        }
        val exported = ByteArrayOutputStream().also {
            SettingsArchiveWriter.write(
                it, SettingsSnapshot.capture(listOf(SettingCategory.APPEARANCE, SettingCategory.CONSOLE)), "t",
                SettingsArchiveManifest.Host("org.autojs.autojs6", 5460, "6.8.0"), SettingsArchiveManifest.Device("XQ-DQ72", 35),
            )
        }.toByteArray()
        // This device: other values, and a key the archive does not have.
        store.edit {
            clear()
            put(AppearanceKeys.THEME_COLOR_PRIMARY.name, 6)
            put(AppearanceKeys.THEME_COLOR_ACCENT.name, 7)
            put(ConsoleKeys.LOG_ACTIVITY_TIME_VISIBLE.name, true)
        }
        val reader = SettingsArchiveReader({ ByteArrayInputStream(exported) })
        val manifest = reader.manifest()
        val plan = SettingsImportPlanner.plan(reader.categories(listOf("appearance"), manifest), listOf("appearance"), ImportEnvironment())
        val result = SettingsImportApplier.apply(plan, recording)
        // Appearance is the exported one, console is left as it is.
        assertEquals(mapOf(AppearanceKeys.THEME_COLOR_PRIMARY.name to 5, AppearanceKeys.NIGHT_MODE_ENABLED.name to true, ConsoleKeys.LOG_ACTIVITY_TIME_VISIBLE.name to true), store.all())
        assertEquals(listOf(Entry.THEME_COLOR, Entry.NIGHT_MODE), result.effects.applied)
    }

    private fun put(key: SettingKey<*>, value: Any) = ImportPlan.Put(key.category.id, key.store, key.name, value, key.effect)

    private fun remove(key: SettingKey<*>) = ImportPlan.Remove(key.category.id, key.store, key.name, key.effect)

    private fun plan(vararg changes: Any) = ImportPlan(
        categories = changes.map { if (it is ImportPlan.Put) it.category else (it as ImportPlan.Remove).category }.distinct(),
        puts = changes.filterIsInstance<ImportPlan.Put>(),
        removes = changes.filterIsInstance<ImportPlan.Remove>(),
        skips = emptyList(),
        deployments = emptyList(),
    )
}
