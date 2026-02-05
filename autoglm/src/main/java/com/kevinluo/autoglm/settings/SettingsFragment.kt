package com.kevinluo.autoglm.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kevinluo.autoglm.R
import com.kevinluo.autoglm.agent.AgentConfig
import com.kevinluo.autoglm.model.ModelClient
import com.kevinluo.autoglm.model.ModelConfig
import com.kevinluo.autoglm.ui.MainViewModel
import com.kevinluo.autoglm.ui.PermissionStates
import com.kevinluo.autoglm.ui.PermissionType
import com.kevinluo.autoglm.util.LogFileManager
import com.kevinluo.autoglm.util.Logger
import com.kevinluo.autoglm.util.applyPrimaryButtonColors
import com.kevinluo.autoglm.util.showWithPrimaryButtons
import com.kevinluo.autoglm.voice.ContinuousListeningService
import com.kevinluo.autoglm.voice.VoiceModelDownloadListener
import com.kevinluo.autoglm.voice.VoiceModelManager
import com.kevinluo.autoglm.voice.VoiceModelState
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * Settings Fragment for app configuration and permissions.
 *
 * Migrated from SettingsActivity to support bottom navigation architecture.
 * Includes permissions center (collapsible), model configuration, agent configuration,
 * task templates, voice settings, advanced settings, and debug logs.
 *
 * _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 5.3_
 */
class SettingsFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var settingsManager: SettingsManager

    // Permissions center views
    private lateinit var permissionsCenterCard: View
    private lateinit var permissionsHeader: View
    private lateinit var permissionsContent: View
    private lateinit var permissionsSummary: TextView
    private lateinit var btnExpandCollapse: ImageButton
    private var isPermissionsExpanded = false

    // Permission item views
    private lateinit var permissionShizuku: View
    private lateinit var permissionOverlay: View
    private lateinit var permissionKeyboard: View
    private lateinit var permissionBattery: View

    // Profile selector views
    private lateinit var profileSelectorLayout: TextInputLayout
    private lateinit var profileSelector: AutoCompleteTextView
    private lateinit var btnProfileMenu: ImageButton

    // Model settings views
    private lateinit var baseUrlLayout: TextInputLayout
    private lateinit var baseUrlInput: TextInputEditText
    private lateinit var modelNameLayout: TextInputLayout
    private lateinit var modelNameInput: TextInputEditText
    private lateinit var apiKeyLayout: TextInputLayout
    private lateinit var apiKeyInput: TextInputEditText

    // Agent settings views
    private lateinit var maxStepsLayout: TextInputLayout
    private lateinit var maxStepsInput: TextInputEditText
    private lateinit var screenshotDelayLayout: TextInputLayout
    private lateinit var screenshotDelayInput: TextInputEditText
    private lateinit var languageRadioGroup: RadioGroup
    private lateinit var languageChinese: RadioButton
    private lateinit var languageEnglish: RadioButton

    // Buttons
    private lateinit var saveButton: ImageButton
    private lateinit var resetButton: ImageButton
    private lateinit var testConnectionButton: Button

    // Task templates views
    private lateinit var templatesRecyclerView: RecyclerView
    private lateinit var emptyTemplatesText: TextView
    private lateinit var btnAddTemplate: ImageButton
    private var templatesAdapter: TaskTemplatesAdapter? = null
    private var taskTemplates: MutableList<TaskTemplate> = mutableListOf()

    // Advanced settings views
    private lateinit var promptCnStatus: TextView
    private lateinit var promptEnStatus: TextView
    private lateinit var btnEditPromptCn: Button
    private lateinit var btnEditPromptEn: Button

    // Debug logs views
    private lateinit var logSizeText: TextView
    private lateinit var btnExportLogs: Button
    private lateinit var btnClearLogs: Button

    // Voice settings views
    private lateinit var voiceModelStatus: TextView
    private lateinit var btnVoiceModelAction: com.google.android.material.button.MaterialButton
    private lateinit var switchContinuousListening: MaterialSwitch
    private lateinit var wakeWordInput: TextInputEditText
    private lateinit var sensitivitySlider: Slider
    private var voiceModelManager: VoiceModelManager? = null

    // Flag to prevent listener from firing during programmatic changes
    private var isUpdatingContinuousListeningSwitch = false

    // Profile data
    private var savedProfiles: List<SavedModelProfile> = emptyList()
    private var currentProfileId: String? = null

    // Permission launchers
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startContinuousListeningService()
            } else {
                isUpdatingContinuousListeningSwitch = true
                switchContinuousListening.isChecked = false
                isUpdatingContinuousListeningSwitch = false
                settingsManager.setContinuousListening(false)
                Toast.makeText(
                    requireContext(),
                    R.string.voice_notification_permission_required,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                checkNotificationPermissionAndStart()
            } else {
                isUpdatingContinuousListeningSwitch = true
                switchContinuousListening.isChecked = false
                isUpdatingContinuousListeningSwitch = false
                settingsManager.setContinuousListening(false)
                Toast.makeText(
                    requireContext(),
                    R.string.voice_audio_permission_required,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logger.d(TAG, "SettingsFragment created")
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager.getInstance(requireContext())
        initViews(view)
        loadCurrentSettings()
        setupListeners()
        observePermissionStates()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
        updateLogSizeDisplay()
    }

    /**
     * Initializes all view references.
     */
    private fun initViews(view: View) {
        // Permissions center
        permissionsCenterCard = view.findViewById(R.id.permissionsCenterCard)
        permissionsHeader = view.findViewById(R.id.permissionsHeader)
        permissionsContent = view.findViewById(R.id.permissionsContent)
        permissionsSummary = view.findViewById(R.id.permissionsSummary)
        btnExpandCollapse = view.findViewById(R.id.btnExpandCollapse)

        // Permission items
        permissionShizuku = view.findViewById(R.id.permissionShizuku)
        permissionOverlay = view.findViewById(R.id.permissionOverlay)
        permissionKeyboard = view.findViewById(R.id.permissionKeyboard)
        permissionBattery = view.findViewById(R.id.permissionBattery)

        // Profile selector
        profileSelectorLayout = view.findViewById(R.id.profileSelectorLayout)
        profileSelector = view.findViewById(R.id.profileSelector)
        btnProfileMenu = view.findViewById(R.id.btnProfileMenu)

        // Model settings
        baseUrlLayout = view.findViewById(R.id.baseUrlLayout)
        baseUrlInput = view.findViewById(R.id.baseUrlInput)
        modelNameLayout = view.findViewById(R.id.modelNameLayout)
        modelNameInput = view.findViewById(R.id.modelNameInput)
        apiKeyLayout = view.findViewById(R.id.apiKeyLayout)
        apiKeyInput = view.findViewById(R.id.apiKeyInput)

        // Agent settings
        maxStepsLayout = view.findViewById(R.id.maxStepsLayout)
        maxStepsInput = view.findViewById(R.id.maxStepsInput)
        screenshotDelayLayout = view.findViewById(R.id.screenshotDelayLayout)
        screenshotDelayInput = view.findViewById(R.id.screenshotDelayInput)
        languageRadioGroup = view.findViewById(R.id.languageRadioGroup)
        languageChinese = view.findViewById(R.id.languageChinese)
        languageEnglish = view.findViewById(R.id.languageEnglish)

        // Buttons
        saveButton = view.findViewById(R.id.saveButton)
        resetButton = view.findViewById(R.id.resetButton)
        testConnectionButton = view.findViewById(R.id.testConnectionButton)

        // Task templates
        templatesRecyclerView = view.findViewById(R.id.templatesRecyclerView)
        emptyTemplatesText = view.findViewById(R.id.emptyTemplatesText)
        btnAddTemplate = view.findViewById(R.id.btnAddTemplate)

        templatesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        templatesAdapter =
            TaskTemplatesAdapter(
                templates = taskTemplates,
                onEditClick = { template -> showEditTemplateDialog(template) },
                onDeleteClick = { template -> showDeleteTemplateDialog(template) },
            )
        templatesRecyclerView.adapter = templatesAdapter

        // Advanced settings
        promptCnStatus = view.findViewById(R.id.promptCnStatus)
        promptEnStatus = view.findViewById(R.id.promptEnStatus)
        btnEditPromptCn = view.findViewById(R.id.btnEditPromptCn)
        btnEditPromptEn = view.findViewById(R.id.btnEditPromptEn)

        // Debug logs
        logSizeText = view.findViewById(R.id.logSizeText)
        btnExportLogs = view.findViewById(R.id.btnExportLogs)
        btnClearLogs = view.findViewById(R.id.btnClearLogs)

        // Voice settings
        voiceModelStatus = view.findViewById(R.id.voiceModelStatus)
        btnVoiceModelAction = view.findViewById(R.id.btnVoiceModelAction)
        switchContinuousListening = view.findViewById(R.id.switchContinuousListening)
        wakeWordInput = view.findViewById(R.id.wakeWordInput)
        sensitivitySlider = view.findViewById(R.id.sensitivitySlider)

        // Setup permission items
        setupPermissionItem(
            permissionShizuku,
            getString(R.string.shizuku_status_title),
            R.drawable.ic_layers,
        )
        setupPermissionItem(
            permissionOverlay,
            getString(R.string.overlay_permission_title),
            R.drawable.ic_layers,
        )
        setupPermissionItem(
            permissionKeyboard,
            getString(R.string.keyboard_title),
            R.drawable.ic_keyboard,
        )
        setupPermissionItem(
            permissionBattery,
            getString(R.string.battery_opt_title),
            R.drawable.ic_battery,
        )
    }

    /**
     * Sets up a permission item view with name and icon.
     */
    private fun setupPermissionItem(itemView: View, name: String, iconRes: Int) {
        itemView.findViewById<TextView>(R.id.permissionName).text = name
        itemView.findViewById<ImageView>(R.id.permissionIcon).setImageResource(iconRes)
    }

    /**
     * Loads current settings from storage and displays them.
     */
    private fun loadCurrentSettings() {
        Logger.d(TAG, "Loading current settings")
        val modelConfig = settingsManager.getModelConfig()
        val agentConfig = settingsManager.getAgentConfig()

        loadSavedProfiles()
        loadTaskTemplates()
        updatePromptStatus()
        updateLogSizeDisplay()
        loadVoiceSettings()

        baseUrlInput.setText(modelConfig.baseUrl)
        modelNameInput.setText(modelConfig.modelName)
        apiKeyInput.setText(if (modelConfig.apiKey == "EMPTY") "" else modelConfig.apiKey)

        maxStepsInput.setText(agentConfig.maxSteps.toString())
        screenshotDelayInput.setText((agentConfig.screenshotDelayMs / 1000.0).toString())

        when (agentConfig.language) {
            "en" -> languageEnglish.isChecked = true
            else -> languageChinese.isChecked = true
        }

        clearErrors()
    }

    /**
     * Loads saved profiles and updates the dropdown.
     */
    private fun loadSavedProfiles() {
        Logger.d(TAG, "Loading saved profiles")
        savedProfiles = settingsManager.getSavedProfiles()
        currentProfileId = settingsManager.getCurrentProfileId()

        val displayNames = mutableListOf(getString(R.string.settings_new_profile))
        displayNames.addAll(savedProfiles.map { it.displayName })

        val adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                displayNames,
            )
        profileSelector.setAdapter(adapter)

        if (currentProfileId != null) {
            val profile = savedProfiles.find { it.id == currentProfileId }
            if (profile != null) {
                profileSelector.setText(profile.displayName, false)
            } else {
                profileSelector.setText(getString(R.string.settings_new_profile), false)
            }
        } else {
            profileSelector.setText(getString(R.string.settings_new_profile), false)
        }
    }

    /**
     * Sets up click listeners for all interactive views.
     */
    private fun setupListeners() {
        // Permissions center expand/collapse
        permissionsHeader.setOnClickListener { togglePermissionsExpanded() }
        btnExpandCollapse.setOnClickListener { togglePermissionsExpanded() }

        // Permission action buttons
        setupPermissionActionListeners()

        saveButton.setOnClickListener {
            if (validateInput()) {
                saveSettings()
            }
        }

        resetButton.setOnClickListener { resetToDefaults() }
        testConnectionButton.setOnClickListener { testModelConnection() }

        profileSelector.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                currentProfileId = null
                clearModelFields()
            } else {
                val profile = savedProfiles[position - 1]
                currentProfileId = profile.id
                loadProfileToFields(profile)
            }
            settingsManager.setCurrentProfileId(currentProfileId)
        }

        btnProfileMenu.setOnClickListener { view -> showProfileMenu(view) }
        btnAddTemplate.setOnClickListener { showAddTemplateDialog() }
        btnEditPromptCn.setOnClickListener { showEditPromptDialog("cn") }
        btnEditPromptEn.setOnClickListener { showEditPromptDialog("en") }
        btnExportLogs.setOnClickListener { exportDebugLogs() }
        btnClearLogs.setOnClickListener { showClearLogsDialog() }
        btnVoiceModelAction.setOnClickListener { onVoiceModelActionClick() }

        switchContinuousListening.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingContinuousListeningSwitch) return@setOnCheckedChangeListener
            Logger.d(TAG, "Continuous listening switch changed to: $isChecked")
            settingsManager.setContinuousListening(isChecked)
            if (isChecked) {
                if (voiceModelManager?.isModelDownloaded() == true) {
                    if (ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.RECORD_AUDIO,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@setOnCheckedChangeListener
                    }
                    checkNotificationPermissionAndStart()
                } else {
                    isUpdatingContinuousListeningSwitch = true
                    switchContinuousListening.isChecked = false
                    isUpdatingContinuousListeningSwitch = false
                    settingsManager.setContinuousListening(false)
                    Toast.makeText(requireContext(), R.string.voice_model_required, Toast.LENGTH_SHORT).show()
                }
            } else {
                ContinuousListeningService.stop(requireContext())
            }
        }

        wakeWordInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveWakeWords()
        }

        sensitivitySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) settingsManager.setWakeWordSensitivity(value / 100f)
        }

        baseUrlInput.setOnFocusChangeListener { _, _ -> baseUrlLayout.error = null }
        modelNameInput.setOnFocusChangeListener { _, _ -> modelNameLayout.error = null }
        maxStepsInput.setOnFocusChangeListener { _, _ -> maxStepsLayout.error = null }
        screenshotDelayInput.setOnFocusChangeListener { _, _ -> screenshotDelayLayout.error = null }
    }

    /**
     * Sets up permission action button listeners.
     */
    private fun setupPermissionActionListeners() {
        permissionShizuku.findViewById<Button>(R.id.btnPermissionAction).setOnClickListener {
            requestShizukuPermission()
        }
        permissionOverlay.findViewById<Button>(R.id.btnPermissionAction).setOnClickListener {
            requestOverlayPermission()
        }
        permissionKeyboard.findViewById<Button>(R.id.btnPermissionAction).setOnClickListener {
            openKeyboardSettings()
        }
        permissionBattery.findViewById<Button>(R.id.btnPermissionAction).setOnClickListener {
            requestBatteryOptimization()
        }
    }

    /**
     * Toggles the permissions center expanded/collapsed state.
     */
    private fun togglePermissionsExpanded() {
        isPermissionsExpanded = !isPermissionsExpanded
        permissionsContent.visibility = if (isPermissionsExpanded) View.VISIBLE else View.GONE
        btnExpandCollapse.setImageResource(
            if (isPermissionsExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more,
        )
    }

    /**
     * Observes permission states from ViewModel for cross-Fragment synchronization.
     */
    private fun observePermissionStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.permissionStates.collect { states ->
                    updatePermissionUI(states)
                }
            }
        }
    }

    /**
     * Refreshes all permission states and updates ViewModel.
     */
    private fun refreshPermissionStates() {
        val context = requireContext()
        val states =
            PermissionStates(
                shizuku = isShizukuConnected(),
                overlay = Settings.canDrawOverlays(context),
                keyboard = isKeyboardEnabled(),
                battery = isBatteryOptimizationIgnored(),
            )
        viewModel.updateAllPermissionStates(states)
    }

    /**
     * Updates permission UI based on states.
     */
    private fun updatePermissionUI(states: PermissionStates) {
        updatePermissionItemUI(
            permissionShizuku,
            states.shizuku,
            getString(R.string.request_shizuku_permission),
        )
        updatePermissionItemUI(
            permissionOverlay,
            states.overlay,
            getString(R.string.request_overlay_permission),
        )
        updatePermissionItemUI(
            permissionKeyboard,
            states.keyboard,
            getString(R.string.enable_keyboard),
        )
        updatePermissionItemUI(
            permissionBattery,
            states.battery,
            getString(R.string.battery_opt_request),
        )

        // Update summary
        val grantedCount =
            listOf(
                states.shizuku,
                states.overlay,
                states.keyboard,
                states.battery,
            ).count { it }
        permissionsSummary.text =
            if (grantedCount == 4) {
                getString(R.string.permissions_summary_all_granted)
            } else {
                getString(R.string.permissions_summary_partial, grantedCount, 4)
            }
    }

    /**
     * Updates a single permission item UI.
     */
    private fun updatePermissionItemUI(itemView: View, isGranted: Boolean, actionText: String) {
        val icon = itemView.findViewById<ImageView>(R.id.permissionIcon)
        val status = itemView.findViewById<TextView>(R.id.permissionStatus)
        val actionBtn = itemView.findViewById<Button>(R.id.btnPermissionAction)

        if (isGranted) {
            icon.setImageResource(R.drawable.ic_check_circle)
            icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.status_running))
            status.text = getString(R.string.permission_granted)
            actionBtn.visibility = View.GONE
        } else {
            icon.setImageResource(R.drawable.ic_error)
            icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.status_failed))
            status.text = getString(R.string.permission_not_granted)
            actionBtn.text = actionText
            actionBtn.visibility = View.VISIBLE
        }
    }

    // region Permission Helpers

    private fun isShizukuConnected(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    private fun isKeyboardEnabled(): Boolean {
        val enabledInputMethods =
            Settings.Secure.getString(
                requireContext().contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS,
            ) ?: ""
        return enabledInputMethods.contains(requireContext().packageName)
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val pm = requireContext().getSystemService(PowerManager::class.java)
        return pm?.isIgnoringBatteryOptimizations(requireContext().packageName) == true
    }

    private fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(requireContext(), R.string.toast_shizuku_not_running, Toast.LENGTH_SHORT).show()
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), R.string.toast_shizuku_already_granted, Toast.LENGTH_SHORT).show()
                return
            }
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        } catch (e: Exception) {
            Logger.e(TAG, "Error requesting Shizuku permission", e)
        }
    }

    private fun requestOverlayPermission() {
        val intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}"),
            )
        startActivity(intent)
    }

    private fun openKeyboardSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    private fun requestBatteryOptimization() {
        val intent =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
        startActivity(intent)
    }

    // endregion

    // region Model Profile Management

    private fun clearModelFields() {
        baseUrlInput.setText("")
        modelNameInput.setText("")
        apiKeyInput.setText("")
    }

    private fun loadProfileToFields(profile: SavedModelProfile) {
        baseUrlInput.setText(profile.config.baseUrl)
        modelNameInput.setText(profile.config.modelName)
        apiKeyInput.setText(if (profile.config.apiKey == "EMPTY") "" else profile.config.apiKey)
    }

    private fun showProfileMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_profile, popup.menu)

        popup.menu.findItem(R.id.action_delete_profile)?.isEnabled = currentProfileId != null
        popup.menu.findItem(R.id.action_copy_profile)?.isEnabled = currentProfileId != null

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save_profile -> {
                    showSaveProfileDialog()
                    true
                }

                R.id.action_copy_profile -> {
                    copyCurrentProfile()
                    true
                }

                R.id.action_delete_profile -> {
                    showDeleteProfileDialog()
                    true
                }

                else -> {
                    false
                }
            }
        }
        popup.show()
    }

    private fun showSaveProfileDialog() {
        val editText =
            TextInputEditText(requireContext()).apply {
                hint = getString(R.string.settings_profile_name_hint)
                currentProfileId?.let { id ->
                    savedProfiles.find { it.id == id }?.let { profile ->
                        setText(profile.displayName)
                    }
                }
            }

        val layout =
            TextInputLayout(requireContext()).apply {
                addView(editText)
                setPadding(48, 16, 48, 0)
            }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_save_profile)
            .setView(layout)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val name = editText.text?.toString()?.trim() ?: ""
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.settings_profile_name_empty, Toast.LENGTH_SHORT).show()
                } else {
                    saveCurrentAsProfile(name)
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    private fun saveCurrentAsProfile(displayName: String) {
        Logger.d(TAG, "Saving current configuration as profile: $displayName")
        val baseUrl = baseUrlInput.text?.toString()?.trim() ?: ""
        val modelName = modelNameInput.text?.toString()?.trim() ?: ""
        val apiKey =
            apiKeyInput.text?.toString()?.trim().let {
                if (it.isNullOrEmpty()) "EMPTY" else it
            }

        val config = ModelConfig(baseUrl = baseUrl, apiKey = apiKey, modelName = modelName)
        val profileId = currentProfileId ?: settingsManager.generateProfileId()
        val profile = SavedModelProfile(id = profileId, displayName = displayName, config = config)

        settingsManager.saveProfile(profile)
        currentProfileId = profileId
        settingsManager.setCurrentProfileId(profileId)

        loadSavedProfiles()
        Toast.makeText(requireContext(), R.string.settings_profile_saved, Toast.LENGTH_SHORT).show()
    }

    private fun copyCurrentProfile() {
        if (currentProfileId == null) {
            Toast.makeText(requireContext(), R.string.settings_profile_name_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val currentProfile = savedProfiles.find { it.id == currentProfileId } ?: return
        val newName = getString(R.string.settings_copy_profile_name, currentProfile.displayName)

        val baseUrl = baseUrlInput.text?.toString()?.trim() ?: ""
        val modelName = modelNameInput.text?.toString()?.trim() ?: ""
        val apiKey =
            apiKeyInput.text?.toString()?.trim().let {
                if (it.isNullOrEmpty()) "EMPTY" else it
            }

        val config = ModelConfig(baseUrl = baseUrl, apiKey = apiKey, modelName = modelName)
        val newProfileId = settingsManager.generateProfileId()
        val newProfile = SavedModelProfile(id = newProfileId, displayName = newName, config = config)

        settingsManager.saveProfile(newProfile)
        currentProfileId = newProfileId
        settingsManager.setCurrentProfileId(newProfileId)

        loadSavedProfiles()
        Toast.makeText(requireContext(), R.string.settings_profile_copied, Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteProfileDialog() {
        if (currentProfileId == null) return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_delete_profile)
            .setMessage(R.string.settings_delete_profile_confirm)
            .setPositiveButton(R.string.dialog_confirm) { _, _ -> deleteCurrentProfile() }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    private fun deleteCurrentProfile() {
        currentProfileId?.let { id ->
            Logger.d(TAG, "Deleting current profile: $id")
            settingsManager.deleteProfile(id)
            currentProfileId = null
            clearModelFields()
            loadSavedProfiles()
            Toast.makeText(requireContext(), R.string.settings_profile_deleted, Toast.LENGTH_SHORT).show()
        }
    }

    // endregion

    // region Settings Validation and Save

    private fun validateInput(): Boolean {
        Logger.d(TAG, "Validating input")
        var isValid = true
        clearErrors()

        val baseUrl = baseUrlInput.text?.toString()?.trim() ?: ""
        if (baseUrl.isEmpty() || !isValidUrl(baseUrl)) {
            baseUrlLayout.error = getString(R.string.settings_validation_error_url)
            isValid = false
        }

        val modelName = modelNameInput.text?.toString()?.trim() ?: ""
        if (modelName.isEmpty()) {
            modelNameLayout.error = getString(R.string.settings_validation_error_model)
            isValid = false
        }

        val maxStepsStr = maxStepsInput.text?.toString()?.trim() ?: ""
        val maxSteps = maxStepsStr.toIntOrNull()
        if (maxSteps == null || maxSteps < 0) {
            maxStepsLayout.error = getString(R.string.settings_validation_error_steps)
            isValid = false
        }

        val screenshotDelayStr = screenshotDelayInput.text?.toString()?.trim() ?: ""
        val screenshotDelay = screenshotDelayStr.toDoubleOrNull() ?: -1.0
        if (screenshotDelay < 0) {
            screenshotDelayLayout.error = getString(R.string.settings_validation_error_delay)
            isValid = false
        }

        return isValid
    }

    private fun isValidUrl(url: String): Boolean = try {
        val uri = android.net.Uri.parse(url)
        uri.scheme?.startsWith("http") == true && !uri.host.isNullOrEmpty()
    } catch (e: Exception) {
        false
    }

    private fun clearErrors() {
        baseUrlLayout.error = null
        modelNameLayout.error = null
        apiKeyLayout.error = null
        maxStepsLayout.error = null
        screenshotDelayLayout.error = null
    }

    private fun saveSettings() {
        Logger.i(TAG, "Saving settings")
        val baseUrl = baseUrlInput.text?.toString()?.trim() ?: ""
        val modelName = modelNameInput.text?.toString()?.trim() ?: ""
        val apiKey =
            apiKeyInput.text?.toString()?.trim().let {
                if (it.isNullOrEmpty()) "EMPTY" else it
            }
        val maxSteps = maxStepsInput.text?.toString()?.trim()?.toIntOrNull() ?: 100
        val screenshotDelaySeconds = screenshotDelayInput.text?.toString()?.trim()?.toDoubleOrNull() ?: 2.0
        val screenshotDelayMs = (screenshotDelaySeconds * 1000).toLong()
        val language = if (languageEnglish.isChecked) "en" else "cn"

        val modelConfig = ModelConfig(baseUrl = baseUrl, apiKey = apiKey, modelName = modelName)
        settingsManager.saveModelConfig(modelConfig)

        val agentConfig =
            AgentConfig(
                maxSteps = maxSteps,
                language = language,
                screenshotDelayMs = screenshotDelayMs,
            )
        settingsManager.saveAgentConfig(agentConfig)

        saveWakeWords()

        if (ContinuousListeningService.isRunning()) {
            Logger.i(TAG, "Restarting continuous listening service to apply new wake words")
            ContinuousListeningService.stop(requireContext())
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                ContinuousListeningService.start(requireContext())
            }, 500)
        }

        Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
    }

    private fun resetToDefaults() {
        Logger.i(TAG, "Resetting settings to defaults")
        settingsManager.clearAll()
        loadCurrentSettings()
        Toast.makeText(requireContext(), R.string.settings_reset_done, Toast.LENGTH_SHORT).show()
    }

    private fun testModelConnection() {
        Logger.d(TAG, "Testing model connection")
        val baseUrl = baseUrlInput.text?.toString()?.trim() ?: ""
        val modelName = modelNameInput.text?.toString()?.trim() ?: ""
        val apiKey =
            apiKeyInput.text?.toString()?.trim().let {
                if (it.isNullOrEmpty()) "EMPTY" else it
            }

        if (baseUrl.isEmpty() || !isValidUrl(baseUrl) || modelName.isEmpty()) {
            Toast.makeText(requireContext(), R.string.settings_test_invalid_config, Toast.LENGTH_SHORT).show()
            return
        }

        val testConfig =
            ModelConfig(
                baseUrl = baseUrl,
                apiKey = apiKey,
                modelName = modelName,
                timeoutSeconds = 30,
            )

        val client = ModelClient(testConfig)

        testConnectionButton.isEnabled = false
        testConnectionButton.text = getString(R.string.settings_testing)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = client.testConnection()
            Logger.d(TAG, "Connection test result: $result")

            testConnectionButton.isEnabled = true
            testConnectionButton.text = getString(R.string.settings_test_connection)

            val message =
                when (result) {
                    is ModelClient.TestResult.Success -> {
                        getString(R.string.settings_test_success, result.latencyMs)
                    }

                    is ModelClient.TestResult.AuthError -> {
                        getString(R.string.settings_test_auth_error, result.message)
                    }

                    is ModelClient.TestResult.ModelNotFound -> {
                        getString(R.string.settings_test_model_not_found, result.message)
                    }

                    is ModelClient.TestResult.ServerError -> {
                        getString(R.string.settings_test_server_error, result.code, result.message)
                    }

                    is ModelClient.TestResult.ConnectionError -> {
                        getString(R.string.settings_test_connection_error, result.message)
                    }

                    is ModelClient.TestResult.Timeout -> {
                        getString(R.string.settings_test_timeout, result.message)
                    }
                }

            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    // endregion

    // region Task Templates

    private fun loadTaskTemplates() {
        taskTemplates.clear()
        taskTemplates.addAll(settingsManager.getTaskTemplates())
        templatesAdapter?.notifyDataSetChanged()
        updateTemplatesEmptyState()
    }

    private fun updateTemplatesEmptyState() {
        if (taskTemplates.isEmpty()) {
            templatesRecyclerView.visibility = View.GONE
            emptyTemplatesText.visibility = View.VISIBLE
        } else {
            templatesRecyclerView.visibility = View.VISIBLE
            emptyTemplatesText.visibility = View.GONE
        }
    }

    private fun showAddTemplateDialog() {
        showTemplateDialog(null)
    }

    private fun showEditTemplateDialog(template: TaskTemplate) {
        showTemplateDialog(template)
    }

    private fun showTemplateDialog(template: TaskTemplate?) {
        val isEdit = template != null

        val dialogView =
            LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_task_template, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.templateNameInput)
        val descInput = dialogView.findViewById<TextInputEditText>(R.id.templateDescriptionInput)

        if (isEdit) {
            nameInput.setText(template!!.name)
            descInput.setText(template.description)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isEdit) R.string.settings_edit_template else R.string.settings_add_template)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val name = nameInput.text?.toString()?.trim() ?: ""
                val description = descInput.text?.toString()?.trim() ?: ""

                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.settings_template_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (description.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.settings_template_desc_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newTemplate =
                    TaskTemplate(
                        id = template?.id ?: settingsManager.generateTemplateId(),
                        name = name,
                        description = description,
                    )
                settingsManager.saveTaskTemplate(newTemplate)
                loadTaskTemplates()
                Toast.makeText(requireContext(), R.string.settings_template_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    private fun showDeleteTemplateDialog(template: TaskTemplate) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_delete_template)
            .setMessage(R.string.settings_delete_template_confirm)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                settingsManager.deleteTaskTemplate(template.id)
                loadTaskTemplates()
                Toast.makeText(requireContext(), R.string.settings_template_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    /**
     * Adapter for task templates RecyclerView.
     */
    private inner class TaskTemplatesAdapter(
        private val templates: List<TaskTemplate>,
        private val onEditClick: (TaskTemplate) -> Unit,
        private val onDeleteClick: (TaskTemplate) -> Unit,
    ) : RecyclerView.Adapter<TaskTemplatesAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.templateName)
            val descText: TextView = view.findViewById(R.id.templateDescription)
            val editBtn: ImageButton = view.findViewById(R.id.btnEdit)
            val deleteBtn: ImageButton = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_task_template, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val template = templates[position]
            holder.nameText.text = template.name
            holder.descText.text = template.description
            holder.editBtn.setOnClickListener { onEditClick(template) }
            holder.deleteBtn.setOnClickListener { onDeleteClick(template) }
        }

        override fun getItemCount() = templates.size
    }

    // endregion

    // region System Prompt

    private fun updatePromptStatus() {
        promptCnStatus.text =
            if (settingsManager.hasCustomSystemPrompt("cn")) {
                getString(R.string.settings_system_prompt_custom)
            } else {
                getString(R.string.settings_system_prompt_default)
            }

        promptEnStatus.text =
            if (settingsManager.hasCustomSystemPrompt("en")) {
                getString(R.string.settings_system_prompt_custom)
            } else {
                getString(R.string.settings_system_prompt_default)
            }
    }

    private fun showEditPromptDialog(language: String) {
        Logger.d(TAG, "Showing edit prompt dialog for language: $language")
        val dialogView =
            LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_system_prompt, null)
        val promptInput = dialogView.findViewById<TextInputEditText>(R.id.promptInput)
        val btnReset = dialogView.findViewById<Button>(R.id.btnResetPrompt)

        val currentPrompt =
            settingsManager.getCustomSystemPrompt(language)
                ?: if (language == "en") {
                    com.kevinluo.autoglm.config.SystemPrompts.getEnglishPromptTemplate()
                } else {
                    com.kevinluo.autoglm.config.SystemPrompts.getChinesePromptTemplate()
                }
        promptInput.setText(currentPrompt)

        val title =
            if (language == "en") {
                getString(R.string.settings_system_prompt_en)
            } else {
                getString(R.string.settings_system_prompt_cn)
            }

        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                    val newPrompt = promptInput.text?.toString() ?: ""
                    if (newPrompt.isNotBlank()) {
                        settingsManager.saveCustomSystemPrompt(language, newPrompt)
                        if (language == "en") {
                            com.kevinluo.autoglm.config.SystemPrompts.setCustomEnglishPrompt(newPrompt)
                        } else {
                            com.kevinluo.autoglm.config.SystemPrompts.setCustomChinesePrompt(newPrompt)
                        }
                        updatePromptStatus()
                        Toast.makeText(
                            requireContext(),
                            R.string.settings_system_prompt_saved,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .create()

        btnReset.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_system_prompt_reset)
                .setMessage(R.string.settings_system_prompt_reset_confirm)
                .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                    settingsManager.clearCustomSystemPrompt(language)
                    if (language == "en") {
                        com.kevinluo.autoglm.config.SystemPrompts.setCustomEnglishPrompt(null)
                    } else {
                        com.kevinluo.autoglm.config.SystemPrompts.setCustomChinesePrompt(null)
                    }
                    updatePromptStatus()
                    Toast.makeText(
                        requireContext(),
                        R.string.settings_system_prompt_reset_done,
                        Toast.LENGTH_SHORT,
                    ).show()
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .showWithPrimaryButtons()
        }

        dialog.show()
        dialog.applyPrimaryButtonColors()
    }

    // endregion

    // region Debug Logs

    private fun updateLogSizeDisplay() {
        val totalSize = LogFileManager.getTotalLogSize()
        val formattedSize = LogFileManager.formatSize(totalSize)
        logSizeText.text = getString(R.string.settings_debug_logs_size, formattedSize)
    }

    private fun exportDebugLogs() {
        Logger.i(TAG, "Exporting debug logs")

        val logFiles = LogFileManager.getLogFiles()
        if (logFiles.isEmpty()) {
            Toast.makeText(requireContext(), R.string.settings_logs_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = LogFileManager.exportLogs(requireContext())
        if (shareIntent != null) {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.settings_export_logs)))
        } else {
            Toast.makeText(requireContext(), R.string.settings_logs_export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showClearLogsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_clear_logs)
            .setMessage(R.string.settings_clear_logs_confirm)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                LogFileManager.clearAllLogs()
                updateLogSizeDisplay()
                Toast.makeText(requireContext(), R.string.settings_logs_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    // endregion

    // region Voice Settings

    private fun loadVoiceSettings() {
        Logger.d(TAG, "Loading voice settings")

        if (voiceModelManager == null) {
            voiceModelManager = VoiceModelManager.getInstance(requireContext())
        }

        updateVoiceModelStatus()

        isUpdatingContinuousListeningSwitch = true
        switchContinuousListening.isChecked = settingsManager.isContinuousListeningEnabled()
        isUpdatingContinuousListeningSwitch = false

        val wakeWords = settingsManager.getWakeWordsList()
        wakeWordInput.setText(wakeWords.joinToString(", "))

        val sensitivity = settingsManager.getWakeWordSensitivity()
        sensitivitySlider.value = sensitivity * 100f
    }

    private fun updateVoiceModelStatus() {
        val state = voiceModelManager?.state?.value

        when (state) {
            is VoiceModelState.Downloaded -> {
                voiceModelStatus.text = getString(R.string.voice_model_downloaded, state.sizeMB)
                voiceModelStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_running))
                btnVoiceModelAction.text = getString(R.string.voice_delete_model)
                btnVoiceModelAction.setIconResource(R.drawable.ic_delete)
                btnVoiceModelAction.isEnabled = true
            }

            is VoiceModelState.Downloading -> {
                voiceModelStatus.text = getString(R.string.voice_downloading_progress, state.progress)
                voiceModelStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_running))
                btnVoiceModelAction.text = getString(R.string.voice_downloading)
                btnVoiceModelAction.isEnabled = false
            }

            is VoiceModelState.Error -> {
                voiceModelStatus.text = getString(R.string.voice_download_failed, state.message)
                voiceModelStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_failed))
                btnVoiceModelAction.text = getString(R.string.voice_download_model)
                btnVoiceModelAction.setIconResource(R.drawable.ic_download)
                btnVoiceModelAction.isEnabled = true
            }

            VoiceModelState.NotDownloaded, null -> {
                voiceModelStatus.text = getString(R.string.voice_model_not_downloaded)
                voiceModelStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                btnVoiceModelAction.text = getString(R.string.voice_download_model)
                btnVoiceModelAction.setIconResource(R.drawable.ic_download)
                btnVoiceModelAction.isEnabled = true
            }
        }
    }

    private fun onVoiceModelActionClick() {
        val state = voiceModelManager?.state?.value

        when (state) {
            is VoiceModelState.Downloaded -> {
                showDeleteModelDialog()
            }

            is VoiceModelState.Downloading -> { /* Do nothing while downloading */ }

            else -> {
                showDownloadModelDialog()
            }
        }
    }

    private fun showDownloadModelDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_voice_download_confirm, null)

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton(R.string.voice_download_confirm_title) { _, _ ->
                startModelDownload()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    private fun startModelDownload() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_voice_download_progress, null)
        val progressBar = dialogView.findViewById<android.widget.ProgressBar>(R.id.downloadProgressBar)
        val progressText = dialogView.findViewById<TextView>(R.id.downloadProgressText)

        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .setNegativeButton(R.string.dialog_cancel) { _, _ ->
                    voiceModelManager?.cancelDownload()
                }
                .create()

        dialog.show()
        dialog.applyPrimaryButtonColors()

        viewLifecycleOwner.lifecycleScope.launch {
            voiceModelManager?.downloadModel(
                object : VoiceModelDownloadListener {
                    override fun onDownloadStarted() {
                        activity?.runOnUiThread {
                            progressText.text = getString(R.string.voice_download_status_preparing)
                        }
                    }

                    override fun onDownloadProgress(progress: Int, downloadedBytes: Long, totalBytes: Long) {
                        activity?.runOnUiThread {
                            progressBar.progress = progress
                            progressText.text = getString(R.string.voice_downloading_progress, progress)
                        }
                    }

                    override fun onDownloadCompleted(modelPath: String) {
                        activity?.runOnUiThread {
                            dialog.dismiss()
                            updateVoiceModelStatus()
                            Toast.makeText(
                                requireContext(),
                                R.string.voice_download_complete,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }

                    override fun onDownloadFailed(error: String) {
                        activity?.runOnUiThread {
                            dialog.dismiss()
                            updateVoiceModelStatus()
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.voice_download_failed, error),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }

                    override fun onDownloadCancelled() {
                        activity?.runOnUiThread {
                            dialog.dismiss()
                            updateVoiceModelStatus()
                        }
                    }
                },
            )
        }
    }

    private fun showDeleteModelDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.voice_delete_confirm_title)
            .setMessage(R.string.voice_delete_confirm_message)
            .setPositiveButton(R.string.dialog_confirm) { _, _ -> deleteVoiceModel() }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    private fun deleteVoiceModel() {
        val success = voiceModelManager?.deleteModel() == true
        if (success) {
            updateVoiceModelStatus()
            Toast.makeText(requireContext(), R.string.voice_delete_success, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), R.string.voice_download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveWakeWords() {
        val input = wakeWordInput.text?.toString() ?: ""
        val wakeWords = input.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
        settingsManager.setWakeWords(wakeWords)
    }

    private fun checkNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startContinuousListeningService()
    }

    private fun startContinuousListeningService() {
        ContinuousListeningService.start(requireContext())
        Toast.makeText(requireContext(), R.string.voice_listening_started, Toast.LENGTH_SHORT).show()
    }

    // endregion

    companion object {
        private const val TAG = "SettingsFragment"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
    }
}
