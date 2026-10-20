package org.autojs.autojs.runtime.api.augment.settings

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.pref.SettingType
import org.autojs.autojs.core.pref.SettingsRegistry
import org.autojs.autojs.core.pref.store.SettingsStores
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
            isEnabledFor(resolvePreferenceKey(it))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setEnabled(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLength(args, 2) {
            val (key, enabled) = it
            SettingsStores.default.put(resolvePreferenceKey(key), coerceBoolean(enabled))
            UNDEFINED
        }

        /**
         * The stored switch of [preferenceKey], else the default of its Boolean declaration, as the settings page shows
         * it (docs/settings/ROADMAP.md appendix C Q10); false for a name without one, as before.
         * zh-CN: [preferenceKey] 的存储值, 缺键时为其 Boolean 键声明的默认值, 与设置页显示一致 (ROADMAP 附录 C Q10); 没有声明的名称
         * 仍为 false.
         */
        internal fun isEnabledFor(preferenceKey: String): Boolean {
            val declared = SettingsRegistry.find(preferenceKey)?.takeIf { it.type == SettingType.Bool }
            return SettingsStores.default.getBoolean(preferenceKey, declared?.defaultOrNull() as Boolean? ?: false)
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
