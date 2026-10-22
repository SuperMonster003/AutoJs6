package org.autojs.autojs.runtime.api.modules.media

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptPrototype

/**
 * The `media` module on the new script module layer (roadmap P3.5): an object without own members whose prototype is the
 * runtime's Java object `org.autojs.autojs.runtime.api.Media`, which provides every documented member, as the current
 * `augment/media/Media` (and the obsolete `__media__.js`, whose object inherited from the same Java object).
 *
 * zh-CN: 新脚本模块层上的 media 模块 (P3.5): 没有自有成员的对象, 原型为运行时的 Java 对象 org.autojs.autojs.runtime.api.Media, 由它提供文档所述的全部成员,
 * 与现有 augment/media/Media (以及原型为同一 Java 对象的旧 __media__.js) 相同.
 */
@ScriptModule(name = "media")
class MediaModule(private val runtime: ScriptRuntime) {

    @ScriptPrototype
    val prototype: Any
        get() = runtime.media

}
