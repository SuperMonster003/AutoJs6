package org.autojs.autojs.ui.settings

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import org.autojs.autojs.util.ContextUtils.findActivity
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs6.R
import kotlin.math.min
import kotlin.math.roundToInt

class CodeEditorSettingsDialog : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return createSettingsContainer(inflater.context)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (childFragmentManager.findFragmentById(R.id.code_editor_settings_dialog_container) == null) {
            childFragmentManager
                .beginTransaction()
                .replace(
                    R.id.code_editor_settings_dialog_container,
                    CodeEditorSettingsFragment.newInstance(
                        embeddedInDialog = true,
                        initialPreferenceKey = initialPreferenceKey,
                        recordAceActionFavorites = recordAceActionFavorites,
                        suppressInitialActionRecord = suppressInitialActionRecord,
                    ),
                )
                .commit()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return FragmentHostMaterialDialog.Builder(requireContext())
            .title(R.string.text_code_editor_settings)
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .widgetThemeColor()
            .build()
            .apply {
                setCanceledOnTouchOutside(true)
            }
    }

    override fun onStart() {
        super.onStart()
        val width = min((resources.displayMetrics.widthPixels * 0.94f).roundToInt(), requireContext().dp(720))
        dialog?.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun createSettingsContainer(context: Context): FrameLayout {
        val height = min((resources.displayMetrics.heightPixels * 0.68f).roundToInt(), context.dp(560))
        return FrameLayout(context).apply {
            id = R.id.code_editor_settings_dialog_container
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                height,
            )
        }
    }

    private fun Context.dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).roundToInt()
    }

    fun openPreference(preferenceKey: String, suppressActionRecord: Boolean = false) {
        (childFragmentManager.findFragmentById(R.id.code_editor_settings_dialog_container) as? CodeEditorSettingsFragment)
            ?.openPreference(preferenceKey, suppressActionRecord)
    }

    private val initialPreferenceKey: String?
        get() = arguments?.getString(ARG_INITIAL_PREFERENCE_KEY)

    private val recordAceActionFavorites: Boolean
        get() = arguments?.getBoolean(ARG_RECORD_ACE_ACTION_FAVORITES) == true

    private val suppressInitialActionRecord: Boolean
        get() = arguments?.getBoolean(ARG_SUPPRESS_INITIAL_ACTION_RECORD) == true

    companion object {

        private const val TAG = "CodeEditorSettingsDialog"
        private const val ARG_INITIAL_PREFERENCE_KEY = "initial_preference_key"
        private const val ARG_RECORD_ACE_ACTION_FAVORITES = "record_ace_action_favorites"
        private const val ARG_SUPPRESS_INITIAL_ACTION_RECORD = "suppress_initial_action_record"

        @JvmStatic
        @JvmOverloads
        fun show(
            context: Context,
            initialPreferenceKey: String? = null,
            recordAceActionFavorites: Boolean = false,
            suppressInitialActionRecord: Boolean = false,
        ) {
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
            val existingDialog = fragmentManager.findFragmentByTag(TAG) as? CodeEditorSettingsDialog
            if (existingDialog != null) {
                initialPreferenceKey?.let { existingDialog.openPreference(it, suppressInitialActionRecord) }
                return
            }
            CodeEditorSettingsDialog().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_RECORD_ACE_ACTION_FAVORITES, recordAceActionFavorites)
                    putBoolean(ARG_SUPPRESS_INITIAL_ACTION_RECORD, suppressInitialActionRecord)
                    initialPreferenceKey?.let { putString(ARG_INITIAL_PREFERENCE_KEY, it) }
                }
            }.show(fragmentManager, TAG)
        }
    }
}
