package org.autojs.autojs.runtime.api.modules.sensors

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptPrototype

/**
 * The `sensors` module on the new script module layer (roadmap P3.5): an object without own members whose prototype is
 * the runtime's Java object `org.autojs.autojs.runtime.api.Sensors` (an event emitter with `register`, `unregister` and
 * the delay constants), as the current `augment/sensors/Sensors`; the prototype is read when the module is first used,
 * after the runtime has created that object.
 *
 * zh-CN: 新脚本模块层上的 sensors 模块 (P3.5): 没有自有成员的对象, 原型为运行时的 Java 对象 org.autojs.autojs.runtime.api.Sensors (带 register, unregister 与
 * 延迟常量的事件发射器), 与现有 augment/sensors/Sensors 相同; 原型在首次使用模块时读取, 此时运行时已创建该对象.
 */
@ScriptModule(name = "sensors")
class SensorsModule(private val runtime: ScriptRuntime) {

    @ScriptPrototype
    val prototype: Any
        get() = runtime.sensors

}
