package org.autojs.autojs.app.tool

import android.content.Context
import android.provider.Settings
import org.autojs.autojs.runtime.api.ProcessShell
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.runtime.api.augment.device.Device.Companion.KEY_POINTER_LOCATION
import org.autojs.autojs.ui.main.drawer.ShowableItemHelper
import org.autojs.autojs.util.IntentUtils

/**
 * Created by SuperMonster003 on Jan 27, 2026.
 */
open class PointerLocationTool(final override val context: Context) : ShowableItemHelper {

    override val isShowing
        get() = isPointerLocationEnabled(context)

    override fun show(): Boolean {
        if (setPointerLocationEnabled(context)) {
            return true
        }
        config()
        return false
    }

    override fun showIfNeeded() {
        if (!isShowing) show()
    }

    override fun hide(): Boolean {
        if (setPointerLocationDisabled(context)) {
            return true
        }
        config()
        return false
    }

    fun config() {
        IntentUtils.launchDeveloperOptionsOrSettings(context)
    }

    companion object {

        @JvmStatic
        fun togglePointerLocation(context: Context): Boolean =
            when (isPointerLocationDisabled(context)) {
                true -> setPointerLocationEnabled(context)
                else -> setPointerLocationDisabled(context)
            }

        @JvmStatic
        fun checkPointerLocationState(context: Context, aimState: Boolean) =
            when (aimState) {
                true -> isPointerLocationEnabled(context)
                else -> isPointerLocationDisabled(context)
            }

        @JvmStatic
        fun setPointerLocationEnabled(context: Context): Boolean {
            val command = "settings put system pointer_location ${PointerLocation.ENABLED.value}"
            return when {
                setPointerLocationStateByRoot(command) && isPointerLocationEnabled(context) -> true
                setPointerLocationStateByShizuku(context, command) && isPointerLocationEnabled(context) -> true
                else -> false
            }
        }

        @JvmStatic
        fun setPointerLocationDisabled(context: Context): Boolean {
            val command = "settings put system pointer_location ${PointerLocation.DISABLED.value}"
            return when {
                setPointerLocationStateByRoot(command) && isPointerLocationDisabled(context) -> true
                setPointerLocationStateByShizuku(context, command) && isPointerLocationDisabled(context) -> true
                else -> false
            }
        }

        fun isPointerLocationEnabled(context: Context) = getPointerLocationResult(context) == PointerLocation.ENABLED.value

        fun isPointerLocationDisabled(context: Context) = getPointerLocationResult(context) == PointerLocation.DISABLED.value

        fun getPointerLocationResult(context: Context): Int {

            val def = PointerLocation.DISABLED.value
            val cmd = "settings get system pointer_location"

            runCatching bySettingsSystem@{
                Settings.System.getInt(context.contentResolver, KEY_POINTER_LOCATION, def)
            }.onFailure { it.printStackTrace() }.getOrNull()?.let { return it }

            runCatching byRootShell@{
                // @Caution by SuperMonster003 on Mar 2, 2022.
                //  ! Result of execCommand() contains a "\n" and its length() of result is 2 not 1.
                //  ! zh-CN: 方法 execCommand() 返回值的 result 字段含有一个换行符, 且 result 的长度为 2 而非 1.
                ProcessShell.execCommand(cmd, true).result.trim().toIntOrNull()
            }.onFailure { it.printStackTrace() }.getOrNull()?.let { return it }

            runCatching byShizuku@{
                when {
                    WrappedShizuku.hasService() && WrappedShizuku.isOperational() -> {
                        WrappedShizuku.execCommand(context, cmd).result.trim().toIntOrNull()
                    }
                    else -> null
                }
            }.onFailure { it.printStackTrace() }.getOrNull()?.let { return it }

            return def
        }

        private fun setPointerLocationStateByRoot(command: String) = runCatching {
            ProcessShell.execCommand(command, true)
        }.isSuccess

        private fun setPointerLocationStateByShizuku(context: Context, command: String) = runCatching {
            WrappedShizuku.execCommand(context, command)
        }.isSuccess

        enum class PointerLocation(val value: Int) {
            ENABLED(1),
            DISABLED(0)
        }

        class StateChangedEvent

    }

}