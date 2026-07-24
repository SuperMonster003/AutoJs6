package org.autojs.autojs.ui.settings

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.ai.AiProviderValidationCode
import org.autojs.autojs.core.ai.AiProviderValidationException
import org.autojs.autojs.core.ai.AiProviderValidationField
import org.autojs.autojs.core.ai.AiProviderDefinition
import org.autojs.autojs.core.ai.AiProviderProfile
import org.autojs.autojs.core.ai.AiProviderProfileSummary
import org.autojs.autojs.core.ai.AiProviderVault
import org.autojs.autojs.core.ai.DefaultProfileUpdate
import org.autojs.autojs.core.ai.SecretUpdate
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogAiProviderBinding
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

class AiProviderDialogFragment : DialogFragment() {

    private var _binding: DialogAiProviderBinding? = null
    private val binding: DialogAiProviderBinding
        get() = requireNotNull(_binding)

    private lateinit var vault: AiProviderVault
    private lateinit var draftProfileId: String
    private var existingProfile: AiProviderProfile? = null
    private var existingSummary: AiProviderProfileSummary? = null
    private var suppressClearConfirmation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        draftProfileId = savedInstanceState?.getString(STATE_DRAFT_PROFILE_ID)
            ?: arguments?.getString(ARG_DRAFT_PROFILE_ID)
            ?: arguments?.getString(ARG_PROFILE_ID)
            ?: UUID.randomUUID().toString()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_DRAFT_PROFILE_ID, draftProfileId)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogAiProviderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vault = AiProviderVault(requireContext())
        loadExistingProfile()
        configureProviderSpinner()
        configureSecretInput()
        configureButtons()
        populateFields()
        ViewCompat.setAccessibilityHeading(binding.title, true)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.apply {
                addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
            }
            setCanceledOnTouchOutside(true)
        }
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        dialog?.window?.setLayout(
            min((metrics.widthPixels * 0.94f).roundToInt(), dp(720)),
            min((metrics.heightPixels * 0.9f).roundToInt(), dp(760)),
        )
    }

    override fun onDestroyView() {
        _binding?.apiKey?.text?.clear()
        _binding = null
        super.onDestroyView()
    }

    private fun loadExistingProfile() {
        val profileId = arguments?.getString(ARG_PROFILE_ID) ?: return
        existingProfile = runCatching { vault.getProfile(profileId) }.getOrElse {
            showError(it)
            dismissAllowingStateLoss()
            null
        }
        existingSummary = runCatching(vault::listProfiles)
            .getOrNull()
            ?.firstOrNull { it.id == profileId }
        if (existingProfile == null) {
            dismissAllowingStateLoss()
        }
    }

    private fun configureProviderSpinner() {
        val providers = AiProviderDefinition.entries
        binding.provider.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            providers.map(::providerDisplayName),
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.provider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var previousProvider: AiProviderDefinition? = null

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                val provider = providers[position]
                val currentBaseUrl = binding.baseUrl.text?.toString().orEmpty()
                if (
                    currentBaseUrl.isBlank() ||
                    currentBaseUrl == previousProvider?.defaultBaseUrl
                ) {
                    binding.baseUrl.setText(provider.defaultBaseUrl.orEmpty())
                }
                previousProvider = provider
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun configureSecretInput() {
        binding.apiKey.imeOptions =
            binding.apiKey.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        binding.clearApiKey.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked || suppressClearConfirmation) {
                syncSecretInputEnabledState()
                return@setOnCheckedChangeListener
            }
            MaterialDialog.Builder(requireContext())
                .content(R.string.text_ai_clear_api_key_confirm)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .onNegative { _, _ -> setClearSecretChecked(false) }
                .onPositive { _, _ -> syncSecretInputEnabledState() }
                .cancelListener { setClearSecretChecked(false) }
                .show()
        }
    }

    private fun configureButtons() {
        binding.cancel.setOnClickListener { dismiss() }
        binding.save.setOnClickListener { saveProfile() }
        binding.delete.setOnClickListener { confirmDeleteProfile() }
    }

    private fun populateFields() {
        val profile = existingProfile
        binding.title.setText(
            if (profile == null) R.string.text_ai_new_profile else R.string.text_ai_edit_profile,
        )
        binding.delete.visibility = if (profile == null) View.GONE else View.VISIBLE
        binding.clearApiKey.visibility =
            if (existingSummary?.hasApiKey == true) View.VISIBLE else View.GONE
        binding.apiKeyHint.setText(
            if (existingSummary?.hasApiKey == true) {
                R.string.summary_ai_api_key_existing
            } else {
                R.string.summary_ai_api_key_new
            },
        )

        if (profile == null) {
            val provider = AiProviderDefinition.OPENAI
            binding.provider.setSelection(AiProviderDefinition.entries.indexOf(provider))
            binding.baseUrl.setText(provider.defaultBaseUrl)
            binding.setAsDefault.isChecked = runCatching(vault::listProfiles)
                .getOrDefault(emptyList())
                .isEmpty()
            return
        }

        binding.name.setText(profile.name)
        binding.provider.setSelection(AiProviderDefinition.entries.indexOf(profile.provider))
        binding.baseUrl.setText(profile.baseUrl)
        binding.model.setText(profile.model)
        binding.setAsDefault.isChecked = existingSummary?.isDefault == true
    }

    private fun saveProfile() {
        clearFieldErrors()
        val name = binding.name.text?.toString()?.trim().orEmpty()
        val baseUrl = binding.baseUrl.text?.toString()?.trim().orEmpty()
        val model = binding.model.text?.toString()?.trim().orEmpty()
        var invalid = false
        if (name.isEmpty()) {
            binding.nameTextInputLayout.error =
                getString(R.string.error_ai_profile_name_required)
            invalid = true
        }
        if (baseUrl.isEmpty()) {
            binding.baseUrlTextInputLayout.error =
                getString(R.string.error_ai_base_url_invalid)
            invalid = true
        }
        if (model.isEmpty()) {
            binding.modelTextInputLayout.error = getString(R.string.error_ai_model_required)
            invalid = true
        }
        if (invalid) {
            return
        }

        val provider = AiProviderDefinition.entries[binding.provider.selectedItemPosition]
        val candidate = try {
            val existing = existingProfile
            if (existing == null) {
                AiProviderProfile(
                    id = draftProfileId,
                    name = name,
                    provider = provider,
                    baseUrl = baseUrl,
                    model = model,
                )
            } else {
                existing.copy(
                    name = name,
                    provider = provider,
                    baseUrl = baseUrl,
                    model = model,
                )
            }
        } catch (error: Throwable) {
            showError(error)
            return
        }
        val secretUpdate = when {
            binding.clearApiKey.isChecked -> SecretUpdate.Clear
            else -> SecretUpdate.fromInput(binding.apiKey.text)
        }

        runCatching {
            val defaultUpdate = if (binding.setAsDefault.isChecked) {
                DefaultProfileUpdate.Set
            } else {
                DefaultProfileUpdate.Unset
            }
            vault.saveProfile(candidate, secretUpdate, defaultUpdate)
        }.onSuccess {
            notifyProfileChanged()
            dismiss()
        }.onFailure { error ->
            if (!showValidationError(error)) {
                showError(error)
            }
        }
    }

    private fun confirmDeleteProfile() {
        val profile = existingProfile ?: return
        MaterialDialog.Builder(requireContext())
            .content(getString(R.string.text_ai_delete_profile_confirm, profile.name))
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.text_delete)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { _, _ ->
                runCatching { vault.deleteProfile(profile.id) }
                    .onSuccess {
                        notifyProfileChanged()
                        dismiss()
                    }
                    .onFailure(::showError)
            }
            .show()
    }

    private fun syncSecretInputEnabledState() {
        val enabled = !binding.clearApiKey.isChecked
        binding.apiKey.isEnabled = enabled
        binding.apiKeyTextInputLayout.isEnabled = enabled
        if (!enabled) {
            binding.apiKey.text?.clear()
        }
    }

    private fun setClearSecretChecked(checked: Boolean) {
        suppressClearConfirmation = true
        binding.clearApiKey.isChecked = checked
        suppressClearConfirmation = false
        syncSecretInputEnabledState()
    }

    private fun clearFieldErrors() {
        binding.nameTextInputLayout.error = null
        binding.baseUrlTextInputLayout.error = null
        binding.modelTextInputLayout.error = null
        binding.apiKeyTextInputLayout.error = null
    }

    private fun notifyProfileChanged() {
        parentFragmentManager.setFragmentResult(
            RESULT_PROFILE_CHANGED,
            bundleOf(),
        )
    }

    private fun showError(error: Throwable) {
        val detail = error.message?.takeIf(String::isNotBlank)
        val message = if (detail == null) {
            getString(R.string.error_an_error_occurred)
        } else {
            "${getString(R.string.error_an_error_occurred)}: $detail"
        }
        ViewUtils.showToast(requireContext(), message, true)
    }

    private fun showValidationError(error: Throwable): Boolean {
        val validationError = error as? AiProviderValidationException ?: return false
        val message = when (validationError.field) {
            AiProviderValidationField.PROFILE_NAME -> getString(
                when (validationError.code) {
                    AiProviderValidationCode.REQUIRED ->
                        R.string.error_ai_profile_name_required
                    AiProviderValidationCode.DUPLICATE ->
                        R.string.error_ai_profile_name_duplicate
                    else -> R.string.error_ai_profile_name_invalid
                },
            )
            AiProviderValidationField.BASE_URL ->
                getString(R.string.error_ai_base_url_invalid)
            AiProviderValidationField.MODEL -> getString(
                if (validationError.code == AiProviderValidationCode.REQUIRED) {
                    R.string.error_ai_model_required
                } else {
                    R.string.error_ai_model_invalid
                },
            )
            AiProviderValidationField.API_KEY -> getString(
                if (validationError.code == AiProviderValidationCode.SECRET_REENTRY_REQUIRED) {
                    R.string.error_ai_api_key_reentry_required
                } else {
                    R.string.error_ai_api_key_invalid
                },
            )
            AiProviderValidationField.PROFILE_ID,
            AiProviderValidationField.PROVIDER,
            -> return false
        }
        when (validationError.field) {
            AiProviderValidationField.PROFILE_NAME ->
                binding.nameTextInputLayout.error = message
            AiProviderValidationField.BASE_URL ->
                binding.baseUrlTextInputLayout.error = message
            AiProviderValidationField.MODEL ->
                binding.modelTextInputLayout.error = message
            AiProviderValidationField.API_KEY ->
                binding.apiKeyTextInputLayout.error = message
            AiProviderValidationField.PROFILE_ID,
            AiProviderValidationField.PROVIDER,
            -> return false
        }
        return true
    }

    private fun providerDisplayName(provider: AiProviderDefinition): String = when (provider) {
        AiProviderDefinition.OPENAI -> "OpenAI"
        AiProviderDefinition.ANTHROPIC -> "Anthropic"
        AiProviderDefinition.GEMINI -> "Gemini"
        AiProviderDefinition.DEEPSEEK -> "DeepSeek"
        AiProviderDefinition.OPENROUTER -> "OpenRouter"
        AiProviderDefinition.OPENAI_COMPATIBLE -> "OpenAI-compatible"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {

        const val RESULT_PROFILE_CHANGED = "ai_provider_profile_changed"

        private const val ARG_DRAFT_PROFILE_ID = "draft_profile_id"
        private const val ARG_PROFILE_ID = "profile_id"
        private const val STATE_DRAFT_PROFILE_ID = "state_draft_profile_id"
        private const val TAG = "AiProviderDialogFragment"

        @JvmStatic
        @JvmOverloads
        fun showProfile(fragmentManager: FragmentManager, profileId: String? = null) {
            if (fragmentManager.isStateSaved || fragmentManager.findFragmentByTag(TAG) != null) {
                return
            }
            AiProviderDialogFragment().apply {
                arguments = bundleOf(
                    ARG_DRAFT_PROFILE_ID to (profileId ?: UUID.randomUUID().toString()),
                    ARG_PROFILE_ID to profileId,
                )
            }.show(fragmentManager, TAG)
        }
    }
}
