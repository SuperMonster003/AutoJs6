package com.kevinluo.autoglm.home

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.kevinluo.autoglm.R
import com.kevinluo.autoglm.settings.SettingsManager
import com.kevinluo.autoglm.settings.TaskTemplate
import com.kevinluo.autoglm.ui.FloatingWindowStateManager
import com.kevinluo.autoglm.ui.MainUiState
import com.kevinluo.autoglm.ui.MainViewModel
import com.kevinluo.autoglm.ui.ShizukuStatus
import com.kevinluo.autoglm.util.Logger
import com.kevinluo.autoglm.util.showWithPrimaryButtons
import com.kevinluo.autoglm.voice.VoiceError
import com.kevinluo.autoglm.voice.VoiceInputManager
import com.kevinluo.autoglm.voice.VoiceRecognitionResult
import com.kevinluo.autoglm.voice.VoiceRecordingDialog
import kotlinx.coroutines.launch
import android.content.Intent
import com.kevinluo.autoglm.api.CapabilitiesProvider
import android.widget.TextView
import com.kevinluo.autoglm.api.ScriptExecutionEvent
import com.kevinluo.autoglm.api.Subscription
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home Fragment for task input and execution status display.
 *
 * Responsible for:
 * - Displaying task input field with voice input and template selection
 * - Showing task execution status and controls
 * - Managing floating window quick action
 * - Observing MainViewModel state for cross-Fragment synchronization
 */
class TaskFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var settingsManager: SettingsManager
    private var voiceInputManager: VoiceInputManager? = null

    // Task Input Views
    private lateinit var taskInput: TextInputEditText
    private lateinit var btnVoiceInput: ImageButton
    private lateinit var btnSelectTemplate: ImageButton
    private lateinit var btnStartTask: MaterialButton

    // Open AutoJs Button
    private lateinit var btnOpenAutoJs: android.widget.Button
    private lateinit var tvAutoJsStatus: TextView
    private var autoJsExecutionSub: Subscription? = null

    // Floating Window Button
    private lateinit var btnFloatingWindow: ImageButton

    // Permission request launcher
    private val audioPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                showVoiceInputDialog()
            } else {
                Toast.makeText(
                    requireContext(),
                    R.string.voice_permission_denied,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logger.d(TAG, "TaskFragment onCreateView")
        return inflater.inflate(R.layout.autoglm_fragment_task, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Logger.d(TAG, "TaskFragment onViewCreated")

        settingsManager = SettingsManager.getInstance(requireContext())

        initViews(view)
        setupListeners()
        observeViewModel()
        startObserveAutoJsExecutions()
    }

    override fun onDestroyView() {
        // Clean up AutoJs execution subscription to avoid memory leaks
        autoJsExecutionSub?.dispose()
        autoJsExecutionSub = null

        super.onDestroyView()
        voiceInputManager?.release()
        voiceInputManager = null
    }

    /* *
     * Starts observing AutoJs script execution events.
     *
     * Subscribes to AutoJs capabilities to receive real-time updates on script execution status.
     * Updates the UI with the latest status of running scripts, including any results or exceptions.
     */
    private fun startObserveAutoJsExecutions() {
        // 避免重复订阅
        if (autoJsExecutionSub != null) return

        autoJsExecutionSub = runCatching {
            CapabilitiesProvider.autoJs().observeScriptExecutions { ev ->
                // 确保在主线程更新 UI（listener 可能来自非 UI 线程）
                activity?.runOnUiThread {
                    tvAutoJsStatus.text = formatAutoJsStatus(ev)
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "observeScriptExecutions failed", e)
            tvAutoJsStatus.text = "脚本状态：监听初始化失败"
        }.getOrNull()
    }

    private fun formatAutoJsStatus(ev: ScriptExecutionEvent): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ev.timestamp))
        val nameOrPath = ev.scriptPath ?: "(unknown)"
        return when (ev.type) {
            ScriptExecutionEvent.Type.START ->
                "[$time] 运行中：$nameOrPath"
            ScriptExecutionEvent.Type.SUCCESS ->
                "[$time] 已完成：$nameOrPath" + (ev.message?.let { "\nResult: $it" } ?: "")
            ScriptExecutionEvent.Type.EXCEPTION ->
                "[$time] 异常：$nameOrPath" + (ev.message?.let { "\nError: $it" } ?: "")
        }
    }

    /**
     * Initializes all view references.
     */
    private fun initViews(view: View) {
        // Task Input Views
        taskInput = view.findViewById(R.id.taskInput)
        btnVoiceInput = view.findViewById(R.id.btnVoiceInput)
        btnSelectTemplate = view.findViewById(R.id.btnSelectTemplate)
        btnStartTask = view.findViewById(R.id.btnStartTask)

        // Open AutoJs Button
        btnOpenAutoJs = view.findViewById(R.id.btn_open_autojs)
        tvAutoJsStatus = view.findViewById(R.id.tvAutoJsStatus)

        // Floating Window Button
        btnFloatingWindow = view.findViewById(R.id.btnFloatingWindow)
    }

    /**
     * Sets up click listeners and text change listeners.
     */
    private fun setupListeners() {
        // Task input text change listener
        taskInput.doAfterTextChanged { text ->
            viewModel.updateTaskInput(text?.isNotBlank() == true)
        }

        // Voice input button
        btnVoiceInput.setOnClickListener {
            startVoiceInput()
        }

        // Template selection button
        btnSelectTemplate.setOnClickListener {
            showTemplateSelector()
        }

        // Start task button
        btnStartTask.setOnClickListener {
            startTask()
        }

        // Floating window button
        btnFloatingWindow.setOnClickListener {
            toggleFloatingWindow()
        }

        btnOpenAutoJs.setOnClickListener {
            // val intent = Intent().apply {
            //     setClassName(requireContext().packageName, "org.autojs.autojs.ui.main.MainActivity")
            //     addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            // }
            // startActivity(intent)
            runCatching {
                CapabilitiesProvider.autoJs()
                    .pickWorkspaceScript(requireContext()) { path ->
                        taskInput.setText(path)
                        taskInput.setSelection(path.length)
                    }
            }.onFailure { e ->
                // 未注入时会抛 IllegalStateException，这里给个友好提示
                Logger.e(TAG, "AutoJsCapabilities not ready", e)
                Toast.makeText(requireContext(), "脚本选择功能未初始化", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Observes ViewModel state and updates UI accordingly.
     *
     * Observes both the main UI state and permission states for cross-Fragment
     * synchronization. When permissions change in SettingsFragment, this Fragment
     * will automatically update its UI.
     *
     * _Requirements: 5.2, 5.3_
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        updateUiState(state)
                    }
                }

                // Observe permission states for cross-Fragment synchronization
                // When permissions change in SettingsFragment, update UI here
                launch {
                    viewModel.permissionStates.collect { permissionStates ->
                        onPermissionStatesChanged(permissionStates)
                    }
                }
            }
        }
    }

    /**
     * Handles permission state changes from cross-Fragment synchronization.
     *
     * When permissions are granted or revoked in SettingsFragment, this method
     * updates the TaskFragment UI accordingly.
     *
     * @param permissionStates The updated permission states
     *
     * _Requirements: 5.2, 5.3_
     */
    private fun onPermissionStatesChanged(permissionStates: com.kevinluo.autoglm.ui.PermissionStates) {
        Logger.d(TAG, "Permission states changed: $permissionStates")

        // The start button state is already managed through uiState.canStartTask
        // which is updated when permissions change. However, we can add additional
        // UI feedback here if needed.

        // Update floating window button state based on overlay permission
        btnFloatingWindow.isEnabled = permissionStates.overlay

        // If Shizuku is not connected, we could show a hint or disable certain features
        if (!permissionStates.shizuku) {
            // The start button will already be disabled through canStartTask
            // Additional UI hints could be added here if needed
        }
    }

    /**
     * Updates UI based on the current state.
     */
    private fun updateUiState(state: MainUiState) {
        // Update start button state
        btnStartTask.isEnabled = state.canStartTask
    }

    /**
     * Starts a new task with the current input.
     */
    private fun startTask() {
        val taskDescription = taskInput.text?.toString()?.trim()

        if (taskDescription.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.toast_task_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val state = viewModel.uiState.value

        // Check Shizuku connection
        if (state.shizukuStatus != ShizukuStatus.CONNECTED) {
            Toast.makeText(
                requireContext(),
                R.string.toast_shizuku_not_running,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        // Check overlay permission
        if (!state.hasOverlayPermission) {
            Toast.makeText(
                requireContext(),
                R.string.toast_overlay_permission_required,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        Logger.i(TAG, "Starting task: ${taskDescription.take(50)}...")
        viewModel.startTask(taskDescription)
    }

    /**
     * Starts voice input for task description.
     */
    private fun startVoiceInput() {
        // Check audio permission
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        // Initialize voice input manager if needed
        if (voiceInputManager == null) {
            voiceInputManager = VoiceInputManager(requireContext())
        }

        // Check if model is ready
        if (!voiceInputManager!!.isModelReady()) {
            Toast.makeText(
                requireContext(),
                R.string.voice_model_required,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        showVoiceInputDialog()
    }

    /**
     * Shows the voice recording dialog.
     */
    private fun showVoiceInputDialog() {
        val manager = voiceInputManager ?: return

        VoiceRecordingDialog(
            context = requireContext(),
            voiceInputManager = manager,
            onResult = { result: VoiceRecognitionResult ->
                handleVoiceResult(result)
            },
            onError = { error: VoiceError ->
                handleVoiceError(error)
            },
        ).show()
    }

    /**
     * Handles voice recognition result.
     */
    private fun handleVoiceResult(result: VoiceRecognitionResult) {
        if (result.text.isNotBlank()) {
            taskInput.setText(result.text)
            taskInput.setSelection(result.text.length)
        }
    }

    /**
     * Handles voice recognition error.
     */
    private fun handleVoiceError(error: VoiceError) {
        val messageResId =
            when (error) {
                VoiceError.PermissionDenied -> R.string.voice_permission_denied
                VoiceError.ModelNotDownloaded -> R.string.voice_model_required
                VoiceError.ModelLoadFailed -> R.string.voice_model_load_failed
                VoiceError.RecordingFailed -> R.string.voice_recording_failed
                VoiceError.RecognitionFailed -> R.string.voice_recognition_failed
                VoiceError.NetworkError -> R.string.voice_network_error
                is VoiceError.Unknown -> R.string.voice_unknown_error
            }
        Toast.makeText(requireContext(), messageResId, Toast.LENGTH_SHORT).show()
    }

    /**
     * Shows the template selection dialog.
     */
    private fun showTemplateSelector() {
        val templates = settingsManager.getTaskTemplates()

        if (templates.isEmpty()) {
            Toast.makeText(
                requireContext(),
                R.string.settings_no_templates,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        val templateNames = templates.map { it.name }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.task_select_template)
            .setItems(templateNames) { _, which ->
                val selectedTemplate = templates[which]
                applyTemplate(selectedTemplate)
            }
            .setNegativeButton(R.string.cancel, null)
            .showWithPrimaryButtons()
    }

    /**
     * Applies a template to the task input.
     */
    private fun applyTemplate(template: TaskTemplate) {
        taskInput.setText(template.description)
        taskInput.setSelection(template.description.length)
        Logger.d(TAG, "Applied template: ${template.name}")
    }

    /**
     * Toggles the floating window visibility.
     *
     * When enabling, minimizes the app first so the floating window becomes visible.
     */
    private fun toggleFloatingWindow() {
        val wasEnabled = FloatingWindowStateManager.isUserEnabled()
        FloatingWindowStateManager.toggleByUser(requireContext())

        // If enabling floating window, minimize app so it becomes visible
        if (!wasEnabled) {
            activity?.moveTaskToBack(true)
        }
    }

    companion object {
        private const val TAG = "TaskFragment"
    }
}
