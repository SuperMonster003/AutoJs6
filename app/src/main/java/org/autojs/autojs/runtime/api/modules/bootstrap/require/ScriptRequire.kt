package org.autojs.autojs.runtime.api.modules.bootstrap.require

import org.autojs.autojs.engine.encryption.ScriptEncryption
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.callMethod
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.literal
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.method
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.name
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.prop
import org.autojs.autojs.runtime.api.modules.bootstrap.require.ScriptModuleResolver.Resolved
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.ACallFrame
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.BoundFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.SerializableCallable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.WrappedException
import java.io.File
import java.io.FileNotFoundException
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The global `require` and `Module` of a script runtime with the new script module layer (roadmap P3.7, design
 * `docs/augment/P3-7-require-design.md`), formerly `assets/modules/jvm-npm.js`, which stays for the runtimes without it.
 *
 * Names, `length`, attributes and behavior are those of jvm-npm: the lookup of [ScriptModuleResolver], `require.cache`
 * keyed by canonical paths and written before a module runs, a module run as the body of
 * `function (exports, require, module, __filename, __dirname)` called with `this` as `module.exports` (so its top-level
 * declarations are local, issue #564), the Node core modules and the ids missed by the lookup from [sources], and URLs from
 * the engine's native `require`. A script that replaces `Module._load` sees every load go through it, as before. The
 * differences are the nine fixes of appendix C Q81 and the error of Q83 (appendix E "P3.7"):
 *
 * - a parent directory is the parent of `parent.id` as a path (jvm-npm also split it at `,` and `|`);
 * - a cached module gives its `exports` and any other cached value is given as is, even `undefined` exports or a JSON
 *   value with an `exports` key;
 * - an empty file is an empty module;
 * - the extensions are matched in any case for loading too (`./A.JS` loads, where jvm-npm returned undefined);
 * - nothing is printed when the lookup misses;
 * - the source name of a module error is the module path, with the same line numbers;
 * - an id that is not a non-empty string is a TypeError (D10);
 * - `Module.runMain` of a missing file is the not-found error (jvm-npm read the file `false`);
 * - IO_ERROR and PARSE_ERROR are real Error objects with `message` (with the cause's message), `code` and `cause`;
 * - a missing module is `Error: Cannot find module '<id>' (from <dir>)` with code `MODULE_NOT_FOUND`, where jvm-npm gave
 *   the native `require`'s error.
 *
 * zh-CN: 启用新脚本模块层的运行时的全局 `require` 与 `Module` (ROADMAP P3.7, 设计文档 `docs/augment/P3-7-require-design.md`), 原为
 * `assets/modules/jvm-npm.js` (未启用新层的运行时仍用它). 名称, length, 特性与行为同 jvm-npm: [ScriptModuleResolver] 的解析, 以规范路径为键,
 * 在模块执行前写入的 `require.cache`, 模块作为 `function (exports, require, module, __filename, __dirname)` 的函数体执行并以
 * `module.exports` 为 this 调用 (顶层声明为局部变量, issue #564), Node 核心模块与本地未命中的 id 来自 [sources], URL 来自引擎的原生 require.
 * 脚本替换 `Module._load` 后所有加载都经过它, 同以前. 与 jvm-npm 的差别是附录 C Q81 的 9 项修复与 Q83 的错误 (附录 E "P3.7"): 父目录按路径取
 * `parent.id` 的上级 (jvm-npm 还按 `,` 与 `|` 拆分); 缓存的模块给出其 exports, 其他缓存值原样给出, exports 为 undefined 或 JSON 值含 exports
 * 键时也是如此; 空文件为空模块; 加载时扩展名同样不分大小写 (`./A.JS` 可加载, jvm-npm 返回 undefined); 解析未命中时不打印; 模块错误的源名为模块路径,
 * 行号不变; id 不是非空字符串时为 TypeError (D10); `Module.runMain` 找不到文件时为 "找不到模块" 错误 (jvm-npm 读取文件 `false`); IO_ERROR 与
 * PARSE_ERROR 为真正的 Error, 带 message (含原因的消息), code 与 cause; 找不到模块为 `Error: Cannot find module '<id>' (from <目录>)`,
 * code 为 `MODULE_NOT_FOUND` (jvm-npm 为原生 require 的错误).
 *
 * @param scope the top-level scope, where modules are compiled and the objects are created
 * @param host the files of the runtime (`runtime.files` of jvm-npm)
 * @param sources the named modules (T11)
 * @param nativeRequire the engine's Rhino `require`, for `http://` and `https://` ids
 * @param nodePath the environment variable `NODE_PATH`
 */
internal class ScriptRequire(
    private val scope: VarScope,
    private val host: Host,
    private val sources: ScriptModuleSource,
    private val nativeRequire: (id: String) -> Any?,
    private val nodePath: () -> String? = { System.getenv(NODE_PATH) },
) {

    /** The files of the runtime. zh-CN: 运行时的文件操作. */
    interface Host {
        /** The working directory, the initial `require.root`. */
        fun cwd(): String?

        /** Whether [path] exists, relative to the working directory. */
        fun exists(path: String): Boolean

        fun readBytes(path: String): ByteArray
    }

    private val resolver = ScriptModuleResolver(packageMain = ::packageMain)

    private val cache: NativeObject = RhinoUtils.newNativeObject(scope)
    private val extensions: NativeObject = RhinoUtils.newNativeObject(scope)
    private var root: Any? = host.cwd()
    private var main: Any? = Undefined.instance

    /**
     * What a Kotlin step gives [RUNNER] to go on loading: `next.file` (with `next.core`) is to be loaded, or the file
     * passed to `Module._load` is not cached.
     * zh-CN: Kotlin 步骤交给 [RUNNER] 的 "继续加载" 标记: 需加载 `next.file` (及 `next.core`), 或传给 `Module._load` 的文件未缓存.
     */
    private val next: NativeObject = RhinoUtils.newNativeObject(scope)

    /** The getter and the setter of `module.exports`. zh-CN: `module.exports` 的 getter 与 setter. */
    private val exportsDescriptor = literal(
        scope,
        "get" to method(scope, "get", 0) { cx, s, thisObj, _ -> prop(cx, s, thisObj, EXPORTS_FIELD) },
        "set" to method(scope, "set", 1) { cx, s, thisObj, args ->
            RhinoScriptRuntime.setObjectProp(thisObj, EXPORTS_FIELD, args[0], cx, s)
            Undefined.instance
        },
    )

    /** The global `Module`. zh-CN: 全局 `Module`. */
    val module: BaseFunction = ModuleConstructor()

    /**
     * `require`, `Module._load`, `Module.runMain` and the target of `module.require` (bound to its module), compiled from
     * [RUNNER]: the script functions on the way from a script to a module body, which the interpreter calls in place, so
     * that a module can capture a continuation at its top level, as with jvm-npm. The Kotlin steps they call return
     * before any module runs.
     * zh-CN: 由 [RUNNER] 编译的 `require`, `Module._load`, `Module.runMain` 与 `module.require` 的目标函数 (绑定到各自的模块): 从脚本到模块函数体
     * 途经的都是脚本函数, 解释器原地调用它们, 因此模块顶层可以捕获 continuation, 同 jvm-npm. 它们调用的 Kotlin 步骤都在模块执行之前返回.
     */
    private val runner: Scriptable = compileRunner()

    /** The global `require`, which is `Module.require`. zh-CN: 全局 `require`, 即 `Module.require`. */
    val require: BaseFunction = runner.get(0, runner) as BaseFunction

    private val moduleRequireTarget = runner.get(3, runner) as Callable

    init {
        val cx = requireNotNull(Context.getCurrentContext()) { "ScriptRequire is created in a script thread" }
        require.defineProperty("resolve", LambdaFunction(scope, "", 2, SerializableCallable { c, _, _, args ->
            resolve(c, stringArgument(c, args.getOrElse(0) { Undefined.instance }, RESOLVE, ID), args.getOrElse(1) { Undefined.instance })
        }), ScriptableObject.EMPTY)
        require.defineProperty("paths", pathsFunction(), ScriptableObject.EMPTY)
        require.defineProperty("cache", cache, ScriptableObject.EMPTY)
        require.defineProperty("extensions", extensions, ScriptableObject.EMPTY)
        require.defineOwnProperty(cx, "main", literal(scope, "get" to method(scope, "get", 0) { _, _, _, _ -> main }))
        require.defineOwnProperty(cx, "root", literal(
            scope,
            "get" to method(scope, "get", 0) { _, _, _, _ -> root },
            "set" to method(scope, "set", 1) { _, _, _, args ->
                root = args[0]
                Undefined.instance
            },
        ))
        module.defineProperty("require", require, ScriptableObject.EMPTY)
        module.defineProperty("_load", runner.get(1, runner), ScriptableObject.EMPTY)
        module.defineProperty("runMain", runner.get(2, runner), ScriptableObject.EMPTY)
    }

    private fun compileRunner(): Scriptable {
        val cx = Context.getCurrentContext()
        val steps = arrayOf<Any?>(
            module,
            cache,
            next,
            method(scope, "request", 2) { c, _, _, args -> request(c, args[0], args[1]) },
            method(scope, "cached", 1) { c, _, _, args -> cached(c, stringArgument(c, args[0], LOAD, FILE)) },
            method(scope, "compile", 4) { c, _, _, args -> compile(c, RhinoScriptRuntime.toString(args[0]), args[1], args[2], args[3]) },
            method(scope, "dirname", 2) { _, _, _, args ->
                if (RhinoScriptRuntime.toBoolean(args[1])) "" else File(RhinoScriptRuntime.toString(args[0])).parent ?: ""
            },
            method(scope, "mainFile", 1) { c, _, _, args -> mainFile(c, args[0]) },
            method(scope, "setMain", 1) { c, _, _, args -> setMain(c, RhinoScriptRuntime.toString(args[0])) },
        )
        return cx.compileFunction(scope, RUNNER, RUNNER_NAME, 1, null).call(cx, scope, scope, steps) as Scriptable
    }

    /**
     * `new Module(id, parent, core)`: the fields of jvm-npm in its order, `exports` as an accessor of `_exports`, the
     * module added to `parent.children`, and `require` bound to the module.
     */
    private inner class ModuleConstructor : BaseFunction(scope, ScriptableObject.getFunctionPrototype(scope)) {

        init {
            // `Module.prototype` exists from the start, as for a script function, for `instanceof` from Kotlin.
            // zh-CN: Module.prototype 从一开始就存在 (同脚本函数), 供 Kotlin 侧做 instanceof 判断.
            setupDefaultPrototype(scope)
        }

        override fun getFunctionName() = "Module"

        override fun getLength() = 3

        override fun getArity() = 3

        override fun call(cx: Context, nt: Any?, scope: VarScope, thisObj: Any?, args: Array<out Any?>): Any? {
            val self = thisObj as? ScriptableObject ?: throw RhinoScriptRuntime.typeError(ScriptMessages.failedToCall("Module", "not an object: ${ScriptArgs.describe(thisObj)}"))
            val id = args.getOrElse(0) { Undefined.instance }
            val parent = args.getOrElse(1) { Undefined.instance }
            self.put(EXPORTS_FIELD, self, RhinoUtils.newNativeObject(this@ScriptRequire.scope))
            self.put("id", self, id)
            self.put("core", self, args.getOrElse(2) { Undefined.instance })
            self.put("parent", self, parent)
            self.put("children", self, RhinoUtils.newNativeArray(this@ScriptRequire.scope))
            self.put("filename", self, id)
            self.put("loaded", self, false)
            self.defineOwnProperty(cx, "exports", exportsDescriptor)
            if (RhinoScriptRuntime.toBoolean(parent)) {
                val children = prop(cx, scope, parent, "children")
                if (RhinoScriptRuntime.toBoolean(children)) callMethod(cx, scope, children, "push", self)
            }
            self.put("require", self, BoundFunction(cx, this@ScriptRequire.scope, moduleRequireTarget, self, emptyArray()))
            return Undefined.instance
        }

    }

    /** `require(id, parent)`: the steps of design (a), up to [next] for a script file to load with `Module._load`. */
    private fun request(cx: Context, idArg: Any?, parent: Any?): Any? {
        val request = stringArgument(cx, idArg, REQUIRE, ID)
        val id = request.removePrefix(NODE_PROTOCOL)
        if (id.isEmpty()) throw notFound(cx, request, null)
        val normalized = ScriptModuleResolver.normalizeName(id)
        if (sources.isCore(id) && !host.exists(normalized)) {
            val exports = sources.require(normalized)
            if (exports !== ScriptModuleSource.ABSENT) return exports
        }
        if (id.startsWith("http://") || id.startsWith("https://")) return nativeRequire(id)
        val base = baseOf(cx, parent)
        val (file, core) = when (val resolved = resolveLocal(cx, id, base)) {
            is Resolved.FileModule -> resolved.path to false
            is Resolved.ResourceModule -> resolved.name to true
            null -> {
                val exports = sources.require(id)
                if (exports !== ScriptModuleSource.ABSENT && RhinoScriptRuntime.toBoolean(exports)) return exports
                throw notFound(cx, request, base)
            }
        }
        val cached = cached(cx, file)
        if (cached !== next) return cached
        return when {
            isScriptFile(file) -> next.also {
                it.put("file", it, file)
                it.put("core", it, core)
            }
            file.endsWith(ScriptModuleResolver.JSON, ignoreCase = true) -> loadJson(cx, file)
            else -> Undefined.instance
        }
    }

    /**
     * The step of `Module._load` before the module [file] runs: its source (IO_ERROR), the functions of its `require`
     * and the wrapper.
     */
    private fun compile(cx: Context, file: String, core: Any?, moduleRequire: Any?, instance: Any?): Any? {
        val body = readSource(cx, file, RhinoScriptRuntime.toBoolean(core)).let { if (it.startsWith("#!")) "//$it" else it }
        RhinoScriptRuntime.setObjectProp(moduleRequire, "resolve", LambdaFunction(scope, "", 1, SerializableCallable { c, _, _, args ->
            resolve(c, stringArgument(c, args.getOrElse(0) { Undefined.instance }, RESOLVE, ID), instance)
        }), cx, scope)
        RhinoScriptRuntime.setObjectProp(moduleRequire, "paths", pathsFunction(), cx, scope)
        RhinoScriptRuntime.setObjectProp(moduleRequire, "cache", cache, cx, scope)
        RhinoScriptRuntime.setObjectProp(moduleRequire, "extensions", extensions, cx, scope)
        // The header shares the first line with the source, so that line numbers are those of the file.
        // zh-CN: 函数头与源码第一行同行, 因此行号与文件一致.
        return try {
            cx.compileFunction(scope, "$WRAPPER_HEAD$body\n}", file, 1, null)
        } catch (e: EvaluatorException) {
            // A SyntaxError (or TypeError) as from `new Function` in jvm-npm, not the InternalError of a Java exception.
            // zh-CN: 同 jvm-npm 中 new Function 的 SyntaxError (或 TypeError), 而不是 Java 异常的 InternalError.
            val details = e.details()
            val typeError = details.startsWith(TYPE_ERROR_PREFIX)
            throw RhinoScriptRuntime.constructError(
                if (typeError) "TypeError" else "SyntaxError", if (typeError) details.substring(TYPE_ERROR_PREFIX.length) else details,
                e.sourceName(), e.lineNumber(), e.lineSource(), e.columnNumber(),
            )
        }
    }

    private fun loadJson(cx: Context, file: String): Any? {
        val value = callMethod(cx, scope, name(cx, scope, "JSON"), "parse", readSource(cx, file, false))
        cache.put(file, cache, value)
        return value
    }

    /** The step of `Module.runMain(main)` before loading: the file of [mainArg], with `next.core` for a class loader resource. */
    private fun mainFile(cx: Context, mainArg: Any?): String {
        val id = stringArgument(cx, mainArg, RUN_MAIN, MAIN)
        val base = baseOf(cx, Undefined.instance)
        val (file, core) = when (val resolved = resolveLocal(cx, id, base)) {
            is Resolved.FileModule -> resolved.path to false
            is Resolved.ResourceModule -> resolved.name to true
            null -> throw notFound(cx, id, base)
        }
        next.put("core", next, core)
        return file
    }

    /** The step of `Module.runMain` after loading [file]: `require.main` and `process.mainModule`. */
    private fun setMain(cx: Context, file: String): Any? {
        main = ScriptableObject.getProperty(cache, file).takeIf { it !== Scriptable.NOT_FOUND && RhinoScriptRuntime.toBoolean(it) }
        val process = prop(cx, scope, name(cx, scope, "global"), "process")
        if (RhinoScriptRuntime.toBoolean(process)) RhinoScriptRuntime.setObjectProp(process, "mainModule", main, cx, scope)
        return Undefined.instance
    }

    /** `require.resolve(id, parent)`: the canonical path, `{ path, core: true }` for a class loader resource, or false. */
    private fun resolve(cx: Context, id: String, parent: Any?): Any? = when (val resolved = resolveLocal(cx, id, baseOf(cx, parent))) {
        is Resolved.FileModule -> resolved.path
        is Resolved.ResourceModule -> literal(scope, "path" to resolved.name, "core" to true)
        null -> false
    }

    private fun resolveLocal(cx: Context, id: String, base: String?): Resolved? = try {
        resolver.resolve(id, base, paths())
    } catch (e: ScriptModuleResolver.PackageError) {
        throw moduleError(cx, "Cannot load JSON file", PARSE_ERROR, scriptValueOf(cx, e.cause ?: e))
    }

    /** The directory of the module [parent], or `require.root` without one (jvm-npm's findRoot, Q81 fix 1). */
    private fun baseOf(cx: Context, parent: Any?): String? {
        if (RhinoScriptRuntime.toBoolean(parent)) {
            val id = prop(cx, scope, parent, "id")
            if (RhinoScriptRuntime.toBoolean(id)) return File(RhinoScriptRuntime.toString(id)).parent ?: ""
        }
        val root = root
        return if (root == null || Undefined.isUndefined(root)) null else RhinoScriptRuntime.toString(root)
    }

    /** `require.paths()`: `~/.node_modules`, `~/.node_libraries` and `NODE_PATH`. */
    private fun paths(): List<String> {
        val home = System.getProperty("user.home")
        val nodePath = nodePath()
        val separator = if (System.getProperty("os.name").orEmpty().lowercase().contains("win")) ";" else ":"
        return listOf("$home/.node_modules", "$home/.node_libraries") + if (nodePath.isNullOrEmpty()) emptyList() else nodePath.split(separator)
    }

    private fun pathsFunction() = LambdaFunction(scope, "", 0, SerializableCallable { _, _, _, _ ->
        RhinoUtils.newNativeArray(scope, paths().toTypedArray<Any?>())
    })

    /** The value `require` gives for the cached [file], or [next] (Q81 fix 2). */
    private fun cached(cx: Context, file: String): Any? {
        val value = ScriptableObject.getProperty(cache, file)
        if (value === Scriptable.NOT_FOUND || !RhinoScriptRuntime.toBoolean(value)) return next
        return if (value is Scriptable && module.hasInstance(value)) prop(cx, scope, value, "exports") else value
    }

    /** The `main` of the `package.json` at [path] for [resolver]; it reads and parses as jvm-npm did. */
    private fun packageMain(path: String): String? {
        val cx = Context.getCurrentContext()
        val pkg = callMethod(cx, scope, name(cx, scope, "JSON"), "parse", readSource(cx, path, false))
        val main = prop(cx, scope, pkg, "main")
        if (!RhinoScriptRuntime.toBoolean(main)) return null
        if (main !is CharSequence) throw RhinoScriptRuntime.typeError("The \"main\" of $path must be a string, not ${ScriptArgs.describe(main)}")
        return main.toString()
    }

    /** The source of [file]: decrypted when encrypted (the clear bytes are zeroed after use), else UTF-8 text. */
    private fun readSource(cx: Context, file: String, core: Boolean): String = try {
        when {
            core -> {
                val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(file) ?: throw FileNotFoundException(file)
                stream.use { String(it.readBytes(), Charsets.UTF_8) }
            }
            else -> {
                val bytes = host.readBytes(file)
                if (bytes.size >= EncryptedScriptFileHeader.BLOCK_SIZE && EncryptedScriptFileHeader.isValidFile(bytes)) {
                    val clearText = ScriptEncryption.decrypt(bytes, EncryptedScriptFileHeader.BLOCK_SIZE, bytes.size)
                    try {
                        String(clearText, Charsets.UTF_8)
                    } finally {
                        clearText.fill(0)
                    }
                } else {
                    String(bytes, Charsets.UTF_8)
                }
            }
        }
    } catch (e: Exception) {
        throw moduleError(cx, "Cannot read file [$file]: ${e.message}", IO_ERROR, scriptValueOf(cx, e))
    }

    /** The value a script `catch (e)` receives for [t]. zh-CN: 脚本 `catch (e)` 对 [t] 得到的值. */
    private fun scriptValueOf(cx: Context, t: Throwable): Any? {
        val throwable = t as? RhinoException ?: WrappedException(t)
        return ScriptableObject.getProperty(RhinoScriptRuntime.newCatchScope(throwable, null, "e", cx, scope), "e")
    }

    /** The Q83 error for [id] seen from [base]. */
    private fun notFound(cx: Context, id: String, base: String?): JavaScriptException {
        val from = if (base.isNullOrEmpty()) "" else " (from $base)"
        return moduleError(cx, "Cannot find module '$id'$from", MODULE_NOT_FOUND, NO_CAUSE)
    }

    /** An `Error` with [code] and [cause] at the position of the calling script. */
    private fun moduleError(cx: Context, message: String, code: String, cause: Any?): JavaScriptException {
        val error = cx.newObject(scope, "Error", arrayOf(message))
        ScriptableObject.putProperty(error, "code", code)
        if (cause !== NO_CAUSE) ScriptableObject.putProperty(error, "cause", cause)
        val line = IntArray(1)
        val sourceName = callerPosition(cx, line)
        ScriptableObject.putProperty(error, "fileName", sourceName)
        ScriptableObject.putProperty(error, "lineNumber", line[0])
        return JavaScriptException(error, sourceName, line[0])
    }

    /** [value] as a non-empty string argument [param] of [function], else a TypeError (D10). */
    private fun stringArgument(cx: Context, value: Any?, function: String, param: String): String {
        if (value !is CharSequence) {
            throw typeError(cx, ScriptMessages.failedToCall(function, ScriptMessages.argumentType(param, "string", ScriptArgs.describe(value))))
        }
        if (value.isEmpty()) {
            throw typeError(cx, ScriptMessages.failedToCall(function, ScriptMessages.argumentCannotBe(param, "an empty string")))
        }
        return value.toString()
    }

    private fun typeError(cx: Context, message: String): EcmaError {
        val line = IntArray(1)
        return RhinoScriptRuntime.constructError("TypeError", message, callerPosition(cx, line), line[0], null, 0)
    }

    /**
     * The source name and line ([line]) of the script that called into this `require`, for its errors: the innermost
     * interpreted frame outside [RUNNER_NAME], where [Context.getSourcePositionFromStack] would give the runner's own.
     * zh-CN: 调用本 require 的脚本的源名与行号 ([line]), 用于错误: [RUNNER_NAME] 之外最内层的解释帧 ([Context.getSourcePositionFromStack] 会给出 RUNNER 自身的位置).
     */
    private fun callerPosition(cx: Context, line: IntArray): String? {
        var frame: ACallFrame<*, *>? = cx.lastInterpreterFrame
        while (frame != null) {
            val sourceName = frame.getFnOrScript().descriptor.sourceName
            if (sourceName != RUNNER_NAME) {
                line[0] = frame.compilerData.getLineNumberFromPc(frame.pc, frame.getPcSourceLineStart())
                return sourceName
            }
            frame = frame.getParentFrame() ?: frame.getPreviousInterpreterFrame()
        }
        return Context.getSourcePositionFromStack(line)
    }

    companion object {

        const val MODULE_NOT_FOUND = "MODULE_NOT_FOUND"
        const val PARSE_ERROR = "PARSE_ERROR"
        const val IO_ERROR = "IO_ERROR"

        private const val NODE_PATH = "NODE_PATH"
        private const val NODE_PROTOCOL = "node:"
        private const val EXPORTS_FIELD = "_exports"
        private const val WRAPPER_HEAD = "function (exports, require, module, __filename, __dirname) {"
        private const val TYPE_ERROR_PREFIX = "TypeError: "

        private const val RUNNER_NAME = "<require>"

        /**
         * The script functions of [runner], with the Kotlin steps as parameters: `require` and `Module._load` follow
         * jvm-npm, `Module._load` read on each load (a script may replace it), and the cache entry removed when a module
         * fails (a `finally`, so that the error passes unchanged).
         */
        private const val RUNNER = """function (Module, cache, next, request, cached, compile, dirname, mainFile, setMain) {
    var methods = {
        require(id, parent) {
            var exports = request(id, parent);
            return exports === next ? Module._load(next.file, parent, next.core) : exports;
        },
        _load(file, parent, core) {
            var exports = cached(file);
            if (exports !== next) {
                return exports;
            }
            var module = new Module(file, parent, core);
            cache[file] = module;
            var loaded = false;
            try {
                var moduleRequire = module.require;
                var wrapper = compile(file, core, moduleRequire, module);
                exports = module.exports;
                wrapper.call(exports, exports, moduleRequire, module, file, dirname(file, core));
                module.loaded = true;
                exports = module.exports;
                loaded = true;
                return exports;
            } finally {
                if (!loaded) {
                    delete cache[file];
                }
            }
        },
        runMain(main) {
            var file = mainFile(main);
            var exported = next.core ? Module._load(file, undefined, true) : Module._load(file);
            setMain(file);
            return exported;
        },
    };
    var require = methods.require;
    return [require, methods._load, methods.runMain, function (id) {
        return require(id, this);
    }];
}"""

        private const val REQUIRE = "require"
        private const val RESOLVE = "require.resolve"
        private const val LOAD = "Module._load"
        private const val RUN_MAIN = "Module.runMain"
        private const val ID = "id"
        private const val FILE = "file"
        private const val MAIN = "main"

        private val NO_CAUSE = Any()

        private fun isScriptFile(file: String) = file.endsWith(ScriptModuleResolver.JS, ignoreCase = true) || file.endsWith(ScriptModuleResolver.MJS, ignoreCase = true)

        /**
         * Defines the global `Module` and `require` on [global] where jvm-npm's were (PERMANENT, after the modules), as lazy
         * data properties: [instance] is created when a script first reads either of them (roadmap T3).
         * zh-CN: 在 [global] 上 jvm-npm 原来的位置 (PERMANENT, 在各模块之后) 以惰性数据属性定义全局 `Module` 与 `require`: 脚本首次读取其一时才创建 [instance] (T3).
         */
        fun mount(global: ScriptableObject, instance: () -> ScriptRequire) {
            LazyDataSlot.define(global, "Module", ScriptableObject.PERMANENT) { instance().module }
            LazyDataSlot.define(global, "require", ScriptableObject.PERMANENT) { instance().require }
        }

    }

}
