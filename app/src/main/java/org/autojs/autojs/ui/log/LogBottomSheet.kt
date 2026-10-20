package org.autojs.autojs.ui.log

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.console.ConsoleTimeSettings
import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ContextUtils.getColorsForMode
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.BottomSheetLogBinding
import java.io.File
import kotlin.math.roundToInt
import kotlin.properties.Delegates

/**
 * Bottom sheet dialog for displaying script logs in the editor.
 * Provides quick access to log output without leaving the editor.
 * Clickable stack frames (file:line) are highlighted in blue.
 */
class LogBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetLogBinding? = null
    private val binding get() = _binding!!

    private lateinit var mScriptName: String
    private lateinit var mScriptPath: String
    private lateinit var mWorkingDirectory: String
    private var mEditorBackgroundColor by Delegates.notNull<Int>()
    private var mEditorForegroundColor by Delegates.notNull<Int>()
    private lateinit var mTypeScriptEvaluator: TypeScriptConsoleEvaluator
    private var mTypeScriptAvailabilityJob: Job? = null
    private var mTypeScriptConsoleAvailable = false
    private var mTypeScriptConsoleVisible = false
    private lateinit var mSettings: LogPageConsoleSettings

    private var mStackFrameClickListener: OnStackFrameClickListener? = null
    private var mScriptControlListener: ScriptControlListener? = null

    private var mBottomSheetView: FrameLayout? = null
    private var mBottomSheetBehavior: BottomSheetBehavior<FrameLayout>? = null
    private var mHeightAnimator: ValueAnimator? = null

    private var mMinSheetHeight = 0
    private var mMaxSheetHeight = 0
    private var mDragFloorHeight = 1
    private var mDismissThresholdHeight = 0
    private var mCurrentSheetHeight = 0
    private var mDragStartY = 0f
    private var mDragStartHeight = 0
    private var mDidLaunchFullLogActivity = false

    private val mExportLogsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(Mime.TEXT_PLAIN),
    ) { uri ->
        if (uri != null) {
            AutoJs.instance.globalConsole.export(uri)
        }
    }

    enum class StackFrameClickResult {
        DISMISS,
        KEEP_OPEN,
        FAILED,
    }

    interface OnStackFrameClickListener {
        fun onStackFrameClick(fileName: String, lineNumber: Int, columnNumber: Int): StackFrameClickResult
    }

    interface ScriptControlListener {
        fun isScriptRunning(): Boolean
        fun onRunScript()
        fun onStopScript()
    }

    companion object {
        private const val ARG_SCRIPT_NAME = "script_name"
        private const val ARG_SCRIPT_PATH = "script_path"
        private const val ARG_EDITOR_BACKGROUND_COLOR = "editor_background_color"
        private const val ARG_EDITOR_FOREGROUND_COLOR = "editor_foreground_color"

        /**
         * Create a new instance with script info
         */
        @JvmStatic
        fun newInstance(
            scriptName: String?,
            scriptPath: String?,
            editorBackgroundColor: Int?,
            editorForegroundColor: Int?,
        ): LogBottomSheet {
            val fragment = LogBottomSheet()
            val args = Bundle()
            args.putString(ARG_SCRIPT_NAME, scriptName)
            args.putString(ARG_SCRIPT_PATH, scriptPath)
            args.putInt(ARG_EDITOR_BACKGROUND_COLOR, editorBackgroundColor ?: Color.TRANSPARENT)
            args.putInt(ARG_EDITOR_FOREGROUND_COLOR, editorForegroundColor ?: Color.TRANSPARENT)
            fragment.arguments = args
            return fragment
        }
    }

    fun setOnStackFrameClickListener(listener: OnStackFrameClickListener) {
        mStackFrameClickListener = listener
    }

    fun setScriptControlListener(listener: ScriptControlListener) {
        mScriptControlListener = listener
        if (_binding != null) {
            updateScriptRunningState(listener.isScriptRunning())
        }
    }

    fun applyEditorPalette(
        editorBackgroundColor: Int,
        editorForegroundColor: Int,
    ) {
        mEditorBackgroundColor = editorBackgroundColor
        mEditorForegroundColor = editorForegroundColor
        if (_binding != null) {
            applyEditorPaletteToViews()
        }
        applyEditorPaletteToDialogWindow()
    }

    fun updateScriptRunningState(isRunning: Boolean) {
        if (_binding == null) {
            return
        }
        val icon = if (isRunning) R.drawable.ic_stop_button_fill else R.drawable.ic_play_arrow_white_48dp
        val tint = if (isRunning) R.color.dialog_button_caution else R.color.dialog_button_finish
        val desc = if (isRunning) R.string.text_stop else R.string.text_run
        binding.btnRunStop.setImageResource(icon)
        binding.btnRunStop.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), tint))
        binding.btnRunStop.contentDescription = getString(desc)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var argScriptName = ""
        var argScriptPath = ""
        var argEditorBackgroundColor = requireContext().getColor(R.color.window_background)
        var argEditorForegroundColor = requireContext().getColor(R.color.day_night)
        arguments?.let {
            argScriptName = it.getString(ARG_SCRIPT_NAME, argScriptName)
            argScriptPath = it.getString(ARG_SCRIPT_PATH, argScriptPath)
            argEditorBackgroundColor = it.getInt(ARG_EDITOR_BACKGROUND_COLOR, argEditorBackgroundColor)
            argEditorForegroundColor = it.getInt(ARG_EDITOR_FOREGROUND_COLOR, argEditorForegroundColor)
        }
        mScriptName = argScriptName
        mScriptPath = argScriptPath
        mWorkingDirectory = mScriptPath
            .takeIf(String::isNotBlank)
            ?.let(::File)
            ?.absoluteFile
            ?.parent
            ?.takeIf(String::isNotBlank)
            ?: WorkingDirectoryUtils.path
        mEditorBackgroundColor = argEditorBackgroundColor
        mEditorForegroundColor = argEditorForegroundColor
        mTypeScriptEvaluator = TypeScriptConsoleEvaluator(
            workingDirectory = mWorkingDirectory,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
    }

    override fun onStart() {
        super.onStart()
        mDidLaunchFullLogActivity = false
        setupResizableBottomSheet()
        updateScriptRunningState(mScriptControlListener?.isScriptRunning() == true)
        applyEditorPaletteToDialogWindow()
    }

    override fun onResume() {
        super.onResume()
        refreshTypeScriptConsoleAvailability()
    }

    private fun setupViews() {
        binding.dragHandleTouchArea.setOnTouchListener { _, event -> handleDrag(event) }

        if (mScriptName.isNotBlank()) {
            binding.tvTitle.text = mScriptName
        }

        binding.console.setConsole(AutoJs.instance.globalConsole)
        binding.console.setEnableStackFrameLinks(true)
        binding.console.setOnStackFrameClickListener { fileName, lineNumber, columnNumber ->
            when (mStackFrameClickListener?.onStackFrameClick(fileName, lineNumber, columnNumber)) {
                StackFrameClickResult.DISMISS -> dismissAllowingStateLoss()
                StackFrameClickResult.KEEP_OPEN -> Unit
                StackFrameClickResult.FAILED, null -> ViewUtils.showToast(requireContext(), R.string.text_failed_to_locate, true)
            }
        }
        mSettings = LogPageConsoleSettings(binding.console, EditorKeys.EDITOR_LOG_PANEL_INPUT_BAR_VISIBLE,
            ConsoleTimeSettings.EDITOR_PANEL).also { it.apply() }
        binding.console.setOnCommandSubmitListener { source ->
            binding.console.setCommandInputBusy(true)
            mTypeScriptEvaluator.evaluate(source) {
                _binding?.console?.post {
                    _binding?.console?.setCommandInputBusy(false)
                }
            }
        }
        binding.console.setOnCommandStopListener { mTypeScriptEvaluator.stop() }
        setupConsoleLogList()

        binding.btnRunStop.setOnClickListener {
            val listener = mScriptControlListener ?: return@setOnClickListener
            if (listener.isScriptRunning()) {
                listener.onStopScript()
            } else {
                listener.onRunScript()
            }
            updateScriptRunningState(listener.isScriptRunning())
        }

        binding.btnClear.setOnClickListener {
            AutoJs.instance.globalConsole.clear()
        }

        binding.btnMaximize.setOnClickListener {
            openFullLogActivity()
        }

        binding.btnClose.setOnClickListener {
            dismissAllowingStateLoss()
        }

        binding.btnMore.setOnClickListener {
            showMoreMenu()
        }

        applyEditorPaletteToViews()
    }

    private fun applyEditorPaletteToViews() {
        val context = requireContext()
        val asDarkMode = ColorUtils.isLuminanceDark(mEditorBackgroundColor)
        val titleBarBackgroundColor = when {
            asDarkMode -> ColorUtils.adjustContrast(mEditorBackgroundColor, 1.05f)
            else -> ColorUtils.adjustContrast(mEditorBackgroundColor, 0.95f)
        }.let { ColorUtils.adjustColorForContrast(mEditorBackgroundColor, it, 1.15) }
        val foregroundColor = ColorUtils.adjustColorForContrast(
            titleBarBackgroundColor,
            mEditorForegroundColor,
            3.0,
        )

        binding.root.setBackgroundColor(mEditorBackgroundColor)
        mBottomSheetView?.setBackgroundColor(mEditorBackgroundColor)
        binding.dragHandleTouchArea.setBackgroundColor(titleBarBackgroundColor)
        binding.dragHandle.background = (binding.dragHandle.background as? GradientDrawable)?.apply {
            mutate()
            setColor(ColorUtils.applyAlpha(foregroundColor, 0.20))
        }
        binding.header.setBackgroundColor(titleBarBackgroundColor)
        binding.tvTitle.setTextColor(foregroundColor)
        binding.btnClear.imageTintList = ColorStateList.valueOf(foregroundColor)
        binding.btnMaximize.imageTintList = ColorStateList.valueOf(foregroundColor)
        binding.btnClose.imageTintList = ColorStateList.valueOf(foregroundColor)
        binding.btnMore.imageTintList = ColorStateList.valueOf(foregroundColor)
        binding.console.setTextColors(
            context.getColorsForMode(
                darkMode = asDarkMode,
                colorResArray = arrayOf(
                    R.color.console_view_verbose,
                    R.color.console_view_debug,
                    R.color.console_view_info,
                    R.color.console_view_warn,
                    R.color.console_view_error,
                    R.color.console_view_assert,
                ),
            ),
        )
        binding.console.setCommandInputTextColor(foregroundColor)
    }

    private fun showMoreMenu() {
        PopupMenu(requireContext(), binding.btnMore, Gravity.END).apply {
            menuInflater.inflate(R.menu.menu_log_bottom_sheet_more, menu)
            menu.findItem(R.id.action_typescript_console)?.apply {
                isVisible = mTypeScriptConsoleAvailable
                isChecked = mTypeScriptConsoleVisible
            }
            menu.findItem(R.id.action_input_bar)?.isChecked = mSettings.isInputBarVisible
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_settings -> true.also { mSettings.showDialog(requireContext()) }
                    R.id.action_input_bar -> true.also { mSettings.isInputBarVisible = !mSettings.isInputBarVisible }
                    R.id.action_copy -> true.also { AutoJs.instance.globalConsole.copyAll() }
                    R.id.action_send -> true.also { AutoJs.instance.globalConsole.send() }
                    R.id.action_export -> true.also {
                        mExportLogsLauncher.launch(ConsoleImpl.createLogExportFileName())
                    }
                    R.id.action_typescript_console -> true.also {
                        setTypeScriptConsoleVisible(!mTypeScriptConsoleVisible, persist = true)
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun refreshTypeScriptConsoleAvailability() {
        mTypeScriptAvailabilityJob?.cancel()
        val appContext = requireContext().applicationContext
        mTypeScriptAvailabilityJob = viewLifecycleOwner.lifecycleScope.launch {
            val available = withContext(Dispatchers.IO) {
                TypeScriptConsoleAvailability.isAvailable(appContext)
            }
            mTypeScriptConsoleAvailable = available
            if (!available) {
                mTypeScriptEvaluator.stop()
            }
            setTypeScriptConsoleVisible(
                visible = available && EditorKeys.EDITOR_LOG_PANEL_TYPESCRIPT_CONSOLE_VISIBLE.get(),
                persist = false,
            )
        }
    }

    private fun setTypeScriptConsoleVisible(visible: Boolean, persist: Boolean) {
        val resolved = visible && mTypeScriptConsoleAvailable
        mTypeScriptConsoleVisible = resolved
        _binding?.console?.isCommandModeEnabled = resolved
        if (persist) {
            EditorKeys.EDITOR_LOG_PANEL_TYPESCRIPT_CONSOLE_VISIBLE.set(resolved)
        }
    }

    private fun applyEditorPaletteToDialogWindow() {
        val window = dialog?.window ?: return
        val navigationBarBackground = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ||
                ColorUtils.isLuminanceDark(mEditorBackgroundColor) -> mEditorBackgroundColor
            else -> ColorUtils.adjustColorForContrast(
                Color.WHITE,
                mEditorBackgroundColor,
                3.0,
            )
        }
        @Suppress("DEPRECATION")
        window.navigationBarColor = navigationBarBackground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightNavigationBars =
                !ColorUtils.isLuminanceDark(navigationBarBackground)
            isAppearanceLightStatusBars =
                !ColorUtils.isLuminanceDark(mEditorBackgroundColor)
        }
    }

    private fun setupConsoleLogList() {
        val logList = binding.console.findViewById<RecyclerView>(R.id.log_list) ?: return
        logList.clipToPadding = false
        logList.isNestedScrollingEnabled = false
        logList.setPadding(logList.paddingLeft, dpToPx(8), logList.paddingRight, logList.paddingBottom)
    }

    private fun setupResizableBottomSheet() {
        val bottomSheetDialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet) ?: return

        mBottomSheetView = bottomSheet
        bottomSheet.setBackgroundColor(mEditorBackgroundColor)
        mBottomSheetBehavior = BottomSheetBehavior.from(bottomSheet).apply {
            isFitToContents = false
            expandedOffset = 0
            isHideable = true
            skipCollapsed = false
            isDraggable = false
            state = BottomSheetBehavior.STATE_COLLAPSED
        }

        val screenHeight = resources.displayMetrics.heightPixels
        mMinSheetHeight = maxOf(dpToPx(180), (screenHeight * 0.18f).roundToInt())
        mMaxSheetHeight = (screenHeight * 0.92f).roundToInt()
        mDragFloorHeight = 1
        mDismissThresholdHeight = maxOf(dpToPx(96), (screenHeight * 0.11f).roundToInt())

        mCurrentSheetHeight = when (mCurrentSheetHeight) {
            0 ->
                (screenHeight * 0.64f).roundToInt().coerceIn(mMinSheetHeight, mMaxSheetHeight)
            else ->
                mCurrentSheetHeight.coerceIn(mMinSheetHeight, mMaxSheetHeight)
        }
        setSheetHeight(mCurrentSheetHeight)
    }

    private fun handleDrag(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mHeightAnimator?.cancel()
                mDragStartY = event.rawY
                mDragStartHeight = mCurrentSheetHeight
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val delta = (mDragStartY - event.rawY).roundToInt()
                val target = (mDragStartHeight + delta).coerceIn(mDragFloorHeight, mMaxSheetHeight)
                setSheetHeight(target, clampToMin = false)
                return true
            }

            MotionEvent.ACTION_UP -> {
                settleSheetAfterDrag()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                animateSheetHeightTo(snapHeight(maxOf(mCurrentSheetHeight, mMinSheetHeight)))
                return true
            }
        }
        return false
    }

    private fun settleSheetAfterDrag() {
        val nearTopThreshold = dpToPx(56)
        when {
            mCurrentSheetHeight <= mDismissThresholdHeight -> {
                dismissAllowingStateLoss()
            }

            mCurrentSheetHeight >= mMaxSheetHeight - nearTopThreshold -> {
                openFullLogActivity()
            }

            else -> {
                animateSheetHeightTo(snapHeight(maxOf(mCurrentSheetHeight, mMinSheetHeight)))
            }
        }
    }

    private fun snapHeight(height: Int): Int {
        val step = dpToPx(16).coerceAtLeast(1)
        val snapped = ((height / step.toFloat()).roundToInt() * step)
        return snapped.coerceIn(mMinSheetHeight, mMaxSheetHeight)
    }

    private fun animateSheetHeightTo(targetHeight: Int) {
        val from = mCurrentSheetHeight
        val to = targetHeight.coerceIn(mMinSheetHeight, mMaxSheetHeight)
        if (from == to) {
            return
        }
        mHeightAnimator?.cancel()
        mHeightAnimator = ValueAnimator.ofInt(from, to).apply {
            duration = 120L
            addUpdateListener { animator ->
                setSheetHeight(animator.animatedValue as Int)
            }
            start()
        }
    }

    private fun setSheetHeight(height: Int, clampToMin: Boolean = true) {
        val bottomSheet = mBottomSheetView ?: return
        val behavior = mBottomSheetBehavior ?: return
        val minAllowed = if (clampToMin) mMinSheetHeight else mDragFloorHeight
        val clamped = height.coerceIn(minAllowed, mMaxSheetHeight).coerceAtLeast(1)
        mCurrentSheetHeight = clamped
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            this.height = clamped
        }
        behavior.peekHeight = clamped
        if (behavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        bottomSheet.requestLayout()
    }

    private fun openFullLogActivity() {
        if (mDidLaunchFullLogActivity || !isAdded) {
            return
        }
        mDidLaunchFullLogActivity = true
        LogActivity.launch(requireContext(), mScriptName, mScriptPath)
        dismissAllowingStateLoss()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

    override fun onDestroyView() {
        mTypeScriptAvailabilityJob?.cancel()
        mTypeScriptAvailabilityJob = null
        mHeightAnimator?.cancel()
        mHeightAnimator = null
        mBottomSheetBehavior = null
        mBottomSheetView = null
        mDidLaunchFullLogActivity = false
        _binding = null
        super.onDestroyView()
    }
}
