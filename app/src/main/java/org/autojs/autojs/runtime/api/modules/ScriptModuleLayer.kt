package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.generated.ScriptModules
import org.mozilla.javascript.ScriptableObject

/**
 * Entry of the new script module layer for `ScriptRuntime` (roadmap D8 / T8). The switch
 * ([Pref.isNewScriptModuleLayerEnabled], off by default, in the developer options since P1.3) is read once per runtime
 * in `initPrologue`. [ORDER] decides where every module is mounted (appendix C Q8): `ScriptRuntime.augment()` walks
 * its top-level modules in order, and each one comes either from the `augment` package (its old augment call) or,
 * when the switch is on and the module is migrated ([MIGRATED]), from the generated registry. Either way the order of
 * `Object.keys(global)` stays the one of the old `augment()`.
 *
 * zh-CN: 新脚本模块层在 ScriptRuntime 中的入口 (D8 / T8). 开关 (默认关, P1.3 起位于开发者选项) 在 initPrologue 中每个运行时读取一次. [ORDER] 决定每个模块的
 * 挂载位置 (附录 C Q8): `ScriptRuntime.augment()` 按序遍历其中的顶层模块, 每个模块或来自 augment 包 (原 augment 调用), 或在开关打开且模块已迁移
 * ([MIGRATED]) 时来自生成的注册表; 两种情况下 Object.keys(global) 的顺序都与原 augment() 相同.
 */
object ScriptModuleLayer {

    /**
     * Every module of the old `ScriptRuntime.augment()` in its mounting order (91 augment calls, appendix C Q8; 92 since
     * `memory` of docs/memory/ROADMAP.md P5.1): each top-level module by its primary name, followed by its sub-modules as
     * `parent.name`. `global` and `legacy` assign their members to the global object without a module object. The
     * relative order of the top-level names is the one of `Object.keys(global)` in the device baseline
     * `tools/augment/baseline/surface-XQ-AT72-5586.txt` (`ScriptModuleOrderTest`), modules added after the baseline go
     * last; the mounter also binds sub-modules in this order. Kept when the old implementation is removed (P5.2).
     */
    @JvmField
    val ORDER: List<String> = listOf(
        "global", "legacy", "isNullish",
        "util", "util.java", "util.version", "util.versionCodes", "util.inspect", "util.morseCode",
        "species", "app", "autojs", "autojs.version", "shell", "timers", "auto", "automator", "flow", "selector", "events", "keys",
        "Canvas", "images", "ocr", "ocr.mlkit", "ocr.paddle", "ocr.rapid", "barcode", "qrcode", "yolo", "threads", "ui",
        "colors", "colors.theme", "Color", "tasks", "workManager", "dialogs", "continuation", "ai", "tts", "http", "web", "WebSocket",
        "s13n", "cvt", "cvt.bytes", "fmt", "fmt.bytes", "console", "plugins", "plugins.tools", "Arrayx", "Numberx", "Mathx", "jsox",
        "files", "files.batchRename", "crypto", "RootAutomator", "engines", "floaty", "storages", "device", "powerManager", "settings",
        "recorder", "toast", "media", "sensors", "base64", "notice", "notice.channel", "shizuku", "opencc", "mime", "sysprops",
        "sqlite", "zip", "nanoid", "pinyin", "pinyin4j", "mediainfo", "mail", "installer", "epub", "gallery", "terminal",
        "speech", "speech.models", "compose", "memory",
    )

    /** The top-level entries of [ORDER], the names `ScriptRuntime.augment()` mounts in turn. */
    @JvmField
    val TOP_LEVEL_ORDER: List<String> = ORDER.filter { '.' !in it }

    /**
     * Top-level modules served by the new layer when the switch is on: every top-level module of the host registry
     * `generated.ScriptModules`, that is, a module is migrated once its @ScriptModule class exists in this package (P2.1;
     * [ORDER] still decides where it is mounted). A migrated module brings all its sub-modules: the old augment call it
     * replaces mounted them too (`ScriptModuleOrderTest` checks every registered module against [ORDER]).
     */
    @JvmStatic
    val MIGRATED: Set<String> by lazy { ScriptModules.moduleNames.toSet() }

    /**
     * The modules `jsox.extendAll()` and the "extend JavaScript built-in objects" option apply, in the order of the old
     * `Jsox.presetModules` (roadmap P2.1).
     */
    @JvmField
    val BUILT_IN_EXTENSIONS: List<String> = listOf("Mathx", "Numberx", "Arrayx")

    private const val JSOX = "jsox"

    private val noDebugPrinting = ScriptErrorMapper.DebugPrinting { false }

    @JvmStatic
    val isEnabled: Boolean
        get() = Pref.isNewScriptModuleLayerEnabled

    /**
     * A mounter of the host registry for [runtime]'s [global] object, or null when the switch is off.
     *
     * The debug printing policy (appendix C Q4: a master switch, off by default, plus a per-module list, all on by
     * default) is read from the preferences here, once per runtime like the switch itself (the developer options say
     * changes apply to scripts started afterwards), so mapping a Java exception reads no preference (R2); loading this
     * object (unit tests read [ORDER]) changes nothing.
     */
    @JvmStatic
    fun mounterOrNull(runtime: ScriptRuntime, global: ScriptableObject): ScriptModuleMounter? {
        if (!isEnabled) return null
        val excluded = if (Pref.isScriptModuleDebugPrintEnabled) Pref.scriptModuleDebugPrintDisabledModules else null
        ScriptErrorMapper.debugPrinting = excluded?.let { ScriptErrorMapper.DebugPrinting { module -> ScriptModuleDebugSettings.prints(module, true, it) } } ?: noDebugPrinting
        return ScriptModuleMounter(ScriptModules, runtime, runtime.topLevelScope, global, order = ORDER)
    }

    /**
     * Mounts the top-level modules of [ORDER] in turn: each migrated one with [mounter], every other one (and all of
     * them without a mounter) by [legacy], the old augment call of the module and its sub-modules.
     */
    @JvmStatic
    inline fun mountInOrder(mounter: ScriptModuleMounter?, legacy: (name: String) -> Unit) {
        for (name in TOP_LEVEL_ORDER) mountOr(mounter, name) { legacy(name) }
    }

    /** Installs the migrated module [name] with [mounter], or runs [legacy] (the old augment call) without one. */
    @JvmStatic
    inline fun mountOr(mounter: ScriptModuleMounter?, name: String, legacy: () -> Unit) {
        if (mounter == null || name !in MIGRATED || !mounter.install(name)) legacy()
    }

    /**
     * The "extend JavaScript built-in objects" option in `initEpilogue`: with [mounter] and jsox migrated, the modules of
     * [BUILT_IN_EXTENSIONS] extend their built-in objects ([ScriptBuiltInExtender]); otherwise [legacy] runs (the old
     * `Jsox.extendAllRhinoWithRuntime`).
     */
    @JvmStatic
    fun extendBuiltInsOr(mounter: ScriptModuleMounter?, legacy: () -> Unit) {
        if (mounter == null || JSOX !in MIGRATED) legacy() else BUILT_IN_EXTENSIONS.forEach { mounter.builtIns.extend(it) }
    }

}
