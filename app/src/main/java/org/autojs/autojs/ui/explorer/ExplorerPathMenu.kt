package org.autojs.autojs.ui.explorer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.text.format.Formatter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerPage
import org.autojs.autojs.core.pref.keys.ExplorerKeys
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.ui.settings.PreferencesActivity
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.EnvironmentUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs6.R
import java.io.File
import kotlin.math.roundToInt

/** A width-constrained, scrollable panel anchored below the path row, also usable in file dialogs. */
internal class ExplorerPathMenu(
    private val explorer: ExplorerView,
    private val anchor: View,
    private val trigger: View,
) {
    private val context: Context get() = anchor.context
    private var popup: PopupWindow? = null
    private var storageRequest: Disposable? = null
    private val foregroundColor get() = themeColor(android.R.attr.textColorPrimary, R.color.text_color_primary)
    private val secondary get() = themeColor(android.R.attr.textColorSecondary, R.color.text_color_primary_alpha_70)
    private val categoryColor get() = context.getColor(R.color.explorer_category_operation_button)
    private val surface get() = context.getColor(R.color.item_background_dark)
    private val accent: Int
        get() {
            val theme = ThemeColorManager.colorPrimary
            return if (ColorUtils.calculateContrast(theme, surface) >= 3.0) theme
            else ColorUtils.blendARGB(theme, foregroundColor, 0.6f)
        }

    val isShowing: Boolean get() = popup?.isShowing == true

    fun toggle() {
        if (isShowing) dismiss() else show()
    }

    fun dismiss() {
        storageRequest?.dispose()
        storageRequest = null
        popup?.dismiss()
        popup = null
    }

    private fun show() {
        val page = explorer.currentPageState.page ?: return
        if (!anchor.isAttachedToWindow || anchor.width == 0) return
        val frame = Rect().also { anchor.getWindowVisibleDisplayFrame(it) }
        val screen = IntArray(2).also(anchor::getLocationOnScreen)
        val window = IntArray(2).also(anchor::getLocationInWindow)
        val width = (anchor.width - dp(16)).coerceAtMost(frame.width() - dp(16))
        val availableHeight = frame.bottom - screen[1] - anchor.height - dp(12)
        if (width <= 0 || availableHeight <= 0) return

        // The path row already identifies the current location. Keep the popup a compact
        // continuation of the file list, without repeating its path or adding a large header.
        val body = column().apply { setPadding(0, dp(4), 0, dp(4)) }
        val navigation = actionRow()
        navigation.addView(action(R.id.explorer_menu_back, R.string.text_back, R.drawable.ic_explorer_back, explorer.canNavigateBackFromPathMenu()) {
            explorer.goBackFromPathMenu()
        }, weighted())
        navigation.addView(action(R.id.explorer_menu_forward, R.string.text_explorer_forward, R.drawable.ic_explorer_forward, explorer.canGoForward()) {
            explorer.goForward()
        }, weighted())
        navigation.addView(action(R.id.explorer_menu_up, R.string.text_explorer_up, R.drawable.ic_explorer_up, explorer.canGoUp()) {
            explorer.goUp()
        }, weighted())
        navigation.addView(action(R.id.explorer_menu_refresh, R.string.text_refresh, R.drawable.ic_explorer_refresh) {
            explorer.refreshFromPathMenu()
        }, weighted())
        body.addView(navigation)
        body.addView(divider())

        val paths = actionRow()
        paths.addView(action(R.id.explorer_menu_home, R.string.text_explorer_home, R.drawable.ic_explorer_home) {
            explorer.navigateToPath(WorkingDirectoryUtils.path)
        }, weighted())
        paths.addView(action(R.id.explorer_menu_copy_path, R.string.text_copy_path, R.drawable.ic_content_copy_black_24dp, page !is ArchiveExplorerPage) {
            ClipboardUtils.setClip(context, page.path)
            ViewUtils.showToast(context, R.string.text_already_copied_to_clip)
        }, weighted())
        paths.addView(action(R.id.explorer_menu_set_home, R.string.text_set_as_working_dir, R.drawable.ic_explorer_set_home, explorer.canSetCurrentAsWorkingDirectory()) {
            ScriptOperations(context, explorer, page).setAsWorkingDir(page.toScriptFile())
        }, weighted())
        body.addView(paths)
        body.addView(divider())

        body.addView(section(R.string.text_explorer_storage))
        val storage = column().apply { id = R.id.explorer_menu_storage }
        storage.addView(storageMessage(R.string.text_loading_with_dots))
        body.addView(storage)

        val special = actionRow()
        listOf("data", "obb").forEach { directory ->
            special.addView(label("Android/$directory", 12f).apply {
                gravity = Gravity.CENTER
                minHeight = dp(48)
                background = ripple()
                val lock = context.getDrawable(R.drawable.ic_explorer_lock)?.mutate()?.apply {
                    setTint(categoryColor)
                    setBounds(0, 0, dp(15), dp(15))
                }
                setCompoundDrawablesRelative(lock, null, null, null)
                compoundDrawablePadding = dp(5)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                isFocusable = true
                setOnClickListener {
                    dismiss()
                    explorer.navigateToPath(File(EnvironmentUtils.externalStorageDirectory, "Android/$directory").path)
                }
                buttonRole(this)
            }, weighted())
        }
        body.addView(special)
        body.addView(divider())
        body.addView(settingRow(R.string.text_explorer_access_mode, explorer.directoryAccessLabel) {
            dismiss()
            explorer.showDirectoryAccessOptions()
        })
        body.addView(divider())

        body.addView(displaySection())
        body.addView(SwitchCompat(context).apply {
            id = R.id.explorer_menu_hidden
            text = context.getString(R.string.text_hidden_files_title)
            setTextColor(foregroundColor)
            textSize = 14f
            minHeight = dp(48)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            switchPadding = dp(12)
            background = ripple()
            isChecked = ExplorerKeys.isHiddenFilesShown
            val checked = intArrayOf(android.R.attr.state_checked)
            thumbTintList = ColorStateList(arrayOf(checked, intArrayOf()), intArrayOf(accent, secondary))
            trackTintList = ColorStateList(arrayOf(checked, intArrayOf()), intArrayOf(ColorUtils.setAlphaComponent(accent, 90), ColorUtils.setAlphaComponent(secondary, 55)))
            setOnCheckedChangeListener { _, checkedValue ->
                ExplorerKeys.HIDDEN_FILES.set(context.getString(if (checkedValue) R.string.key_hidden_files_show else R.string.key_hidden_files_not_show))
                explorer.refreshFromPathMenu()
            }
        })
        body.addView(divider())
        val extensionRow = settingRow(R.string.text_file_extensions_title, extensionSummary()) {
            showExtensions()
        }.apply {
            id = R.id.explorer_menu_extensions
            contentDescription = "${context.getString(R.string.text_file_extensions_title)}, ${context.resources.getStringArray(R.array.values_file_extensions)[extensionIndex()]}"
        }
        body.addView(extensionRow)
        body.addView(divider())
        body.addView(settingRow(R.string.text_privileged_backups, null) {
            dismiss()
            PrivilegedBackupUi.show(explorer, directory = page.path.takeUnless { page is ArchiveExplorerPage }, mode = explorer.privilegedAccessMode)
        }.apply { id = R.id.explorer_privileged_backups })

        val scroll = ScrollView(context).apply {
            id = R.id.explorer_menu_panel
            isFillViewport = false
            clipToOutline = true
            background = rounded(surface, 4)
            addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        scroll.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(availableHeight, View.MeasureSpec.AT_MOST))
        val panel = PopupWindow(scroll, width, scroll.measuredHeight.coerceAtMost(availableHeight), true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(rounded(surface, 4))
            elevation = dp(3).toFloat()
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            setOnDismissListener {
                storageRequest?.dispose()
                storageRequest = null
                popup = null
                trigger.isSelected = false
                trigger.requestFocus()
            }
        }
        popup = panel
        trigger.isSelected = true
        ViewCompat.setAccessibilityPaneTitle(scroll, context.getString(R.string.text_explorer_path_menu))
        panel.showAtLocation(anchor, Gravity.TOP or Gravity.LEFT, window[0] + dp(8), window[1] + anchor.height + dp(2))
        scroll.pivotY = 0f
        scroll.alpha = 0f
        scroll.translationY = -dp(8).toFloat()
        scroll.scaleY = 0.97f
        scroll.animate().alpha(1f).translationY(0f).scaleY(1f).setDuration(160).setInterpolator(DecelerateInterpolator()).start()

        storageRequest = Single.fromCallable { ExplorerStorageCatalog.load(context) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ entries ->
                if (popup !== panel) return@subscribe
                storage.removeAllViews()
                val selectedPath = entries.filter { ExplorerPathSegmentBuilder.isSameOrDescendant(page.path, it.path) }
                    .maxByOrNull { it.path.length }?.path
                entries.forEach { entry ->
                    storage.addView(storageRow(entry, entry.path == selectedPath))
                    storage.addView(divider())
                }
                resize(panel, scroll, width, availableHeight)
            }, {
                if (popup !== panel) return@subscribe
                storage.removeAllViews()
                storage.addView(storageMessage(R.string.text_explorer_storage_unavailable))
                storage.addView(divider())
                resize(panel, scroll, width, availableHeight)
            })
    }

    private fun resize(panel: PopupWindow, scroll: View, width: Int, availableHeight: Int) {
        scroll.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(availableHeight, View.MeasureSpec.AT_MOST))
        panel.update(width, scroll.measuredHeight.coerceAtMost(availableHeight))
    }

    private fun storageRow(entry: ExplorerStorageEntry, selected: Boolean): View {
        val line = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = ripple(if (selected) context.getColor(R.color.window_background_selected) else Color.TRANSPARENT)
            isSelected = selected
            isFocusable = true
            setOnClickListener {
                dismiss()
                explorer.navigateToPath(entry.path)
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            buttonRole(this)
        }
        line.addView(ImageView(context).apply {
            setImageResource(if (entry.kind == ExplorerStorageKind.REMOVABLE) R.drawable.ic_explorer_sd_card else R.drawable.ic_explorer_storage)
            imageTintList = ColorStateList.valueOf(if (selected) accent else categoryColor)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) })
        val content = column()
        val title = row().apply { gravity = Gravity.CENTER_VERTICAL }
        title.addView(label(entry.title, 14f).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, weighted())
        val usage = entry.usage
        if (usage != null) title.addView(label(context.getString(R.string.text_explorer_storage_percent, usage.usedPercent), 11f).apply {
            setTextColor(accent)
            setPadding(dp(8), 0, 0, 0)
        })
        content.addView(title)
        if (usage != null) {
            content.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = usage.usedPercent
                progressTintList = ColorStateList.valueOf(accent)
                progressBackgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(secondary, 35))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, dp(3)).apply { topMargin = dp(3); bottomMargin = dp(3) })
        }
        content.addView(label(if (usage == null) context.getString(R.string.text_explorer_storage_unavailable) else context.getString(
            R.string.text_explorer_storage_usage,
            Formatter.formatFileSize(context, usage.usedBytes),
            Formatter.formatFileSize(context, usage.availableBytes),
        ), 11f).apply { setTextColor(secondary) })
        line.addView(content, weighted())
        line.contentDescription = listOf(entry.title, entry.path, (content.getChildAt(content.childCount - 1) as TextView).text).joinToString(", ")
        return line
    }

    private fun showExtensions() {
        val keys = context.resources.getStringArray(R.array.keys_file_extensions)
        val values = context.resources.getStringArray(R.array.values_file_extensions)
        MaterialDialog.Builder(context)
            .title(R.string.text_file_extensions_title)
            .items(*values)
            .itemsCallbackSingleChoice(extensionIndex()) { dialog, _, which, _ ->
                if (which in keys.indices) {
                    ExplorerKeys.FILE_EXTENSIONS.set(keys[which])
                    explorer.notifyDataSetChanged()
                    explorer.refreshFromPathMenu()
                }
                dialog.dismiss()
                dismiss()
                true
            }
            .choiceWidgetThemeColor()
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_confirm)
            .showAdaptive()
    }

    private fun extensionIndex(): Int {
        val keys = context.resources.getStringArray(R.array.keys_file_extensions)
        val value = ExplorerKeys.FILE_EXTENSIONS.storedKeyOrDefault()
        return keys.indexOf(value).takeIf { it >= 0 } ?: keys.indexOf(context.getString(R.string.default_key_file_extensions)).coerceAtLeast(0)
    }

    private fun extensionSummary() = context.getString(when (extensionIndex()) {
        0 -> R.string.text_explorer_extensions_hidden
        1 -> R.string.text_explorer_extensions_visible
        else -> R.string.text_explorer_extensions_except_scripts
    })

    private fun action(id: Int, title: Int, icon: Int, enabled: Boolean = true, onClick: () -> Unit): View = column().apply {
        this.id = id
        gravity = Gravity.CENTER
        minimumHeight = dp(48)
        setPadding(dp(3), dp(4), dp(3), dp(4))
        background = ripple()
        contentDescription = context.getString(title)
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.32f
        isFocusable = true
        setOnClickListener { dismiss(); onClick() }
        buttonRole(this)
        addView(ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(categoryColor)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(20), dp(20)))
        addView(label(title, 12f).apply {
            gravity = Gravity.CENTER
            // Let translated labels and larger system fonts grow the row instead of clipping.
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
    }

    private fun settingRow(title: Int, summary: String?, action: () -> Unit): LinearLayout = row().apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(48)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = ripple()
        isFocusable = true
        setOnClickListener { action() }
        buttonRole(this)
        addView(label(title, 14f), weighted())
        if (summary != null) addView(label(summary, 12f).apply {
            setTextColor(secondary)
            gravity = Gravity.END
            setPadding(dp(8), 0, dp(4), 0)
        }, weighted())
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_explorer_chevron)
            imageTintList = ColorStateList.valueOf(categoryColor)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(16), dp(16)))
    }

    private fun displaySection() = row().apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(section(R.string.text_explorer_display_options), weighted())
        addView(label(R.string.text_all_settings, 12f).apply {
            setTextColor(categoryColor)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            minWidth = dp(48)
            minHeight = dp(48)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = ripple()
            isFocusable = true
            setOnClickListener {
                dismiss()
                PreferencesActivity.launch(context)
            }
            buttonRole(this)
        }, weighted())
    }

    private fun section(title: Int) = label(title, 13f).apply {
        setTextColor(categoryColor)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        ViewCompat.setAccessibilityHeading(this, true)
    }

    private fun storageMessage(message: Int) = label(message, 12f).apply {
        setTextColor(secondary)
        gravity = Gravity.CENTER_VERTICAL
        minHeight = dp(56)
        setPadding(dp(12), dp(6), dp(12), dp(6))
    }

    private fun divider() = View(context).apply {
        val value = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.divider, value, true)) {
            if (value.resourceId != 0) setBackgroundResource(value.resourceId) else setBackgroundColor(value.data)
        } else {
            setBackgroundColor(ColorUtils.setAlphaComponent(foregroundColor, 32))
        }
        // File rows use a physical pixel separator; preserve that density here as well.
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun label(res: Int, size: Float) = label(context.getString(res), size)
    private fun label(value: String, size: Float) = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(foregroundColor)
    }
    private fun themeColor(attribute: Int, fallback: Int): Int {
        val values = context.obtainStyledAttributes(intArrayOf(attribute))
        return try {
            values.getColor(0, context.getColor(fallback))
        } finally {
            values.recycle()
        }
    }
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
    private fun actionRow() = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), 0, dp(4), 0) }
    private fun weighted() = LinearLayout.LayoutParams(0, -2, 1f)
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun ripple(color: Int = Color.TRANSPARENT) = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 35)), rounded(color, 0), rounded(Color.WHITE, 0))
    private fun dp(value: Int) = (context.resources.displayMetrics.density * value).roundToInt()
    private fun buttonRole(view: View) = ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = Button::class.java.name
        }
    })
}
