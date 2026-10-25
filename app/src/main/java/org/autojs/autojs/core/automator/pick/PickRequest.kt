package org.autojs.autojs.core.automator.pick

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiNode
import org.mozilla.javascript.Callable
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * One `pickup` / `detect` call as [PickArguments] normalized it (docs/selector/ROADMAP.md D5, P1.1): where to search
 * ([root]: under that node for `pickup`, the node itself for `detect`), what ([selector], built on the bridge of the
 * calling script; null for `detect`), the [compass] walk from what is found, the [result] to shape and the optional
 * [callback]. Free of Android types: a request over `FakeUiNode` is built and executed on the JVM ([PickExecutor]).
 *
 * zh-CN: 经 [PickArguments] 归一化的一次 `pickup` / `detect` 调用 (D5, P1.1): 在哪里搜索 ([root]: `pickup` 时在其下, `detect` 时即该节点), 搜索什么
 * ([selector], 建在调用方脚本的 bridge 上; `detect` 时为 null), 从找到的节点起的 [compass] 行走, 要整形的 [result] 与可选的 [callback]. 不含 Android 类型:
 * 以 `FakeUiNode` 为节点的请求可在 JVM 上构造并执行 ([PickExecutor]).
 *
 * Created by SuperMonster003 on Oct 25, 2026.
 */
class PickRequest<N : UiNode<N>> internal constructor(
    val function: String,
    val root: N?,
    val selector: UiSelector?,
    val compass: PickCompass,
    val result: PickResult,
    val callback: Callable?,
)

/**
 * The compass of a request. zh-CN: 请求的罗盘.
 */
sealed class PickCompass {

    /** No compass or [Compass.PASS_ON]: the node itself, and the selector forms of `@` / `?` apply. zh-CN: 没有罗盘或 [Compass.PASS_ON]: 节点自身. */
    object PassOn : PickCompass()

    /** The [steps] to walk (none for the empty string, which still counts as a compass). zh-CN: 要行走的 [steps] (空串没有步骤, 但仍算作罗盘). */
    class Steps(val steps: List<CompassStep>) : PickCompass()

    /**
     * A compass that does not parse, refused once a node is to be walked (the entries of the current augment layer:
     * `UiObject.compass` threw there); the new layer refuses it before the search.
     * zh-CN: 无法解析的罗盘, 在要行走节点时拒绝 (现有 augment 层的入口: `UiObject.compass` 在此时抛出); 新层在搜索前即拒绝.
     */
    class Refused(val error: PickError) : PickCompass()

}

/**
 * The result a request shapes. zh-CN: 请求要整形的结果.
 */
sealed class PickResult {

    /** The empty string (and a nullish result type of `detect`): null, without the callback. zh-CN: 空串 (以及 `detect` 的空值结果类型): null, 不调用回调. */
    object Empty : PickResult()

    /** A value without a result (a number, a list whose head is no string, an empty method name): null. zh-CN: 没有结果的值: null. */
    object Nothing : PickResult()

    /** A symbolic [type] (`#`, `{}`, `[]`, `$`, `contents`, `.`, `points`, `@`, `?` and their spellings). zh-CN: 符号类型. */
    class Symbol(val type: PickResultType) : PickResult()

    /** A registry method of the node, [name] as given. zh-CN: 节点的注册表方法, [name] 为给出的名称. */
    class Call(val name: String, val method: NodeMethodResolution.Found) : PickResult()

    /** A one-element list whose head is no method but a symbol: its array form ([type] is `Widgets`, `Contents` or `Points`). zh-CN: 首项不是方法而是符号的单元素列表: 其数组形式. */
    class ArrayForm(val type: PickResultType) : PickResult()

    /**
     * A result type refused when it is used (the entries of the current augment layer): [always], or once a node is
     * found (a method that is no registry method, arguments it does not take). The new layer refuses it before the search.
     * zh-CN: 在使用时拒绝的结果类型 (现有 augment 层的入口): [always] 时总是, 否则在找到节点时 (不是注册表方法, 方法不接受的参数). 新层在搜索前即拒绝.
     */
    class Refused(val error: PickError, val always: Boolean) : PickResult()

}

/**
 * An argument error of `pickup` / `detect` or of a selector form read like theirs: a TypeError or a RangeError with the
 * function and parameter names (P1.1), thrown by the caller ([exception]) once it is told.
 * zh-CN: `pickup` / `detect` 或按其方式读取的选择器形式的参数错误: 带函数名与参数名的 TypeError 或 RangeError (P1.1), 判定后由调用方抛出 ([exception]).
 */
class PickError internal constructor(val kind: Kind, val message: String) {

    enum class Kind { TYPE, RANGE }

    fun exception(): RuntimeException = when (kind) {
        Kind.TYPE -> RhinoScriptRuntime.typeError(message)
        Kind.RANGE -> RhinoScriptRuntime.rangeError(message)
    }

    override fun toString() = "${if (kind == Kind.TYPE) "TypeError" else "RangeError"}: $message"

}

/**
 * What [PickArguments] makes of a call: the [request][Accepted.request], or the [error][Refused.error] to throw.
 * zh-CN: [PickArguments] 对一次调用的判定: 请求, 或要抛出的错误.
 */
sealed class PickVerdict<N : UiNode<N>> {

    class Accepted<N : UiNode<N>>(val request: PickRequest<N>) : PickVerdict<N>()

    class Refused<N : UiNode<N>>(val error: PickError) : PickVerdict<N>()

    /** The request, or the error thrown. zh-CN: 请求, 或抛出错误. */
    fun requestOrThrow(): PickRequest<N> = when (this) {
        is Accepted -> request
        is Refused -> throw error.exception()
    }

}
