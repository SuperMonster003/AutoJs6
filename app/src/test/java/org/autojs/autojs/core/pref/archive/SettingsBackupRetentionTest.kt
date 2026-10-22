package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.archive.SettingsAutoBackup.Mechanism
import org.autojs.autojs.core.pref.archive.SettingsAutoBackup.Outcome
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.pref.keys.CredentialKeys
import org.autojs.autojs.core.pref.keys.DeveloperKeys
import org.autojs.autojs.core.pref.keys.DomainKeysTestBase
import org.autojs.autojs.core.pref.keys.GeneralKeys
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.ZoneId
import java.util.zip.ZipFile
import kotlin.random.Random

/**
 * docs/settings/ROADMAP.md P9.1: the automatic backups of [SettingsAutoBackup] (T20): their directory and names, seven
 * kept per mechanism, the categories selected by default without credentials and `other`, and failures that leave
 * nothing behind.
 *
 * zh-CN: ROADMAP P9.1: [SettingsAutoBackup] 的自动备份 (T20): 目录与文件名, 每种机制保留 7 份, 内容为默认勾选的分类且不含凭据与
 * `other`, 失败时不留下文件.
 */
class SettingsBackupRetentionTest : DomainKeysTestBase() {

    @get:Rule
    val folder = TemporaryFolder()

    private val host = SettingsArchiveManifest.Host("org.autojs.autojs6", 5460, "6.8.0")

    private val device = SettingsArchiveManifest.Device("XQ-DQ72", 35)

    /** 2026-10-21 10:00:00 at +08:00, one minute per call. */
    private var now = START

    private fun auto(directory: File = SettingsAutoBackup.directoryIn(folder.root)) =
        SettingsAutoBackup(directory, { now.also { now += MINUTE } }, ZONE)

    private fun everything() = SettingsSnapshot.capture(SettingCategory.ALL)

    @Test
    fun theNewestSevenBackupsOfEachMechanismAreKept() {
        val auto = auto()
        val order = List(9) { Mechanism.OPERATION } + List(8) { Mechanism.CHANGE } + List(2) { Mechanism.PERIODIC }
        val written = order.shuffled(Random(7)).map { auto.write(it, everything(), host, device) }
        Mechanism.values().forEach { mechanism ->
            val ofMechanism = written.filter { it.mechanism == mechanism }
            assertEquals(mechanism.name, ofMechanism.takeLast(SettingsAutoBackup.KEEP).reversed().map { it.name }, auto.backups(mechanism).map { it.name })
            ofMechanism.dropLast(SettingsAutoBackup.KEEP).forEach { assertFalse(it.name, it.file.exists()) }
        }
        assertEquals(7 + 7 + 2, auto.backups().size)
        assertEquals(auto.backups().map { it.name }, auto.backups().sortedByDescending { it.time }.map { it.name })
        // Files that are no automatic backups are never pruned: exports, internal backups, partial and unknown ones.
        val others = listOf(
            "notes.txt",
            "autojs6-settings-20261021-100000.zip",
            "auto-import-20261021-100000.zip",
            "autojs6-settings-auto-operation-20261021-100000.zip.tmp",
            "autojs6-settings-auto-weekly-20261021-100000.zip",
            "autojs6-settings-auto-operation-yesterday.zip",
        )
        others.forEach { File(auto.directory, it).writeText("x") }
        File(auto.directory, "autojs6-settings-auto-operation-20261021-100000-1.zip").mkdir()
        auto.write(Mechanism.OPERATION, everything(), host, device)
        assertEquals(SettingsAutoBackup.KEEP, auto.backups(Mechanism.OPERATION).size)
        others.forEach { assertTrue(it, File(auto.directory, it).isFile) }
        assertTrue(File(auto.directory, "autojs6-settings-auto-operation-20261021-100000-1.zip").isDirectory)
    }

    @Test
    fun backupsAreNamedByMechanismAndTimeUnderTheWorkingDirectory() {
        assertEquals(File(File("/storage/emulated/0/Scripts"), "backups/settings"), SettingsAutoBackup.directoryIn(File("/storage/emulated/0/Scripts")))
        assertEquals(listOf("periodic", "change", "operation"), Mechanism.values().map { it.label })
        assertEquals("autojs6-settings-auto-change-", Mechanism.CHANGE.prefix)
        val first = auto().write(Mechanism.OPERATION, everything(), host, device)
        assertEquals("autojs6-settings-auto-operation-20261021-100000.zip", first.name)
        assertEquals(SettingsAutoBackup.directoryIn(folder.root), first.file.parentFile)
        // Backups of the same second are counted across mechanisms, and kept per mechanism.
        val directory = File(folder.root, "same-second")
        val auto = SettingsAutoBackup(directory, { START }, ZONE, keep = 2)
        val names = listOf(Mechanism.CHANGE, Mechanism.OPERATION, Mechanism.CHANGE, Mechanism.CHANGE).map { auto.write(it, everything(), host, device).name }
        assertEquals(
            listOf(
                "autojs6-settings-auto-change-20261021-100000.zip",
                "autojs6-settings-auto-operation-20261021-100000-1.zip",
                "autojs6-settings-auto-change-20261021-100000-2.zip",
                "autojs6-settings-auto-change-20261021-100000-3.zip",
            ),
            names,
        )
        assertEquals(listOf(names[3], names[2]), auto.backups(Mechanism.CHANGE).map { it.name })
        assertEquals(listOf(names[1]), auto.backups(Mechanism.OPERATION).map { it.name })
        assertThrows(IllegalArgumentException::class.java) { SettingsAutoBackup(directory, keep = 0) }
    }

    @Test
    fun aBackupHoldsTheDefaultCategoriesWithoutCredentialsAndOther() {
        val token = CredentialKeys.GITHUB_TOKEN
        store(token.store).edit { put(token.name, "ghp_secret") }
        store.edit {
            put(AppearanceKeys.NIGHT_MODE_ENABLED.name, true)
            put(MiscStateKeys.FIND_OR_REPLACE_USING_REGEX.name, true)
            put(DeveloperKeys.LEAKCANARY_ENABLED.name, false)
        }
        val ids = SettingsAutoBackup.CATEGORIES.map { it.id }
        assertEquals(SettingCategory.ALL.filter { it.defaultSelected }.map { it.id }, ids)
        assertTrue(ids.containsAll(listOf("data_shortcuts", "data_color_library")))
        assertTrue(ids.none { it in listOf("credentials", "other", "developer") })

        val data = listOf(
            SettingsArchiveWriter.DataFile("data_shortcuts", 2, "shortcuts.zip", byteArrayOf(1, 2)),
            SettingsArchiveWriter.DataFile("data_color_library", 1, "color-library.json", "{}".toByteArray()),
            SettingsArchiveWriter.DataFile("data_future", 1, "future.bin", byteArrayOf(3)),
        )
        val backup = auto().write(Mechanism.OPERATION, everything(), host, device, data)
        val reader = SettingsArchiveReader({ backup.file.inputStream() })
        val manifest = reader.manifest()
        assertEquals(ids.filterNot { SettingCategory.ofId(it).isData }, manifest.categories.map { it.id })
        assertTrue(manifest.categories.none { it.sensitive })
        assertEquals(listOf("data_shortcuts", "data_color_library"), manifest.data.map { it.id })
        assertEquals("2026-10-21T10:00:00+08:00", manifest.exportedAt)
        assertEquals(true, reader.category("appearance").category.stores.getValue("default").getValue(AppearanceKeys.NIGHT_MODE_ENABLED.name).value)
        // Nothing of the categories left out is in the file, sealed or not (R6).
        ZipFile(backup.file).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertTrue(names.toString(), names.none { it.contains("credentials") || it.contains("other") || it.contains("developer") || it.contains("future") })
            val text = zip.entries().toList().joinToString("\n") { zip.getInputStream(it).readBytes().toString(Charsets.ISO_8859_1) }
            listOf("ghp_secret", token.name, MiscStateKeys.FIND_OR_REPLACE_USING_REGEX.name, DeveloperKeys.LEAKCANARY_ENABLED.name).forEach {
                assertFalse(it, text.contains(it))
            }
        }
    }

    @Test
    fun eachMechanismFollowsItsSwitch() {
        // T20: the operation backup is on by default, the periodic and change backups off; AndroidSettingsAutoBackup.write
        // returns Outcome.Off for a mechanism switched off.
        assertEquals(listOf(false, false, true), Mechanism.values().map { AndroidSettingsAutoBackup.isOn(it) })
        // No periodic work while its switch is off; then one every 7 days (weekly by default, Q32), 1 or 30.
        assertEquals(null, SettingsPeriodicBackupWorker.repeatDays())
        GeneralKeys.SETTINGS_AUTO_BACKUP_PERIODIC.set(true)
        assertEquals(7L, SettingsPeriodicBackupWorker.repeatDays())
        GeneralKeys.SETTINGS_AUTO_BACKUP_PERIOD.set(key("key_settings_auto_backup_period_daily"))
        assertEquals(1L, SettingsPeriodicBackupWorker.repeatDays())
        GeneralKeys.SETTINGS_AUTO_BACKUP_PERIOD.set(key("key_settings_auto_backup_period_monthly"))
        assertEquals(30L, SettingsPeriodicBackupWorker.repeatDays())
        GeneralKeys.SETTINGS_AUTO_BACKUP_CHANGE.set(true)
        GeneralKeys.SETTINGS_AUTO_BACKUP_OPERATION.set(false)
        assertEquals(listOf(true, true, false), Mechanism.values().map { AndroidSettingsAutoBackup.isOn(it) })
        // The switches and the period take no change backup; another `general` setting does.
        val filter = SettingsChangeDebounce.Filter(ownKeys = GeneralKeys.SETTINGS_AUTO_BACKUP_KEYS)
        GeneralKeys.SETTINGS_AUTO_BACKUP_KEYS.forEach { assertFalse(it.name, filter.tracks(it.store, it.name)) }
        assertTrue(filter.tracks(GeneralKeys.RESTART_STRATEGY.store, GeneralKeys.RESTART_STRATEGY.name))
    }

    @Test
    fun aFailedBackupLeavesNothingBehindAndIsReported() {
        val blocked = File(folder.root, "not-a-directory").apply { writeText("x") }
        assertThrows(IOException::class.java) { auto(File(blocked, "settings")).write(Mechanism.OPERATION, everything(), host, device) }
        val failed = auto(File(blocked, "settings")).tryWrite(Mechanism.OPERATION, everything(), host, device)
        assertTrue(failed is Outcome.Failed)

        // An archive beyond 16 MB is refused while it is written: no backup, no partial file.
        val auto = auto()
        val written = auto.tryWrite(Mechanism.CHANGE, everything(), host, device) as Outcome.Written
        val huge = SettingsArchiveWriter.DataFile("data_shortcuts", 1, "shortcuts.zip", Random(1).nextBytes(SettingsArchive.MAX_BYTES.toInt() + 1))
        val tooLarge = auto.tryWrite(Mechanism.CHANGE, everything(), host, device, listOf(huge))
        assertEquals(SettingsArchiveException.Reason.TOO_LARGE, ((tooLarge as Outcome.Failed).error as SettingsArchiveException).reason)
        assertEquals(listOf(written.backup.name), auto.directory.list()!!.toList())
    }

    private companion object {
        const val START = 1_792_548_000_000L
        const val MINUTE = 60_000L
        val ZONE: ZoneId = ZoneId.of("+08:00")
    }
}
