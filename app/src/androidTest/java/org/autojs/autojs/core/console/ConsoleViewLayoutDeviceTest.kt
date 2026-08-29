package org.autojs.autojs.core.console

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.autojs.autojs6.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsoleViewLayoutDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun commandInputKeepsLogViewportVisible() {
        instrumentation.runOnMainSync {
            val width = 1080
            val height = 1920
            val view = ConsoleView(instrumentation.targetContext).apply {
                setCommandInputVisible(true)
                measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                )
                layout(0, 0, width, height)
            }
            val logList = view.findViewById<RecyclerView>(R.id.log_list)
            val inputContainer = view.findViewById<View>(R.id.input_container)

            assertTrue(
                "The command row must not consume the log viewport",
                logList.height >= height / 2,
            )
            assertTrue(
                "The command row must stay above the log viewport",
                inputContainer.bottom <= logList.top,
            )
        }
    }

    @Test
    fun bottomSheetCommandInputKeepsLogViewportVisible() {
        instrumentation.runOnMainSync {
            val width = 1080
            val height = 1200
            val themedContext = ContextThemeWrapper(
                instrumentation.targetContext,
                R.style.EditorTheme,
            )
            val bottomSheet = LayoutInflater.from(themedContext)
                .inflate(R.layout.bottom_sheet_log, null, false)
            val console = bottomSheet.findViewById<ConsoleView>(R.id.console).apply {
                setCommandInputVisible(true)
            }
            bottomSheet.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            bottomSheet.layout(0, 0, width, height)
            val logList = console.findViewById<RecyclerView>(R.id.log_list)
            val inputContainer = console.findViewById<View>(R.id.input_container)

            assertTrue(
                "The bottom sheet command row must not consume the log viewport",
                logList.height >= height / 2,
            )
            assertTrue(
                "The bottom sheet command row must stay above the log viewport",
                inputContainer.bottom <= logList.top,
            )
        }
    }
}
