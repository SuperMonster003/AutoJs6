package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * The Android-free part of the `ui` window family of the JS runtimes' bridge (api-coverage P3.3): the routes, the
 * `windowControl` operations and their value types, the `barHeight` options, the `floatyControl` operations and the
 * capabilities each route needs. [RuntimeUiInterop] applies the checked values; the JVM tests cover this object.
 * zh-CN: JS 运行时桥接 `ui` 窗口族中不依赖 Android 的部分 (api-coverage P3.3): 路由, `windowControl` 操作及其值类型, `barHeight` 选项,
 * `floatyControl` 操作, 以及各路由所需的能力. 由 [RuntimeUiInterop] 应用检查后的值; JVM 测试覆盖本对象.
 */
internal object RuntimeUiWindowControls {

    /** Window routes of the `ui` module served by [RuntimeUiInterop] (api-coverage P3.3). zh-CN: 由 [RuntimeUiInterop] 提供的 `ui` 模块窗口路由 (api-coverage P3.3). */
    val METHODS = setOf("windowControl", "barHeight", "floatyWindow", "floatyRawWindow", "floatyControl", "floatyCloseAll")

    /** `windowControl` operations taking a boolean. zh-CN: 取布尔值的 `windowControl` 操作. */
    val BOOLEAN_OPS = setOf("interceptBack", "statusBarIconLight", "navigationBarIconLight", "keepScreenOn")

    /** `windowControl` operations taking a color string or number (converted on the host as Rhino does). zh-CN: 取颜色字符串或数值的 `windowControl` 操作 (在宿主上按 Rhino 的方式转换). */
    val COLOR_OPS = setOf("statusBarColor", "navigationBarColor", "backgroundColor", "statusBarIconLightBy", "navigationBarIconLightBy")

    val WINDOW_OPS = BOOLEAN_OPS + COLOR_OPS

    /**
     * The window option an operation sets: `xxxIconLightBy` decides the same icon lightness as `xxxIconLight`, so the later
     * call of either one wins when the options are applied again.
     * zh-CN: 操作所设置的窗口选项: `xxxIconLightBy` 与 `xxxIconLight` 决定同一个图标明暗, 因此重新应用选项时两者中后调用的那个生效.
     */
    fun optionKey(op: String): String = op.removeSuffix("By")

    /**
     * Checks [value] for the `windowControl` operation [op]; a boolean operation also accepts null as true (Rhino's
     * `statusBarIconLight()` default). Returns the boolean, or the color string / number for the host to convert.
     * zh-CN: 检查 `windowControl` 操作 [op] 的 [value]; 布尔操作也接受 null, 视为 true (Rhino `statusBarIconLight()` 的默认值).
     * 返回布尔值, 或交给宿主转换的颜色字符串 / 数值.
     */
    fun checkControl(op: String, value: Any?): Any {
        require(op in WINDOW_OPS) { "Unknown windowControl operation '$op'; expected one of ${WINDOW_OPS.joinToString(", ")}" }
        val present = value?.takeUnless { it == JSONObject.NULL }
        return if (op in BOOLEAN_OPS) {
            present?.let { it as? Boolean ?: throw IllegalArgumentException("windowControl '$op' requires a boolean") } ?: true
        } else {
            require(present is String || present is Number) { "windowControl '$op' requires a color string or number" }
            if (present is Number) require(present.toDouble().isFinite()) { "windowControl '$op' requires a finite color number" }
            present
        }
    }

    /** `barHeight` options with Rhino's defaults (all true). zh-CN: 采用 Rhino 默认值 (全部为 true) 的 `barHeight` 选项. */
    data class BarOptions(val withComputed: Boolean, val withDimen: Boolean, val ignoreVisibility: Boolean)

    private val BAR_KINDS = setOf("status", "navigation")
    private val BAR_KEYS = setOf("withComputed", "withDimen", "ignoreVisibility")

    /**
     * `barHeight [kind, visible, options]`: [visible] true is Rhino's `getVisible*Height`, which forces `ignoreVisibility`
     * to false; otherwise `options.ignoreVisibility` applies (default true).
     * zh-CN: `barHeight [kind, visible, options]`: [visible] 为 true 对应 Rhino 的 `getVisible*Height`, 强制 `ignoreVisibility` 为 false;
     * 否则使用 `options.ignoreVisibility` (默认 true).
     */
    fun barRequest(args: JSONArray): Pair<String, BarOptions> {
        require(args.length() in 1..3) { "barHeight expects [kind, visible?, options?]" }
        val kind = args.opt(0) as? String ?: throw IllegalArgumentException("barHeight kind must be 'status' or 'navigation'")
        require(kind in BAR_KINDS) { "barHeight kind must be 'status' or 'navigation', got '$kind'" }
        val visible = args.opt(1).let { if (it == null || it == JSONObject.NULL) false else it as? Boolean ?: throw IllegalArgumentException("barHeight visible must be a boolean") }
        val options = args.opt(2).let { if (it == null || it == JSONObject.NULL) JSONObject() else it as? JSONObject ?: throw IllegalArgumentException("barHeight options must be an object") }
        options.keys().forEach { key -> require(key in BAR_KEYS) { "Unknown barHeight option '$key'; expected one of ${BAR_KEYS.joinToString(", ")}" } }
        fun flag(key: String): Boolean = options.opt(key).let { if (it == null || it == JSONObject.NULL) true else it as? Boolean ?: throw IllegalArgumentException("barHeight option '$key' must be a boolean") }
        return kind to BarOptions(flag("withComputed"), flag("withDimen"), if (visible) false else flag("ignoreVisibility"))
    }

    /** `floatyControl` operations and their argument counts after `[id, op]`. zh-CN: `floatyControl` 的操作及其在 `[id, op]` 之后的参数个数. */
    val FLOATY_OPS: Map<String, Int> = linkedMapOf(
        "setPosition" to 2, "setSize" to 2, "setTouchable" to 1, "requestFocus" to 0, "disableFocus" to 0,
        "setAdjustEnabled" to 1, "isAdjustEnabled" to 0, "geometry" to 0,
    )

    /** Checks `floatyControl [id, op, ...args]` and returns the operation. zh-CN: 检查 `floatyControl [id, op, ...args]` 并返回操作名. */
    fun checkFloatyControl(args: JSONArray): String {
        val op = args.opt(1) as? String ?: throw IllegalArgumentException("floatyControl expects [id, op, ...args]")
        val count = FLOATY_OPS[op] ?: throw IllegalArgumentException("Unknown floatyControl operation '$op'; expected one of ${FLOATY_OPS.keys.joinToString(", ")}")
        require(args.length() == 2 + count) { "floatyControl '$op' expects $count argument(s)" }
        return op
    }

    /**
     * Capabilities of the `ui` routes: opening and controlling floating windows need `ui.overlay` as well, a raw one also
     * `floaty.raw`, and `floatyControl setTouchable` also `floaty.touch`; every other route, `floatyCloseAll` included (it
     * only closes what the execution opened), needs `ui` (api-coverage P3.3).
     * zh-CN: `ui` 路由所需的能力: 打开与控制悬浮窗还需要 `ui.overlay`, raw 悬浮窗另需 `floaty.raw`, `floatyControl setTouchable` 另需
     * `floaty.touch`; 其余路由 (包括只关闭本执行所开窗口的 `floatyCloseAll`) 只需 `ui` (api-coverage P3.3).
     */
    fun requiredCapabilities(method: String, argsJson: String): List<String> = when (method) {
        "floatyWindow" -> listOf(NodeBridgePermissionManifest.UI, NodeBridgePermissionManifest.UI_OVERLAY)
        "floatyRawWindow" -> listOf(NodeBridgePermissionManifest.UI, NodeBridgePermissionManifest.UI_OVERLAY, NodeBridgePermissionManifest.FLOATY_RAW)
        "floatyControl" -> {
            val op = runCatching { JSONArray(argsJson).optString(1) }.getOrDefault("")
            if (op == "setTouchable") listOf(NodeBridgePermissionManifest.UI, NodeBridgePermissionManifest.UI_OVERLAY, NodeBridgePermissionManifest.FLOATY_TOUCH)
            else listOf(NodeBridgePermissionManifest.UI, NodeBridgePermissionManifest.UI_OVERLAY)
        }
        else -> listOf(NodeBridgePermissionManifest.UI)
    }
}
