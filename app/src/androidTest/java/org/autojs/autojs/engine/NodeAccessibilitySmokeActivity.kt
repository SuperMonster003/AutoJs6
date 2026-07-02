package org.autojs.autojs.engine

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NodeAccessibilitySmokeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = TextView(this).apply {
            text = TARGET_TEXT
            contentDescription = TARGET_DESC
            textSize = 20f
            gravity = Gravity.CENTER
        }
        val button = Button(this).apply {
            text = BUTTON_TEXT
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
            },
        )
    }

    companion object {
        const val TARGET_TEXT = "AutoJs6 Node accessibility smoke target"
        const val TARGET_DESC = "AutoJs6 Node accessibility smoke description"
        const val BUTTON_TEXT = "AutoJs6 Node accessibility smoke click"
        const val BUTTON_DESC = "AutoJs6 Node accessibility smoke button"
        const val CLICKED_TEXT = "AutoJs6 Node accessibility smoke clicked"
        const val LONG_CLICKED_TEXT = "AutoJs6 Node accessibility smoke long clicked"

        private val clickCount = AtomicInteger(0)
        private val longClickCount = AtomicInteger(0)

        @Volatile
        private var clickLatch = CountDownLatch(1)

        @Volatile
        private var longClickLatch = CountDownLatch(1)

        fun reset() {
            clickCount.set(0)
            longClickCount.set(0)
            clickLatch = CountDownLatch(1)
            longClickLatch = CountDownLatch(1)
        }

        fun awaitClick(timeoutMs: Long): Boolean {
            return clickLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        }

        fun awaitLongClick(timeoutMs: Long): Boolean {
            return longClickLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        }

        fun clickedCount(): Int = clickCount.get()

        fun longClickedCount(): Int = longClickCount.get()
    }
}
