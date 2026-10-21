package org.autojs.autojs.runtime.api.modules.colors

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.keys.MiscStateKeys
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
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.displayColorsOf
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.presetColorLibraries
import org.autojs.autojs.theme.app.ColorSelectBaseActivity
import org.autojs.autojs.theme.app.IntelligentSummaryFormatter
import org.autojs.autojs.theme.app.ScriptThemeChanges
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import org.autojs.autojs.theme.intelligent.IntelligentPresets
import org.autojs.autojs.theme.intelligent.IntelligentSnapshot
import org.autojs.autojs.theme.intelligent.StateSource
import org.autojs.autojs.theme.library.ColorLibraryIds
import org.autojs.autojs.theme.library.ColorLibraryRepository
import org.autojs.autojs.theme.library.ColorSelectionCodec
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
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
    fun libraries(cx: Context, scope: VarScope): List<Scriptable> {
        val context = GlobalAppContext.get()
        val repository = ColorLibraryRepository.get(context)
        val presets = presetColorLibraries.filterNot { library -> library.isIntelligent }.map { library ->
            summary(cx, scope, library.id, context.getString(library.nameRes), KIND_PRESET, false, displayColorsOf(library).size)
        }
        val intelligentPresets = IntelligentPresets.all(context).map { snapshot ->
            summary(cx, scope, snapshot.libraryId, snapshot.name, KIND_PRESET, true, snapshot.groups.size)
        }
        val users = runBlocking(Dispatchers.IO) {
            val kinds = repository.listLibraries().associate { library -> library.id to library.kind.lowercase() }
            repository.listPlainLibraries().map { summaryOf ->
                val library = summaryOf.library
                summary(cx, scope, ColorLibraryIds.ofUserLibrary(library.id, intelligent = false), library.name, library.kind.lowercase(), false, summaryOf.colorCount)
            } + repository.listIntelligentLibraries().map { snapshot ->
                val kind = ColorLibraryIds.userRowIdOf(snapshot.libraryId)?.let(kinds::get) ?: KIND_CREATED
                summary(cx, scope, snapshot.libraryId, snapshot.name, kind, true, snapshot.groups.size)
            }
        }
        return presets + intelligentPresets + users
    }

    /** Activates an intelligent library by its id, optionally from `options.group` (a group id). */
    @ScriptFunction
    fun activate(libraryId: Any?, options: Any? = null) {
        val function = "colors.theme.activate"
        if (!JsoxValues.isNullish(options) && options !is NativeObject) throw typeError(function, "options", "Object", options)
        val id = integer(function, "libraryId", libraryId).toInt()
        val snapshot = loadIntelligent(id)
            ?: throw rangeError(function, ScriptMessages.argumentType("libraryId", "id of an intelligent color library", quote(libraryId)))
        val groupId = property(options, "group")?.let { value ->
            integer(function, "options.group", value).also { group ->
                if (snapshot.groups.none { it.id == group && it.enabled && it.hasColor }) {
                    throw rangeError(function, ScriptMessages.argumentType("options.group", "id of a usable group of ${snapshot.name}", quote(value)))
                }
            }
        }
        onMain { IntelligentColorEngine.activate(snapshot, groupId) }
    }

    /** Stops the active intelligent library and keeps its current color; false when none is active. */
    @ScriptFunction
    fun deactivate(): Boolean = onMain {
        when (IntelligentColorEngine.activeSnapshot) {
            null -> false
            else -> {
                IntelligentColorEngine.deactivate()
                ScriptThemeChanges.onDeactivatedByScript()
                true
            }
        }
    }

    /** Moves the active intelligent library to its next group at once; false when none is active. */
    @ScriptFunction
    fun next(): Boolean = onMain { IntelligentColorEngine.next() }

    /** Switches the "Script" state source of the active intelligent library to the state named [name]. */
    @ScriptFunction
    fun state(name: Any?) {
        val function = "colors.theme.state"
        if (JsoxValues.isNullish(name)) throw typeError(function, ScriptMessages.argumentCannotBe("name", quote(name)))
        val stateName = RhinoScriptRuntime.toString(name)
        onMain {
            runCatching { IntelligentColorEngine.setScriptState(stateName) }.getOrElse { error ->
                throw rangeError(function, error.message ?: error.javaClass.name)
            }
        }
    }

    /** `{ libraryId, libraryName, intelligent, groupId, groupName, state, color, accent, dark, summary }`. */
    @ScriptFunction
    fun current(cx: Context, scope: VarScope): Scriptable {
        val context = GlobalAppContext.get()
        return onMain {
            val themeColor = ThemeColorManager.currentThemeColor
            val active = IntelligentColorEngine.activeSnapshot
            cx.newObject(scope).also { o ->
                when (active) {
                    null -> {
                        val selection = ColorSelectBaseActivity.currentColorSelection()
                        o.put("libraryId", o, ColorSelectionCodec.encode(selection).first)
                        o.put("libraryName", o, MiscStateKeys.COLOR_SELECT_LIBRARY_NAME.storedOrNull())
                        o.put("intelligent", o, false)
                        o.put("groupId", o, null)
                        o.put("groupName", o, MiscStateKeys.COLOR_SELECT_ITEM_NAME.storedOrNull())
                        o.put("state", o, null)
                    }
                    else -> {
                        val group = active.group(IntelligentColorEngine.shownGroupId)
                        val stateKey = IntelligentColorEngine.runtimeState.lastStateKey
                        o.put("libraryId", o, active.libraryId)
                        o.put("libraryName", o, active.name)
                        o.put("intelligent", o, true)
                        o.put("groupId", o, group?.id)
                        o.put("groupName", o, group?.let(IntelligentColorEngine::groupDisplayName))
                        o.put("state", o, stateKey?.let { key ->
                            when (val source = active.config.stateSource) {
                                is StateSource.ScriptStates -> IntelligentSummaryFormatter.stateLabel(context, source, key)
                                else -> key
                            }
                        })
                    }
                }
                o.put("color", o, themeColor.colorPrimary)
                o.put("accent", o, themeColor.colorAccent)
                o.put("dark", o, themeColor.colorPrimaryDark)
                o.put("summary", o, ColorSelectBaseActivity.getCurrentColorSummary(context))
            }
        }
    }

    private fun summary(cx: Context, scope: VarScope, id: Int, name: String, kind: String, intelligent: Boolean, size: Int): Scriptable = cx.newObject(scope).also {
        it.put("id", it, id)
        it.put("name", it, name)
        it.put("kind", it, kind)
        it.put("intelligent", it, intelligent)
        it.put("size", it, size)
    }

    private fun loadIntelligent(libraryId: Int): IntelligentSnapshot? = when {
        ColorLibraryIds.isPresetIntelligent(libraryId) -> IntelligentPresets.find(GlobalAppContext.get(), libraryId)
        ColorLibraryIds.isUserIntelligent(libraryId) -> runBlocking(Dispatchers.IO) {
            ColorLibraryRepository.get(GlobalAppContext.get()).loadIntelligent(ColorLibraryIds.userRowIdOf(libraryId)!!)
        }
        else -> null
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

    /** Runs [block] on the main thread, where the engine and the coordinator live, and waits for its result. */
    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val task = FutureTask(block)
        mainHandler.post(task)
        return try {
            task.get(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    private companion object {
        const val MAIN_THREAD_TIMEOUT_SECONDS = 10L
        const val KIND_PRESET = "preset"
        const val KIND_CREATED = "created"
        val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    }

}
