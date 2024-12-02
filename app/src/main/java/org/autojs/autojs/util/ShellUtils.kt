package org.autojs.autojs.util

import android.content.Context
import org.autojs.autojs.runtime.api.ProcessShell
import org.autojs.autojs.runtime.api.WrappedShizuku

object ShellUtils {

    @JvmStatic
    fun togglePointerLocation(context: Context): Boolean {
        val aimState = !isPointerLocationEnabled(context)
        val command = "settings put system pointer_location " + when (aimState) {
            true -> PointerLocation.ENABLED.value
            else -> PointerLocation.DISABLED.value
        }
        return when {
            setPointerLocationStateByRoot(command) && checkPointerLocationState(context, aimState) -> true
            setPointerLocationStateByShizuku(context, command) && checkPointerLocationState(context, aimState) -> true
            else -> false
        }
    }

    private fun checkPointerLocationState(context: Context, aimState: Boolean) = when (aimState) {
        true -> isPointerLocationEnabled(context)
        else -> isPointerLocationDisabled(context)
    }

    private fun isPointerLocationEnabled(context: Context) = getPointerLocationResult(context) == PointerLocation.ENABLED.value

    private fun isPointerLocationDisabled(context: Context) = getPointerLocationResult(context) == PointerLocation.DISABLED.value

    private fun getPointerLocationResult(context: Context): Int {
        val cmd = "settings get system pointer_location"
        return try {
            // @Caution by SuperMonster003 on Mar 2, 2022.
            //  ! Result of execCommand() contains a "\n" and its length() of result is 2 not 1.
            //  ! zh-CN: 方法 execCommand() 返回值的 result 字段含有一个换行符, 且 result 的长度为 2 而非 1.
            ProcessShell.execCommand(cmd, true).result.trim().toInt()
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                WrappedShizuku.execCommand(context, cmd).result.trim().toInt()
            } catch (e: Exception) {
                PointerLocation.DISABLED.value.also { e.printStackTrace() }
            }
        }
    }

    private fun setPointerLocationStateByRoot(command: String) = runCatching {
        ProcessShell.execCommand(command, true)
    }.isSuccess

    private fun setPointerLocationStateByShizuku(context: Context, command: String) = runCatching {
        WrappedShizuku.execCommand(context, command)
    }.isSuccess

    private enum class PointerLocation(val value: Int) {
        ENABLED(1),
        DISABLED(0)
    }

}
