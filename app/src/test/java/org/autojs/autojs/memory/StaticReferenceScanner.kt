package org.autojs.autojs.memory

import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Kotlin port of the rules `tools/memory/static_audit.py --guard` uses to select the rows of the source guard whitelist
 * (docs/memory/ROADMAP.md T8 / P4.3): process-level fields of sections (a) and (c) and the files of section (b) that call
 * registerReceiver / bindService / addIdleHandler / Looper.prepare or create a WebView. Functions keep the names and the
 * order of the Python script, regular expressions are copied from it. They are compiled with UNICODE_CHARACTER_CLASS and
 * UNIX_LINES so that `\w`, `\s`, `\b` and `.` behave like Python's `re` on text whose line breaks are `\n` (the files are
 * read with universal newlines, as Python does). [StaticReferenceGuardTest] checks that both give the same rows.
 * zh-CN: `tools/memory/static_audit.py --guard` 选取源码守卫白名单行的规则的 Kotlin 移植 (docs/memory/ROADMAP.md T8 / P4.3): (a) 与 (c) 节的进程级字段,
 * 以及 (b) 节中调用 registerReceiver / bindService / addIdleHandler / Looper.prepare 或创建 WebView 的文件. 函数名与顺序同 Python 脚本, 正则照搬;
 * 编译时加 UNICODE_CHARACTER_CLASS 与 UNIX_LINES, 使 `\w`, `\s`, `\b`, `.` 在以 `\n` 换行的文本上与 Python `re` 一致 (文件按通用换行读取, 同 Python).
 * [StaticReferenceGuardTest] 核对两边给出相同的行.
 */
object StaticReferenceScanner {

    /** One generated whitelist row; [owner] and [type] are for messages only. zh-CN: 一个生成的白名单行; [owner] 与 [type] 只用于提示信息. */
    data class Row(val kind: String, val path: String, val name: String, val detail: String, val line: Int = 0, val owner: String = "", val type: String = "") {
        val key get() = "$kind | $path | $name"
        val generated get() = "$kind | $path | $name | $detail"
    }

    private class Field(
        val file: String, val line: Int, val holder: String, val name: String, val type: String,
        val categories: List<String>, val holding: String, val owner: String,
    )

    private class Calls(val file: String, val registrations: Int, val releases: Int) {
        val paired get() = releases > 0
    }

    private val RHINO_SCOPE_TYPES = setOf(
        "Scriptable", "ScriptableObject", "NativeObject", "NativeArray", "NativeFunction", "BaseFunction",
        "TopLevelScope", "VarScope", "ImporterTopLevel", "NativeJavaObject", "IdScriptableObject",
    )
    private val ENGINE_TYPES = setOf("ScriptEngine", "JavaScriptEngine", "RhinoJavaScriptEngine", "LoopBasedJavaScriptEngine")
    private val WEAK_WRAPPERS = setOf("WeakReference", "SoftReference")
    private val COLLECTIONS = setOf(
        "List", "MutableList", "ArrayList", "Set", "MutableSet", "HashSet", "LinkedHashSet", "Map", "MutableMap",
        "HashMap", "LinkedHashMap", "ConcurrentHashMap", "CopyOnWriteArrayList", "CopyOnWriteArraySet",
        "SparseArray", "Array", "Collection", "Queue", "Deque", "ArrayDeque", "WeakHashMap", "LruCache",
    )

    /** Section (b) pairs the guard checks: name, registration, release. zh-CN: 守卫检查的 (b) 节配对: 名称, 登记, 释放. */
    private val GUARD_CALLS = listOf(
        Triple("registerReceiver", re("\\bregisterReceiver\\s*\\("), re("\\bunregisterReceiver\\s*\\(")),
        Triple("bindService", re("\\bbindService\\s*\\("), re("\\bunbindService\\s*\\(")),
        Triple("WebView(", re("(?<![\\w.])(?:new\\s+)?\\w*WebView\\s*\\(\\s*[\\w@]"), re("\\.destroy\\s*\\(\\s*\\)")),
        Triple("addIdleHandler", re("\\baddIdleHandler\\s*\\("), re("\\bremoveIdleHandler\\s*\\(")),
        Triple("Looper.prepare", re("\\bLooper\\.prepare\\s*\\("), re("\\.quit(?:Safely)?\\s*\\(|\\bquitForThread\\s*\\(")),
    )
    private val SCRIPT_PATHS = listOf(
        "runtime/", "execution/", "core/looper/", "core/image/", "core/ui/", "core/web/", "core/console/",
        "core/record/", "core/automator/", "core/accessibility/", "core/eventloop/", "core/floaty/", "rhino/",
        "engine/Runtime", "core/opencv/", "core/pref/Pref",
    )
    private val PAGE_PATHS = listOf("ui/", "theme/")
    private val KOTLIN_FACTORIES = linkedMapOf(
        "mutableListOf" to "MutableList", "mutableSetOf" to "MutableSet", "mutableMapOf" to "MutableMap",
        "arrayListOf" to "ArrayList", "hashSetOf" to "HashSet", "hashMapOf" to "HashMap",
        "linkedSetOf" to "LinkedHashSet", "linkedMapOf" to "LinkedHashMap",
    )
    private val REGISTRY_HINT = re(
        "Thread|Looper|Listener|Callback|Window|Wrapper|Engine|Runtime|ClassLoader|Receiver|" +
            "Connection|Observer|Handler|Execution|Session|Scope|Delegate|Bitmap|Image|\\bMat\\b",
    )
    private val CONSTANT_TOKENS = setOf(
        "List", "Set", "Map", "MutableList", "MutableSet", "Array", "String", "Int", "Integer", "Long", "Boolean",
        "Double", "Float", "Char", "Byte", "Short", "Pair", "IntRange", "Charset", "Regex", "Class", "KClass",
        "int", "long", "double", "float", "boolean", "byte", "char", "short", "get", "out", "in", "newKeySet",
        "ConcurrentHashMap", "HashMap", "LinkedHashMap", "HashSet", "SparseArray", "Unit", "Any",
    )

    private val IMPORT = re("^\\s*import\\s+([\\w.]+\\.(\\w+))\\s*;?\\s*$", Pattern.MULTILINE)
    private val TOKEN = re("[A-Za-z_][\\w.]*")
    private val OUTER_TYPE = re("\\s*([A-Za-z_][\\w.]*)")
    private val REGISTRY_SUFFIX = re("(?:Map|Set|List|Cache|Registry|Queue)$")
    private val WEAK_HASH_MAP = re("\\bWeakHashMap\\b")
    private val WEAK_REFERENCE = re("\\b(?:WeakReference|SoftReference)\\b")
    private val JAVA_STATIC = re(
        "\\bstatic\\b(?:\\s+(?:final|volatile|transient|public|private|protected))*\\s+" +
            "([A-Za-z_][\\w.]*(?:\\s*<[^;=(){}]*>)?(?:\\s*\\[\\s*\\])*)\\s+(\\w+)\\s*(?:=([^;]{0,300})|;)",
    )
    private val KOTLIN_PROPERTY = re(
        "^(?:(?:private|internal|public|protected|override|lateinit|const|@\\w+(?:\\([^)]*\\))?|" +
            "@field:\\w+|@Volatile|@JvmField|@JvmStatic)\\s+)*(?:val|var)\\s+(\\w+)\\s*" +
            "(?::\\s*([^=\\n{]+?))?\\s*(?:=\\s*([^\\n]*)|by\\s+([^\\n]*))?$",
    )
    private val INFERRED_TYPE = re("(?:lazy\\s*\\{\\s*)?(?:Collections\\.\\w+\\s*\\(\\s*)?([A-Z][\\w.]*\\s*(?:<.*>)?)\\s*[({]")
    private val FACTORY_TYPE = re("(?:lazy\\s*\\{\\s*)?(" + KOTLIN_FACTORIES.keys.joinToString("|") + ")\\s*(<.*>)\\s*\\(")
    private val COMPANION_HEADER = re("\\bcompanion\\s+object\\b")
    private val OBJECT_HEADER = re("(?:^|[\\s;])object\\s+\\w+")
    private val CLASS_HEADER = re("\\b(?:class|interface)\\s+\\w+")
    private val FUN_HEADER = re("\\bfun\\b")
    private val IMPORT_OR_PACKAGE = re("^\\s*(?:import|package)\\b")
    private val WEBVIEW_DECLARATION = re("\\b(?:class|fun|constructor|interface)\\b|\\bis\\s+\\w*WebView|\\bas\\??\\s+\\w*WebView")

    /** Display only (not part of the shared rules): the named class or object around a line. zh-CN: 仅用于显示 (不属于共用规则): 某行所在的具名类或对象. */
    private val NAMED_SCOPE = re("\\bcompanion\\s+object(?:\\s+(\\w+))?|\\b(?:class|interface|object)\\s+(\\w+)|\\benum\\s+(?!class\\b)(\\w+)")

    private fun re(pattern: String, flags: Int = 0): Pattern = Pattern.compile(pattern, Pattern.UNICODE_CHARACTER_CLASS or Pattern.UNIX_LINES or flags)

    private fun Pattern.search(text: CharSequence): Matcher? = matcher(text).takeIf { it.find() }

    /** Python `re.match`: anchored at the start, not at the end. zh-CN: Python 的 `re.match`: 只锚定开头. */
    private fun Pattern.matchStart(text: CharSequence): Matcher? = matcher(text).takeIf { it.lookingAt() }

    private fun Pattern.count(text: CharSequence): Int = matcher(text).let { matcher -> var n = 0; while (matcher.find()) n++; n }

    private fun Pattern.tokens(text: CharSequence): List<String> = matcher(text).let { matcher -> buildList { while (matcher.find()) add(matcher.group()) } }

    /** Python `" ".join(text.split())`. */
    private fun normalizeSpaces(text: String): String {
        val words = ArrayList<String>()
        var start = -1
        text.forEachIndexed { i, c ->
            if (!c.isWhitespace()) {
                if (start < 0) start = i
            } else if (start >= 0) {
                words += text.substring(start, i)
                start = -1
            }
        }
        if (start >= 0) words += text.substring(start)
        return words.joinToString(" ")
    }

    private fun simpleName(token: String) = token.substringAfterLast('.')

    /** Blanks comments and string / char literals, keeping line breaks. zh-CN: 抹掉注释与字符串 / 字符字面量, 保留换行. */
    fun stripCode(text: String, kotlin: Boolean): String {
        val out = StringBuilder(text.length)
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (text.startsWith("//", i)) {
                while (i < n && text[i] != '\n') {
                    out.append(' ')
                    i++
                }
                continue
            }
            if (text.startsWith("/*", i)) {
                var depth = 1
                out.append("  ")
                i += 2
                while (i < n && depth > 0) {
                    when {
                        kotlin && text.startsWith("/*", i) -> {
                            depth++
                            out.append("  ")
                            i += 2
                        }
                        text.startsWith("*/", i) -> {
                            depth--
                            out.append("  ")
                            i += 2
                        }
                        else -> {
                            out.append(if (text[i] == '\n') '\n' else ' ')
                            i++
                        }
                    }
                }
                continue
            }
            if (kotlin && text.startsWith("\"\"\"", i)) {
                out.append("\"\"\"")
                i += 3
                while (i < n && !text.startsWith("\"\"\"", i)) {
                    out.append(if (text[i] == '\n') '\n' else ' ')
                    i++
                }
                out.append("\"\"\"")
                i += 3
                continue
            }
            if (c == '"' || c == '\'') {
                out.append(c)
                i++
                while (i < n && text[i] != c && text[i] != '\n') {
                    if (text[i] == '\\') {
                        out.append("  ")
                        i += 2
                        continue
                    }
                    out.append(' ')
                    i++
                }
                if (i < n && text[i] == c) {
                    out.append(c)
                    i++
                }
                continue
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun importsOf(text: String): Map<String, String> {
        val matcher = IMPORT.matcher(text)
        val imports = LinkedHashMap<String, String>()
        while (matcher.find()) imports[matcher.group(2)!!] = matcher.group(1)!!
        return imports
    }

    private fun categories(typeText: String, imports: Map<String, String>): Pair<List<String>, String>? {
        val tokens = TOKEN.tokens(typeText)
        val simple = tokens.map(::simpleName)
        val found = sortedSetOf<String>()
        tokens.zip(simple).forEach { (token, name) ->
            val qualified = if ('.' in token) token else imports[name].orEmpty()
            when {
                name == "ScriptRuntime" -> found += "ScriptRuntime"
                name in ENGINE_TYPES || (name.endsWith("ScriptEngine") && name != "ScriptEngineService") -> found += "ScriptEngine"
                name in RHINO_SCOPE_TYPES -> found += "Scriptable"
                name == "AutoJsContext" || (name == "Context" && qualified.startsWith("org.mozilla.javascript")) -> found += "RhinoContext"
                name == "Context" -> found += "AndroidContext"
                name == "Activity" || name.endsWith("Activity") -> found += "Activity"
                name == "Fragment" || name.endsWith("Fragment") -> found += "Fragment"
                name == "Dialog" || name.endsWith("Dialog") -> found += "Dialog"
                (name == "View" || name.endsWith("View") || name == "ViewGroup") && !name.endsWith("ViewModel") -> found += "View"
            }
        }
        if (found.isEmpty()) return null
        val holding = when {
            simple.any { it in WEAK_WRAPPERS } -> "weak"
            simple.isNotEmpty() && simple[0] == "WeakHashMap" -> "weak-key map"
            simple.any { it in COLLECTIONS } -> "strong collection"
            else -> "strong"
        }
        return found.toList() to holding
    }

    private fun containerKind(typeText: String): String {
        val name = OUTER_TYPE.matchStart(typeText)?.group(1)?.let(::simpleName).orEmpty()
        if (name in COLLECTIONS || REGISTRY_SUFFIX.search(name) != null || typeText.trimEnd().endsWith("[]")) {
            return if (name == "WeakHashMap") "weak-key registry" else "registry"
        }
        return "value"
    }

    private fun weakInitializer(initializer: String?, holding: String): String {
        if (!initializer.isNullOrEmpty() && WEAK_HASH_MAP.search(initializer) != null && holding in setOf("strong collection", "registry")) {
            return if (holding == "strong collection") "weak-key map" else "weak-key registry"
        }
        if (!initializer.isNullOrEmpty() && WEAK_REFERENCE.search(initializer) != null && holding == "strong") return "weak"
        return holding
    }

    /**
     * Scope of each line start, the brace tracking of `kotlin_statics`; the second list holds the named classes or objects
     * around each line, for messages only.
     * zh-CN: 每行行首所处的作用域, 即 `kotlin_statics` 的花括号跟踪; 第二个列表是每行外围的具名类或对象, 只用于提示信息.
     */
    private fun scopes(code: String): Pair<List<String>, List<String>> {
        val stack = ArrayList<Pair<String, String?>>()
        var statementStart = 0
        val scopesByLine = arrayListOf("top")
        val ownersByLine = arrayListOf("")
        fun owner() = stack.mapNotNull { it.second }.joinToString(".")
        code.forEachIndexed { i, c ->
            when (c) {
                '{' -> {
                    val header = code.subSequence(maxOf(statementStart, i - 300), i)
                    val kind = when {
                        COMPANION_HEADER.search(header) != null || OBJECT_HEADER.search(header) != null -> "object"
                        CLASS_HEADER.search(header) != null -> "class"
                        FUN_HEADER.search(header) != null -> "fun"
                        else -> "block"
                    }
                    val named = NAMED_SCOPE.matcher(header).let { matcher ->
                        var last: String? = null
                        while (matcher.find()) last = matcher.group(2) ?: matcher.group(3) ?: matcher.group(1) ?: "Companion"
                        last?.takeIf { kind == "object" || kind == "class" || kind == "block" }
                    }
                    stack += kind to named
                    statementStart = i + 1
                }
                '}' -> {
                    while (stack.isNotEmpty() && stack.last().first == "paren") stack.removeAt(stack.lastIndex)
                    if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                    statementStart = i + 1
                }
                '(' -> stack += "paren" to null
                ')' -> if (stack.isNotEmpty() && stack.last().first == "paren") stack.removeAt(stack.lastIndex)
                ';' -> statementStart = i + 1
                '\n' -> {
                    scopesByLine += stack.lastOrNull()?.first ?: "top"
                    ownersByLine += owner()
                }
            }
        }
        return scopesByLine to ownersByLine
    }

    private fun javaStatics(path: String, code: String, imports: Map<String, String>, owners: List<String>): List<Field> {
        val rows = ArrayList<Field>()
        val matcher = JAVA_STATIC.matcher(code)
        while (matcher.find()) {
            val typeText = matcher.group(1)!!
            val name = matcher.group(2)!!
            if (typeText in setOf("class", "interface", "enum", "final", "abstract", "void")) continue
            val (found, holding) = categories(typeText, imports) ?: (emptyList<String>() to null)
            val line = code.substring(0, matcher.start()).count { it == '\n' } + 1
            rows += Field(
                path, line, "java static", name, normalizeSpaces(typeText), found,
                weakInitializer(matcher.group(3), holding ?: containerKind(typeText)), owners.getOrElse(line - 1) { "" },
            )
        }
        return rows
    }

    private fun kotlinStatics(path: String, code: String, imports: Map<String, String>): List<Field> {
        val rows = ArrayList<Field>()
        val (scopesByLine, owners) = scopes(code)
        code.split("\n").forEachIndexed { number, line ->
            val scope = scopesByLine[number]
            if (scope != "top" && scope != "object") return@forEachIndexed
            val match = KOTLIN_PROPERTY.matcher(line.trim()).takeIf { it.find() } ?: return@forEachIndexed
            val name = match.group(1)!!
            val declared: String? = match.group(2)
            val initializer: String? = match.group(3)
            val delegate: String? = match.group(4)
            var typeText = declared.orEmpty()
            if (typeText.isEmpty()) {
                val source = listOf(initializer, delegate).firstOrNull { !it.isNullOrEmpty() }.orEmpty().trim()
                val inferred = INFERRED_TYPE.matchStart(source)
                val factory = FACTORY_TYPE.matchStart(source)
                typeText = when {
                    inferred != null -> inferred.group(1)!!
                    factory != null -> KOTLIN_FACTORIES.getValue(factory.group(1)!!) + factory.group(2)!!
                    else -> ""
                }
            }
            if (typeText.isEmpty()) return@forEachIndexed
            val (found, holding) = categories(typeText, imports) ?: (emptyList<String>() to null)
            rows += Field(
                path, number + 1, "kotlin " + if (scope == "top") "top-level" else "object", name, normalizeSpaces(typeText), found,
                weakInitializer(listOf(initializer, delegate).firstOrNull { !it.isNullOrEmpty() }, holding ?: containerKind(typeText)),
                owners[number],
            )
        }
        return rows
    }

    fun ownership(path: String): String {
        if (PAGE_PATHS.any { it in path } && !path.startsWith("runtime/")) return "page"
        if (SCRIPT_PATHS.any { path.startsWith(it) || ("/$it") in path }) return "script"
        return "process"
    }

    private fun interesting(field: Field) = !CONSTANT_TOKENS.containsAll(TOKEN.tokens(field.type).map(::simpleName).toSet())

    private fun registryRows(statics: List<Field>) = statics.filter { row ->
        row.holding in setOf("registry", "weak-key registry", "strong collection", "weak-key map") &&
            row.categories != listOf("AndroidContext") && interesting(row) &&
            (ownership(row.file) == "script" || REGISTRY_HINT.search(row.type) != null)
    }

    /** Universal newlines, as Python's `read_text`. zh-CN: 通用换行, 同 Python 的 `read_text`. */
    private fun readSource(file: File) = file.readText(Charsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n')

    /** Rows for one source file given by its path under `org/autojs/autojs/`. zh-CN: 单个源文件 (以 `org/autojs/autojs/` 下的路径给出) 的行. */
    fun scanText(path: String, text: String): List<Row> = rows(scanFile(path, text))

    /** Rows of every Java and Kotlin file under [sourceRoot] (`app/src/main/java`). zh-CN: [sourceRoot] (`app/src/main/java`) 下全部 Java 与 Kotlin 文件的行. */
    fun scan(sourceRoot: File): List<Row> {
        val base = File(sourceRoot, "org/autojs/autojs")
        val scans = sourceRoot.walk().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.map { file ->
            val relative = if (file.startsWith(base)) file.relativeTo(base) else file.relativeTo(sourceRoot)
            scanFile(relative.invariantSeparatorsPath, readSource(file))
        }.toList()
        return rows(Scan(scans.flatMap { it.statics }, scans.flatMap { it.calls.toList() }))
    }

    private class Scan(val statics: List<Field>, val calls: List<Pair<String, Calls>>)

    private fun scanFile(path: String, text: String): Scan {
        val kotlin = path.endsWith(".kt")
        val code = stripCode(text, kotlin)
        val imports = importsOf(text)
        val statics = if (kotlin) kotlinStatics(path, code, imports) else javaStatics(path, code, imports, scopes(code).second)
        val lines = code.split("\n").filter { IMPORT_OR_PACKAGE.search(it) == null }
        val calls = GUARD_CALLS.mapNotNull { (name, register, release) ->
            val body = if (name == "WebView(") lines.filter { WEBVIEW_DECLARATION.search(it) == null }.joinToString("\n") else lines.joinToString("\n")
            val registered = register.count(body)
            if (registered == 0) null else name to Calls(path, registered, release.count(body))
        }
        return Scan(statics, calls)
    }

    private fun rows(scan: Scan): List<Row> {
        val selected = LinkedHashMap<Triple<String, Int, String>, Field>()
        (scan.statics.filter { it.categories.isNotEmpty() && it.holding != "weak" } + registryRows(scan.statics)).forEach {
            selected[Triple(it.file, it.line, it.name)] = it
        }
        val seen = HashMap<Pair<String, String>, Int>()
        val rows = selected.values.sortedWith(compareBy({ it.file }, { it.line }, { it.name })).map { field ->
            val count = seen.merge(field.file to field.name, 1, Int::plus)!!
            val key = if (count == 1) field.name else "${field.name}#$count"
            val detail = "${field.holder}; ${field.holding}; ${field.categories.joinToString(", ").ifEmpty { "-" }}"
            Row("field", field.file, key, detail, field.line, field.owner, field.type)
        } + scan.calls.map { (name, calls) ->
            Row("call", calls.file, name, "released: ${if (calls.paired) "yes" else "no"}; ${ownership(calls.file)}")
        }
        return rows.sortedBy { it.generated }
    }

}
