package org.autojs.autojs.runtime.api.modules.yolo

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule

/**
 * The `yolo` module on the new script module layer (roadmap P3.1): `load(modelDir, options?)` of the current
 * `augment/yolo/Yolo`, which opens a session with the explicitly chosen YOLO plugin and returns the current
 * `YoloDetectorNativeObject` (appendix C Q24). The arguments are handed on as given: the documentation makes every
 * failure of `load`, wrong arguments included, a coded error (`YOLO_INVALID_ARGUMENT`, `YOLO_COMPONENT_REQUIRED`, ...,
 * read from `error.code`), which the current function raises (to be moved out before P5.2).
 *
 * zh-CN: 新脚本模块层上的 yolo 模块 (P3.1): 现有 augment/yolo/Yolo 的 load(modelDir, options?), 它与显式指定的 YOLO 插件建立会话并返回现有
 * YoloDetectorNativeObject (附录 C Q24). 参数原样交给现有函数: 文档规定 load 的一切失败 (含参数错误) 都是带错误码的异常 (YOLO_INVALID_ARGUMENT,
 * YOLO_COMPONENT_REQUIRED 等, 读取 error.code), 由现有函数抛出 (P5.2 前迁出).
 */
@ScriptModule(name = "yolo")
class YoloModule(private val runtime: ScriptRuntime) {

    @ScriptFunction(arity = [1, 2])
    fun load(vararg args: Any?): YoloDetectorNativeObject = Yolo.load(runtime, args)

}
