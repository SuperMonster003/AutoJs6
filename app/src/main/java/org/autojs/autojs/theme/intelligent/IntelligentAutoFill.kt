package org.autojs.autojs.theme.intelligent

import org.autojs.autojs.theme.color.ColorHarmony
import org.autojs.autojs.theme.color.ThemeColorDerivation

/**
 * Fills the empty states of a group from its first colored state (roadmap P4.4). Colored states are never changed.
 * - Day / night: the contrast pair of D24 (the nearest colors with at least 3:1 against each window background).
 * - Other sources with several states: a tonal scale of the seed, from light (first state) to dark (last state).
 * zh-CN: 由组中第一个有颜色的状态填充其余空状态 (Roadmap P4.4). 已有颜色的状态保持不变.
 * - 日 / 夜: D24 的对比度配对 (与各自窗口背景对比度不低于 3:1 的最近颜色).
 * - 其他多状态的状态源: 种子色的色阶, 由浅 (第一个状态) 到深 (最后一个状态).
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object IntelligentAutoFill {

    /**
     * The primary color of every state in [keys]; null when no state has a color.
     * zh-CN: [keys] 中每个状态的主色; 没有任何状态有颜色时返回 null.
     */
    fun fill(keys: List<String>, colors: Map<String, Int?>): Map<String, Int>? {
        val seed = keys.firstNotNullOfOrNull { colors[it] } ?: return null
        val generated: List<Int> = when {
            keys == listOf(StateKeys.DAY, StateKeys.NIGHT) -> ThemeColorDerivation.dayNightPair(seed).toList()
            keys.size == 1 -> listOf(seed)
            else -> ColorHarmony.tones(seed, keys.size)
        }
        return keys.withIndex().associate { (index, key) -> key to (colors[key] ?: generated[index]) }
    }

}
