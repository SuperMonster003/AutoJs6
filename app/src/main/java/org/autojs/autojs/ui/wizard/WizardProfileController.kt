package org.autojs.autojs.ui.wizard

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Drives the profile spinner of a wizard dialog and provides the profile entries of
 * its options menu (save as, update, rename and delete a custom profile).
 *
 * zh-CN: 驱动向导对话框的配置下拉框, 并提供选项菜单中的配置条目
 * (另存为, 更新, 重命名及删除自定义配置).
 */
class WizardProfileController(
    private val context: Context,
    private val store: WizardProfileStore,
    private val spinner: Spinner,
    private val callbacks: Callbacks,
) {

    interface Callbacks {

        /** Members written when the current selection is saved as a profile. */
        fun membersToSave(profile: WizardProfile): Set<String>

        /** [profile] became the current profile; its selection should be applied. */
        fun onProfileApplied(profile: WizardProfile)

        /** The profile list or the current profile changed; dependent menus should be rebuilt. */
        fun onProfilesChanged()

    }

    var profiles: List<WizardProfile> = emptyList()
        private set

    var current: WizardProfile = WizardProfile.STANDARD
        private set

    private var suppressSpinnerCallback = false

    fun setUp() {
        reloadProfiles(store.getLastProfileId(context))
        spinner.adapter = buildSpinnerAdapter()
        spinner.setSelection(profiles.indexOf(current).coerceAtLeast(0), false)
        spinner.post {
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (suppressSpinnerCallback) return
                    val profile = profiles.getOrNull(position) ?: return
                    if (profile.id == current.id) return
                    current = profile
                    store.setLastProfileId(context, profile.id)
                    callbacks.onProfileApplied(profile)
                    callbacks.onProfilesChanged()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }

    fun options(): List<MaterialDialog.OptionMenuItemSpec> = buildList {
        add(MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_wizard_profile_save_as)) { saveAsNewProfile() })
        if (current.isCustom) {
            add(MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_wizard_profile_update)) { updateCurrentProfile() })
            add(MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_wizard_profile_rename)) { renameCurrentProfile() })
            add(MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_wizard_profile_delete)) { deleteCurrentProfile() })
        }
    }

    fun displayName(profile: WizardProfile): String = when (profile.kind) {
        WizardProfileKind.STANDARD -> context.getString(R.string.text_wizard_profile_standard)
        WizardProfileKind.MINIMAL -> context.getString(R.string.text_wizard_profile_minimal)
        WizardProfileKind.ALL -> context.getString(R.string.text_all)
        WizardProfileKind.CUSTOM -> profile.customName.orEmpty()
    }

    private fun reloadProfiles(selectedId: String?) {
        profiles = WizardProfile.builtIns + store.getCustomProfiles(context)
        current = profiles.firstOrNull { it.id == selectedId } ?: WizardProfile.STANDARD
    }

    private fun buildSpinnerAdapter(): ArrayAdapter<String> {
        val labels = profiles.map { displayName(it) }
        return object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, labels) {
            init {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).apply {
                    (this as? TextView)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, SPINNER_TEXT_SIZE_SP)
                }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getDropDownView(position, convertView, parent).apply {
                    (this as? TextView)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, SPINNER_TEXT_SIZE_SP)
                }
        }
    }

    private fun refreshSpinner(selectedId: String) {
        reloadProfiles(selectedId)
        suppressSpinnerCallback = true
        spinner.adapter = buildSpinnerAdapter()
        spinner.setSelection(profiles.indexOf(current).coerceAtLeast(0), false)
        spinner.post { suppressSpinnerCallback = false }
    }

    private fun select(profileId: String) {
        store.setLastProfileId(context, profileId)
        refreshSpinner(profileId)
        callbacks.onProfileApplied(current)
        callbacks.onProfilesChanged()
    }

    private fun saveAsNewProfile() {
        promptProfileName(R.string.text_wizard_profile_save_as, prefill = null, excludingId = null) { name ->
            val profile = WizardProfile.custom(name, callbacks.membersToSave(current))
            store.addCustomProfile(context, profile)
            select(profile.id)
            ViewUtils.showToast(context, R.string.text_wizard_profile_saved)
        }
    }

    private fun updateCurrentProfile() {
        val profile = current.takeIf { it.isCustom } ?: return
        val updated = profile.copy(memberIds = callbacks.membersToSave(profile))
        store.updateCustomProfile(context, updated)
        select(updated.id)
        ViewUtils.showToast(context, R.string.text_wizard_profile_saved)
    }

    private fun renameCurrentProfile() {
        val profile = current.takeIf { it.isCustom } ?: return
        promptProfileName(R.string.text_wizard_profile_rename, prefill = profile.customName, excludingId = profile.id) { name ->
            val renamed = profile.copy(customName = name)
            store.updateCustomProfile(context, renamed)
            refreshSpinner(renamed.id)
            callbacks.onProfilesChanged()
            ViewUtils.showToast(context, R.string.text_wizard_profile_saved)
        }
    }

    private fun deleteCurrentProfile() {
        val profile = current.takeIf { it.isCustom } ?: return
        MaterialDialog.Builder(context)
            .title(R.string.text_prompt)
            .content(context.getString(R.string.text_wizard_profile_delete_confirm, profile.customName.orEmpty()))
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.text_delete)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { _, _ ->
                store.deleteCustomProfile(context, profile.id)
                select(WizardProfile.ID_STANDARD)
                ViewUtils.showToast(context, R.string.text_wizard_profile_deleted)
            }
            .show()
    }

    private fun promptProfileName(
        titleRes: Int,
        prefill: String?,
        excludingId: String?,
        onConfirmed: (String) -> Unit,
    ) {
        MaterialDialog.Builder(context)
            .title(titleRes)
            .input(context.getString(R.string.text_wizard_profile_name_hint), prefill, false) { d, input ->
                val validation = WizardSelectionPolicy.validateCustomName(input.toString(), profiles, excludingId)
                d.getActionButton(DialogAction.POSITIVE).isEnabled = validation == WizardSelectionPolicy.NameValidation.OK
                d.inputEditText?.error = when (validation) {
                    WizardSelectionPolicy.NameValidation.DUPLICATE -> context.getString(R.string.text_wizard_profile_name_exists)
                    else -> null
                }
            }
            .alwaysCallInputCallback()
            .widgetThemeColor()
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { d, _ -> d.dismiss() }
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { d, _ ->
                val name = d.inputEditText?.text?.toString()?.trim().orEmpty()
                if (WizardSelectionPolicy.validateCustomName(name, profiles, excludingId) != WizardSelectionPolicy.NameValidation.OK) {
                    return@onPositive
                }
                d.dismiss()
                onConfirmed(name)
            }
            .autoDismiss(false)
            .show()
    }

    private companion object {
        const val SPINNER_TEXT_SIZE_SP = 14f
    }

}
