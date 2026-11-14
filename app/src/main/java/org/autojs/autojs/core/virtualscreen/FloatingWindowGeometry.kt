package org.autojs.autojs.core.virtualscreen

import kotlin.math.roundToInt

/**
 * Pure geometry for the home-screen floating preview: keep the card narrow, derive its preview
 * height from the virtual display aspect ratio, and clamp it so a landscape or unusually tall
 * display cannot cover the whole UI. No Android types, so it is unit testable on the JVM.
 *
 * zh-CN: 首页悬浮预览的纯几何计算: 卡片宽度固定, 预览高度按虚拟屏宽高比推导并做上下限截断, 避免横屏或
 * 超高屏幕把整个界面盖住. 不含 Android 类型, 可在 JVM 单测.
 */
internal object FloatingWindowGeometry {

    const val CARD_WIDTH_DP = 148
    const val DEFAULT_PREVIEW_HEIGHT_DP = 220
    const val MIN_PREVIEW_HEIGHT_DP = 96
    const val MAX_PREVIEW_HEIGHT_DP = 260

    fun previewHeightDp(displayWidth: Int, displayHeight: Int, cardWidthDp: Int = CARD_WIDTH_DP): Int {
        if (displayWidth <= 0 || displayHeight <= 0 || cardWidthDp <= 0) return DEFAULT_PREVIEW_HEIGHT_DP
        return (cardWidthDp.toDouble() * displayHeight / displayWidth)
            .roundToInt()
            .coerceIn(MIN_PREVIEW_HEIGHT_DP, MAX_PREVIEW_HEIGHT_DP)
    }

}
