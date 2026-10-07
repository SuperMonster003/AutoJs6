package org.autojs.autojs.theme.color

/**
 * Simulates color vision deficiencies with the matrices of Machado, Oliveira and Fernandes (2009) at full severity,
 * applied in linear RGB (roadmap P4.3). Grays map to themselves because every row sums to one.
 * zh-CN: 按 Machado, Oliveira 与 Fernandes (2009) 的完全程度矩阵模拟色觉缺陷, 在线性 RGB 中计算 (Roadmap P4.3).
 * 每行之和为 1, 因此灰色保持不变.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object ColorVisionSimulation {

    enum class Deficiency(val key: String, internal val matrix: DoubleArray) {
        PROTANOPIA(
            "protanopia", doubleArrayOf(
                0.152286, 1.052583, -0.204868,
                0.114503, 0.786281, 0.099216,
                -0.003882, -0.048116, 1.051998,
            )
        ),
        DEUTERANOPIA(
            "deuteranopia", doubleArrayOf(
                0.367322, 0.860646, -0.227968,
                0.280085, 0.672501, 0.047413,
                -0.011820, 0.042940, 0.968881,
            )
        ),
        TRITANOPIA(
            "tritanopia", doubleArrayOf(
                1.255528, -0.076749, -0.178779,
                -0.078411, 0.930809, 0.147602,
                0.004733, 0.691367, 0.303900,
            )
        );

        companion object {
            fun of(key: String?) = entries.firstOrNull { it.key == key }
        }
    }

    /** [argb] as seen with [deficiency]; alpha is kept. zh-CN: 具有 [deficiency] 时看到的 [argb]; 透明度保持不变. */
    fun simulate(argb: Int, deficiency: Deficiency): Int {
        val r = OkColor.srgbToLinear(((argb shr 16) and 0xFF) / 255.0)
        val g = OkColor.srgbToLinear(((argb shr 8) and 0xFF) / 255.0)
        val b = OkColor.srgbToLinear((argb and 0xFF) / 255.0)
        val m = deficiency.matrix
        fun channel(row: Int): Int {
            val linear = (m[row * 3] * r + m[row * 3 + 1] * g + m[row * 3 + 2] * b).coerceIn(0.0, 1.0)
            return Math.round(OkColor.linearToSrgb(linear) * 255.0).toInt().coerceIn(0, 255)
        }
        return (argb and (0xFF shl 24)) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
    }

}
