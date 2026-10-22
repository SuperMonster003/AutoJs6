package org.autojs.autojs.runtime.api.modules.zip

import okhttp3.internal.closeQuietly
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.zip.ZipNativeObject
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `zip` module on the new script module layer (roadmap P3.5): `zip(zipPath)` and `zip.open` / `zipFile` / `zipDir` /
 * `zipFiles` / `unzip`, in the order of the current `augment/zip/Zip`. Every function returns the documented
 * ZipFileHandle, which is still the current `ZipNativeObject` (appendix C Q24: the instance classes of P3.5 keep their
 * current classes until P5.2); it parses the options and runs the Zip4j operations as before.
 *
 * Arguments follow the real signatures (T4, appendix C Q7). On top of the current checks: a nullish path is a TypeError
 * (the current module threw WrappedIllegalArgumentException), a blank path a RangeError (IllegalArgumentException),
 * options that are not an object a TypeError (IllegalArgumentException), as are a destination that is neither a path
 * nor nullish and a `filePathList` that is not an array or a Java iterable (D10). As documented, a plain object in
 * place of the destination path is the options when no options follow; a missing source file of `zipFiles` stays an
 * IllegalArgumentException (host state), and Zip4j and option errors keep their Java types (T5).
 *
 * zh-CN: 新脚本模块层上的 zip 模块 (P3.5): zip(zipPath) 与 zip.open / zipFile / zipDir / zipFiles / unzip, 顺序同现有 augment/zip/Zip. 各函数返回文档所述的
 * ZipFileHandle, 仍为现有 ZipNativeObject (附录 C Q24: P3.5 的实例类在 P5.2 前沿用现有类), 选项解析与 Zip4j 操作照旧. 参数按真实签名 (T4, 附录 C Q7);
 * 在现有检查之外: nullish 路径为 TypeError (现有模块抛 WrappedIllegalArgumentException), 空白路径为 RangeError (IllegalArgumentException), 选项不是对象为
 * TypeError (IllegalArgumentException), 目标既不是路径也不是 nullish, 以及 filePathList 不是数组或 Java Iterable 同为 TypeError (D10). 按文档, 目标路径位置
 * 是普通对象且其后没有选项时, 它就是选项; zipFiles 的源文件不存在仍为 IllegalArgumentException (宿主状态), Zip4j 与选项的错误保留 Java 类型 (T5).
 */
@ScriptModule(name = "zip")
class ZipModule(private val runtime: ScriptRuntime) {

    @ScriptInvoke
    fun invoke(zipPath: String): ZipNativeObject = handle(OPEN, nonBlank(MODULE, "zipPath", zipPath), null)

    @ScriptFunction
    fun open(zipPath: String, options: Any? = null): ZipNativeObject =
        handle(OPEN, nonBlank("zip.open", "zipPath", zipPath), options(FUNCTION_OPEN, options))

    @ScriptFunction
    fun zipFile(filePath: String, destZipPath: Any?, options: Any? = ABSENT): ZipNativeObject {
        val (destination, opts) = destinationAndOptions(FUNCTION_ZIP_FILE, "destZipPath", destZipPath, options)
        val file = File(runtime.files.nonNullPath(nonBlank(FUNCTION_ZIP_FILE, "filePath", filePath)))
        return handle(ZIP_FILE, destination ?: "${file.nameWithoutExtension}.zip", opts).tryWithSelf { zipFile.addFile(file, zipParameters) }
    }

    @ScriptFunction
    fun zipDir(dirPath: String, destZipPath: Any?, options: Any? = ABSENT): ZipNativeObject {
        val (destination, opts) = destinationAndOptions(FUNCTION_ZIP_DIR, "destZipPath", destZipPath, options)
        val dir = File(runtime.files.nonNullPath(nonBlank(FUNCTION_ZIP_DIR, "dirPath", dirPath)))
        return handle(ZIP_DIR, destination ?: "${dir.nameWithoutExtension}.zip", opts).tryWithSelf {
            zipFile.createSplitZipFileFromFolder(dir, zipParameters, false, -1)
        }
    }

    @ScriptFunction
    fun zipFiles(filePathList: Any, destZipPath: Any?, options: Any? = ABSENT): ZipNativeObject {
        val paths: Iterable<*> = when {
            filePathList is NativeArray -> ScriptArgs.list(filePathList, FUNCTION_ZIP_FILES, "filePathList")
            filePathList is Wrapper && filePathList.unwrap() is Iterable<*> -> filePathList.unwrap() as Iterable<*>
            filePathList is Iterable<*> && filePathList !is ScriptableObject -> filePathList
            else -> throw UtilValues.typeError(FUNCTION_ZIP_FILES, ScriptMessages.argumentType("filePathList", "Array | java.lang.Iterable", ScriptArgs.describe(filePathList)))
        }
        val (destination, opts) = destinationAndOptions(FUNCTION_ZIP_FILES, "destZipPath", destZipPath, options)
        val files = paths.mapIndexed { i, path ->
            File(runtime.files.nonNullPath(nonBlank(FUNCTION_ZIP_FILES, "filePathList[$i]", ScriptArgs.string(path, FUNCTION_ZIP_FILES, "filePathList[$i]")))).also {
                require(it.exists()) { "File ${it.absolutePath} does not exist" }
            }
        }
        val path = destination ?: when (files.size) {
            1 -> "${files.first().nameWithoutExtension}.zip"
            else -> files.map { if (it.isFile) it.parent else it.absolutePath }.toSet().singleOrNull()?.let { "${File(it).nameWithoutExtension}.zip" } ?: randomZipName()
        }
        return handle(ZIP_FILES, path, opts).tryWithSelf { zipFile.addFiles(files, zipParameters) }
    }

    @ScriptFunction
    fun unzip(zipPath: String, destPath: Any?, options: Any? = ABSENT): ZipNativeObject {
        val (destination, opts) = destinationAndOptions(FUNCTION_UNZIP, "destPath", destPath, options)
        return handle(UNZIP, nonBlank(FUNCTION_UNZIP, "zipPath", zipPath), opts).tryWithSelf {
            zipFile.extractAll(runtime.files.nonNullPath(destination ?: ""), unzipParameters)
        }
    }

    private fun handle(operation: String, path: String, options: ScriptableObject?) = ZipNativeObject(runtime, operation, path, options)

    /**
     * The destination path (null for the documented default) and the options: a plain object at the destination's place
     * is the options when [options] is omitted, as the current module did for two arguments.
     */
    private fun destinationAndOptions(function: String, param: String, destination: Any?, options: Any?): Pair<String?, ScriptableObject?> {
        if (options === ABSENT && destination is NativeObject) return null to destination
        val path = when (val value = if (destination is Wrapper) destination.unwrap() else destination) {
            null, Undefined.instance -> null
            is CharSequence, is Number, is Boolean -> RhinoScriptRuntime.toString(value)
            else -> throw UtilValues.typeError(function, ScriptMessages.argumentType(param, "string | null | undefined", ScriptArgs.describe(destination)))
        }
        return path to options(function, options.takeUnless { it === ABSENT })
    }

    private fun options(function: String, options: Any?): ScriptableObject? = when {
        ScriptArgs.isNullish(options) -> null
        options is ScriptableObject && options !is Callable && options !is NativeArray -> options
        else -> throw UtilValues.typeError(function, ScriptMessages.argumentType("options", "Object", ScriptArgs.describe(options)))
    }

    private fun ZipNativeObject.tryWithSelf(operation: ZipNativeObject.() -> Unit): ZipNativeObject {
        runCatching { operation() }.getOrElse { t -> zipFile.closeQuietly(); throw t }
        return this
    }

    internal companion object {

        /** The default of an omitted options argument (`zipFile(path, {...})` passes the options as the second argument). */
        private val ABSENT = Any()

        private const val MODULE = "zip"
        private const val FUNCTION_OPEN = "zip.open"
        private const val FUNCTION_ZIP_FILE = "zip.zipFile"
        private const val FUNCTION_ZIP_DIR = "zip.zipDir"
        private const val FUNCTION_ZIP_FILES = "zip.zipFiles"
        private const val FUNCTION_UNZIP = "zip.unzip"

        // Operation names shown by the handle (`name`, `toString`), as with the current module.
        private const val OPEN = "open"
        private const val ZIP_FILE = "zipFile"
        private const val ZIP_DIR = "zipDir"
        private const val ZIP_FILES = "zipFiles"
        private const val UNZIP = "unzip"

        /** [value] unless blank, which no file path can be (RangeError, D10). */
        fun nonBlank(function: String, param: String, value: String): String {
            if (value.isBlank()) throw UtilValues.rangeError(function, ScriptMessages.argumentCannotBe(param, UtilValues.quote(value)))
            return value
        }

        /** `yyyyMMdd-HHmmss-XXXX.zip` with four random upper-case hexadecimal characters (documented). */
        fun randomZipName(): String {
            val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            return "$time-${UUID.randomUUID().toString().substring(0, 4).uppercase()}.zip"
        }

    }

}
