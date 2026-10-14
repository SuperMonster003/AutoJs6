package org.autojs.autojs.ui.doc

import org.autojs.autojs.theme.color.ColorMetrics
import org.autojs.autojs.theme.color.ThemeColorDerivation
import kotlin.math.roundToInt

/**
 * Colors of [OfflineDocsUnavailableView], derived from a surface palette and the theme accent color.
 * zh-CN: [OfflineDocsUnavailableView] 的配色, 由界面背景 / 前景色与主题强调色推导得到.
 *
 * The theme color can be any color, and the editor supplies its own surface palette, so no fixed resource color
 * works everywhere. Every text color meets [TEXT_CONTRAST] against what it is drawn on, and the decorative icon
 * is a faded accent that still meets [ICON_CONTRAST] against the surface.
 * zh-CN: 主题色可为任意颜色, 编辑器也会提供自己的界面配色, 因此固定的资源颜色无法适配所有情况.
 * 所有文字颜色与其底色的对比度均不低于 [TEXT_CONTRAST]; 装饰图标为淡化的强调色, 与背景的对比度仍不低于 [ICON_CONTRAST].
 *
 * All colors are opaque. zh-CN: 所有颜色均不透明.
 */
data class OfflineDocsUnavailablePalette(
    val background: Int,
    val title: Int,
    val body: Int,
    val icon: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondaryContent: Int,
) {

    companion object {

        /** WCAG AA minimum for normal text. zh-CN: WCAG AA 对普通文字的最低对比度. */
        const val TEXT_CONTRAST = 4.5

        /** WCAG 1.4.11 minimum for graphical objects. zh-CN: WCAG 1.4.11 对图形对象的最低对比度. */
        const val ICON_CONTRAST = ThemeColorDerivation.UI_COMPONENT_CONTRAST

        /**
         * Minimum contrast between the filled primary action and a light surface, the same as the floating action button.
         * zh-CN: 填充型主要操作与浅色背景的最低对比度, 与悬浮按钮一致.
         */
        const val PRIMARY_CONTAINER_CONTRAST_ON_LIGHT = 1.15

        /**
         * Minimum contrast between the filled primary action and a dark surface. Dark surfaces separate layers by
         * lightness, so a dark theme color (such as md_blue_gray_900) needs more lift than a pale one needs on a light surface.
         * zh-CN: 填充型主要操作与深色背景的最低对比度. 深色界面依靠亮度区分层次, 因此深色主题色 (如 md_blue_gray_900)
         * 在深色背景上需要比浅色主题色在浅色背景上更多的提亮.
         */
        const val PRIMARY_CONTAINER_CONTRAST_ON_DARK = 1.6

        private const val BODY_WEIGHT = 0.72

        /** Luminance threshold of `ColorUtils.isLuminanceLight`. zh-CN: 与 `ColorUtils.isLuminanceLight` 的亮度阈值一致. */
        private const val LIGHT_LUMINANCE_THRESHOLD = 0.179

        private const val OPAQUE = 0xFF shl 24
        private val DARK_CONTENT = 0xFF212121.toInt()
        private val LIGHT_CONTENT = 0xFFFFFFFF.toInt()

        /**
         * @param background The surface color; alpha is ignored. zh-CN: 界面背景色, 忽略 alpha.
         * @param foreground The primary text color, possibly translucent. zh-CN: 主要文字颜色, 可为半透明.
         * @param accent The theme accent color; alpha is ignored. zh-CN: 主题强调色, 忽略 alpha.
         */
        @JvmStatic
        fun derive(background: Int, foreground: Int, accent: Int): OfflineDocsUnavailablePalette {
            val surface = background or OPAQUE
            val reference = accent or OPAQUE
            val title = ThemeColorDerivation.ensureContrast(composite(foreground, surface), surface, TEXT_CONTRAST)
            val body = ThemeColorDerivation.ensureContrast(blend(title, surface, BODY_WEIGHT), surface, TEXT_CONTRAST)

            val secondaryContent = ThemeColorDerivation.ensureContrast(reference, surface, TEXT_CONTRAST)
            val icon = fadeTowardContrast(secondaryContent, surface, ICON_CONTRAST)

            val primaryContainerContrast = when {
                ColorMetrics.relativeLuminance(surface) < LIGHT_LUMINANCE_THRESHOLD -> PRIMARY_CONTAINER_CONTRAST_ON_DARK
                else -> PRIMARY_CONTAINER_CONTRAST_ON_LIGHT
            }
            val primaryCandidate = ThemeColorDerivation.ensureContrast(reference, surface, primaryContainerContrast)
            val onPrimaryContainer = listOf(DARK_CONTENT, LIGHT_CONTENT).maxBy { ColorMetrics.contrastRatio(it, primaryCandidate) }
            val primaryContainer = ThemeColorDerivation.ensureContrast(primaryCandidate, onPrimaryContainer, TEXT_CONTRAST)

            return OfflineDocsUnavailablePalette(
                background = surface,
                title = title,
                body = body,
                icon = icon,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondaryContent = secondaryContent,
            )
        }

        /**
         * The mix of [color] over [background] that is as faint as possible while still meeting [ratio];
         * [color] itself when it does not meet [ratio].
         * zh-CN: [color] 叠加在 [background] 上, 在仍满足 [ratio] 的前提下尽量淡的混合色; [color] 本身不满足 [ratio] 时直接返回 [color].
         */
        private fun fadeTowardContrast(color: Int, background: Int, ratio: Double): Int {
            if (ColorMetrics.contrastRatio(color, background) < ratio) return color
            var failing = 0.0
            var passing = 1.0
            repeat(20) {
                val middle = (failing + passing) / 2
                if (ColorMetrics.contrastRatio(blend(color, background, middle), background) >= ratio) passing = middle else failing = middle
            }
            return blend(color, background, passing)
        }

        private fun composite(color: Int, background: Int): Int = blend(color, background, ((color ushr 24) and 0xFF) / 255.0)

        private fun blend(color: Int, background: Int, weight: Double): Int {
            fun channel(shift: Int): Int {
                val top = (color shr shift) and 0xFF
                val bottom = (background shr shift) and 0xFF
                return (top * weight + bottom * (1 - weight)).roundToInt().coerceIn(0, 0xFF) shl shift
            }
            return OPAQUE or channel(16) or channel(8) or channel(0)
        }

    }

}
