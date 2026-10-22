package org.autojs.autojs.ui.settings.transfer

import org.autojs.autojs.core.pref.DeploymentKind
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.TypedValue
import org.autojs.autojs.core.pref.archive.ImportEnvironment
import org.autojs.autojs.core.pref.archive.ImportPlan.SkipReason
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest.CategoryEntry
import org.autojs.autojs.core.pref.archive.SettingsArchiveReader
import org.autojs.autojs.core.pref.archive.SettingsArchiveWriter
import org.autojs.autojs.core.pref.archive.SettingsDeviceKey
import org.autojs.autojs.core.pref.archive.SettingsEffects
import org.autojs.autojs.core.pref.archive.SettingsEffects.EffectException
import org.autojs.autojs.core.pref.archive.SettingsImportApplier
import org.autojs.autojs.core.pref.archive.SettingsSnapshot
import org.autojs.autojs.core.pref.data.ColorLibraryDataCodec
import org.autojs.autojs.core.pref.data.ShortcutDataCodec
import org.autojs.autojs.core.pref.deploy.DeploymentException
import org.autojs.autojs.core.pref.deploy.DeploymentResult
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.pref.keys.ConsoleKeys
import org.autojs.autojs.core.pref.keys.CredentialKeys
import org.autojs.autojs.core.pref.keys.DomainKeysTestBase
import org.autojs.autojs.external.shortcut.ShortcutBackupEntry
import org.autojs.autojs.external.shortcut.ShortcutBackupState
import org.autojs.autojs.theme.library.ColorLibraryJson
import org.autojs.autojs.ui.settings.transfer.SettingsCategoryChoice.State
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Kind
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * docs/settings/ROADMAP.md P7.5: the import wizard lists only the categories of the file and disables the credentials
 * sealed for another device (D23), starts with the default selection of appendix A.1, plans the selected categories plus
 * the unknown ones (`NEWER_FORMAT`), and keeps its outcome in the wizard state.
 *
 * zh-CN: ROADMAP P7.5: 导入向导只列出文件中的分类, 以其他设备密钥加密的凭据不可勾选 (D23), 初始勾选为附录 A.1 的默认勾选, 计划勾选的
 * 分类与本版本不认识的分类 (`NEWER_FORMAT`), 结果保存在向导状态中.
 */
class SettingsImportFlowTest : DomainKeysTestBase() {

    private val host = SettingsArchiveManifest.Host("org.autojs.autojs6.other", 5460, "6.8.0")

    private val device = SettingsArchiveManifest.Device("XQ-DQ72", 33)

    @Test
    fun theImportListsTheCategoriesOfTheFileInPageOrder() {
        val manifest = SettingsArchiveManifest(
            "2026-10-22T10:00:00+08:00", host, device,
            categories = listOf(
                CategoryEntry("other", 3, false),
                CategoryEntry("appearance", 2, false),
                CategoryEntry("credentials", 1, true),
                CategoryEntry("future_category", 4, false),
            ),
            data = listOf(SettingsArchiveManifest.DataEntry("data_shortcuts", 2, "data/shortcuts.zip")),
        )
        val choices = SettingsCategoryChoices.import(manifest, locked = listOf("credentials"))
        assertEquals(listOf("appearance", "data_shortcuts", "credentials", "other"), choices.map { it.category.id })
        assertEquals(listOf(State.SELECTABLE, State.SELECTABLE, State.LOCKED, State.SELECTABLE), choices.map { it.state })
        assertEquals(listOf(2, 2, 1, 3), choices.map { it.entries })
        // Ticked by default: appendix A.1 with the data categories (P10.2), without credentials and other.
        assertEquals(listOf("appearance", "data_shortcuts"), SettingsCategoryChoices.importSelection(choices))
        val selectable = SettingsCategoryChoices.selectable(choices)
        assertEquals(listOf(SettingCategory.APPEARANCE, SettingCategory.DATA_SHORTCUTS, SettingCategory.OTHER), selectable)
        assertEquals(listOf("appearance", "data_shortcuts", "other"), SettingsCategorySelection.all(selectable))
        assertEquals(listOf("data_shortcuts", "other"), SettingsCategorySelection.inverted(listOf("appearance"), selectable))
        assertEquals(listOf("appearance", "data_shortcuts"), SettingsCategorySelection.normalize(listOf("credentials", "appearance", "data_shortcuts"), selectable))
        // Unlocked credentials can be ticked, but are not ticked by default.
        val unlocked = SettingsCategoryChoices.import(manifest, locked = emptyList())
        assertEquals(State.SELECTABLE, unlocked.single { it.category == SettingCategory.CREDENTIALS }.state)
        assertEquals(listOf("appearance", "data_shortcuts"), SettingsCategoryChoices.importSelection(unlocked))
    }

    @Test
    fun theExportListsEveryCategoryAsSelectable() {
        val choices = SettingsCategoryChoices.export(mapOf("appearance" to 4, "data_shortcuts" to 2))
        assertEquals(SettingCategory.ALL, choices.map { it.category })
        assertEquals(4, choices.first().entries)
        assertEquals(0, choices.single { it.category == SettingCategory.GENERAL }.entries)
        assertEquals(2, choices.single { it.category == SettingCategory.DATA_SHORTCUTS }.entries)
        assertEquals(0, choices.single { it.category == SettingCategory.DATA_COLOR_LIBRARY }.entries)
        assertTrue(choices.all { it.state == State.SELECTABLE })
        assertEquals(SettingCategory.ALL, SettingsCategoryChoices.selectable(choices))
    }

    @Test
    fun aFileOfAnotherDeviceIsInspectedPlannedAndApplied() {
        store.edit {
            put(AppearanceKeys.THEME_COLOR_PRIMARY.name, 5)
            put(AppearanceKeys.NIGHT_MODE_ENABLED.name, true)
            put(ConsoleKeys.LOG_ACTIVITY_TIME_VISIBLE.name, false)
        }
        CredentialKeys.GITHUB_TOKEN.set("token of the other device")
        val captured = SettingsSnapshot.capture(listOf(SettingCategory.APPEARANCE, SettingCategory.CONSOLE, SettingCategory.CREDENTIALS))
        val future = SettingsSnapshot.Category("future_category", mapOf("default" to mapOf("a" to TypedValue("bool", true), "b" to TypedValue("int", 2))))
        val exported = ByteArrayOutputStream().also {
            SettingsArchiveWriter.write(it, SettingsSnapshot(captured.categories + future), "2026-10-22T10:00:00+08:00", host, device, key = OTHER_KEY)
        }.toByteArray()

        // This device: other values, a key the file does not have, its own token.
        store.edit {
            clear()
            put(AppearanceKeys.THEME_COLOR_PRIMARY.name, 6)
            put(AppearanceKeys.THEME_COLOR_ACCENT.name, 7)
            put(ConsoleKeys.LOG_ACTIVITY_TIME_VISIBLE.name, true)
        }
        CredentialKeys.GITHUB_TOKEN.set("token of this device")
        val reader = SettingsArchiveReader({ ByteArrayInputStream(exported) }, key = KEY)
        val inspection = SettingsImporter.inspect(reader, "autojs6-settings-20261022-100000.zip", "org.autojs.autojs6")
        assertEquals(setOf(SettingsArchiveManifest.Warning.OTHER_HOST_PACKAGE), inspection.warnings)
        assertEquals(listOf("credentials"), inspection.locked)
        assertEquals(listOf("future_category"), inspection.unknown)
        assertEquals(listOf("appearance", "console", "credentials"), inspection.choices.map { it.category.id })
        assertEquals(State.LOCKED, inspection.choices.last().state)

        // Credentials stay out even when asked for; the unknown category only counts.
        val plan = SettingsImporter.plan(reader, inspection, listOf("appearance", "credentials"), ImportEnvironment())
        assertEquals(listOf("appearance", "future_category"), plan.categories)
        assertEquals(mapOf(SkipReason.NEWER_FORMAT to 2), plan.skipCounts.filterValues { it > 0 })
        val changes = SettingsImportApplier.write(plan)
        val failure = EffectException(EffectException.Reason.NOTIFICATION_PERMISSION, "no permission")
        val report = SettingsEffects.run(changes, { entry -> SettingsEffects.Action { if (entry == SettingsEffects.Entry.NIGHT_MODE) throw failure } })
        val outcome = SettingsTransferOutcome.of(plan, changes, report, credentialsLocked = inspection.locked.isNotEmpty())

        assertEquals(mapOf(AppearanceKeys.THEME_COLOR_PRIMARY.name to 5, AppearanceKeys.NIGHT_MODE_ENABLED.name to true, ConsoleKeys.LOG_ACTIVITY_TIME_VISIBLE.name to true), store.all())
        assertEquals("token of this device", CredentialKeys.GITHUB_TOKEN.get())
        assertEquals(2, outcome.categories)
        assertEquals(plan.puts.size, outcome.written)
        assertEquals(1, outcome.restored)
        assertEquals(3, outcome.changed)
        assertEquals(mapOf(SkipReason.NEWER_FORMAT to 2), outcome.skips)
        assertEquals(2, outcome.skipped)
        assertEquals(listOf(SettingsEffects.Entry.NIGHT_MODE), outcome.failedEffects)
        assertTrue(outcome.notificationPermission)
        assertTrue(outcome.credentialsLocked)
        assertEquals(outcome, SettingsTransferOutcome.fromJson(outcome.toJson()))
    }

    @Test
    fun theDataCategoriesAreImportedApartWithTheChosenEntries() {
        val shortcuts = ShortcutDataCodec.encode(
            ShortcutBackupState(
                listOf(
                    ShortcutBackupEntry("copy-1", "A", "Script A", "script", "/sdcard/scripts/a.js", icon = null),
                    ShortcutBackupEntry("copy-2", "B", "Settings", "settings", icon = null),
                ),
            ),
            TIME,
        )
        val colors = ColorLibraryDataCodec.encode(listOf(ColorLibraryJson.PlainLibrary("灰阶", emptyList()), ColorLibraryJson.PlainLibrary("品牌色", emptyList())), TIME)
        val captured = SettingsSnapshot.capture(listOf(SettingCategory.APPEARANCE))
        val exported = ByteArrayOutputStream().also {
            SettingsArchiveWriter.write(it, captured, "2026-10-22T10:00:00+08:00", host, device, listOf(shortcuts, colors))
        }.toByteArray()
        val reader = SettingsArchiveReader({ ByteArrayInputStream(exported) }, key = KEY)
        val inspection = SettingsImporter.inspect(reader, "a.zip", "org.autojs.autojs6.other")
        assertEquals(listOf("appearance", "data_shortcuts", "data_color_library"), inspection.choices.map { it.category.id })
        assertEquals(listOf(2, 2), inspection.choices.drop(1).map { it.entries })
        val selected = listOf("appearance", "data_shortcuts", "data_color_library")

        // The plan holds the settings categories only.
        assertEquals(listOf("appearance"), SettingsImporter.plan(reader, inspection, selected, ImportEnvironment()).categories)
        // No choice keeps every entry; a choice keeps its entries; a category without entries left is not imported.
        assertEquals(listOf(shortcuts.bytes.toList(), colors.bytes.toList()), SettingsImporter.dataFiles(reader, inspection, selected).map { it.bytes.toList() })
        val chosen = SettingsImporter.dataFiles(reader, inspection, selected, mapOf("data_shortcuts" to listOf("copy-2"), "data_color_library" to emptyList()), TIME)
        assertEquals(listOf("data_shortcuts"), chosen.map { it.id })
        assertEquals(listOf("copy-2"), ShortcutDataCodec.decode(chosen.single().bytes).entries.map { it.id })
        assertEquals("shortcuts.zip", chosen.single().name)
        // A data category that is not ticked is not imported.
        assertEquals(listOf("data_color_library"), SettingsImporter.dataFiles(reader, inspection, listOf("data_color_library")).map { it.id })
    }

    @Test
    fun theOutcomeAndTheImportStateSurviveAsJson() {
        val outcome = SettingsTransferOutcome(
            categories = 3, written = 40, restored = 2, changed = 5,
            skips = linkedMapOf(SkipReason.UNKNOWN_KEY to 1, SkipReason.TARGET_MISSING to 2),
            failedEffects = listOf(SettingsEffects.Entry.LANGUAGE),
            restartNeeded = true,
            dataNotUndone = listOf("data_shortcuts"),
            backupFailure = "Cannot create /sdcard/Scripts/backups/settings",
            launcherIconPending = true,
            deployments = listOf(
                DeploymentResult(DeploymentKind.CREATE_DIRECTORY, "default", "key_\$_working_directory", "/storage/emulated/0/Projects"),
                DeploymentResult(DeploymentKind.DOWNLOAD_EDITOR_FONT, "default", "key_\$_ace_editor_font", "JetBrains Mono", DeploymentException.Reason.NETWORK, "timeout"),
            ),
            data = listOf(
                SettingsTransferOutcome.DataOutcome("data_shortcuts", 3, 2, listOf("copy-1", "copy-3"), iconsMissing = true),
                SettingsTransferOutcome.DataOutcome("data_color_library", 2, 1, failure = "No space left on device"),
                // As the undo gives it (P10.3).
                SettingsTransferOutcome.DataOutcome("data_shortcuts", 3, 0, removed = 2, kept = 1),
            ),
        )
        assertEquals(listOf("copy-1", "copy-3"), outcome.restorableShortcuts)
        val state = SettingsImportWizard.initialState().copy(
            step = Step.RESULT, selected = listOf("appearance"), archive = "/data/cache/settings-import/import.zip",
            fileName = "a.zip", deploy = listOf("default:key_\$_working_directory"), operation = "0b2e", outcome = outcome,
        )
        assertEquals(state, SettingsWizardState.fromText(state.toText()))
        assertEquals(Kind.IMPORT, SettingsWizardState.fromText(state.toText())!!.kind)
        // Unknown reasons and entries of a newer version are left out; a broken outcome is none.
        val newer = outcome.toJson() + mapOf("skips" to mapOf("UNKNOWN_KEY" to 1, "SOMETHING_NEW" to 4), "failedEffects" to listOf("LANGUAGE", "NEW_ENTRY"))
        assertEquals(outcome.copy(skips = mapOf(SkipReason.UNKNOWN_KEY to 1)), SettingsTransferOutcome.fromJson(newer))
        assertNull(SettingsTransferOutcome.fromJson(mapOf("categories" to 1)))
        // A data outcome of a version before P10.3 has no removals.
        assertEquals(
            SettingsTransferOutcome.DataOutcome("data_shortcuts", 1, 1),
            SettingsTransferOutcome.DataOutcome.fromJson(mapOf("id" to "data_shortcuts", "entries" to 1, "added" to 1)),
        )
        // A data outcome without a valid category id is left out.
        val unknownData = outcome.toJson() + mapOf("data" to (outcome.data.map { it.toJson() } + mapOf("id" to "../shortcuts", "entries" to 1, "added" to 1)))
        assertEquals(outcome, SettingsTransferOutcome.fromJson(unknownData))
        assertEquals(SettingsWizardState(Kind.UNDO, Step.CONFIRM), SettingsUndoWizard.initialState())
        val another = SettingsImportWizard.anotherFile(state.copy(step = Step.FAILED, failure = SettingsWizardState.Failure.INVALID_FILE))
        assertEquals(SettingsWizardState(Kind.IMPORT, Step.CHOOSE_FILE), another)
    }

    private companion object {
        /** 2026-10-20T15:30:00+08:00. */
        const val TIME = 1_792_481_400_000L
        val KEY = SettingsDeviceKey.ofBytes(ByteArray(32) { (it * 7).toByte() })
        val OTHER_KEY = SettingsDeviceKey.ofBytes(ByteArray(32) { (it * 11 + 1).toByte() })
    }
}
