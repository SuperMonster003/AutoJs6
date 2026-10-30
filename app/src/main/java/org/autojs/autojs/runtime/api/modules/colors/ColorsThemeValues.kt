package org.autojs.autojs.runtime.api.modules.colors

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.keys.MiscStateKeys
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
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * The plain values and main-thread hops of `colors.theme`, shared by [ColorsThemeModule] and the `theme` module of the JS
 * runtime bridge (api-coverage Q80 (a)): nothing here touches Rhino.
 *
 * zh-CN: colors.theme 的普通值与主线程切换, 由 [ColorsThemeModule] 与 JS 运行时桥接的 theme 模块共用 (api-coverage Q80 (a)): 这里不触碰 Rhino.
 */
internal object ColorsThemeValues {

    private const val MAIN_THREAD_TIMEOUT_SECONDS = 10L
    private const val KIND_PRESET = "preset"
    private const val KIND_CREATED = "created"
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** `{ id, name, kind, intelligent, size }` of the preset and user libraries. */
    fun libraries(): List<Map<String, Any?>> {
        val context = GlobalAppContext.get()
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
        return presets + intelligentPresets + users
    }

    /** `{ libraryId, libraryName, intelligent, groupId, groupName, state, color, accent, dark, summary }`. */
    fun current(): Map<String, Any?> {
        val context = GlobalAppContext.get()
        return onMain {
            val themeColor = ThemeColorManager.currentThemeColor
            val active = IntelligentColorEngine.activeSnapshot
            val values = LinkedHashMap<String, Any?>()
            when (active) {
                null -> {
                    val selection = ColorSelectBaseActivity.currentColorSelection()
                    values["libraryId"] = ColorSelectionCodec.encode(selection).first
                    values["libraryName"] = MiscStateKeys.COLOR_SELECT_LIBRARY_NAME.storedOrNull()
                    values["intelligent"] = false
                    values["groupId"] = null
                    values["groupName"] = MiscStateKeys.COLOR_SELECT_ITEM_NAME.storedOrNull()
                    values["state"] = null
                }
                else -> {
                    val group = active.group(IntelligentColorEngine.shownGroupId)
                    val stateKey = IntelligentColorEngine.runtimeState.lastStateKey
                    values["libraryId"] = active.libraryId
                    values["libraryName"] = active.name
                    values["intelligent"] = true
                    values["groupId"] = group?.id
                    values["groupName"] = group?.let(IntelligentColorEngine::groupDisplayName)
                    values["state"] = stateKey?.let { key ->
                        when (val source = active.config.stateSource) {
                            is StateSource.ScriptStates -> IntelligentSummaryFormatter.stateLabel(context, source, key)
                            else -> key
                        }
                    }
                }
            }
            values["color"] = themeColor.colorPrimary
            values["accent"] = themeColor.colorAccent
            values["dark"] = themeColor.colorPrimaryDark
            values["summary"] = ColorSelectBaseActivity.getCurrentColorSummary(context)
            values
        }
    }

    /** Stops the active intelligent library and keeps its current color; false when none is active. */
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

    fun loadIntelligent(libraryId: Int): IntelligentSnapshot? = when {
        ColorLibraryIds.isPresetIntelligent(libraryId) -> IntelligentPresets.find(GlobalAppContext.get(), libraryId)
        ColorLibraryIds.isUserIntelligent(libraryId) -> runBlocking(Dispatchers.IO) {
            ColorLibraryRepository.get(GlobalAppContext.get()).loadIntelligent(ColorLibraryIds.userRowIdOf(libraryId)!!)
        }
        else -> null
    }

    /** Runs [block] on the main thread, where the engine and the coordinator live, and waits for its result. */
    fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val task = FutureTask(block)
        mainHandler.post(task)
        return try {
            task.get(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    private fun summary(id: Int, name: String, kind: String, intelligent: Boolean, size: Int): Map<String, Any?> =
        linkedMapOf("id" to id, "name" to name, "kind" to kind, "intelligent" to intelligent, "size" to size)
}
