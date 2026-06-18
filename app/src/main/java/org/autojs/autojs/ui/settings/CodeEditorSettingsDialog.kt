package org.autojs.autojs.ui.settings

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ContextUtils.findActivity
import org.autojs.autojs6.R
import kotlin.math.min
import kotlin.math.roundToInt

class CodeEditorSettingsDialog : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = inflater.context
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(context.resolveThemeColor(android.R.attr.colorBackground))

            addView(createTitleView(context))
            addView(createDivider(context))
            addView(createSettingsContainer(context))
            addView(createDivider(context))
            addView(createButtonBar(context))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (childFragmentManager.findFragmentById(R.id.code_editor_settings_dialog_container) == null) {
            childFragmentManager
                .beginTransaction()
                .replace(
                    R.id.code_editor_settings_dialog_container,
                    CodeEditorSettingsFragment.newInstance(embeddedInDialog = true),
                )
                .commit()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialDialog.Builder(requireContext()).build().apply {
            setCanceledOnTouchOutside(true)
        }
    }

    override fun onStart() {
        super.onStart()
        val width = min((resources.displayMetrics.widthPixels * 0.94f).roundToInt(), requireContext().dp(720))
        dialog?.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun createTitleView(context: Context): TextView {
        return TextView(context).apply {
            text = context.getString(R.string.text_code_editor_settings)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(ThemeColorManager.colorPrimary)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(24), context.dp(20), context.dp(24), context.dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun createSettingsContainer(context: Context): FrameLayout {
        val height = min((resources.displayMetrics.heightPixels * 0.68f).roundToInt(), context.dp(560))
        return FrameLayout(context).apply {
            id = R.id.code_editor_settings_dialog_container
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height,
            )
        }
    }

    private fun createButtonBar(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
            addView(Button(context).apply {
                text = context.getString(R.string.dialog_button_dismiss)
                setTextColor(ThemeColorManager.colorPrimary)
                setOnClickListener { dismissAllowingStateLoss() }
            })
        }
    }

    private fun createDivider(context: Context): View {
        return View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1,
            )
        }
    }

    private fun Context.resolveThemeColor(attr: Int): Int {
        val out = TypedValue()
        theme.resolveAttribute(attr, out, true)
        return if (out.resourceId != 0) {
            getColor(out.resourceId)
        } else {
            out.data
        }
    }

    private fun Context.dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).roundToInt()
    }

    companion object {

        private const val TAG = "CodeEditorSettingsDialog"

        @JvmStatic
        fun show(context: Context) {
            val activity = context.findActivity()
            if (activity !is FragmentActivity || activity.isFinishing || activity.isDestroyed) {
                CodeEditorSettingsActivity.launch(context)
                return
            }
            val fragmentManager = activity.supportFragmentManager
            if (fragmentManager.isStateSaved) {
                CodeEditorSettingsActivity.launch(context)
                return
            }
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                CodeEditorSettingsDialog().show(fragmentManager, TAG)
            }
        }
    }
}
