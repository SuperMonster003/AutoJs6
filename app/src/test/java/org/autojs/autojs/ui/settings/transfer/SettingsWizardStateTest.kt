package org.autojs.autojs.ui.settings.transfer

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Failure
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Kind
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P6.2: the wizard state survives the recreation of the page as JSON text (T10).
 * zh-CN: ROADMAP P6.2: 向导状态以 JSON 文本跨页面重建保留 (T10).
 */
class SettingsWizardStateTest {

    @Test
    fun roundTripsEveryField() {
        val state = SettingsWizardState(
            kind = Kind.EXPORT,
            step = Step.FAILED,
            selected = listOf("appearance", "credentials"),
            credentialsConfirmed = true,
            locationRequested = true,
            document = "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fa.zip",
            fileName = "autojs6-settings-20261021-210000.zip",
            categories = 2,
            entries = 17,
            unexportable = 1,
            failure = Failure.IO,
            failureDetail = "No space left on device",
        )
        assertEquals(state, SettingsWizardState.fromText(state.toText()))
        Step.entries.forEach { step ->
            val minimal = SettingsWizardState(Kind.EXPORT, step)
            assertEquals(minimal, SettingsWizardState.fromText(minimal.toText()))
        }
    }

    @Test
    fun writesIdsNotEnumNames() {
        val json = SettingsJson.parse(SettingsWizardState(Kind.EXPORT, Step.PICK_CATEGORIES, listOf("a11y")).toText()) as Map<*, *>
        assertEquals("export", json["kind"])
        assertEquals("pick_categories", json["step"])
        assertEquals(listOf("a11y"), json["selected"])
        assertNull(json["failure"])
    }

    @Test
    fun rejectsWhatIsNoWizardState() {
        assertNull(SettingsWizardState.fromText(null))
        assertNull(SettingsWizardState.fromText("{"))
        assertNull(SettingsWizardState.fromText("[]"))
        assertNull(SettingsWizardState.fromText("""{"kind":"import","step":"pick_categories","selected":[]}"""))
        assertNull(SettingsWizardState.fromText("""{"kind":"export","step":"unknown","selected":[]}"""))
        assertNull(SettingsWizardState.fromText("""{"kind":"export","step":"done"}"""))
        assertNull(SettingsWizardState.fromText("""{"kind":"export","step":"done","selected":[1]}"""))
    }

    @Test
    fun toleratesMissingOptionalFields() {
        val state = SettingsWizardState.fromText("""{"kind":"export","step":"done","selected":["general"],"failure":"SOMETHING_NEW"}""")
        assertEquals(SettingsWizardState(Kind.EXPORT, Step.DONE, listOf("general")), state)
    }

    @Test
    fun exportStartsWithTheDefaultSelectionWithoutDataCategories() {
        val state = SettingsExportWizard.initialState(null)
        assertEquals(Step.PICK_CATEGORIES, state.step)
        val expected = SettingCategory.ALL.filter { it.defaultSelected && !it.isData }.map { it.id }
        assertEquals(expected, state.selected)
        assertEquals(26, state.selected.size)
    }

    @Test
    fun exportStartsWithTheRememberedSelection() {
        val state = SettingsExportWizard.initialState(listOf("other", "credentials", "appearance"))
        assertEquals(listOf("appearance", "other"), state.selected)
        assertEquals(false, state.credentialsConfirmed)
    }
}
