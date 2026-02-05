package com.kevinluo.autoglm.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.kevinluo.autoglm.R

/**
 * 语音波形显示视图（微信风格）
 *
 * 中间高两边低的对称波形，根据音量实时跳动
 */
class VoiceWaveformView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val barPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = context.getColor(R.color.primary)
        }

    private val barCount = 31 // 奇数，中间一个最高
    private val amplitudes = FloatArray(barCount) { 0.15f }
    private var currentAmplitude = 0f

    // 中间高两边低的权重分布（31个柱子）
    private val weights =
        floatArrayOf(
            0.10f,
            0.14f,
            0.18f,
            0.22f,
            0.26f,
            0.30f,
            0.35f,
            0.40f,
            0.45f,
            0.50f,
            0.56f,
            0.62f,
            0.68f,
            0.76f,
            0.85f,
            1.0f,
            0.85f,
            0.76f,
            0.68f,
            0.62f,
            0.56f,
            0.50f,
            0.45f,
            0.40f,
            0.35f,
            0.30f,
            0.26f,
            0.22f,
            0.18f,
            0.14f,
            0.10f,
        )

    private val barWidth: Float
        get() = width / (barCount * 3f)

    private val barSpacing: Float
        get() = barWidth * 2f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerY = height / 2f
        val maxBarHeight = height * 0.95f
        val totalWidth = barCount * barWidth + (barCount - 1) * barSpacing
        val startX = (width - totalWidth) / 2f

        for (i in 0 until barCount) {
            val amplitude = amplitudes[i]
            val barHeight = maxBarHeight * amplitude.coerceAtLeast(0.05f)

            val left = startX + i * (barWidth + barSpacing)
            val top = centerY - barHeight / 2
            val right = left + barWidth
            val bottom = centerY + barHeight / 2

            canvas.drawRoundRect(left, top, right, bottom, barWidth / 2, barWidth / 2, barPaint)
        }
    }

    /**
     * 从音频样本更新波形
     * @param samples 音频样本数据
     * @param readSize 有效样本数
     */
    fun updateFromSamples(samples: ShortArray, readSize: Int) {
        if (readSize <= 0) return

        // 计算音量 (RMS)
        var sum = 0.0
        for (i in 0 until readSize) {
            val sample = samples[i] / 32768.0
            sum += sample * sample
        }
        val rms = kotlin.math.sqrt(sum / readSize)

        // 转换为 0-1 范围，增大增益
        val amplitude = (rms * 6).coerceIn(0.1, 1.0).toFloat()

        // 平滑过渡，低音量时更平滑
        val smoothFactor = if (amplitude < 0.3f) 0.15f else 0.5f
        currentAmplitude = currentAmplitude * (1 - smoothFactor) + amplitude * smoothFactor

        // 根据权重分布更新各柱子高度
        for (i in 0 until barCount) {
            val baseHeight = currentAmplitude * weights[i]
            // 低音量时减少随机波动
            val randomRange = if (currentAmplitude < 0.3f) 0.1f else 0.4f
            val randomFactor = 1f - randomRange / 2 + (Math.random() * randomRange).toFloat()
            val targetHeight = (baseHeight * randomFactor).coerceIn(0.08f, 1f)
            // 柱子高度也做平滑
            amplitudes[i] = amplitudes[i] * 0.6f + targetHeight * 0.4f
        }

        invalidate()
    }

    /**
     * 重置波形
     */
    fun reset() {
        currentAmplitude = 0f
        for (i in 0 until barCount) {
            amplitudes[i] = 0.15f
        }
        invalidate()
    }

    /**
     * 设置波形颜色
     */
    fun setWaveformColor(color: Int) {
        barPaint.color = color
        invalidate()
    }
}
