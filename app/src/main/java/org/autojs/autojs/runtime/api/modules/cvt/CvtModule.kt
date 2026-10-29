package org.autojs.autojs.runtime.api.modules.cvt

import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptProperty

/**
 * The `cvt` module on the new script module layer (roadmap P2.2): unit conversion, at present byte units in its
 * sub-module [CvtBytesModule] (`cvt.bytes`).
 *
 * zh-CN: 新脚本模块层上的 cvt 模块 (P2.2): 单位换算, 目前只有子模块 cvt.bytes 的字节单位.
 */
@ScriptModule(name = "cvt")
object CvtModule

/**
 * `cvt.bytes` (roadmap P2.2): a callable object converting byte values between units ([ByteUnits]), with the forced
 * modes `strict` / `loose` and the unit constants. Its overloads are resolved by the type of the last argument, so the
 * functions take the arguments after `source` as a rest parameter (at most three, as the current module accepts; their
 * `arity` declares the 1 to 4 arguments for the metadata, appendix C Q171).
 *
 * zh-CN: cvt.bytes (P2.2): 在字节单位之间换算的可调用对象 (ByteUnits), 带强制模式 strict / loose 与单位常量. 重载按最后一个参数的类型区分, 因此 source 之后的参数以
 * 剩余参数接收 (至多三个, 与现有模块相同; arity 向元数据声明 1 至 4 个参数, 附录 C Q171).
 */
@ScriptModule(name = "bytes", parent = CvtModule::class, dollarPrefix = false)
object CvtBytesModule {

    @ScriptProperty
    val UNITS: String = ByteUnits.UNITS

    @ScriptProperty
    val AUTO: String = ByteUnits.AUTO

    @ScriptProperty
    val IEC_DIV: Long = ByteUnits.IEC_DIV

    @ScriptProperty
    val SI_DIV: Long = ByteUnits.SI_DIV

    @ScriptInvoke(arity = [1, 4])
    fun invoke(source: Any?, vararg args: Any?): Any = convert("cvt.bytes", source, args, ByteUnits.Mode.DEFAULT)

    @ScriptFunction(arity = [1, 4])
    fun strict(source: Any?, vararg args: Any?): Any = convert("cvt.bytes.strict", source, args, ByteUnits.Mode.STRICT)

    @ScriptFunction(arity = [1, 4])
    fun loose(source: Any?, vararg args: Any?): Any = convert("cvt.bytes.loose", source, args, ByteUnits.Mode.LOOSE)

    private fun convert(function: String, source: Any?, args: Array<out Any?>, mode: ByteUnits.Mode): Any =
        ByteUnits.number(ByteUnits.request(function, source, args, mode))

}
