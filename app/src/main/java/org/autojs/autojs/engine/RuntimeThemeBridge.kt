package org.autojs.autojs.engine

import org.autojs.autojs.runtime.api.modules.colors.ColorsThemeValues
import org.autojs.autojs.theme.ThemeColorCoordinator
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import org.json.JSONArray
import org.json.JSONObject

/**
 * Host side of `colors.theme` for the JS runtimes (api-coverage Q80 (a)): the `theme` bridge module, Node and Bun sharing it.
 * The theme color lives in the host process, so each call acts on it like [org.autojs.autojs.runtime.api.modules.colors.ColorsThemeModule]
 * does: `set` / `reset` are recorded as "Script: name" and merged within the script window. Colors arrive as the integers
 * the facade's Rhino color rules produced. A refused `activate` / `state` answers `{ library: false }`, `{ group: false, name }`
 * or `{ error }`, the facade raising Rhino's RangeError from it.
 *
 * zh-CN: JS 运行时的 colors.theme 宿主侧 (api-coverage Q80 (a)): theme 桥接模块, Node 与 Bun 共用. 主题色在宿主进程内, 因此各调用的行为同 ColorsThemeModule: set / reset 记录为
 * "脚本: 名称" 并在脚本窗口内合并. 颜色以门面按 Rhino 规则算出的整数到达. 被拒绝的 activate / state 应答 { library: false }, { group: false, name } 或 { error }, 由门面抛出 Rhino 的 RangeError.
 */
internal class RuntimeThemeBridge(private val scriptName: () -> String?) {

    fun invoke(method: String, args: JSONArray): Any? = when (method) {
        "get" -> ThemeColorCoordinator.pendingOrCurrent.colorPrimary
        "set" -> {
            fun optional(index: Int) = if (args.isNull(index)) null else args.getLong(index).toInt()
            ThemeColorCoordinator.applyFromScript(ThemeColorManager.resolve(args.getLong(0).toInt(), optional(1), optional(2)), scriptName())
            null
        }
        "reset" -> {
            ThemeColorCoordinator.applyFromScript(ThemeColorManager.resolve(ThemeColorManager.defaultThemeColor.colorPrimary), scriptName())
            null
        }
        "libraries" -> JSONObject.wrap(ColorsThemeValues.libraries())
        "activate" -> activate(args.getLong(0).toInt(), if (args.isNull(1)) null else args.getLong(1))
        "deactivate" -> ColorsThemeValues.deactivate()
        "next" -> ColorsThemeValues.onMain { IntelligentColorEngine.next() }
        "state" -> runCatching { ColorsThemeValues.onMain { IntelligentColorEngine.setScriptState(args.getString(0)) } }
            .fold({ JSONObject() }, { error -> JSONObject().put("error", error.message ?: error.javaClass.name) })
        "current" -> JSONObject.wrap(ColorsThemeValues.current())
        else -> throw IllegalArgumentException("Unknown theme method: $method")
    }

    private fun activate(libraryId: Int, group: Long?): JSONObject {
        val snapshot = ColorsThemeValues.loadIntelligent(libraryId) ?: return JSONObject().put("library", false)
        if (group != null && snapshot.groups.none { it.id == group && it.enabled && it.hasColor }) {
            return JSONObject().put("group", false).put("name", snapshot.name)
        }
        ColorsThemeValues.onMain { IntelligentColorEngine.activate(snapshot, group) }
        return JSONObject()
    }

    companion object {
        val METHODS = setOf("get", "set", "reset", "libraries", "activate", "deactivate", "next", "state", "current")
    }
}
