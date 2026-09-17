package org.autojs.autojs.ui.terminal

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import jackpal.androidterm.emulatorview.EmulatorView
import jackpal.androidterm.emulatorview.TermSession
import org.autojs.autojs.core.terminal.TerminalEnvironment
import org.autojs.autojs.core.terminal.TerminalPreferences

/**
 * [EmulatorView] with pinch-to-zoom text size (persisted) and the host's terminal defaults.
 * zh-CN: 支持捏合缩放字号 (持久化) 并应用宿主终端默认设置的 [EmulatorView].
 */
class TerminalEmulatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : EmulatorView(context, attrs) {

    var textSizeSp: Int = TerminalPreferences.textSizeSp
        set(value) {
            val clamped = value.coerceIn(TerminalPreferences.MIN_TEXT_SIZE_SP, TerminalPreferences.MAX_TEXT_SIZE_SP)
            if (field == clamped) return
            field = clamped
            if (termSession != null) {
                setTextSize(clamped)
            }
            TerminalPreferences.textSizeSp = clamped
            onTextSizeChanged?.invoke(clamped)
        }

    var onTextSizeChanged: ((Int) -> Unit)? = null
    var onSelectionRequested: ((Float, Float) -> Unit)? = null

    val transcriptTopLine: Int get() = computeVerticalScrollOffset()

    private var scaleAccumulator = 1f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            scaleAccumulator = 1f
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleAccumulator *= detector.scaleFactor
            when {
                scaleAccumulator >= ZOOM_STEP -> {
                    textSizeSp += 1
                    scaleAccumulator = 1f
                }
                scaleAccumulator <= 1f / ZOOM_STEP -> {
                    textSizeSp -= 1
                    scaleAccumulator = 1f
                }
            }
            return true
        }
    })

    init {
        setDensity(resources.displayMetrics)
        setUseCookedIME(false)
    }

    override fun attachSession(session: TermSession) {
        super.attachSession(session)
        // The key listener that owns the term type and the text paints only exist once a session is attached.
        // zh-CN: 持有终端类型的按键监听器与文本画笔在会话附加后才存在.
        setTermType(TerminalEnvironment.DEFAULT_TERM)
        setTextSize(textSizeSp)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) {
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onLongPress(event: MotionEvent) {
        onSelectionRequested?.invoke(event.x, event.y)
    }

    companion object {
        private const val ZOOM_STEP = 1.12f
    }

}
