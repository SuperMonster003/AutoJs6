package org.autojs.autojs.runtime.api.modules.power

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.AugmentableKey
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.mozilla.javascript.Undefined
import android.os.PowerManager as AndroidPowerManager

@Suppress("unused", "UNUSED_PARAMETER")
class PowerManager(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val keys = listOf(
        AugmentableKey("powerManager"),
        AugmentableKey("power_manager"),
    )

    override val selfAssignmentFunctions = listOf(
        ::isIgnoringBatteryOptimizations.name,
        ::requestIgnoreBatteryOptimizations.name,
    )

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isIgnoringBatteryOptimizations(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsAtMost(args, 1) {
            val (packageName) = it
            val context = ScriptRuntime.applicationContext
            powerManager(context).isIgnoringBatteryOptimizations(resolvePackageName(context, packageName))
        }

        @JvmStatic
        @SuppressLint("BatteryLife")
        @RhinoRuntimeFunctionInterface
        fun requestIgnoreBatteryOptimizations(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsAtMost(args, 2) {
            val (forceRequest, packageName) = it
            val context = ScriptRuntime.applicationContext
            val nicePackageName = resolvePackageName(context, packageName)
            if (coerceBoolean(forceRequest, false) || !powerManager(context).isIgnoringBatteryOptimizations(nicePackageName)) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData("package:$nicePackageName".toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            UNDEFINED
        }

        private fun powerManager(context: Context) = context.getSystemService(Context.POWER_SERVICE) as AndroidPowerManager

        internal fun resolvePackageName(context: Context, packageName: Any?): String = when {
            packageName.isJsNullish() -> context.packageName
            else -> coerceString(packageName)
        }
    }
}
