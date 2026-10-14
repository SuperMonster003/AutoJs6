package org.autojs.autojs.ui.doc

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.ImageViewCompat
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorMutable
import org.autojs.autojs.ui.edit.theme.EditorDialogViewThemeApplier
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs6.R

/**
 * Page shown in place of the documentation WebView when the Offline Docs plugin is selected but unavailable.
 * zh-CN: 已选择离线文档但 Offline Docs 插件不可用时, 代替文档 WebView 显示的页面.
 *
 * Colors follow the current theme color and, when [setPalette] is called, the surface palette of the host
 * (such as the code editor) instead of the app day/night colors. See [OfflineDocsUnavailablePalette].
 * zh-CN: 配色跟随当前主题色; 调用 [setPalette] 后改用宿主 (如代码编辑器) 的界面配色, 而非应用的日夜间颜色.
 * 参阅 [OfflineDocsUnavailablePalette].
 */
class OfflineDocsUnavailableView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), ThemeColorMutable {

    @ColorInt
    private var paletteBackground: Int? = null

    @ColorInt
    private var paletteForeground: Int? = null

    private var themeColor: ThemeColor? = null

    private lateinit var icon: ImageView
    private lateinit var title: TextView
    private lateinit var prompt: TextView
    private lateinit var primaryAction: TextView
    private lateinit var secondaryAction: TextView

    override fun onFinishInflate() {
        super.onFinishInflate()
        icon = findViewById(R.id.offline_docs_unavailable_icon)
        title = findViewById(R.id.offline_docs_unavailable_title)
        prompt = findViewById(R.id.offline_docs_unavailable_prompt)
        primaryAction = findViewById(R.id.offline_docs_plugin_center)
        secondaryAction = findViewById(R.id.offline_docs_switch_online)
        listOf(primaryAction, secondaryAction).forEach {
            ViewCompat.setAccessibilityDelegate(it, ButtonRoleDelegate)
        }

        // Editor dialogs recolor every TextView and Button in their tree with the editor palette.
        // This page derives its own colors from that palette (see setPalette), so it opts out.
        // zh-CN: 编辑器对话框会用编辑器配色重新着色其中所有 TextView 与 Button.
        // 本页面已基于该配色自行推导颜色 (见 setPalette), 因此退出该机制.
        EditorDialogViewThemeApplier.setSubtreeExcluded(this, true)

        if (isInEditMode) applyColors() else ThemeColorManager.add(this)
    }

    override fun setThemeColor(color: ThemeColor?) {
        themeColor = color
        applyColors()
    }

    /**
     * Uses [background] and [foreground] of the host instead of the app day/night colors.
     * zh-CN: 使用宿主的 [background] 与 [foreground], 而非应用的日夜间颜色.
     */
    fun setPalette(@ColorInt background: Int, @ColorInt foreground: Int) {
        paletteBackground = background
        paletteForeground = foreground
        applyColors()
    }

    private fun applyColors() {
        if (!::icon.isInitialized) return
        val palette = OfflineDocsUnavailablePalette.derive(
            background = paletteBackground ?: context.getColor(R.color.window_background),
            foreground = paletteForeground ?: context.getColor(R.color.text_color_primary),
            accent = themeColor?.colorAccent ?: context.getColor(R.color.colorPrimary),
        )
        val cornerRadius = resources.getDimension(R.dimen.offline_docs_unavailable_action_corner_radius)
        val strokeWidth = resources.getDimensionPixelSize(R.dimen.offline_docs_unavailable_action_stroke_width)

        setBackgroundColor(palette.background)

        icon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.iconContainer)
        }
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(palette.icon))

        title.setTextColor(palette.title)
        prompt.setTextColor(palette.body)

        primaryAction.setTextColor(palette.onPrimaryContainer)
        primaryAction.background = actionBackground(
            content = roundedRect(cornerRadius) { setColor(palette.primaryContainer) },
            cornerRadius = cornerRadius,
            ripple = ColorUtils.applyAlpha(palette.onPrimaryContainer, PRIMARY_RIPPLE_ALPHA),
        )

        secondaryAction.setTextColor(palette.secondaryContent)
        secondaryAction.background = actionBackground(
            content = roundedRect(cornerRadius) {
                setColor(Color.TRANSPARENT)
                setStroke(strokeWidth, palette.secondaryOutline)
            },
            cornerRadius = cornerRadius,
            ripple = ColorUtils.applyAlpha(palette.secondaryContent, SECONDARY_RIPPLE_ALPHA),
        )
    }

    private fun roundedRect(cornerRadius: Float, block: GradientDrawable.() -> Unit) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        this.cornerRadius = cornerRadius
        block()
    }

    private fun actionBackground(content: Drawable, cornerRadius: Float, @ColorInt ripple: Int) = RippleDrawable(
        ColorStateList.valueOf(ripple),
        content,
        roundedRect(cornerRadius) { setColor(Color.WHITE) },
    )

    private object ButtonRoleDelegate : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = Button::class.java.name
        }
    }

    private companion object {
        const val PRIMARY_RIPPLE_ALPHA = 0.16
        const val SECONDARY_RIPPLE_ALPHA = 0.12
    }

}
