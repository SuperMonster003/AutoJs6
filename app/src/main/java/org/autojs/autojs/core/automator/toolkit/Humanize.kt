package org.autojs.autojs.core.automator.toolkit

import kotlin.random.Random

/**
 * The "look human" options shared by the tools and (through `flow.defaults`) the Flow action
 * steps (roadmap P4.1): a random offset added to the tap point of a gesture click, uniform in
 * `[-offsetX, offsetX] x [-offsetY, offsetY]` pixels, and a random pause before every action,
 * uniform in `[minDelay, maxDelay]` milliseconds. [NONE] changes nothing.
 * zh-CN: 工具与 (经 `flow.defaults`) Flow 动作步骤共用的 "拟人" 选项 (Roadmap P4.1): 手势点击的点按坐标加一个随机偏移, 均匀分布于
 * `[-offsetX, offsetX] x [-offsetY, offsetY]` 像素; 每个动作前随机暂停, 均匀分布于 `[minDelay, maxDelay]` 毫秒. [NONE] 不改变任何行为.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
data class Humanize(
    val offsetX: Int = 0,
    val offsetY: Int = offsetX,
    val minDelay: Long = 0,
    val maxDelay: Long = minDelay,
) {

    init {
        require(offsetX >= 0) { "offsetX ($offsetX) cannot be negative" }
        require(offsetY >= 0) { "offsetY ($offsetY) cannot be negative" }
        require(minDelay >= 0) { "minDelay ($minDelay) cannot be negative" }
        require(maxDelay >= minDelay) { "maxDelay ($maxDelay) must be at least minDelay ($minDelay)" }
    }

    val isNoop: Boolean get() = offsetX == 0 && offsetY == 0 && maxDelay == 0L

    /** A random horizontal offset in pixels. zh-CN: 随机的水平偏移 (像素). */
    fun offsetX(random: Random): Int = if (offsetX == 0) 0 else random.nextInt(-offsetX, offsetX + 1)

    /** A random vertical offset in pixels. zh-CN: 随机的垂直偏移 (像素). */
    fun offsetY(random: Random): Int = if (offsetY == 0) 0 else random.nextInt(-offsetY, offsetY + 1)

    /** A random pause in milliseconds, 0 when none is configured. zh-CN: 随机的暂停时长 (毫秒), 未配置时为 0. */
    fun delay(random: Random): Long = if (maxDelay == 0L) 0L else random.nextLong(minDelay, maxDelay + 1)

    companion object {

        val NONE = Humanize()

    }

}
