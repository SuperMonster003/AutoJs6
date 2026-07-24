package org.autojs.autojs.ui.settings

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.ai.AiProviderProfileInspection
import org.autojs.autojs.core.ai.AiProviderProfileSummary
import org.autojs.autojs.core.ai.AiProviderVault
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R

class AiSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var vault: AiProviderVault

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        vault = AiProviderVault(requireContext())
        setPreferencesFromResource(R.xml.fragment_ai_settings, rootKey)

        parentFragmentManager.setFragmentResultListener(
            AiProviderDialogFragment.RESULT_PROFILE_CHANGED,
            this,
        ) { _, _ -> refreshProfiles() }

        findPreference<MaterialPreference>(getString(R.string.key_ai_default_profile))
            ?.setOnPreferenceClickListener {
                showDefaultProfileDialog()
                true
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.excludePaddingClippableViewFromBottomNavigationBar()
    }

    override fun onResume() {
        super.onResume()
        refreshProfiles()
    }

    private fun refreshProfiles() {
        val inspection = runCatching(vault::inspectProfiles).getOrElse {
            showVaultError(it)
            return
        }
        refreshDefaultPreference(inspection.profiles, inspection.issues)
        refreshProfilePreferences(inspection.profiles, inspection.issues)
    }

    private fun refreshDefaultPreference(
        profiles: List<AiProviderProfileSummary>,
        issues: List<AiProviderProfileInspection.Unavailable>,
    ) {
        findPreference<MaterialPreference>(getString(R.string.key_ai_default_profile))?.apply {
            val defaultProfile = profiles.firstOrNull(AiProviderProfileSummary::isDefault)
            val defaultIssue = issues.firstOrNull(AiProviderProfileInspection.Unavailable::isDefault)
            summary = when {
                defaultProfile != null ->
                    getString(R.string.summary_ai_default_profile, defaultProfile.name)
                defaultIssue != null ->
                    getString(
                        R.string.summary_ai_default_profile_unavailable,
                        defaultIssue.id,
                    )
                else -> getString(R.string.summary_ai_default_profile_none)
            }
        }
    }

    private fun refreshProfilePreferences(
        profiles: List<AiProviderProfileSummary>,
        issues: List<AiProviderProfileInspection.Unavailable>,
    ) {
        val category = findPreference<PreferenceCategory>(getString(R.string.key_ai_profiles))
            ?: return
        category.removeAll()
        profiles.forEach { profile ->
            category.addPreference(
                MaterialPreference(requireContext()).apply {
                    key = "ai_provider_profile_${profile.id}"
                    layoutResource = R.layout.preference_custom
                    title = profile.name
                    summary = getString(
                        R.string.summary_ai_profile,
                        providerDisplayName(profile.providerId),
                        profile.model,
                        getString(
                            if (profile.hasApiKey) {
                                R.string.text_ai_api_key_configured
                            } else {
                                R.string.text_ai_api_key_not_configured
                            },
                        ),
                    )
                    setOnPreferenceClickListener {
                        AiProviderDialogFragment.showProfile(parentFragmentManager, profile.id)
                        true
                    }
                },
            )
        }
        issues.forEach { issue ->
            category.addPreference(
                MaterialPreference(requireContext()).apply {
                    key = "ai_provider_profile_unavailable_${issue.id}"
                    layoutResource = R.layout.preference_custom
                    title = getString(R.string.text_ai_profile_unavailable)
                    summary = getString(
                        R.string.summary_ai_profile_unavailable,
                        issue.id,
                        issue.errorCode.id,
                    )
                    setOnPreferenceClickListener {
                        confirmDeleteUnavailableProfile(issue)
                        true
                    }
                },
            )
        }
        category.addPreference(
            MaterialPreference(requireContext()).apply {
                key = KEY_ADD_PROFILE
                layoutResource = R.layout.preference_custom
                title = getString(R.string.text_ai_add_profile)
                summary = getString(R.string.text_ai_new_profile)
                setOnPreferenceClickListener {
                    AiProviderDialogFragment.showProfile(parentFragmentManager)
                    true
                }
            },
        )
    }

    private fun confirmDeleteUnavailableProfile(
        issue: AiProviderProfileInspection.Unavailable,
    ) {
        MaterialDialog.Builder(requireContext())
            .content(
                getString(
                    R.string.text_ai_delete_unavailable_profile_confirm,
                    issue.id,
                ),
            )
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.text_delete)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { _, _ ->
                runCatching { vault.deleteProfile(issue.id) }
                    .onSuccess { refreshProfiles() }
                    .onFailure(::showVaultError)
            }
            .show()
    }

    private fun showDefaultProfileDialog() {
        val profiles = runCatching(vault::listProfiles).getOrElse {
            showVaultError(it)
            return
        }
        if (profiles.isEmpty()) {
            AiProviderDialogFragment.showProfile(parentFragmentManager)
            return
        }

        var selectedIndex = profiles.indexOfFirst(AiProviderProfileSummary::isDefault)
            .takeIf { it >= 0 } ?: 0
        MaterialDialog.Builder(requireContext())
            .title(R.string.text_ai_default_profile)
            .items(profiles.map(AiProviderProfileSummary::name))
            .itemsCallbackSingleChoice(selectedIndex) { _, _, which, _ ->
                selectedIndex = which
                true
            }
            .choiceWidgetThemeColor()
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ ->
                runCatching { vault.setDefaultProfile(profiles[selectedIndex].id) }
                    .onSuccess { refreshProfiles() }
                    .onFailure(::showVaultError)
            }
            .show()
    }

    private fun showVaultError(error: Throwable) {
        val detail = error.message?.takeIf(String::isNotBlank)
        val message = if (detail == null) {
            getString(R.string.error_an_error_occurred)
        } else {
            "${getString(R.string.error_an_error_occurred)}: $detail"
        }
        ViewUtils.showToast(requireContext(), message, true)
    }

    private fun providerDisplayName(providerId: String): String = when (providerId) {
        "openai" -> "OpenAI"
        "anthropic" -> "Anthropic"
        "gemini" -> "Gemini"
        "deepseek" -> "DeepSeek"
        "openrouter" -> "OpenRouter"
        "openai-compatible" -> "OpenAI-compatible"
        else -> providerId
    }

    companion object {

        private const val KEY_ADD_PROFILE = "ai_add_provider_profile"
    }
}
