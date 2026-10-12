package org.autojs.autojs.deviceid

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.ProcessShell
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RootUtils
import java.util.Locale

object AndroidIdReader {

    private val androidIdPattern = Regex("[0-9a-fA-F]{1,16}")

    @JvmStatic
    @SuppressLint("HardwareIds")
    fun getAndroidId(context: Context, useSystemId: Boolean): String? = read(
        useSystemId = useSystemId,
        sdkInt = Build.VERSION.SDK_INT,
        // Android assigns each user a range of 100000 UIDs. Match the calling app's user,
        // including a secondary user or work profile, instead of the foreground user.
        userId = Process.myUid() / 100000,
        readAppId = { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) },
        shizuku = { command ->
            check(WrappedShizuku.isOperational()) { "Shizuku is not running or is not authorized" }
            WrappedShizuku.execCommand(context, command)
        },
        root = { command ->
            check(RootUtils.isRootAvailable()) { "Root is not available" }
            ProcessShell.execCommand(arrayOf(command), true)
        },
    )

    internal fun read(
        useSystemId: Boolean,
        sdkInt: Int,
        userId: Int,
        readAppId: () -> String?,
        shizuku: (String) -> AbstractShell.Result,
        root: (String) -> AbstractShell.Result,
    ): String? {
        if (!useSystemId || sdkInt < Build.VERSION_CODES.O) return readAppId()

        val command = "settings --user $userId get secure android_id"
        val failure = IllegalStateException(
            "device.getAndroidId(true) could not read the system Android ID for user $userId; " +
                    "authorized Shizuku or Root is required",
        )
        for ((name, execute) in listOf("Shizuku" to shizuku, "Root" to root)) {
            try {
                val result = execute(command)
                check(result.code == 0) { "Command failed (${result.code}): ${result.error}" }
                val value = result.result?.trim().orEmpty()
                check(androidIdPattern.matches(value)) { "Command returned an empty or invalid Android ID" }
                return value.lowercase(Locale.ROOT)
            } catch (error: Exception) {
                if (ScriptInterruptedException.causedByInterrupt(error)) throw ScriptInterruptedException(error)
                failure.addSuppressed(IllegalStateException("$name: ${error.message}", error))
            }
        }
        throw failure
    }
}
