package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R

class EditorPinchToZoomPreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context) : super(context) {
        init()
    }

    private fun init() {
        summaryProvider = SummaryProvider<EditorPinchToZoomPreference> {
            values().getOrNull(currentIndex()) ?: prefContext.getString(R.string.text_default)
        }
    }

    override fun onClick() {
        if (handleUnavailableClickIfNeeded()) {
            return
        }
        val itemKeys = keys()
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(prefContext)
                .title(R.string.text_pinch_to_zoom)
                .items(values())
                .choiceWidgetThemeColor()
                .itemsCallbackSingleChoice(currentIndex()) { _, _, which, _ ->
                    MiscStateKeys.EDITOR_PINCH_TO_ZOOM_STRATEGY.set(itemKeys[which])
                    notifyChanged()
                    true
                }
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .build()
        }
    }

    private fun currentIndex(): Int {
        val keys = keys()
        val current = currentKey()
        return keys.indexOf(current).coerceAtLeast(0)
    }

    fun refreshSummary() {
        notifyChanged()
    }

    private fun keys(): List<String> {
        return prefContext.resources.getStringArray(R.array.keys_editor_pinch_to_zoom_strategy).toList()
    }

    private fun values(): List<String> {
        return prefContext.resources.getStringArray(R.array.values_editor_pinch_to_zoom_strategy).toList()
    }

    private fun currentKey(): String {
        val current = MiscStateKeys.EDITOR_PINCH_TO_ZOOM_STRATEGY.storedOrNull()
            ?: key(R.string.default_key_editor_pinch_to_zoom_strategy)
        return if (current == key(R.string.key_editor_pinch_to_zoom_scale_view)) {
            key(R.string.key_editor_pinch_to_zoom_change_text_size)
        } else {
            current
        }
    }
}
