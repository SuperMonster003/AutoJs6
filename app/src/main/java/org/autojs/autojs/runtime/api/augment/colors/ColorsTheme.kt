package org.autojs.autojs.runtime.api.augment.colors

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.AugmentableKey
import org.autojs.autojs.runtime.api.modules.colors.Colors
import org.autojs.autojs.runtime.api.modules.jsox.Numberx
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
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
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/**
 * `colors.theme`: the AutoJs6 theme color under script control (roadmap P6.2, D17). Changes made with set and reset
 * act as explicit choices (an active intelligent library stops), are merged within 500 ms and recorded as
 * "Script: name" (D11); the theme color pages can undo them.
 * zh-CN: `colors.theme`: 由脚本控制 AutoJs6 的主题色 (Roadmap P6.2, D17). set 与 reset 的修改相当于显式选择
 * (激活的智能库会停用), 500 ms 内合并, 并记录为 "脚本: 名称" (D11); 主题色页面可以撤销这些修改.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class ColorsTheme(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val keys = listOf(AugmentableKey("theme", withDollarPrefix = false))

    override val selfAssignmentFunctions = listOf(
        ::get.name,
        ::set.name,
        ::reset.name,
        ::libraries.name,
        ::activate.name,
        ::deactivate.name,
        ::next.name,
        ::state.name,
        ::current.name,
    )

    companion object : ArgumentGuards() {

        private const val MAIN_THREAD_TIMEOUT_SECONDS = 10L

        private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

        /**
         * The primary color now, or the script color waiting in the merge window.
         * zh-CN: 当前的主色, 或在合并窗口中等待生效的脚本颜色.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun get(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Int = ensureArgumentsIsEmpty(args) {
            ThemeColorCoordinator.pendingOrCurrent.colorPrimary
        }

        /**
         * Sets the theme color; options `accent` and `dark` override the derived ones.
         * zh-CN: 设置主题色; 选项 `accent` 与 `dark` 覆盖派生的颜色.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun set(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Unit = ensureArgumentsLengthInRange(args, 1..2) {
            val (color, options) = it
            require(options.isJsNullish() || options is NativeObject) { "Argument \"options\" ${options.jsBrief()} for colors.theme.set must be an object" }
            val opt = options as? NativeObject
            fun optionalColor(key: String) = opt?.prop(key)?.takeUnless { value -> value.isJsNullish() }?.let { value -> Colors.toIntRhino(value) or OPAQUE }
            val themeColor = ThemeColorManager.resolve(Colors.toIntRhino(color) or OPAQUE, optionalColor("accent"), optionalColor("dark"))
            ThemeColorCoordinator.applyFromScript(themeColor, scriptNameOf(scriptRuntime))
        }

        /** Sets the default theme color of AutoJs6. zh-CN: 设置 AutoJs6 的默认主题色. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun reset(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Unit = ensureArgumentsIsEmpty(args) {
            val themeColor = ThemeColorManager.resolve(ThemeColorManager.defaultThemeColor.colorPrimary)
            ThemeColorCoordinator.applyFromScript(themeColor, scriptNameOf(scriptRuntime))
        }

        /**
         * Summaries of the preset and user libraries: `{ id, name, kind, intelligent, size }`, where size counts colors
         * or groups and kind is preset, created, imported or cloned.
         * zh-CN: 预设库与用户库的摘要: `{ id, name, kind, intelligent, size }`, 其中 size 为颜色数或组数,
         * kind 为 preset, created, imported 或 cloned.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun libraries(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsIsEmpty(args) {
            val context = globalContext
            val repository = ColorLibraryRepository.get(context)
            val presets = presetColorLibraries.filterNot { library -> library.isIntelligent }.map { library ->
                summary(library.id, context.getString(library.nameRes), KIND_PRESET, false, displayColorsOf(library).size)
            }
            val intelligentPresets = IntelligentPresets.all(context).map { snapshot ->
                summary(snapshot.libraryId, snapshot.name, KIND_PRESET, true, snapshot.groups.size)
            }
            val users = runBlocking(Dispatchers.IO) {
                val kinds = repository.listLibraries().associate { library -> library.id to library.kind.lowercase() }
                repository.listPlainLibraries().map { summaryOf ->
                    val library = summaryOf.library
                    summary(ColorLibraryIds.ofUserLibrary(library.id, intelligent = false), library.name, library.kind.lowercase(), false, summaryOf.colorCount)
                } + repository.listIntelligentLibraries().map { snapshot ->
                    val kind = ColorLibraryIds.userRowIdOf(snapshot.libraryId)?.let(kinds::get) ?: KIND_CREATED
                    summary(snapshot.libraryId, snapshot.name, kind, true, snapshot.groups.size)
                }
            }
            (presets + intelligentPresets + users).toNativeArray()
        }

        /**
         * Activates an intelligent library by its id, optionally from `options.group` (a group id).
         * zh-CN: 按 id 激活智能库, 可用 `options.group` (组 id) 指定起始组.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun activate(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Unit = ensureArgumentsLengthInRange(args, 1..2) {
            val (libraryIdArg, options) = it
            require(options.isJsNullish() || options is NativeObject) { "Argument \"options\" ${options.jsBrief()} for colors.theme.activate must be an object" }
            val libraryId = parseInteger(libraryIdArg, "libraryId", "colors.theme.activate").toInt()
            val snapshot = loadIntelligent(libraryId)
                ?: throw WrappedIllegalArgumentException("No intelligent color library with id ${libraryIdArg.jsBrief()} for colors.theme.activate")
            val groupId = (options as? NativeObject)?.prop("group")?.takeUnless { value -> value.isJsNullish() }?.let { value ->
                parseInteger(value, "group", "colors.theme.activate").also { id ->
                    require(snapshot.groups.any { group -> group.id == id && group.enabled && group.hasColor }) {
                        "Library ${snapshot.name} has no usable group with id ${value.jsBrief()} for colors.theme.activate"
                    }
                }
            }
            onMain { IntelligentColorEngine.activate(snapshot, groupId) }
        }

        /**
         * Stops the active intelligent library and keeps its current color; false when none is active.
         * zh-CN: 停用激活的智能库并保留其当前颜色; 未激活时返回 false.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun deactivate(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            onMain {
                when (IntelligentColorEngine.activeSnapshot) {
                    null -> false
                    else -> {
                        IntelligentColorEngine.deactivate()
                        ScriptThemeChanges.onDeactivatedByScript()
                        true
                    }
                }
            }
        }

        /**
         * Moves the active intelligent library to its next group at once, without the "Script start" trigger; false
         * when none is active. Nothing changes while the theme color pages are open.
         * zh-CN: 立即将激活的智能库切换到下一组, 不需要勾选 "脚本启动" 换组时机; 未激活时返回 false. 主题色页面打开期间不会变化.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun next(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            onMain { IntelligentColorEngine.next() }
        }

        /**
         * Switches the "Script" state source of the active intelligent library to the state named [name].
         * zh-CN: 将激活智能库的 "脚本" 状态源切换为名为 name 的状态.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun state(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Unit = ensureArgumentsOnlyOne(args) { name ->
            require(!name.isJsNullish()) { "Argument \"name\" for colors.theme.state must be a state name" }
            val stateName = Context.toString(name)
            onMain {
                runCatching { IntelligentColorEngine.setScriptState(stateName) }.getOrElse { error ->
                    throw WrappedIllegalArgumentException("${error.message} for colors.theme.state")
                }
            }
        }

        /**
         * The current theme color and where it comes from: `{ libraryId, libraryName, intelligent, groupId,
         * groupName, state, color, accent, dark, summary }`.
         * zh-CN: 当前主题色及其来源: `{ libraryId, libraryName, intelligent, groupId, groupName, state, color, accent, dark, summary }`.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun current(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsIsEmpty(args) {
            val context = globalContext
            onMain {
                val themeColor = ThemeColorManager.currentThemeColor
                val active = IntelligentColorEngine.activeSnapshot
                newNativeObject().also { o ->
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

        private const val KIND_PRESET = "preset"
        private const val KIND_CREATED = "created"
        private const val OPAQUE = 0xFF shl 24

        private fun summary(id: Int, name: String, kind: String, intelligent: Boolean, size: Int): NativeObject = newNativeObject().also {
            it.put("id", it, id)
            it.put("name", it, name)
            it.put("kind", it, kind)
            it.put("intelligent", it, intelligent)
            it.put("size", it, size)
        }

        private fun loadIntelligent(libraryId: Int): IntelligentSnapshot? = when {
            ColorLibraryIds.isPresetIntelligent(libraryId) -> IntelligentPresets.find(globalContext, libraryId)
            ColorLibraryIds.isUserIntelligent(libraryId) -> runBlocking(Dispatchers.IO) {
                ColorLibraryRepository.get(globalContext).loadIntelligent(ColorLibraryIds.userRowIdOf(libraryId)!!)
            }
            else -> null
        }

        private fun parseInteger(value: Any?, label: String, name: String): Long {
            val number = Numberx.parseAnyRhino(value)
            require(!number.isNaN() && number == Math.floor(number)) { "Argument \"$label\" ${value.jsBrief()} for $name must be an integer" }
            return number.roundToLong()
        }

        private fun scriptNameOf(scriptRuntime: ScriptRuntime): String? = runCatching { scriptRuntime.engines.myEngine().source?.name }.getOrNull()

        /**
         * Runs [block] on the main thread, where the engine and the coordinator live, and waits for its result.
         * zh-CN: 在引擎与协调器所在的主线程运行 [block] 并等待结果.
         */
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

    }

}
