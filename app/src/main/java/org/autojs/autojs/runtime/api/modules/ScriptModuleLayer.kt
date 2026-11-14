package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.core.pref.keys.DeveloperKeys
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.generated.ScriptModules
import org.mozilla.javascript.ScriptableObject

/**
 * Entry of the script module layer for `ScriptRuntime` (roadmap D8 / T8; the only module layer since P5.2, which
 * removed the old `augment` package and its switch). [ORDER] decides where every module is mounted (appendix C Q8):
 * `ScriptRuntime.augment()` installs its top-level modules in order from the generated registry, so the order of
 * `Object.keys(global)` stays the one of the old `augment()`.
 *
 * zh-CN: 脚本模块层在 ScriptRuntime 中的入口 (D8 / T8; P5.2 删除旧 augment 包与开关后为唯一的模块层). [ORDER] 决定每个模块的挂载位置 (附录 C Q8):
 * `ScriptRuntime.augment()` 按序从生成的注册表安装其中的顶层模块, 因此 Object.keys(global) 的顺序仍与原 augment() 相同.
 */
object ScriptModuleLayer {

    /**
     * Every module of the old `ScriptRuntime.augment()` in its mounting order (91 augment calls, appendix C Q8; 92 since
     * `memory` of docs/memory/ROADMAP.md P5.1, 93 since `speech.speaker` of the 3-Macaw Speech roadmap P8.3.2): each
     * top-level module by its primary name, followed by its sub-modules as
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
        "speech", "speech.models", "speech.speaker", "compose", "memory", "imgui", "virtualScreen",
    )

    /** The top-level entries of [ORDER], the names `ScriptRuntime.augment()` mounts in turn. */
    @JvmField
    val TOP_LEVEL_ORDER: List<String> = ORDER.filter { '.' !in it }

    /**
     * The top-level modules of the host registry `generated.ScriptModules` (every @ScriptModule class of this package,
     * P2.1; [ORDER] decides where each is mounted). A module brings all its sub-modules (`ScriptModuleOrderTest` checks
     * every registered module against [ORDER] and every top-level entry of [ORDER] against the registry).
     */
    @JvmStatic
    val MIGRATED: Set<String> by lazy { ScriptModules.moduleNames.toSet() }

    /**
     * The modules `jsox.extendAll()` and the "extend JavaScript built-in objects" option apply, in the order of the old
     * `Jsox.presetModules` (roadmap P2.1).
     */
    @JvmField
    val BUILT_IN_EXTENSIONS: List<String> = listOf("Mathx", "Numberx", "Arrayx")

    private val noDebugPrinting = ScriptErrorMapper.DebugPrinting { false }

    /**
     * A mounter of the host registry for [runtime]'s [global] object.
     *
     * The debug printing policy (appendix C Q4: a master switch, off by default, plus a per-module list, all on by
     * default) is read from the preferences here, once per runtime (the developer options say changes apply to scripts
     * started afterwards), so mapping a Java exception reads no preference (R2); loading this object (unit tests read
     * [ORDER]) changes nothing.
     */
    @JvmStatic
    fun mounter(runtime: ScriptRuntime, global: ScriptableObject): ScriptModuleMounter {
        val excluded = if (DeveloperKeys.SCRIPT_MODULE_DEBUG_PRINT.get()) DeveloperKeys.scriptModuleDebugPrintDisabledModules else null
        ScriptErrorMapper.debugPrinting = excluded?.let { ScriptErrorMapper.DebugPrinting { module -> ScriptModuleDebugSettings.prints(module, true, it) } } ?: noDebugPrinting
        return ScriptModuleMounter(ScriptModules, runtime, runtime.topLevelScope, global, order = ORDER)
    }

    /** Installs the top-level modules of [ORDER] in turn with [mounter], each with its sub-modules. */
    @JvmStatic
    fun mountInOrder(mounter: ScriptModuleMounter) {
        for (name in TOP_LEVEL_ORDER) check(mounter.install(name)) { "Script module $name of ScriptModuleLayer.ORDER is not in the registry" }
    }

    /**
     * The "extend JavaScript built-in objects" option in `initEpilogue` and `jsox.extendAll()`: the modules of
     * [BUILT_IN_EXTENSIONS] extend their built-in objects ([ScriptBuiltInExtender]).
     */
    @JvmStatic
    fun extendBuiltIns(mounter: ScriptModuleMounter) {
        BUILT_IN_EXTENSIONS.forEach { mounter.builtIns.extend(it) }
    }

}
