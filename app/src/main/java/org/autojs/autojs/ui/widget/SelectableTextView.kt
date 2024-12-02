package org.autojs.autojs.ui.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.abs

class SelectableTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val longPressTimeout = 704L  // 自定义长按超时时间
    private var isLongPress = false
    private val longPressHandler = Handler(Looper.getMainLooper())

    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        setTextIsSelectable(true)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isLongPress = true
                longPressHandler.postDelayed({
                    if (isLongPress) {
                        super.performLongClick()
                    }
                }, longPressTimeout)
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                    isLongPress = false
                    longPressHandler.removeCallbacksAndMessages(null)
                }
            }
            MotionEvent.ACTION_UP -> {
                isLongPress = false
                longPressHandler.removeCallbacksAndMessages(null)
            }
            MotionEvent.ACTION_CANCEL -> {
                isLongPress = false
                longPressHandler.removeCallbacksAndMessages(null)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performLongClick(): Boolean {
        // 仅在上面的检查通过后触发长按事件
        return true
    }

}