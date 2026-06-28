package org.autojs.autojs.ui.log

import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.autojs.autojs.AutoJs
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ContextUtils.getColorForMode
import org.autojs.autojs.util.ContextUtils.getColorsForMode
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.BottomSheetLogBinding
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
    private var mEditorBackgroundColor by Delegates.notNull<Int>()

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

    interface OnStackFrameClickListener {
        fun onStackFrameClick(fileName: String, lineNumber: Int, columnNumber: Int): Boolean
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

        /**
         * Create a new instance with script info
         */
        @JvmStatic
        fun newInstance(scriptName: String?, scriptPath: String?, editorBackgroundColor: Int?): LogBottomSheet {
            val fragment = LogBottomSheet()
            val args = Bundle()
            args.putString(ARG_SCRIPT_NAME, scriptName)
            args.putString(ARG_SCRIPT_PATH, scriptPath)
            args.putInt(ARG_EDITOR_BACKGROUND_COLOR, editorBackgroundColor ?: Color.TRANSPARENT)
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
        arguments?.let {
            argScriptName = it.getString(ARG_SCRIPT_NAME, argScriptName)
            argScriptPath = it.getString(ARG_SCRIPT_PATH, argScriptPath)
            argEditorBackgroundColor = it.getInt(ARG_EDITOR_BACKGROUND_COLOR, argEditorBackgroundColor)
        }
        mScriptName = argScriptName
        mScriptPath = argScriptPath
        mEditorBackgroundColor = argEditorBackgroundColor
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
    }

    private fun setupViews() {
        val context = requireContext()

        val asDarkMode = ColorUtils.isLuminanceDark(mEditorBackgroundColor)

        val titleBarBackgroundColor = when {
            asDarkMode -> ColorUtils.adjustContrast(mEditorBackgroundColor, 1.05f)
            else -> ColorUtils.adjustContrast(mEditorBackgroundColor, 0.95f)
        }.let { ColorUtils.adjustColorForContrast(mEditorBackgroundColor, it, 1.15) }

        val dayNightColor = context.getColorForMode(R.color.day_night, asDarkMode)

        binding.root.setBackgroundColor(mEditorBackgroundColor)

        binding.dragHandleTouchArea.setOnTouchListener { _, event -> handleDrag(event) }
        binding.dragHandleTouchArea.setBackgroundColor(titleBarBackgroundColor)

        binding.dragHandle.background = (binding.dragHandle.background as? GradientDrawable)?.apply {
            mutate()
            setColor(context.getColorForMode(R.color.day_night_alpha_20, asDarkMode))
        }

        binding.header.setBackgroundColor(titleBarBackgroundColor)

        if (mScriptName.isNotBlank()) {
            binding.tvTitle.text = mScriptName
        }
        binding.tvTitle.setTextColor(dayNightColor)

        binding.console.setConsole(AutoJs.instance.globalConsole)
        binding.console.findViewById<View>(R.id.input_container)?.visibility = View.GONE
        binding.console.setEnableStackFrameLinks(true)
        binding.console.setOnStackFrameClickListener { fileName, lineNumber, columnNumber ->
            val handled = mStackFrameClickListener?.onStackFrameClick(fileName, lineNumber, columnNumber) == true
            if (handled) {
                dismissAllowingStateLoss()
            } else {
                ViewUtils.showToast(requireContext(), R.string.text_failed_to_locate, true)
            }
        }
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
            )
        )
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
        binding.btnClear.imageTintList = ColorStateList.valueOf(dayNightColor)

        binding.btnMaximize.setOnClickListener {
            openFullLogActivity()
        }
        binding.btnMaximize.imageTintList = ColorStateList.valueOf(dayNightColor)

        binding.btnClose.setOnClickListener {
            dismissAllowingStateLoss()
        }
        binding.btnClose.imageTintList = ColorStateList.valueOf(dayNightColor)
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
        startActivity(Intent(requireContext(), LogActivity::class.java))
        dismissAllowingStateLoss()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

    override fun onDestroyView() {
        mHeightAnimator?.cancel()
        mHeightAnimator = null
        mBottomSheetBehavior = null
        mBottomSheetView = null
        mDidLaunchFullLogActivity = false
        _binding = null
        super.onDestroyView()
    }
}
