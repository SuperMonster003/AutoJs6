package org.autojs.autojs.runtime.api.augment.images

import org.autojs.autojs.util.StringUtils
import org.opencv.core.Point

// @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19. 2023.
/**
 * Created by SuperMonster003 on Aug 9, 2024.
 */
class ObjectFrame(
    @JvmField val topLeft: Point,
    @JvmField val topRight: Point,
    @JvmField val bottomLeft: Point,
    @JvmField val bottomRight: Point,
) {
    @JvmField
    val centerX = (topLeft.x + topRight.x + bottomLeft.x + bottomRight.x) / 4

    @JvmField
    val centerY = (topLeft.y + topRight.y + bottomLeft.y + bottomRight.y) / 4

    @JvmField
    val center = Point(centerX, centerY)

    override fun toString(): String {
        return "[${ObjectFrame::class.java.simpleName}] ${summary()}"
    }

    fun summary(): String = listOf(
        "topLeft" to { topLeft },
        "topRight" to { topRight },
        "bottomLeft" to { bottomLeft },
        "bottomRight" to { bottomRight },
        "center" to { center },
    ).let { StringUtils.toFormattedSummary(it) }

}