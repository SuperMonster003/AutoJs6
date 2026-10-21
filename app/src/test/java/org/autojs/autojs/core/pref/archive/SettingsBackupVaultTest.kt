package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.archive.SettingsBackupVault.LastOperation
import org.autojs.autojs.core.pref.archive.SettingsBackupVault.Operation
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.pref.keys.ConsoleKeys
import org.autojs.autojs.core.pref.keys.CredentialKeys
import org.autojs.autojs.core.pref.keys.DomainKeysTestBase
import org.autojs.autojs.core.pref.keys.ExplorerKeys
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId
import java.util.zip.ZipFile

/**
 * docs/settings/ROADMAP.md P7.3: the internal backups of [SettingsBackupVault] (T9): five kept for imports and resets
 * together, `last-operation.json`, and an undo that touches the recorded categories only.
 *
 * zh-CN: ROADMAP P7.3: [SettingsBackupVault] 的内部备份 (T9): 导入与恢复默认合计保留 5 份, `last-operation.json`, 撤销只触及记录过的分类.
 */
class SettingsBackupVaultTest : DomainKeysTestBase() {

    @get:Rule
    val folder = TemporaryFolder()

    private val host = SettingsArchiveManifest.Host("org.autojs.autojs6", 5460, "6.8.0")

    private val device = SettingsArchiveManifest.Device("XQ-DQ72", 35)

    /** 2026-10-21 10:00:00 at +08:00, one hour per call. */
    private var now = 1_792_548_000_000L

    private fun vault(directory: File = File(folder.root, SettingsBackupVault.DIRECTORY)) =
        SettingsBackupVault(directory, { now.also { now += HOUR } }, ZONE, key = KEY)

    private fun everything() = SettingsSnapshot.capture(SettingCategory.ALL)

    @Test
    fun theNewestFiveBackupsOfImportsAndResetsAreKept() {
        val vault = vault()
        val written = (0 until 7).map { vault.backup(if (it % 3 == 2) Operation.RESET else Operation.IMPORT, everything(), host, device) }
        assertEquals("auto-import-20261021-100000.zip", written.first().name)
        assertEquals("auto-reset-20261021-120000.zip", written[2].name)
        val kept = vault.backups()
        assertEquals(5, kept.size)
        assertEquals(written.drop(2).reversed().map { it.name }, kept.map { it.name })
        assertFalse(written[0].file.exists())
        assertFalse(written[1].file.exists())
        // Files that are no backups are never pruned.
        listOf("notes.txt", "auto-import-yesterday.zip", "auto-import-20261020-100000.zip.tmp").forEach { File(vault.directory, it).writeText("x") }
        vault.backup(Operation.IMPORT, everything(), host, device)
        assertEquals(5, vault.backups().size)
        assertEquals(8, vault.directory.list()!!.size)
        assertTrue(File(vault.directory, "notes.txt").isFile)
    }

    @Test
    fun backupsOfTheSameSecondAreCountedAndOrdered() {
        val directory = File(folder.root, "same-second")
        val vault = SettingsBackupVault(directory, { now }, ZONE, keep = 2, key = KEY)
        val first = vault.backup(Operation.IMPORT, everything(), host, device)
        val second = vault.backup(Operation.RESET, everything(), host, device)
        val third = vault.backup(Operation.IMPORT, everything(), host, device)
        assertEquals(listOf("auto-import-20261021-100000.zip", "auto-reset-20261021-100000-1.zip", "auto-import-20261021-100000-2.zip"), listOf(first, second, third).map { it.name })
        assertEquals(listOf(third.name, second.name), vault.backups().map { it.name })
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupVault(directory, keep = 0) }
    }

    @Test
    fun aBackupHoldsEveryCategoryCredentialsAndOtherIncluded() {
        val token = CredentialKeys.GITHUB_TOKEN
        store(token.store).edit { put(token.name, "ghp_secret") }
        store.edit {
            put(MiscStateKeys.FIND_OR_REPLACE_USING_REGEX.name, true)
            put(AppearanceKeys.NIGHT_MODE_ENABLED.name, true)
        }
        val backup = vault().backup(Operation.IMPORT, everything(), host, device)
        val reader = SettingsArchiveReader({ backup.file.inputStream() }, key = KEY)
        val manifest = reader.manifest()
        assertEquals(SettingCategory.ALL.filterNot { it.isData }.map { it.id }, manifest.categories.map { it.id })
        assertTrue(manifest.categories.single { it.id == "credentials" }.sensitive)
        assertEquals("ghp_secret", reader.category("credentials").category.stores.getValue(token.store.name).getValue(token.name).value)
        assertEquals(true, reader.category("other").category.stores.getValue("default").getValue(MiscStateKeys.FIND_OR_REPLACE_USING_REGEX.name).value)
        assertEquals("2026-10-21T10:00:00+08:00", manifest.exportedAt)
        // The credentials are sealed for the device key (D23): not readable without it, not in plain text.
        val sealed = ZipFile(backup.file).use { zip -> zip.getInputStream(zip.getEntry("categories/credentials.json")).readBytes().toString(Charsets.UTF_8) }
        assertTrue(SettingsSealedFile.isSealed(SettingsJson.parse(sealed)))
        assertFalse(sealed.contains("ghp_secret") || sealed.contains(token.name))
        assertTrue(SettingsArchiveReader({ backup.file.inputStream() }).isLocked("credentials"))
        assertEquals(
            SettingsArchiveException.Reason.LOCKED,
            assertThrows(SettingsArchiveException::class.java) { SettingsArchiveReader({ backup.file.inputStream() }).category("credentials") }.reason,
        )
        // A vault without the key refuses to back up credentials instead of writing them in plain text.
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupVault(File(folder.root, "no-key")).backup(Operation.IMPORT, everything(), host, device) }
    }

    @Test
    fun theLastOperationIsRecordedAndReadBack() {
        val vault = vault()
        assertNull(vault.lastOperation())
        assertFalse(vault.canUndo())
        val backup = vault.backup(Operation.IMPORT, everything(), host, device)
        val record = LastOperation(Operation.IMPORT, now, backup.name, listOf("appearance", "console"), listOf("data_shortcuts"))
        vault.record(record)
        assertEquals(record, vault.lastOperation())
        assertTrue(vault.canUndo())
        val text = File(vault.directory, SettingsBackupVault.LAST_OPERATION).readText()
        assertTrue(text, text.startsWith("{\n  \"operation\": \"import\",\n  \"time\": "))
        // A record that is not readable is no record.
        listOf("", "[]", "{\"operation\": \"merge\", \"time\": 1, \"backup\": \"auto-import-20261021-100000.zip\", \"categories\": []}",
            "{\"operation\": \"import\", \"time\": 1, \"backup\": \"../x.zip\", \"categories\": []}",
            "{\"operation\": \"import\", \"time\": 1, \"backup\": \"auto-import-20261021-100000.zip\", \"categories\": [\"General\"]}").forEach {
            File(vault.directory, SettingsBackupVault.LAST_OPERATION).writeText(it)
            assertNull(it, vault.lastOperation())
        }
        // A record whose backup has been pruned cannot be undone.
        vault.record(record.copy(backup = "auto-import-20200101-000000.zip"))
        assertFalse(vault.canUndo())
        assertEquals(
            SettingsArchiveException.Reason.MISSING_FILE,
            assertThrows(SettingsArchiveException::class.java) { vault.undoPlan(ImportEnvironment()) }.reason,
        )
        vault.clearLastOperation()
        assertNull(vault.lastOperation())
        assertNull(vault.undoPlan(ImportEnvironment()))
    }

    @Test
    fun anUndoTouchesTheRecordedCategoriesOnly() {
        val timeVisible = ConsoleKeys.LOG_ACTIVITY_TIME_VISIBLE
        store.edit {
            put(AppearanceKeys.NIGHT_MODE_ENABLED.name, false)
            put(timeVisible.name, true)
        }
        val vault = vault()
        val backup = vault.backup(Operation.IMPORT, everything(), host, device)
        // The import changed appearance; console changed afterwards, by hand.
        store.edit {
            put(AppearanceKeys.NIGHT_MODE_ENABLED.name, true)
            put(AppearanceKeys.THEME_COLOR_PRIMARY.name, 9)
            put(timeVisible.name, false)
        }
        vault.record(LastOperation(Operation.IMPORT, now, backup.name, listOf("appearance"), listOf("data_color_library")))
        val undo = vault.undoPlan(ImportEnvironment())!!
        assertEquals(listOf("appearance"), undo.plan.categories)
        assertEquals(listOf(AppearanceKeys.NIGHT_MODE_ENABLED.name to false), undo.plan.puts.map { it.name to it.value })
        assertEquals(listOf(AppearanceKeys.THEME_COLOR_PRIMARY.name), undo.plan.removes.map { it.name })
        assertTrue((undo.plan.puts + undo.plan.removes.map { ImportPlan.Put(it.category, it.store, it.name, 0, it.effect) }).none { it.name == timeVisible.name })
        assertEquals(listOf("data_color_library"), undo.dataNotUndone)
        assertEquals(Operation.IMPORT, undo.operation.operation)
    }

    @Test
    fun anUndoWritesDeploymentKeysWithoutAskingAgain() {
        store.edit { put(ExplorerKeys.WORKING_DIRECTORY.name, "Scripts/Old") }
        val vault = vault()
        val backup = vault.backup(Operation.RESET, everything(), host, device)
        store.edit { remove(ExplorerKeys.WORKING_DIRECTORY.name) }
        vault.record(LastOperation(Operation.RESET, now, backup.name, listOf("explorer", "future_category")))
        // The directory does not exist (any more): the undo still writes the key, with no deployment item.
        val plan = vault.undoPlan(ImportEnvironment())!!.plan
        assertEquals(listOf("explorer"), plan.categories)
        assertEquals(listOf(ExplorerKeys.WORKING_DIRECTORY.name to "Scripts/Old"), plan.puts.map { it.name to it.value })
        assertEquals(emptyList<ImportPlan.Deployment>(), plan.deployments)
    }

    private companion object {
        const val HOUR = 3_600_000L
        val ZONE: ZoneId = ZoneId.of("+08:00")
        val KEY = SettingsDeviceKey.ofBytes(ByteArray(32) { (it * 7).toByte() })
    }
}
