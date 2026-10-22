package org.autojs.autojs.ui.settings.transfer

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.archive.ImportEnvironment
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest
import org.autojs.autojs.core.pref.archive.SettingsArchiveReader
import org.autojs.autojs.core.pref.archive.SettingsArchiveWriter
import org.autojs.autojs.core.pref.archive.SettingsDeviceKey
import org.autojs.autojs.core.pref.archive.SettingsImportApplier
import org.autojs.autojs.core.pref.archive.SettingsPassword
import org.autojs.autojs.core.pref.archive.SettingsSnapshot
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.pref.keys.CredentialKeys
import org.autojs.autojs.core.pref.keys.DomainKeysTestBase
import org.autojs.autojs.ui.settings.transfer.SettingsCategoryChoice.State
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Kind
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * docs/settings/ROADMAP.md P7.7 (D23 / T21): the export wizard writes a password slot for the credentials when a
 * password is set, the import wizard lists such credentials as asking for a password and imports them once the reader
 * has it, and the password itself stays in the process ([SettingsPasswords]); only a token is part of the wizard state.
 *
 * zh-CN: ROADMAP P7.7 (D23 / T21): 设置口令时导出向导为凭据写入口令槽, 导入向导把这类凭据列为需要口令, reader 持有口令后即可导入;
 * 口令本身只保存在本进程中 ([SettingsPasswords]), 向导状态里只有 token.
 */
class SettingsPasswordFlowTest : DomainKeysTestBase() {

    private val host = SettingsArchiveManifest.Host("org.autojs.autojs6", 5460, "6.8.0")

    private val device = SettingsArchiveManifest.Device("XQ-AT72", 33)

    @Test
    fun aPasswordStaysInTheProcessBehindAToken() {
        val token = SettingsPasswords.remember(PASSWORD)
        assertSame(PASSWORD, SettingsPasswords.find(token))
        assertTrue(token != SettingsPasswords.remember(PASSWORD))
        SettingsPasswords.forget(token)
        assertNull(SettingsPasswords.find(token))
        assertNull(SettingsPasswords.find(null))
        SettingsPasswords.forget(null)

        // The state keeps the token only; a state without one reads as before.
        val state = SettingsWizardState(Kind.EXPORT, Step.SET_PASSWORD, listOf("credentials"), credentialsConfirmed = true, password = token)
        assertEquals(state, SettingsWizardState.fromText(state.toText()))
        assertEquals("set_password", state.toJson()["step"])
        assertNull(SettingsWizardState.fromText("""{"kind":"export","step":"set_password","selected":["credentials"]}""")!!.password)
    }

    @Test
    fun anotherDeviceImportsTheCredentialsWithThePassword() {
        val exported = export(PASSWORD)
        store.edit { clear() }
        CredentialKeys.GITHUB_TOKEN.set("token of this device")

        // Without the password: the row asks for it and cannot be ticked, and the credentials stay out of the plan.
        val reader = SettingsArchiveReader({ ByteArrayInputStream(exported) }, key = KEY)
        val inspection = SettingsImporter.inspect(reader, "a.zip", host.packageName)
        assertEquals(listOf("credentials"), inspection.locked)
        assertEquals(listOf("credentials"), inspection.password)
        assertEquals(State.PASSWORD, inspection.choices.single { it.category == SettingCategory.CREDENTIALS }.state)
        assertEquals(listOf(SettingCategory.APPEARANCE), SettingsCategoryChoices.selectable(inspection.choices))
        assertEquals(listOf("appearance"), SettingsImporter.plan(reader, inspection, listOf("appearance", "credentials"), ImportEnvironment()).categories)

        // A wrong password does not open them; the right one does, and they are imported.
        assertFalse(reader.checkPassword("credentials", SettingsPassword.of("wrong horse", SettingsPassword.MIN_ITERATIONS)!!))
        assertTrue(reader.checkPassword("credentials", PASSWORD))
        val unlocked = reader.withPassword(PASSWORD)
        val opened = SettingsImporter.inspect(unlocked, "a.zip", host.packageName)
        assertEquals(emptyList<String>(), opened.locked)
        assertEquals(emptyList<String>(), opened.password)
        assertEquals(State.SELECTABLE, opened.choices.single { it.category == SettingCategory.CREDENTIALS }.state)
        val plan = SettingsImporter.plan(unlocked, opened, listOf("appearance", "credentials"), ImportEnvironment())
        assertEquals(listOf("appearance", "credentials"), plan.categories)
        SettingsImportApplier.write(plan)
        assertEquals("token of the exporting device", CredentialKeys.GITHUB_TOKEN.get())
        assertEquals(5, store.all()[AppearanceKeys.THEME_COLOR_PRIMARY.name])
    }

    @Test
    fun anOlderFileWithTheDeviceSlotOnlyStaysLockedWithoutAskingForAPassword() {
        val exported = export(null)
        val reader = SettingsArchiveReader({ ByteArrayInputStream(exported) }, key = KEY)
        val inspection = SettingsImporter.inspect(reader, "a.zip", host.packageName)
        assertEquals(listOf("credentials"), inspection.locked)
        assertEquals(emptyList<String>(), inspection.password)
        assertEquals(State.LOCKED, inspection.choices.single { it.category == SettingCategory.CREDENTIALS }.state)
        assertFalse(reader.checkPassword("credentials", PASSWORD))
        // The exporting device still opens it with its key alone.
        val own = SettingsImporter.inspect(SettingsArchiveReader({ ByteArrayInputStream(exported) }, key = OTHER_KEY), "a.zip", host.packageName)
        assertEquals(State.SELECTABLE, own.choices.single { it.category == SettingCategory.CREDENTIALS }.state)
    }

    @Test
    fun anotherFileForgetsThePassword() {
        val token = SettingsPasswords.remember(PASSWORD)
        val state = SettingsImportWizard.initialState().copy(step = Step.PICK_CATEGORIES, fileName = "a.zip", archive = "/data/cache/a.zip", password = token)
        assertEquals(SettingsWizardState(Kind.IMPORT, Step.CHOOSE_FILE), SettingsImportWizard.anotherFile(state))
        assertNull(SettingsPasswords.find(token))
    }

    // The appearance and the credentials of the exporting device (OTHER_KEY), with an optional password slot.
    private fun export(password: SettingsPassword?): ByteArray {
        store.edit { put(AppearanceKeys.THEME_COLOR_PRIMARY.name, 5) }
        CredentialKeys.GITHUB_TOKEN.set("token of the exporting device")
        val snapshot = SettingsSnapshot.capture(listOf(SettingCategory.APPEARANCE, SettingCategory.CREDENTIALS))
        return ByteArrayOutputStream().also {
            SettingsArchiveWriter.write(it, snapshot, "2026-10-22T12:00:00+08:00", host, device, key = OTHER_KEY, password = password)
        }.toByteArray()
    }

    private companion object {
        val KEY = SettingsDeviceKey.ofBytes(ByteArray(32) { (it * 7).toByte() })
        val OTHER_KEY = SettingsDeviceKey.ofBytes(ByteArray(32) { (it * 11 + 1).toByte() })
        val PASSWORD = SettingsPassword.of("correct horse", SettingsPassword.MIN_ITERATIONS)!!
    }
}
