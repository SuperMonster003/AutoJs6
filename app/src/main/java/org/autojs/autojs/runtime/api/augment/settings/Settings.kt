package org.autojs.autojs.runtime.api.augment.settings

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs6.R
import org.mozilla.javascript.Undefined

@Suppress("unused", "UNUSED_PARAMETER")
class Settings(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentFunctions = listOf(
        ::isEnabled.name,
        ::setEnabled.name,
    )

    companion object : ArgumentGuards() {

        internal enum class Setting(val keyRes: Int) {
            STABLE_MODE(R.string.key_stable_mode),
            ROOT_ACCESSIBILITY_SERVICE(R.string.key_enable_a11y_service_with_root_access),
            STOP_ALL_ON_VOLUME_UP(R.string.key_use_volume_control_running),
            NOT_SHOW_CONSOLE(R.string.key_not_showing_main_activity),
            FOREGROUND_SERVICE(R.string.key_foreground_service),
        }

        private val aliases = mapOf(
            "stable_mode" to Setting.STABLE_MODE,
            "key_stable_mode" to Setting.STABLE_MODE,
            "enable_accessibility_service_by_root" to Setting.ROOT_ACCESSIBILITY_SERVICE,
            "key_enable_accessibility_service_by_root" to Setting.ROOT_ACCESSIBILITY_SERVICE,
            "key_enable_a11y_service_with_root_access" to Setting.ROOT_ACCESSIBILITY_SERVICE,
            "stop_all_on_volume_up" to Setting.STOP_ALL_ON_VOLUME_UP,
            "key_use_volume_control_running" to Setting.STOP_ALL_ON_VOLUME_UP,
            "not_show_console" to Setting.NOT_SHOW_CONSOLE,
            "key_dont_show_main_activity" to Setting.NOT_SHOW_CONSOLE,
            "key_not_showing_main_activity" to Setting.NOT_SHOW_CONSOLE,
            "foreground_service" to Setting.FOREGROUND_SERVICE,
            "key_foreground_service" to Setting.FOREGROUND_SERVICE,
        )

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isEnabled(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
            Pref.getBoolean(resolvePreferenceKey(it), false)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setEnabled(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLength(args, 2) {
            val (key, enabled) = it
            Pref.putBoolean(resolvePreferenceKey(key), coerceBoolean(enabled))
            UNDEFINED
        }

        internal fun resolveSetting(key: Any?): Setting? {
            val niceKey = coerceString(key)
            return aliases[niceKey]
        }

        internal fun resolvePreferenceKey(
            key: Any?,
            resourceResolver: (Int) -> String = { ScriptRuntime.applicationContext.getString(it) },
        ): String {
            val niceKey = coerceString(key)
            return aliases[niceKey]?.let { resourceResolver(it.keyRes) } ?: niceKey
        }
    }
}
