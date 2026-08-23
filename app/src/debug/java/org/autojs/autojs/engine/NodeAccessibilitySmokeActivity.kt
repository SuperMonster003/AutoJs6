package org.autojs.autojs.engine

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import org.autojs.autojs6.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Debug-only controlled surface for real accessibility gesture acceptance tests. */
class NodeAccessibilitySmokeActivity : Activity() {

    private lateinit var button: Button
    private lateinit var selectorInput: EditText
    private lateinit var swipeTarget: TextView
    private var swipeDownY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = TextView(this).apply {
            text = TARGET_TEXT
            contentDescription = TARGET_DESC
            textSize = 20f
            gravity = Gravity.CENTER
        }
        button = Button(this).apply {
            id = R.id.python_selector_button
            text = BUTTON_TEXT
            isAllCaps = false
            contentDescription = BUTTON_DESC
            setOnClickListener {
                clickCount.incrementAndGet()
                status.text = CLICKED_TEXT
                clickLatch.countDown()
            }
            setOnLongClickListener {
                longClickCount.incrementAndGet()
                status.text = LONG_CLICKED_TEXT
                longClickLatch.countDown()
                true
            }
        }
        selectorInput = EditText(this).apply {
            id = R.id.python_selector_input
            setText(SELECTOR_INPUT_INITIAL_TEXT)
            contentDescription = SELECTOR_INPUT_DESC
            isSingleLine = true
            doAfterTextChanged { editable ->
                if (editable?.toString() == SELECTOR_INPUT_UPDATED_TEXT) {
                    inputTextLatch.countDown()
                }
            }
        }
        swipeTarget = TextView(this).apply {
            text = SWIPE_TEXT
            contentDescription = SWIPE_DESC
            gravity = Gravity.CENTER
            setBackgroundColor(Color.LTGRAY)
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> swipeDownY = event.rawY
                    MotionEvent.ACTION_UP -> {
                        if (abs(event.rawY - swipeDownY) >= SWIPE_MIN_DISTANCE_PX) {
                            swipeCount.incrementAndGet()
                            swipeLatch.countDown()
                        }
                    }
                }
                true
            }
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(
                    status,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    button,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    selectorInput,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    swipeTarget,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (SWIPE_TARGET_HEIGHT_DP * resources.displayMetrics.density).toInt(),
                    ),
                )
            },
        )
    }

    fun buttonCenterOnScreen(): IntArray = centerOnScreen(button)

    fun swipeCoordinatesOnScreen(): Array<IntArray> {
        val location = IntArray(2)
        swipeTarget.getLocationOnScreen(location)
        val x = location[0] + swipeTarget.width / 2
        return arrayOf(
            intArrayOf(x, location[1] + swipeTarget.height * 3 / 4),
            intArrayOf(x, location[1] + swipeTarget.height / 4),
        )
    }

    private fun centerOnScreen(view: android.view.View): IntArray {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return intArrayOf(location[0] + view.width / 2, location[1] + view.height / 2)
    }

    companion object {
        const val TARGET_TEXT = "AutoJs6 Node accessibility smoke target"
        const val TARGET_DESC = "AutoJs6 Node accessibility smoke description"
        const val BUTTON_TEXT = "AutoJs6 Node accessibility smoke click"
        const val BUTTON_DESC = "AutoJs6 Node accessibility smoke button"
        const val SELECTOR_INPUT_DESC = "AutoJs6 Python selector smoke input"
        const val SELECTOR_INPUT_INITIAL_TEXT = "selector seed"
        const val SELECTOR_INPUT_UPDATED_TEXT = "selector updated"
        const val CLICKED_TEXT = "AutoJs6 Node accessibility smoke clicked"
        const val LONG_CLICKED_TEXT = "AutoJs6 Node accessibility smoke long clicked"
        const val SWIPE_TEXT = "AutoJs6 accessibility smoke swipe"
        const val SWIPE_DESC = "AutoJs6 accessibility smoke swipe target"
        private const val SWIPE_TARGET_HEIGHT_DP = 240
        private const val SWIPE_MIN_DISTANCE_PX = 40f

        private val clickCount = AtomicInteger(0)
        private val longClickCount = AtomicInteger(0)
        private val swipeCount = AtomicInteger(0)

        @Volatile
        private var clickLatch = CountDownLatch(1)

        @Volatile
        private var longClickLatch = CountDownLatch(1)

        @Volatile
        private var swipeLatch = CountDownLatch(1)

        @Volatile
        private var inputTextLatch = CountDownLatch(1)

        fun reset() {
            clickCount.set(0)
            longClickCount.set(0)
            swipeCount.set(0)
            clickLatch = CountDownLatch(1)
            longClickLatch = CountDownLatch(1)
            swipeLatch = CountDownLatch(1)
            inputTextLatch = CountDownLatch(1)
        }

        fun awaitClick(timeoutMs: Long): Boolean {
            return clickLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        }

        fun awaitLongClick(timeoutMs: Long): Boolean {
            return longClickLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        }

        fun awaitSwipe(timeoutMs: Long): Boolean {
            return swipeLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        }

        fun awaitInputText(timeoutMs: Long): Boolean {
            return inputTextLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        }

        fun clickedCount(): Int = clickCount.get()

        fun longClickedCount(): Int = longClickCount.get()

        fun swipedCount(): Int = swipeCount.get()
    }
}
