package org.autojs.autojs.runtime.api.modules.fmt

import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.autojs.autojs.runtime.api.modules.cvt.ByteUnits

/**
 * The `fmt` module on the new script module layer (roadmap P2.2): formatting, at present byte values in its sub-module
 * [FmtBytesModule] (`fmt.bytes`).
 *
 * zh-CN: 新脚本模块层上的 fmt 模块 (P2.2): 格式化, 目前只有子模块 fmt.bytes 的字节数据.
 */
@ScriptModule(name = "fmt")
object FmtModule

/**
 * `fmt.bytes` (roadmap P2.2): a callable object formatting byte values as text with a unit ([ByteUnits]), with the
 * forced modes `strict` / `loose` and the unit constants; the arguments are those of `cvt.bytes` plus the formatting
 * options `useIecIdentifier` (also a boolean last argument), `useSpace` and `trimTrailingZero`.
 *
 * zh-CN: fmt.bytes (P2.2): 把字节数据格式化为带单位文本的可调用对象 (ByteUnits), 带强制模式 strict / loose 与单位常量; 参数与 cvt.bytes 相同, 另有格式选项
 * useIecIdentifier (也可为最后一个布尔参数), useSpace 与 trimTrailingZero.
 */
@ScriptModule(name = "bytes", parent = FmtModule::class, dollarPrefix = false)
object FmtBytesModule {

    @ScriptProperty
    val UNITS: String = ByteUnits.UNITS

    @ScriptProperty
    val AUTO: String = ByteUnits.AUTO

    @ScriptProperty
    val IEC_DIV: Long = ByteUnits.IEC_DIV

    @ScriptProperty
    val SI_DIV: Long = ByteUnits.SI_DIV

    @ScriptInvoke
    fun invoke(source: Any?, vararg args: Any?): String = format("fmt.bytes", source, args, ByteUnits.Mode.DEFAULT)

    @ScriptFunction
    fun strict(source: Any?, vararg args: Any?): String = format("fmt.bytes.strict", source, args, ByteUnits.Mode.STRICT)

    @ScriptFunction
    fun loose(source: Any?, vararg args: Any?): String = format("fmt.bytes.loose", source, args, ByteUnits.Mode.LOOSE)

    private fun format(function: String, source: Any?, args: Array<out Any?>, mode: ByteUnits.Mode): String =
        ByteUnits.string(ByteUnits.request(function, arrayOf(source, *args), mode))

}
