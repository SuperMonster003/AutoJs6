package org.autojs.autojs.runtime.api.modules.files

import org.autojs.autojs.pio.PFileInterface
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import java.io.File
import org.autojs.autojs.runtime.api.Files as ApiFiles
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `files` module on the new script module layer (roadmap P3.4): the module object's prototype is the runtime's
 * `Files` (`ScriptRuntime.files`: read / write / copy / list / ...), `open` is also global, and the callable sub-module
 * `batchRename` ([FilesBatchRenameModule]) follows the functions, as with the current module.
 *
 * Arguments follow the real signatures (T4): `open` takes a path and optional mode, encoding and buffer size (an omitted
 * or nullish one takes the host's default; the current module read a middle `undefined` as the text "undefined"), `join`
 * and `toFile` take strings (a nullish part is a TypeError; the current module joined "null"), a buffer size without an
 * integer is a TypeError / RangeError (D10).
 *
 * zh-CN: 新脚本模块层上的 files 模块 (P3.4): 模块对象的原型是运行时的 Files (ScriptRuntime.files: 读写, 复制, 列举等), open 也在全局, 可调用的子模块
 * batchRename 位于各函数之后, 同现有模块. 参数按真实签名 (T4): open 接受路径及可选的模式, 编码与缓冲区大小 (省略或为 null / undefined 时取宿主的默认值;
 * 现有模块把中间的 undefined 读作文本 "undefined"), join 与 toFile 接受字符串 (null / undefined 为 TypeError; 现有模块拼接出 "null"), 缓冲区大小不是整数
 * 为 TypeError / RangeError (D10).
 */
@ScriptModule(name = "files")
class FilesModule(private val runtime: ScriptRuntime) {

    @ScriptPrototype
    val prototype: Any
        get() = runtime.files

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun open(path: String, mode: String? = null, encoding: String? = null, bufferSize: Any? = null): PFileInterface {
        val size = if (isNullish(bufferSize)) null else GlobalValues.int("files.open", "bufferSize", bufferSize)
        return runtime.files.open(path, mode, encoding, size)
    }

    /** The absolute path of [relativePath] against the working directory; the working directory itself without one. */
    @ScriptFunction
    fun path(relativePath: Any? = null): String? = runtime.files.path(if (isNullish(relativePath)) null else RhinoScriptRuntime.toString(relativePath))

    @ScriptFunction
    fun join(base: String, vararg paths: String): String = ApiFiles.join(base, *paths)

    @ScriptFunction
    fun toFile(path: String): File = File(runtime.files.nonNullPath(path))

}
