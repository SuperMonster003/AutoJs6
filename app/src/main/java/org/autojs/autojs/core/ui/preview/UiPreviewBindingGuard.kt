package org.autojs.autojs.core.ui.preview

import org.mozilla.javascript.CompilerEnvirons
import org.mozilla.javascript.Context
import org.mozilla.javascript.Token
import org.mozilla.javascript.ast.AbstractObjectProperty
import org.mozilla.javascript.ast.ArrayLiteral
import org.mozilla.javascript.ast.AstNode
import org.mozilla.javascript.ast.AstRoot
import org.mozilla.javascript.ast.BigIntLiteral
import org.mozilla.javascript.ast.ComputedPropertyKey
import org.mozilla.javascript.ast.ConditionalExpression
import org.mozilla.javascript.ast.ElementGet
import org.mozilla.javascript.ast.EmptyExpression
import org.mozilla.javascript.ast.EmptyStatement
import org.mozilla.javascript.ast.ExpressionStatement
import org.mozilla.javascript.ast.FunctionCall
import org.mozilla.javascript.ast.InfixExpression
import org.mozilla.javascript.ast.KeywordLiteral
import org.mozilla.javascript.ast.Name
import org.mozilla.javascript.ast.NumberLiteral
import org.mozilla.javascript.ast.ObjectLiteral
import org.mozilla.javascript.ast.ObjectProperty
import org.mozilla.javascript.ast.ParenthesizedExpression
import org.mozilla.javascript.ast.PropertyGet
import org.mozilla.javascript.ast.RegExpLiteral
import org.mozilla.javascript.ast.Spread
import org.mozilla.javascript.ast.SpreadObjectProperty
import org.mozilla.javascript.ast.StringLiteral
import org.mozilla.javascript.ast.TemplateCharacters
import org.mozilla.javascript.ast.TemplateLiteral
import org.mozilla.javascript.ast.UnaryExpression
import org.mozilla.javascript.Parser as RhinoParser

/**
 * Decides which `{{ expression }}` bindings the editor's layout preview (3-Block Compose roadmap P11.10) evaluates.
 * The preview never runs the script, so an expression may only combine values: literals, names (which
 * [UiPreviewBindingSandbox] resolves to the script's literal declarations and a few standard globals), property reads,
 * operators, conditionals and calls. Functions, loops, assignments, `new`, `delete`, E4X and the properties that lead
 * to a constructor or a scope (`constructor`, `prototype`, Rhino's `__proto__` and `__parent__`) are rejected before
 * evaluation, so `"".constructor.constructor("...")()` in a file that is only being edited never runs. A computed key
 * (`a[k]`) must be a number or a string that is not one of those properties: a literal, or a name whose declared value
 * is one (names cannot be reassigned here, so the value checked is the value used).
 *
 * zh-CN: 决定编辑器布局预览 (3-Block Compose 路线图 P11.10) 求值哪些 `{{ 表达式 }}` 绑定. 预览从不运行脚本, 表达式只能组合已有的值:
 * 字面量, 名称 (由 [UiPreviewBindingSandbox] 解析为脚本的字面量声明与少量标准全局对象), 属性读取, 运算符, 条件与调用. 函数, 循环, 赋值,
 * `new`, `delete`, E4X 以及通往构造函数或作用域的属性 (`constructor`, `prototype`, Rhino 的 `__proto__` 与 `__parent__`) 在求值前即被拒绝,
 * 因此只是在编辑的文件里的 `"".constructor.constructor("...")()` 不会执行. 计算键 (`a[k]`) 必须是数字或不属于上述属性的字符串: 字面量,
 * 或声明值为此类值的名称 (这里名称不能被重新赋值, 所以检查的值就是使用的值).
 */
internal object UiPreviewBindingGuard {

    /** Whether [expression] may be evaluated; [constant] returns the declared value of a name, or null. */
    fun allows(expression: String, constant: (String) -> Any?): Boolean {
        val root = parse(expression) ?: return false
        return root.all { statement ->
            statement is EmptyStatement || statement is ExpressionStatement && allows(statement.expression, constant)
        }
    }

    /** The error a rejected [expression] is reported with, as the failure of its evaluation. */
    class RejectedException(val expression: String) :
        RuntimeException("The editor preview does not evaluate {{$expression}}, which may only combine the literal declarations of the script")

    // Exact classes: PropertyGet, Assignment and the E4X member gets extend InfixExpression, NewExpression extends FunctionCall.
    // zh-CN: 按确切类判断: PropertyGet, Assignment 与 E4X 成员访问都继承 InfixExpression, NewExpression 继承 FunctionCall.
    private fun allows(node: AstNode?, constant: (String) -> Any?): Boolean {
        node ?: return true
        fun ok(child: AstNode?) = allows(child, constant)
        val type: Class<*> = node.javaClass
        return when (type) {
            Name::class.java, NumberLiteral::class.java, StringLiteral::class.java, BigIntLiteral::class.java,
            RegExpLiteral::class.java, KeywordLiteral::class.java, TemplateCharacters::class.java, EmptyExpression::class.java -> true
            ParenthesizedExpression::class.java -> ok((node as ParenthesizedExpression).expression)
            PropertyGet::class.java -> (node as PropertyGet).let { isReadable(it.property.identifier) && ok(it.target) }
            ElementGet::class.java -> (node as ElementGet).let { isReadableKey(it.element, constant) && ok(it.target) }
            InfixExpression::class.java -> (node as InfixExpression).let { ok(it.left) && ok(it.right) }
            UnaryExpression::class.java -> (node as UnaryExpression).let { it.operator != Token.DELPROP && ok(it.operand) }
            ConditionalExpression::class.java -> (node as ConditionalExpression).let {
                ok(it.testExpression) && ok(it.trueExpression) && ok(it.falseExpression)
            }
            FunctionCall::class.java -> (node as FunctionCall).let { ok(it.target) && it.arguments.all(::ok) }
            ArrayLiteral::class.java -> (node as ArrayLiteral).elements.all(::ok)
            ObjectLiteral::class.java -> (node as ObjectLiteral).elements.all { allowsProperty(it, constant) }
            Spread::class.java -> ok((node as Spread).expression)
            TemplateLiteral::class.java -> (node as TemplateLiteral).elements.all(::ok)
            else -> false
        }
    }

    /** Plain `key: value` and spread properties; getters, setters and methods are functions. */
    private fun allowsProperty(property: AbstractObjectProperty, constant: (String) -> Any?): Boolean = when (property) {
        is SpreadObjectProperty -> allows(property.spreadNode, constant)
        is ObjectProperty -> !property.isGetterMethod && !property.isSetterMethod && !property.isMethod && when (val key = property.key) {
            is ComputedPropertyKey -> allows(key.expression, constant)
            is Name, is StringLiteral, is NumberLiteral -> true
            else -> false
        } && allows(property.value, constant)
        else -> false
    }

    private fun isReadableKey(key: AstNode?, constant: (String) -> Any?): Boolean {
        val value: Any? = when (key) {
            is NumberLiteral -> return true
            is StringLiteral -> key.value
            is Name -> constant(key.identifier)
            else -> return false
        }
        return when (value) {
            is Number -> true
            is CharSequence -> isReadable(value.toString())
            else -> false
        }
    }

    private fun isReadable(property: String) = !property.startsWith("__") && property !in BLOCKED_PROPERTIES

    private fun parse(expression: String): AstRoot? = try {
        val environment = CompilerEnvirons().apply {
            languageVersion = Context.VERSION_ES6
            setRecordingComments(false)
            setRecordingLocalJsDocComments(false)
        }
        RhinoParser(environment, environment.errorReporter).parse(expression, "editor-preview-binding", 1)
    } catch (e: RuntimeException) {
        null
    }

    /**
     * Properties that lead to `Function` (a constructor, the reflection functions of `Object` / `Reflect`, or the members
     * of the global object, which a function called without a receiver gets as `this`), to a caller or to a Java class;
     * `__` names are Rhino's own (`__proto__`, `__parent__`, `__lookupGetter__`).
     */
    private val BLOCKED_PROPERTIES = setOf(
        "constructor", "prototype", "caller", "callee", "arguments", "getClass",
        "Function", "eval", "Object", "Reflect", "Proxy", "globalThis",
        "getPrototypeOf", "setPrototypeOf", "getOwnPropertyDescriptor", "getOwnPropertyDescriptors", "defineProperty", "defineProperties",
    )
}
