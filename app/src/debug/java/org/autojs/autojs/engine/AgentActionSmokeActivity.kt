package org.autojs.autojs.engine

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.widget.*
import java.util.concurrent.atomic.AtomicInteger

/** Debug-only surface for isolated Agent action tests. */
class AgentActionSmokeActivity : Activity() {
    lateinit var button: Button
    lateinit var input: EditText
    lateinit var password: EditText
    lateinit var surface: TextView
    lateinit var label: TextView
    val clicks = AtomicInteger()
    val longClicks = AtomicInteger()
    val touches = AtomicInteger()
    val parentClicks = AtomicInteger()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Recent targets draw edge-to-edge; keep fixture controls out of system bars.
            @Suppress("DEPRECATION")
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        button = Button(this).apply {
            text = "Continue"; contentDescription = "agent_click"; isAllCaps = false
            setOnClickListener { clicks.incrementAndGet() }
            setOnLongClickListener { longClicks.incrementAndGet(); true }
        }
        input = EditText(this).apply { contentDescription = "agent_input"; setText("seed"); isSingleLine = true }
        password = EditText(this).apply { contentDescription = "agent_password"; isSingleLine = true; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        label = TextView(this).apply { text = "Continue"; contentDescription = "agent_label"; importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES }
        val parent = LinearLayout(this).apply { contentDescription = "agent_parent"; addView(label); setOnClickListener { parentClicks.incrementAndGet() } }
        val scroll = ScrollView(this).apply {
            contentDescription = "agent_scroll"
            addView(LinearLayout(this@AgentActionSmokeActivity).apply {
                orientation = LinearLayout.VERTICAL
                repeat(40) { addView(TextView(this@AgentActionSmokeActivity).apply { text = "Row $it"; textSize = 20f }) }
            })
        }
        surface = TextView(this).apply {
            text = "Gesture area"; contentDescription = "agent_gesture"
            setOnTouchListener { _, event -> if (event.action == MotionEvent.ACTION_UP) touches.incrementAndGet(); true }
        }
        layout.addView(button); layout.addView(parent); layout.addView(input); layout.addView(password)
        layout.addView(scroll, LinearLayout.LayoutParams(-1, (140 * resources.displayMetrics.density).toInt()))
        layout.addView(surface, LinearLayout.LayoutParams(-1, (100 * resources.displayMetrics.density).toInt()))
        // Avoid an initial input focus/keyboard window changing the fixture before the first action.
        layout.isFocusableInTouchMode = true; layout.requestFocus()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(layout)
    }
    fun center(view: android.view.View): IntArray = IntArray(2).also { view.getLocationOnScreen(it); it[0] += view.width / 2; it[1] += view.height / 2 }
}
