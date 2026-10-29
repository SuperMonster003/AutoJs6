package org.autojs.autojs.runtime.api.modules.settings

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingType
import org.autojs.autojs.core.pref.SettingsRegistry
import org.autojs.autojs.core.pref.archive.SettingsSnapshot
import org.autojs.autojs.core.pref.data.SettingsDataCapture
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.ui.settings.transfer.SettingsExporter
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newNativeArray
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs6.R
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined
import java.io.File

@Suppress("unused", "UNUSED_PARAMETER")
class Settings(scriptRuntime: ScriptRuntime) : ArgumentGuards() {

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
         * `settings.export(path?, categories?)` (docs/settings/ROADMAP.md T15, P12.1): writes the archive of the export
         * wizard ([SettingsScriptExport]) and returns `{ path, categories, entries, unexportable, skipped }`. Blocks, so it
         * is refused on the UI thread as `sleep` is (after the arguments are checked).
         * zh-CN: `settings.export(path?, categories?)` (ROADMAP T15, P12.1): 写出与导出向导相同的归档 ([SettingsScriptExport]),
         * 返回 `{ path, categories, entries, unexportable, skipped }`. 会阻塞, 因此与 `sleep` 一样拒绝在 UI 线程调用 (在检查参数之后).
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun export(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsAtMost(args, 2) {
            val (path, categories) = it
            val target = SettingsScriptExport.pathOf(path)?.let(scriptRuntime.files::nonNullPath)
            val ids = SettingsScriptExport.categoryIds(categories)
            ensureNonUiThread("settings.export")
            RhinoUtils.toJsValue(scriptRuntime.topLevelScope, exportPlain(target, ids)) as NativeObject
        }

        /**
         * The result of `settings.export` as plain values, for an already resolved [target] (null for the default
         * directory) and the category [ids] (null for the default categories); shared with the JS runtime bridge
         * (api-coverage P3.1b).
         * zh-CN: settings.export 的结果 (普通值), [target] 已解析 (null 为默认目录), [ids] 为分类 id (null 为默认分类); 与 JS 运行时桥共用 (P3.1b).
         */
        internal fun exportPlain(target: String?, ids: List<String>?): Map<String, Any?> {
            val selection = SettingsScriptExport.select(ids)
            val context = ScriptRuntime.applicationContext
            val now = System.currentTimeMillis()
            val result = SettingsScriptExport.write(
                SettingsScriptExport.target(target, File(WorkingDirectoryUtils.path), now),
                selection,
                SettingsSnapshot.capture(selection.categories),
                SettingsDataCapture.capture(context, selection.categories, now),
                now,
                SettingsExporter.host(context),
                SettingsExporter.device(),
            )
            return linkedMapOf(
                "path" to result.file.path,
                "categories" to result.categories.toList(),
                "entries" to result.entries,
                "unexportable" to result.unexportable,
                "skipped" to result.skipped.map { skip -> linkedMapOf("id" to skip.id, "reason" to skip.reason.label) },
            )
        }

        /**
         * `settings.categories()` (P12.1): every category in page order as `{ id, title, kind, entries, defaultSelected,
         * sensitive }`, `kind` being "settings" or "data" and `entries` counted as the export wizard counts them. Reads the
         * data categories, so it is refused on the UI thread as well.
         * zh-CN: `settings.categories()` (P12.1): 按页面顺序列出每个分类 `{ id, title, kind, entries, defaultSelected, sensitive }`,
         * `kind` 为 "settings" 或 "data", `entries` 与导出向导的计数相同. 需要读取数据分类, 因此同样拒绝在 UI 线程调用.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun categories(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsIsEmpty(args) {
            ensureNonUiThread("settings.categories")
            val scope = scriptRuntime.topLevelScope
            newNativeArray(scope, categoriesPlain().map { RhinoUtils.toJsValue(scope, it) }.toTypedArray<Any?>())
        }

        /** `settings.categories()` as plain values, shared with the JS runtime bridge (P3.1b). zh-CN: 普通值形式, 与 JS 运行时桥共用. */
        internal fun categoriesPlain(): List<Map<String, Any?>> {
            val context = ScriptRuntime.applicationContext
            val all = SettingCategory.ALL
            val entries = SettingsScriptExport.entries(
                SettingsSnapshot.capture(all),
                SettingsDataCapture.capture(context, all.filter { it.isData }, System.currentTimeMillis()),
            )
            return entries.map { (category, count) ->
                linkedMapOf(
                    "id" to category.id,
                    "title" to (category.titleRes.takeIf { res -> res != SettingCategory.NO_TITLE }?.let(context::getString) ?: category.id),
                    "kind" to if (category.isData) "data" else "settings",
                    "entries" to count,
                    "defaultSelected" to category.defaultSelected,
                    "sensitive" to category.sensitive,
                )
            }
        }

        private fun ensureNonUiThread(function: String) {
            require(!RhinoUtils.isUiThread()) { str(R.string.error_function_called_in_ui_thread, function) }
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
