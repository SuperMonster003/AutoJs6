package org.autojs.autojs.core.console

import android.view.ContextThemeWrapper
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.autojs.autojs.core.pref.keys.ConsoleKeys
import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs.core.ui.widget.JsConsoleView
import org.autojs.autojs.core.ui.widget.JsGlobalConsoleView
import org.autojs.autojs.tool.UiHandler
import org.autojs.autojs.ui.log.LogPageConsoleSettings
import org.autojs.autojs6.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ConsoleTimeSettingsDeviceTest {
    @Test
    fun fiveConsoleSurfacesKeepIndependentDefaultsAndPersistedTimeSettings() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val keys = ConsoleTimeSettings.entries.flatMap { listOf(context.getString(it.visibleKeyRes), context.getString(it.formatKeyRes)) }
        val saved = preferences.all.filterKeys { it in keys }
        preferences.edit().apply { keys.forEach(::remove) }.commit()
        val result = AtomicReference<Result<Unit>>()
        try {
            instrumentation.runOnMainSync { result.set(runCatching {
                val themed = ContextThemeWrapper(context, R.style.AppTheme)
                val activityView = ConsoleView(themed)
                val panelView = ConsoleView(themed)
                val activity = LogPageConsoleSettings(activityView, ConsoleKeys.LOG_ACTIVITY_INPUT_BAR_VISIBLE)
                val panel = LogPageConsoleSettings(panelView, EditorKeys.EDITOR_LOG_PANEL_INPUT_BAR_VISIBLE, ConsoleTimeSettings.EDITOR_PANEL)
                activity.apply()
                panel.apply()
                assertTrue(activityView.isTimeVisible)
                assertTrue(panelView.isTimeVisible)
                assertFalse(JsConsoleView(themed).isTimeVisible)
                assertFalse(JsGlobalConsoleView(themed).isTimeVisible)
                assertFalse(ConsoleImpl(UiHandler(context)).isTimeVisible())

                panel.isTimeVisible = false
                panel.timeFormat = "HH:mm"
                assertTrue(activity.isTimeVisible)
                assertEquals(ConsoleLogFormat.DEFAULT_TIME_PATTERN, activity.timeFormat)
                activity.isTimeVisible = false
                activity.timeFormat = "HH:mm:ss"
                assertEquals("HH:mm", panel.timeFormat)

                ConsoleTimeSettings.GLOBAL_CONSOLE_ELEMENT.isTimeVisible = true
                ConsoleTimeSettings.GLOBAL_CONSOLE_ELEMENT.timeFormat = "mm:ss"
                assertTrue(JsGlobalConsoleView(themed).isTimeVisible)
                assertEquals("mm:ss", JsGlobalConsoleView(themed).timeFormat)
                assertFalse(JsConsoleView(themed).isTimeVisible)
                ConsoleTimeSettings.CONSOLE_ELEMENT.isTimeVisible = true
                assertTrue(JsConsoleView(themed).isTimeVisible)
                assertFalse(ConsoleImpl(UiHandler(context)).isTimeVisible())
                ConsoleTimeSettings.FLOATING_WINDOW.isTimeVisible = true
                ConsoleTimeSettings.FLOATING_WINDOW.timeFormat = "ss.SSS"
                val floating = ConsoleImpl(UiHandler(context))
                assertTrue(floating.isTimeVisible())
                assertEquals("ss.SSS", floating.getTimeFormat())

                // Script attributes and API calls override their own instance, not user preferences.
                JsConsoleView(themed).isTimeVisible = false
                assertTrue(JsConsoleView(themed).isTimeVisible)
                floating.setTimeVisible(false)
                assertTrue(ConsoleImpl(UiHandler(context)).isTimeVisible())
                panel.apply()
                activity.apply()
                assertFalse(panelView.isTimeVisible)
                assertFalse(activityView.isTimeVisible)
                assertEquals("HH:mm", panelView.timeFormat)
                assertEquals("HH:mm:ss", activityView.timeFormat)
            }) }
            result.get().getOrThrow()
        } finally {
            preferences.edit().apply {
                keys.forEach(::remove)
                saved.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                    }
                }
            }.commit()
        }
    }
}
