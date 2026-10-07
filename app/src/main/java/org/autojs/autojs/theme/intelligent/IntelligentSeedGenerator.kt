package org.autojs.autojs.theme.intelligent

import org.autojs.autojs.theme.color.ColorHarmony

/**
 * Builds an intelligent library from one seed color (roadmap P4.5): the scheme decides the group colors, and night
 * mode libraries fill their night colors like the auto fill of the group editor.
 * zh-CN: 由一个种子色构建智能库 (Roadmap P4.5): 方案决定各组颜色, 夜间模式库按组编辑器的自动填充规则补全夜间颜色.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object IntelligentSeedGenerator {

    sealed interface Scheme {

        val colorCount: Int

        data class Harmony(val type: ColorHarmony.Type) : Scheme {
            override val colorCount get() = type.offsets.size
        }

        /** Tones of the seed from light to dark. zh-CN: 种子色由浅到深的色阶. */
        data class Tones(override val colorCount: Int = DEFAULT_TONES) : Scheme

        companion object {
            const val DEFAULT_TONES = 5
        }

    }

    data class Result(val config: IntelligentConfig, val groups: List<IntelligentGroup>)

    /**
     * Groups get temporary negative ids, as new groups of the editor do. With night mode, groups rotate on state
     * changes, the editor's default for sources with several states.
     * zh-CN: 各组使用临时的负数 id, 与编辑器中新建的组相同. 使用夜间模式时, 组在状态变化时轮换, 这是编辑器对多状态状态源的默认设置.
     */
    /**
     * @param nameOf names a group from its position in the scheme: the hue offset in degrees for harmonies (0 for the
     * seed), null for tones.
     * zh-CN: 由组在方案中的位置为组命名: 和谐配色传入色相偏移角度 (种子色为 0), 色阶传入 null.
     */
    fun generate(
        seed: Int,
        scheme: Scheme,
        nightMode: Boolean,
        now: Long = System.currentTimeMillis(),
        nameOf: (index: Int, hueOffset: Int?) -> String? = { _, _ -> null },
    ): Result {
        val colors: List<Pair<Int, Int?>> = when (scheme) {
            is Scheme.Harmony -> ColorHarmony.harmony(seed, scheme.type).zip(scheme.type.offsets.map { it.toInt() })
            is Scheme.Tones -> ColorHarmony.tones(seed, scheme.colorCount).map { it to null }
        }.distinctBy { it.first }
        val source = if (nightMode) StateSource.NightMode else StateSource.None
        val keys = source.stateKeys
        val groups = colors.mapIndexed { index, (color, hueOffset) ->
            val states = IntelligentAutoFill.fill(keys, mapOf(keys.first() to color))!!.mapValues { StateColors(it.value) }
            IntelligentGroup(id = -(index + 1L), name = nameOf(index, hueOffset), position = index, createdTime = now, modifiedTime = now, states = states)
        }
        val rotation = if (nightMode) listOf(RotationTrigger.StateChange) else emptyList()
        return Result(IntelligentConfig(stateSource = source, rotation = rotation), groups)
    }

}
