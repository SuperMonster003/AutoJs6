package org.autojs.autojs.app.tool

import android.app.PendingIntent
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.screencolorpicker.ScreenColorPickerPluginHost
import org.autojs.autojs.core.plugin.screencolorpicker.ScreenColorPickerPluginStateMonitor
import org.autojs.autojs.ui.main.drawer.ShowableItemHelper
import org.autojs.plugin.screencolorpicker.api.ScreenColorPickerStates
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Drawer helper backed only by cached state; Binder calls run on the drawer worker thread. */
open class ScreenColorPickerTool(
    final override val context: Context,
    private val launchPendingIntent: (PendingIntent) -> Unit,
) : ShowableItemHelper {

    private val mainHandler = Handler(Looper.getMainLooper())

    override val isShowing: Boolean
        get() = ScreenColorPickerPluginStateMonitor.current(context).active

    override fun show(): Boolean {
        val pendingIntent = runBlocking(Dispatchers.IO) {
            ScreenColorPickerPluginHost.getStartPendingIntent(context.applicationContext)
        }
        launchOnMainThread(pendingIntent)
        publishOptimisticRuntimeStateSoon(ScreenColorPickerStates.STARTING)
        return true
    }

    override fun hide(): Boolean {
        val accepted = runBlocking(Dispatchers.IO) {
            ScreenColorPickerPluginHost.stop(context.applicationContext)
        }
        if (accepted) {
            publishOptimisticRuntimeStateSoon(ScreenColorPickerStates.STOPPING)
            refreshSoon()
        }
        return accepted
    }

    fun refresh() {
        ScreenColorPickerPluginStateMonitor.refresh()
    }

    fun refreshAfterActivityResult() {
        refresh()
        mainHandler.postDelayed(::refresh, 300L)
        mainHandler.postDelayed(::refresh, 1_000L)
    }

    private fun refreshSoon() {
        mainHandler.postDelayed(::refresh, 150L)
        mainHandler.postDelayed(::refresh, 500L)
    }

    private fun publishOptimisticRuntimeStateSoon(runtimeState: Int) {
        // DrawerMenuToggleableItem clears its own request spinner when toggle() returns. Reapply
        // the plugin's longer-lived transition state just after that hand-off.
        mainHandler.postDelayed(
            {
                ScreenColorPickerPluginStateMonitor.publishOptimisticRuntimeState(
                    context,
                    runtimeState,
                )
            },
            50L,
        )
    }

    private fun launchOnMainThread(pendingIntent: PendingIntent) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            launchPendingIntent(pendingIntent)
            return
        }
        val completion = CountDownLatch(1)
        val canceled = AtomicBoolean(false)
        val error = AtomicReference<Throwable?>()
        val launch = Runnable {
            if (!canceled.get()) {
                runCatching { launchPendingIntent(pendingIntent) }
                    .onFailure(error::set)
            }
            completion.countDown()
        }
        mainHandler.post(launch)
        if (!completion.await(5L, TimeUnit.SECONDS)) {
            canceled.set(true)
            mainHandler.removeCallbacks(launch)
            error("Timed out while opening Screen Color Picker")
        }
        error.get()?.let { throw it }
    }
}
