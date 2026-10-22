package org.autojs.autojs.runtime.api.modules.recorder

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptPrototype

/**
 * The `recorder` module on the new script module layer (roadmap P3.5): `recorder(key?, timestamp?)` saves a record or
 * returns the time since it (a function as key is called and timed), and the runtime's Java object
 * `org.autojs.autojs.runtime.api.Recorder` as prototype provides `save`, `load`, `isLessThan`, `isGreaterThan`, `has`,
 * `remove` and `clear`, as the current `augment/recorder/Recorder`. As documented, the second argument of a named record
 * is accepted and not used; more than two arguments are a TypeError (appendix C Q7, as before).
 *
 * zh-CN: 新脚本模块层上的 recorder 模块 (P3.5): recorder(key?, timestamp?) 保存记录或返回距该记录的时间 (key 为函数时调用并计时), 运行时的 Java 对象
 * org.autojs.autojs.runtime.api.Recorder 作为原型提供 save, load, isLessThan, isGreaterThan, has, remove 与 clear, 与现有 augment/recorder/Recorder 相同. 按文档,
 * 命名记录的第二个参数被接受但不使用; 多于两个参数为 TypeError (附录 C Q7, 与此前相同).
 */
@ScriptModule(name = "recorder")
class RecorderModule(private val runtime: ScriptRuntime) {

    @ScriptPrototype
    val prototype: Any
        get() = runtime.recorder

    @ScriptInvoke
    fun invoke(key: Any? = null, timestamp: Any? = null): Double = runtime.recorder.shortcut(key, timestamp)

}
