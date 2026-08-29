package org.autojs.autojs.core.console

import android.graphics.Color
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import java.util.concurrent.atomic.AtomicReference
import org.autojs.autojs6.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsoleViewStackFrameLinkDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun submitsOneLineCommandAndClearsTheInput() {
        val submitted = AtomicReference<String>()
        instrumentation.runOnMainSync {
            val view = ConsoleView(instrumentation.targetContext).apply {
                setCommandInputVisible(true)
                setOnCommandSubmitListener(submitted::set)
            }
            val input = view.findViewById<EditText>(R.id.input)
            input.setText("  const answer: number = 42; answer  ")

            view.findViewById<View>(R.id.submit).performClick()

            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.input_container).visibility)
            assertEquals("", input.text.toString())
        }

        assertEquals("const answer: number = 42; answer", submitted.get())
    }

    @Test
    fun dispatchCompilationDiagnosticLinkWithEditorCoordinates() {
        val consoleView = AtomicReference<ConsoleView>()
        val clickableContent = AtomicReference<CharSequence>()
        val clickedTarget = AtomicReference<ClickedTarget>()
        instrumentation.runOnMainSync {
            val view = ConsoleView(instrumentation.targetContext).apply {
                setEnableStackFrameLinks(true)
                setOnStackFrameClickListener { sourceName, lineIndex, columnNumber ->
                    clickedTarget.set(ClickedTarget(sourceName, lineIndex, columnNumber))
                }
            }
            consoleView.set(view)
            clickableContent.set(
                view.createClickableContent(
                    "TypeScript compilation failed\n" +
                        "my script.ts:3:7 - error TS2322: Type mismatch",
                    Color.RED,
                ),
            )
        }

        val content = clickableContent.get()
        assertTrue(content is Spanned)
        content as Spanned
        val spans = content.getSpans(0, content.length, ClickableSpan::class.java)
        assertEquals(1, spans.size)

        instrumentation.runOnMainSync {
            spans.single().onClick(consoleView.get())
        }

        assertEquals(ClickedTarget("my script.ts", 2, 6), clickedTarget.get())
    }

    private data class ClickedTarget(
        val sourceName: String,
        val lineIndex: Int,
        val columnNumber: Int,
    )
}
