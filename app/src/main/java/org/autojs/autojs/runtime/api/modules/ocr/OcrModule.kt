package org.autojs.autojs.runtime.api.modules.ocr

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.ocr.Ocr
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptToString
import org.autojs.autojs.runtime.api.modules.ocr.OcrArgs.ABSENT
import java.util.function.Supplier

/**
 * The `ocr` module on the new script module layer (roadmap P3.1): the recognition with the selected engine (the module
 * call, `recognizeText`, `detect`), the engine selection of the script (`mode`, `tap`), `summary` and the engine modules
 * `ocr.mlkit` / `ocr.paddle` / `ocr.rapid` ([OcrMLKitModule] and the others). The arguments are checked by [OcrArgs]; the
 * recognition, the selection and the summary stay with the current `augment/ocr/Ocr` functions (the `mode` getter reads
 * their supplier), which compare a mode with the engine module objects through `ScriptRuntime.augmentedOcrMLKit` and the
 * others (the new layer's objects when it serves the module). `tap(undefined)` selects automatically as documented, unlike
 * `tap()`.
 *
 * zh-CN: 新脚本模块层上的 ocr 模块 (P3.1): 使用所选引擎识别 (模块调用, recognizeText, detect), 脚本的引擎选择方式 (mode, tap), summary, 以及引擎模块
 * ocr.mlkit / ocr.paddle / ocr.rapid ([OcrMLKitModule] 等). 参数由 [OcrArgs] 检查; 识别, 选择与摘要仍由现有 augment/ocr/Ocr 函数完成 (mode 的 getter 读取其
 * Supplier), 它们经 ScriptRuntime.augmentedOcrMLKit 等与引擎模块对象比较模式 (新层提供该模块时为新层的对象). 按文档 tap(undefined) 恢复自动选择, tap() 则为错误.
 */
@ScriptModule(name = "ocr")
class OcrModule(private val runtime: ScriptRuntime) {

    /** The `mode` getter of the current module (the requested engine, or the first available one when automatic). */
    private val currentMode: Supplier<Any?> by lazy { Ocr(runtime).selfAssignmentGettersAndSetters.single { it.first == "mode" }.second }

    @ScriptInvoke
    fun invoke(input: Any? = ABSENT, optionsOrRegion: Any? = ABSENT): Any =
        Ocr.recognizeText(runtime, OcrArgs.recognition(MODULE, runtime, input, optionsOrRegion))

    @ScriptFunction(name = "toString", toStringRole = ScriptToString.LITERAL)
    fun toStringOf(): String = Ocr.toString(runtime, emptyArray())

    @ScriptFunction
    fun tap(mode: Any?) {
        OcrArgs.mode("$MODULE.tap", runtime, mode)
        Ocr.tap(runtime, arrayOf(mode))
    }

    @ScriptFunction
    fun recognizeText(input: Any? = ABSENT, optionsOrRegion: Any? = ABSENT): Any =
        Ocr.recognizeText(runtime, OcrArgs.recognition("$MODULE.recognizeText", runtime, input, optionsOrRegion))

    @ScriptFunction
    fun detect(input: Any? = ABSENT, optionsOrRegion: Any? = ABSENT): Any =
        Ocr.detect(runtime, OcrArgs.recognition("$MODULE.detect", runtime, input, optionsOrRegion))

    @ScriptFunction
    fun summary(): String = Ocr.summary(runtime, emptyArray())

    @ScriptGetter
    var mode: Any?
        get() = currentMode.get()
        set(value) {
            OcrArgs.mode("$MODULE.mode", runtime, value)
            Ocr.tap(runtime, arrayOf(value))
        }

    private companion object {
        const val MODULE = "ocr"
    }

}
