package org.autojs.autojs.ui.common

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.StateKey
import org.autojs.autojs.theme.widget.ThemeColorScrollView
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs6.R

/** A settings section shares its actual controls with the standalone dialog. */
class ManagerSettingsSection(
    private val context: Context,
    private val container: LinearLayout,
    private val open: ImageButton,
    private val divider: View,
    private val less: ImageButton,
    private val more: ImageButton,
    private val collapsedKey: StateKey<Boolean>,
) {
    private var collapsed = collapsedKey.storedOrNull() ?: false
    private var popup: MaterialDialog? = null

    init {
        open.setOnClickListener { showSettings() }
        less.setOnClickListener { setCollapsed(true) }
        more.setOnClickListener { setCollapsed(false) }
        applyState()
    }

    private fun setCollapsed(value: Boolean) {
        collapsed = value
        collapsedKey.set(value)
        applyState()
    }

    private fun applyState() {
        container.visibility = if (collapsed) View.GONE else View.VISIBLE
        open.visibility = if (collapsed) View.VISIBLE else View.GONE
        divider.visibility = open.visibility
        less.isEnabled = !collapsed
        more.isEnabled = collapsed
        less.alpha = if (collapsed) 0.3f else 1f
        more.alpha = if (collapsed) 1f else 0.3f
    }

    private fun showSettings() {
        if (popup?.isShowing == true) return
        val parent = container.parent as ViewGroup
        val index = parent.indexOfChild(container)
        val params = container.layoutParams
        parent.removeView(container)
        container.visibility = View.VISIBLE
        val scroll = ThemeColorScrollView(context).apply { addView(container) }
        popup = MaterialDialog.Builder(context)
            .title(R.string.text_settings)
            .customView(scroll, false)
            .negativeText(R.string.dialog_button_dismiss)
            .negativeColorRes(R.color.dialog_button_default)
            .dismissListener {
                scroll.removeView(container)
                parent.addView(container, index, params)
                popup = null
                applyState()
            }
            .build()
        popup?.showAdaptive()
    }

    fun dismiss() {
        popup?.dismiss()
    }
}
