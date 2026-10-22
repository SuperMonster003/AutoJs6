package org.autojs.autojs.core.ui.preview

import org.mozilla.javascript.CompilerEnvirons
import org.mozilla.javascript.Context
import org.mozilla.javascript.Token
import org.mozilla.javascript.ast.ArrayLiteral
import org.mozilla.javascript.ast.Assignment
import org.mozilla.javascript.ast.AstNode
import org.mozilla.javascript.ast.AstRoot
import org.mozilla.javascript.ast.ErrorCollector
import org.mozilla.javascript.ast.ExpressionStatement
import org.mozilla.javascript.ast.FunctionCall
import org.mozilla.javascript.ast.FunctionNode
import org.mozilla.javascript.ast.KeywordLiteral
import org.mozilla.javascript.ast.Name
import org.mozilla.javascript.ast.NumberLiteral
import org.mozilla.javascript.ast.ObjectLiteral
import org.mozilla.javascript.ast.ObjectProperty
import org.mozilla.javascript.ast.ParseProblem
import org.mozilla.javascript.ast.PropertyGet
import org.mozilla.javascript.ast.StringLiteral
import org.mozilla.javascript.ast.TemplateLiteral
import org.mozilla.javascript.ast.UnaryExpression
import org.mozilla.javascript.ast.UpdateExpression
import org.mozilla.javascript.ast.VariableDeclaration
import org.mozilla.javascript.ast.VariableInitializer
import org.mozilla.javascript.ast.XmlExpression
import org.mozilla.javascript.ast.XmlLiteral
import org.mozilla.javascript.ast.XmlString
import kotlin.math.abs
import org.mozilla.javascript.Parser as RhinoParser

/**
 * Reads the layout of a `"ui"` script for the editor's preview panel (3-Block Compose roadmap P11.10 / D71) with the
 * host's Rhino parser; nothing of the script is evaluated. The layout is the XML literal of the first `ui.layout(...)`
 * call in the source, given directly or through a variable that one XML literal initializes and nothing reassigns.
 * Its E4X `{expr}` fragments become `{{expr}}` bindings, evaluated against the top-level declarations whose
 * initializers are literals (the bindings JSON) and nothing else of the runtime (see [UiPreviewBindingSandbox]).
 *
 * zh-CN: 用宿主的 Rhino 解析器读取 `"ui"` 脚本的布局, 供编辑器预览面板使用 (3-Block Compose 路线图 P11.10 / D71), 不执行脚本的任何部分.
 * 布局取源码中第一个 `ui.layout(...)` 调用的 XML 字面量, 可直接给出, 也可经由一个只被一个 XML 字面量初始化且未被重新赋值的变量.
 * 其中的 E4X `{expr}` 片段转为 `{{expr}}` 绑定, 只按初始化为字面量的顶层声明 (绑定 JSON) 求值, 不接触运行时的其他内容 (见 [UiPreviewBindingSandbox]).
 */
object UiPreviewSourceReader {

    enum class FailureKind { SYNTAX_ERROR, NO_LAYOUT, UNSUPPORTED_ARGUMENT }

    sealed class Result

    /** Why [source] has no layout to preview; [offset] is where in the source (0 when nowhere in particular). */
    class Failure(val kind: FailureKind, val offset: Int, val detail: String?) : Result()

    /**
     * The layout to apply: its [xml], the [bindingsJson] object and the `ui.useAndroidLayout` mode ([androidLayout],
     * null when automatic), with what maps the XML back to the source the editor holds.
     */
    class Layout internal constructor(
        val xml: String,
        val bindingsJson: String,
        val androidLayout: Boolean?,
        /** The source offset of the XML literal. */
        val literalOffset: Int,
        private val pieces: List<Piece>,
    ) : Result() {

        /** The source offset of every element's start tag, by the pre-order index the preview reports. */
        val elementOffsets: IntArray = UiPreviewFailures.startTagOffsets(xml).map(::sourceOffsetOf).toIntArray()

        /** The source offset an offset of [xml] comes from; inside a converted `{expr}`, the offset of its brace. */
        fun sourceOffsetOf(xmlOffset: Int): Int {
            val piece = pieces.lastOrNull { it.xmlStart <= xmlOffset } ?: return literalOffset
            return if (piece.verbatim) piece.sourceStart + minOf(xmlOffset - piece.xmlStart, piece.length) else piece.sourceStart
        }

        fun elementOffset(elementIndex: Int): Int = elementOffsets.getOrElse(elementIndex) { literalOffset }
    }

    /** A run of [Layout.xml]: copied from the source as is ([verbatim]), or made of the `{expr}` at [sourceStart]. */
    internal class Piece(val xmlStart: Int, val sourceStart: Int, val length: Int, val verbatim: Boolean)

    fun read(source: String): Result {
        val collector = ErrorCollector()
        val root = parse(source, collector)
        val error = collector.errors.firstOrNull { it.type == ParseProblem.Type.Error }
        if (root == null || error != null) {
            return Failure(FailureKind.SYNTAX_ERROR, error?.fileOffset?.coerceIn(0, source.length) ?: 0, error?.message)
        }
        val layoutCalls = ArrayList<FunctionCall>()
        var androidLayoutCall: FunctionCall? = null
        val declarations = HashMap<String, Int>()
        val assigned = HashSet<String>()
        val literalInitializers = HashMap<String, MutableList<XmlLiteral>>()
        root.visit { node ->
            when (node) {
                is FunctionCall -> when {
                    isUiCall(node, "layout") -> layoutCalls += node
                    isUiCall(node, "useAndroidLayout") ->
                        if (androidLayoutCall.let { it == null || node.absolutePosition < it.absolutePosition }) androidLayoutCall = node
                }
                is VariableInitializer -> (node.target as? Name)?.identifier?.let { name ->
                    declarations.merge(name, 1, Int::plus)
                    (node.initializer as? XmlLiteral)?.let { literalInitializers.getOrPut(name) { ArrayList() } += it }
                }
                is FunctionNode -> node.functionName?.identifier?.let { declarations.merge(it, 1, Int::plus) }
                is Assignment -> (node.left as? Name)?.let { assigned += it.identifier }
                is UpdateExpression -> (node.operand as? Name)?.let { assigned += it.identifier }
            }
            true
        }
        val call = layoutCalls.minByOrNull { it.absolutePosition } ?: return Failure(FailureKind.NO_LAYOUT, 0, null)
        val argument = call.arguments.singleOrNull() ?: return Failure(FailureKind.UNSUPPORTED_ARGUMENT, call.absolutePosition, null)
        val literal = when (argument) {
            is XmlLiteral -> argument
            is Name -> literalInitializers[argument.identifier]
                ?.singleOrNull()
                ?.takeIf { argument.identifier !in assigned && declarations[argument.identifier] == 1 }
            else -> null
        } ?: return Failure(FailureKind.UNSUPPORTED_ARGUMENT, argument.absolutePosition, null)
        val bindings = bindingsOf(root, declarations, assigned)
        return layoutOf(source, literal, bindings, androidLayoutOf(androidLayoutCall))
    }

    /** The 0-based line and column of [offset] in [text]. */
    fun positionOf(text: CharSequence, offset: Int): Pair<Int, Int> {
        val end = offset.coerceIn(0, text.length)
        var line = 0
        var lineStart = 0
        for (i in 0 until end) {
            if (text[i] == '\n') {
                line++
                lineStart = i + 1
            }
        }
        return line to end - lineStart
    }

    /** The tree of [source], or null when the parser gave up; the syntax errors go to [collector] either way. */
    private fun parse(source: String, collector: ErrorCollector): AstRoot? {
        val environment = CompilerEnvirons().apply {
            languageVersion = Context.VERSION_ES6
            isIdeMode = true
            setRecordingComments(false)
            setRecordingLocalJsDocComments(false)
            errorReporter = collector
        }
        return try {
            RhinoParser(environment, collector).parse(source, "editor-preview.js", 1)
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun isUiCall(call: FunctionCall, method: String): Boolean {
        val target = call.target as? PropertyGet ?: return false
        return (target.target as? Name)?.identifier == "ui" && target.property.identifier == method
    }

    /** `ui.useAndroidLayout()` and `(true)` keep native XML, `(false)` converts it, anything else is automatic. */
    private fun androidLayoutOf(call: FunctionCall?): Boolean? {
        call ?: return null
        val argument = call.arguments.firstOrNull() ?: return true
        return when ((argument as? KeywordLiteral)?.type) {
            Token.TRUE -> true
            Token.FALSE -> false
            else -> null
        }
    }

    private fun layoutOf(source: String, literal: XmlLiteral, bindings: String, androidLayout: Boolean?): Layout {
        val xml = StringBuilder()
        val pieces = ArrayList<Piece>()
        for (fragment in literal.fragments) {
            val start = fragment.absolutePosition
            when (fragment) {
                is XmlString -> {
                    val text = source.substring(start, sourceEndOf(source, start, fragment.xml.length))
                    pieces += Piece(xml.length, start, text.length, verbatim = true)
                    xml.append(text)
                }
                is XmlExpression -> {
                    val expression = fragment.expression
                    val text = expression?.let { source.substring(it.absolutePosition, it.absolutePosition + it.length) }.orEmpty()
                    val converted = if (fragment.isXmlAttribute) "\"${escapeXml("{{$text}}", attribute = true)}\"" else escapeXml("{{$text}}", attribute = false)
                    pieces += Piece(xml.length, start, converted.length, verbatim = false)
                    xml.append(converted)
                }
            }
        }
        val literalOffset = literal.fragments.firstOrNull()?.absolutePosition ?: literal.absolutePosition
        return Layout(xml.toString(), bindings, androidLayout, literalOffset, pieces)
    }

    /**
     * The end of the source run from [start] that Rhino's scanner read as an XML string of [scannedLength] chars: it reads
     * "\r\n" as one line break and drops format chars (the zero width joiner of emoji sequences, for one) except the BOM.
     *
     * zh-CN: Rhino 扫描器从 [start] 读出 [scannedLength] 个字符的 XML 字符串时对应的源码终点: 它把 "\r\n" 读作一个换行,
     * 并丢弃 BOM 以外的格式字符 (例如 emoji 序列里的零宽连接符).
     */
    private fun sourceEndOf(source: String, start: Int, scannedLength: Int): Int {
        var i = start
        var scanned = 0
        while (scanned < scannedLength && i < source.length) {
            val ch = source[i]
            when {
                ch == '\r' && source.getOrNull(i + 1) == '\n' -> i += 2
                ch.code > 0x7F && ch.code != 0xFEFF && Character.getType(ch) == Character.FORMAT.toInt() -> {
                    i++
                    continue
                }
                else -> i++
            }
            scanned++
        }
        return i
    }

    private fun escapeXml(text: String, attribute: Boolean): String = buildString(text.length + 8) {
        for (ch in text) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                attribute && ch == '"' -> append("&quot;")
                // An attribute value keeps its line breaks and tabs only as character references.
                attribute && ch == '\n' -> append("&#10;")
                attribute && ch == '\r' -> append("&#13;")
                attribute && ch == '\t' -> append("&#9;")
                else -> append(ch)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- bindings

    /**
     * The bindings JSON: every top-level `var` / `let` / `const` whose initializer is a literal value (strings,
     * numbers, booleans, null, templates without substitutions, and arrays / objects of them), unless the name is
     * declared more than once or assigned anywhere in the script.
     */
    private fun bindingsOf(root: AstRoot, declarations: Map<String, Int>, assigned: Set<String>): String {
        val values = LinkedHashMap<String, String>()
        for (statement in root) {
            val declaration = statement as? VariableDeclaration
                ?: (statement as? ExpressionStatement)?.expression as? VariableDeclaration
                ?: continue
            for (variable in declaration.variables) {
                val name = (variable.target as? Name)?.identifier ?: continue
                if (declarations[name] != 1 || name in assigned) continue
                val value = variable.initializer?.let(::jsonOf) ?: continue
                values[name] = value
            }
        }
        return values.entries.joinToString(",", "{", "}") { (name, value) -> "${jsonString(name)}:$value" }
    }

    /** [node] as JSON text when it is a literal value, else null. */
    private fun jsonOf(node: AstNode): String? = when (node) {
        is StringLiteral -> jsonString(node.value)
        is NumberLiteral -> jsonNumber(node.number)
        is KeywordLiteral -> when (node.type) {
            Token.TRUE -> "true"
            Token.FALSE -> "false"
            Token.NULL -> "null"
            else -> null
        }
        is UnaryExpression -> (node.operand as? NumberLiteral)?.let { operand ->
            when (node.operator) {
                Token.NEG -> jsonNumber(-operand.number)
                Token.POS -> jsonNumber(operand.number)
                else -> null
            }
        }
        is TemplateLiteral -> node.takeIf { it.substitutions.isEmpty() }?.let { template ->
            jsonString(template.templateStrings.joinToString("") { it.value ?: "" })
        }
        is ArrayLiteral -> node.elements.map { jsonOf(it) ?: return null }.joinToString(",", "[", "]")
        is ObjectLiteral -> node.elements.map { property ->
            val entry = property as? ObjectProperty ?: return null
            if (entry.isMethod || entry.isGetterMethod || entry.isSetterMethod) return null
            val key = when (val k = entry.key) {
                is Name -> k.identifier
                is StringLiteral -> k.value
                is NumberLiteral -> k.value
                else -> return null
            }
            "${jsonString(key)}:${jsonOf(entry.value) ?: return null}"
        }.joinToString(",", "{", "}")
        else -> null
    }

    private fun jsonNumber(value: Double): String? = when {
        value.isNaN() || value.isInfinite() -> null
        value == Math.rint(value) && abs(value) < 9.007199254740992E15 -> value.toLong().toString()
        else -> value.toString()
    }

    private fun jsonString(value: String): String = buildString(value.length + 2) {
        append('"')
        for (ch in value) {
            when {
                ch == '"' -> append("\\\"")
                ch == '\\' -> append("\\\\")
                ch == '\n' -> append("\\n")
                ch == '\r' -> append("\\r")
                ch == '\t' -> append("\\t")
                ch < ' ' -> append("\\u").append(String.format("%04x", ch.code))
                else -> append(ch)
            }
        }
        append('"')
    }
}
