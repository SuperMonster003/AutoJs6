package org.autojs.autojs.runtime.api.modules.colors

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.colors.ColorValues.number
import org.autojs.autojs.runtime.api.modules.colors.ColorValues.quote
import org.autojs.autojs.runtime.api.modules.colors.ColorValues.rangeError
import org.autojs.autojs.runtime.api.modules.colors.ColorValues.typeError
import org.autojs.autojs.runtime.api.modules.jsox.JsoxValues
import org.autojs.autojs.theme.ThemeColorCoordinator
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope
import kotlin.math.roundToLong
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * `colors.theme` on the new script module layer (roadmap P2.3): the AutoJs6 theme color under script control (theme
 * color roadmap P6.2, D17). `set` and `reset` act as explicit choices (an active intelligent library stops), are merged
 * within 500 ms and recorded as "Script: name"; the theme color pages can undo them. Behavior is that of the current
 * module; errors are TypeError / RangeError (D10) and extra arguments a TypeError (T4).
 *
 * zh-CN: 新脚本模块层上的 colors.theme (P2.3): 由脚本控制 AutoJs6 的主题色 (主题色路线图 P6.2, D17). set 与 reset 相当于显式选择 (激活的智能库会停用),
 * 500 ms 内合并并记录为 "脚本: 名称"; 主题色页面可以撤销. 行为与现有模块相同, 错误为 TypeError / RangeError (D10), 多余参数为 TypeError (T4).
 */
@ScriptModule(name = "theme", parent = ColorsModule::class, dollarPrefix = false)
class ColorsThemeModule(private val runtime: ScriptRuntime?) {

    private val colors = ColorsModule(runtime)

    /** The primary color now, or the script color waiting in the merge window. */
    @ScriptFunction
    fun get(): Int = ThemeColorCoordinator.pendingOrCurrent.colorPrimary

    /** Sets the theme color; options `accent` and `dark` override the derived ones. */
    @ScriptFunction
    fun set(color: Any?, options: Any? = null) {
        val function = "colors.theme.set"
        if (!JsoxValues.isNullish(options) && options !is NativeObject) throw typeError(function, "options", "Object", options)
        val primary = colors.int(color, function) or ColorValues.OPAQUE
        fun optional(key: String) = property(options, key)?.let { colors.int(it, function, "options.$key") or ColorValues.OPAQUE }
        val themeColor = ThemeColorManager.resolve(primary, optional("accent"), optional("dark"))
        ThemeColorCoordinator.applyFromScript(themeColor, scriptName())
    }

    /** Sets the default theme color of AutoJs6. */
    @ScriptFunction
    fun reset() {
        val themeColor = ThemeColorManager.resolve(ThemeColorManager.defaultThemeColor.colorPrimary)
        ThemeColorCoordinator.applyFromScript(themeColor, scriptName())
    }

    /** `{ id, name, kind, intelligent, size }` of the preset and user libraries. */
    @ScriptFunction
    fun libraries(cx: Context, scope: VarScope): List<Scriptable> = ColorsThemeValues.libraries().map { library -> scriptable(cx, scope, library) }

    /** Activates an intelligent library by its id, optionally from `options.group` (a group id). */
    @ScriptFunction
    fun activate(libraryId: Any?, options: Any? = null) {
        val function = "colors.theme.activate"
        if (!JsoxValues.isNullish(options) && options !is NativeObject) throw typeError(function, "options", "Object", options)
        val id = integer(function, "libraryId", libraryId).toInt()
        val snapshot = ColorsThemeValues.loadIntelligent(id)
            ?: throw rangeError(function, ScriptMessages.argumentType("libraryId", "id of an intelligent color library", quote(libraryId)))
        val groupId = property(options, "group")?.let { value ->
            integer(function, "options.group", value).also { group ->
                if (snapshot.groups.none { it.id == group && it.enabled && it.hasColor }) {
                    throw rangeError(function, ScriptMessages.argumentType("options.group", "id of a usable group of ${snapshot.name}", quote(value)))
                }
            }
        }
        ColorsThemeValues.onMain { IntelligentColorEngine.activate(snapshot, groupId) }
    }

    /** Stops the active intelligent library and keeps its current color; false when none is active. */
    @ScriptFunction
    fun deactivate(): Boolean = ColorsThemeValues.deactivate()

    /** Moves the active intelligent library to its next group at once; false when none is active. */
    @ScriptFunction
    fun next(): Boolean = ColorsThemeValues.onMain { IntelligentColorEngine.next() }

    /** Switches the "Script" state source of the active intelligent library to the state named [name]. */
    @ScriptFunction
    fun state(name: Any?) {
        val function = "colors.theme.state"
        if (JsoxValues.isNullish(name)) throw typeError(function, ScriptMessages.argumentCannotBe("name", quote(name)))
        val stateName = RhinoScriptRuntime.toString(name)
        ColorsThemeValues.onMain {
            runCatching { IntelligentColorEngine.setScriptState(stateName) }.getOrElse { error ->
                throw rangeError(function, error.message ?: error.javaClass.name)
            }
        }
    }

    /** `{ libraryId, libraryName, intelligent, groupId, groupName, state, color, accent, dark, summary }`. */
    @ScriptFunction
    fun current(cx: Context, scope: VarScope): Scriptable = scriptable(cx, scope, ColorsThemeValues.current())

    private fun scriptable(cx: Context, scope: VarScope, values: Map<String, Any?>): Scriptable = cx.newObject(scope).also { o ->
        values.forEach { (key, value) -> o.put(key, o, value) }
    }

    /** An integer (numbers and numeric text); NaN is a TypeError, a fraction or an infinite number a RangeError. */
    private fun integer(function: String, param: String, value: Any?): Long {
        val x = ColorValues.component(value, function, param)
        if (x.isInfinite() || x != Math.floor(x)) throw rangeError(function, ScriptMessages.argumentType(param, "integer", number(x)))
        return x.roundToLong()
    }

    private fun property(options: Any?, key: String): Any? {
        if (options !is Scriptable) return null
        val value = ScriptableObject.getProperty(options, key)
        return if (value === Scriptable.NOT_FOUND || JsoxValues.isNullish(value)) null else value
    }

    private fun scriptName(): String? = runCatching { runtime?.engines?.myEngine()?.source?.name }.getOrNull()

}
