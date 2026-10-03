package org.autojs.autojs.ui.floating.codegen

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.autojs.autojs6.R
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray

/** Debug-only controls with observable receipts for generated-code device tests.
 * zh-CN: 仅用于生成代码设备测试的调试控件, 以计数与实际值提供动作回执. */
class CodeGenSmokeActivity : Activity() {
    lateinit var button: Button
    lateinit var input: EditText
    lateinit var scroll: ScrollView
    val clicks = AtomicInteger()
    val longClicks = AtomicInteger()
    val itemClicks = AtomicIntegerArray(20)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            @Suppress("DEPRECATION")
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        button = Button(this).apply {
            text = "Codegen click"
            contentDescription = "codegen-click"
            isAllCaps = false
            setOnClickListener { clicks.incrementAndGet() }
            setOnLongClickListener { longClicks.incrementAndGet(); true }
        }
        input = EditText(this).apply {
            contentDescription = "codegen-input"
            setText("seed")
            isSingleLine = true
        }
        root.addView(button, LinearLayout.LayoutParams(-1, dp(40)))
        root.addView(input, LinearLayout.LayoutParams(-1, dp(40)))
        root.addView(TextView(this).apply { id = android.R.id.title; text = "Shared title" }, LinearLayout.LayoutParams(-1, dp(20)))
        root.addView(TextView(this).apply { id = R.id.title; text = "Shared title" }, LinearLayout.LayoutParams(-1, dp(20)))
        val items = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; id = R.id.codegen_fixture_list }
        repeat(20) { index ->
            items.addView(Button(this).apply {
                id = R.id.codegen_fixture_delete
                text = "删除"
                isAllCaps = false
                textSize = 10f
                setPadding(0, 0, 0, 0)
                minimumHeight = 0
                setOnClickListener { itemClicks.incrementAndGet(index) }
            }, LinearLayout.LayoutParams(-1, dp(20)))
        }
        root.addView(items)
        scroll = ScrollView(this).apply {
            contentDescription = "codegen-scroll"
            addView(LinearLayout(this@CodeGenSmokeActivity).apply {
                orientation = LinearLayout.VERTICAL
                repeat(40) { index ->
                    addView(TextView(this@CodeGenSmokeActivity).apply { text = "Scroll row " + index }, LinearLayout.LayoutParams(-1, dp(28)))
                }
            })
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(72)))
        root.isFocusableInTouchMode = true
        root.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(root)
    }

    fun resetReceipts() {
        clicks.set(0)
        longClicks.set(0)
        repeat(20) { itemClicks.set(it, 0) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
