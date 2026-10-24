package org.autojs.autojs.runtime.api.modules.gallery

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.gallery.Gallery
import org.autojs.autojs.runtime.api.modules.AugmentMembers
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.ScriptRuntimeFields

/**
 * `gallery` (functions with their `Async` forms; GalleryError as a data property) on the new script module layer
 * (roadmap P4.1): only the mounting layer changes. Every member hands its arguments unchanged to the current
 * `augment/gallery/Gallery`, so values, argument errors and the module's own coded errors stay those of the current
 * module (a coded error is a script error and passes the new layer's error mapping unchanged, T5); the data properties
 * the current module computes at run time are defined on the module object as they are when it is built.
 *
 * zh-CN: 新脚本模块层上的 gallery (函数及其 Async 形式; GalleryError 为数据属性) (P4.1): 只换挂载层. 每个成员把参数原样交给现有
 * augment/gallery/Gallery, 取值, 参数错误与模块自己的带错误码的错误都与现有模块相同 (带错误码的错误是脚本错误, 原样通过新层的错误映射, T5);
 * 现有模块在运行时计算的数据属性在模块对象构建时照原值定义.
 */
@ScriptModule(name = "gallery")
class GalleryModule(private val runtime: ScriptRuntime) {

    private val impl by lazy { Gallery(runtime, ScriptRuntimeFields.gallery(runtime)) }

    @ScriptInstall
    fun install(installation: ScriptModuleInstallation) = installation.onBuilt { AugmentMembers.defineProperties(impl, it) }

    @ScriptFunction
    fun pick(vararg args: Any?): Any? = impl.pick(runtime, args)

    @ScriptFunction
    fun pickAsync(vararg args: Any?): Any? = impl.pickAsync(runtime, args)

    @ScriptFunction
    fun view(vararg args: Any?): Any? = impl.view(runtime, args)

    @ScriptFunction
    fun viewAsync(vararg args: Any?): Any? = impl.viewAsync(runtime, args)

    @ScriptFunction
    fun edit(vararg args: Any?): Any? = impl.edit(runtime, args)

    @ScriptFunction
    fun editAsync(vararg args: Any?): Any? = impl.editAsync(runtime, args)

    @ScriptFunction
    fun convert(vararg args: Any?): Any? = impl.convert(runtime, args)

    @ScriptFunction
    fun convertAsync(vararg args: Any?): Any? = impl.convertAsync(runtime, args)

    @ScriptFunction
    fun isAvailable(vararg args: Any?): Any? = impl.isAvailable(runtime, args)

    @ScriptFunction
    fun isAvailableAsync(vararg args: Any?): Any? = impl.isAvailableAsync(runtime, args)

}
