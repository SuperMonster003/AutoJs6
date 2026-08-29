package org.autojs.autojs.core.console

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.autojs.autojs6.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsoleViewLayoutDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun commandInputIsHiddenByDefault() {
        instrumentation.runOnMainSync {
            val view = ConsoleView(instrumentation.targetContext)

            assertEquals(
                View.GONE,
                view.findViewById<View>(R.id.input_container).visibility,
            )
        }
    }

    @Test
    fun commandActionSwitchesBetweenRunAndStop() {
        instrumentation.runOnMainSync {
            var stopCount = 0
            val context = instrumentation.targetContext
            val view = ConsoleView(context).apply {
                setOnCommandSubmitListener { }
                setOnCommandStopListener { stopCount++ }
            }
            val input = view.findViewById<EditText>(R.id.input)
            val action = view.findViewById<ImageButton>(R.id.submit)

            assertTrue(input.isEnabled)
            assertTrue(action.isEnabled)
            assertEquals(context.getString(R.string.text_run), action.contentDescription)
            assertEquals(
                context.getColor(R.color.dialog_button_finish),
                requireNotNull(action.imageTintList).defaultColor,
            )

            view.setCommandInputBusy(true)

            assertFalse(input.isEnabled)
            assertTrue(action.isEnabled)
            assertEquals(context.getString(R.string.text_stop), action.contentDescription)
            assertEquals(
                context.getColor(R.color.dialog_button_caution),
                requireNotNull(action.imageTintList).defaultColor,
            )
            action.performClick()
            assertEquals(1, stopCount)

            view.setCommandInputBusy(false)

            assertTrue(input.isEnabled)
            assertEquals(context.getString(R.string.text_run), action.contentDescription)
        }
    }

    @Test
    fun typeScriptConsoleIsLastAndHiddenInLogMenusByDefault() {
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(
                instrumentation.targetContext,
                R.style.EditorTheme,
            )
            val anchor = View(context)
            val fullLogMenu = PopupMenu(context, anchor).apply {
                menuInflater.inflate(R.menu.menu_console, menu)
            }.menu
            val bottomSheetMenu = PopupMenu(context, anchor).apply {
                menuInflater.inflate(R.menu.menu_log_bottom_sheet_more, menu)
            }.menu

            assertEquals(
                R.id.action_typescript_console,
                fullLogMenu.getItem(fullLogMenu.size() - 1).itemId,
            )
            assertFalse(fullLogMenu.findItem(R.id.action_typescript_console).isVisible)
            assertEquals(
                listOf(
                    R.id.action_copy,
                    R.id.action_send,
                    R.id.action_export,
                    R.id.action_typescript_console,
                ),
                (0 until bottomSheetMenu.size()).map { index ->
                    bottomSheetMenu.getItem(index).itemId
                },
            )
            assertFalse(bottomSheetMenu.findItem(R.id.action_typescript_console).isVisible)
        }
    }

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
            val closeButton = bottomSheet.findViewById<View>(R.id.btn_close)
            val moreButton = bottomSheet.findViewById<View>(R.id.btn_more)

            assertTrue(
                "The bottom sheet command row must not consume the log viewport",
                logList.height >= height / 2,
            )
            assertTrue(
                "The bottom sheet command row must stay above the log viewport",
                inputContainer.bottom <= logList.top,
            )
            assertTrue(
                "The More action must be the rightmost header action",
                closeButton.right <= moreButton.left && moreButton.right > closeButton.right,
            )
        }
    }
}
