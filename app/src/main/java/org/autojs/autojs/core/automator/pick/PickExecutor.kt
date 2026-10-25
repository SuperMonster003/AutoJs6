package org.autojs.autojs.core.automator.pick

import org.autojs.autojs.core.automator.UiNode
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Undefined

/**
 * What [PickExecutor] needs of the platform for one request: the search ([one], [all], [exists]: under the root or
 * over the window roots, or the node of `detect`), the selector of `@`, and the values a script receives (a node
 * collection, an array, a centre point, the callback call and the conversion to a script value). [PickResultAdapter]
 * implements it for `UiObject` and Rhino; JVM tests implement it over `FakeUiNode` with plain lists.
 * zh-CN: [PickExecutor] 执行一个请求所需的平台能力: 搜索 ([one], [all], [exists]: 在 root 之下或各窗口根上, 或 `detect` 的节点), `@` 的选择器, 以及
 * 脚本得到的值 (节点集合, 数组, 中心点, 回调调用与转换为脚本值). [PickResultAdapter] 为 `UiObject` 与 Rhino 实现它; JVM 测试以普通列表在 `FakeUiNode` 上实现.
 */
interface PickTarget<N : UiNode<N>> {

    /** The first node found (`pickup`), or the node given (`detect`). zh-CN: 找到的首个节点 (`pickup`), 或给出的节点 (`detect`). */
    fun one(): N?

    /** Every node found (`pickup` only: `detect` has no plural result). zh-CN: 找到的全部节点 (仅 `pickup`). */
    fun all(): List<N>

    /** Whether the selector finds anything (`?` without a compass). zh-CN: 选择器是否有结果 (不带罗盘的 `?`). */
    fun exists(): Boolean

    /** The value of `@`. zh-CN: `@` 的值. */
    fun selector(): Any?

    fun collection(nodes: List<N>): Any?

    fun array(values: List<Any?>): Any?

    fun point(node: N): Any?

    /** The result of [callback] called with [value]. zh-CN: 以 [value] 调用 [callback] 的结果. */
    fun call(callback: Callable, value: Any?): Any?

    /** [value] as the script receives it. zh-CN: 脚本得到的 [value]. */
    fun toScript(value: Any?): Any?

}

/**
 * Executes a [PickRequest] (docs/selector/ROADMAP.md D5, P1.3; the former `Picker` / `Detector` / `Invoker`): finds
 * the node(s) through the [target], walks the compass from each, shapes the result (the symbols of [PickResultType],
 * a registry method, the array form of a one-element list), calls the callback with it (a callback returning
 * `undefined` keeps the result) and hands it to the script. The refusals a request of the current augment layer
 * carries are thrown where the former readers threw them: a compass once a node is walked, a method once a node is
 * found, `@` with a compass and a plural result of `detect` always.
 *
 * zh-CN: 执行 [PickRequest] (D5, P1.3; 原 `Picker` / `Detector` / `Invoker`): 经 [target] 找到节点, 从每个节点走罗盘, 整形结果 ([PickResultType] 的符号,
 * 注册表方法, 单元素列表的数组形式), 以结果调用回调 (回调返回 `undefined` 时保留原结果) 并交给脚本. 现有 augment 层的请求携带的拒绝在原读取方抛出之处抛出:
 * 罗盘在行走节点时, 方法在找到节点时, `@` 带罗盘与 `detect` 的复数结果总是抛出.
 *
 * Created by SuperMonster003 on Oct 25, 2026.
 */
class PickExecutor<N : UiNode<N>>(private val request: PickRequest<N>, private val target: PickTarget<N>) {

    fun execute(): Any? {
        val result = request.result
        if (result === PickResult.Empty) return null
        val value = shape(result)
        val callback = request.callback ?: return target.toScript(value)
        return target.toScript(target.call(callback, value).let { if (Undefined.isUndefined(it)) value else it })
    }

    private fun walk(node: N): N? = when (val compass = request.compass) {
        PickCompass.PassOn -> node
        is PickCompass.Steps -> Compass.navigate(node, compass.steps)
        is PickCompass.Refused -> throw compass.error.exception()
    }

    private fun node(): N? = target.one()?.let(::walk)

    private fun nodes(): List<N> = target.all().mapNotNull(::walk)

    private fun shape(result: PickResult): Any? = when (result) {
        PickResult.Empty, PickResult.Nothing -> null
        is PickResult.Symbol -> symbol(result.type)
        is PickResult.Call -> node()?.let { result.method.invoke(it) }
        // The array form follows a call that found the node but no method, so an empty search stays null.
        // zh-CN: 数组形式出现在找到节点却没有方法的调用之后, 因此没有结果的搜索仍为 null.
        is PickResult.ArrayForm -> node()?.let { symbol(result.type) }
        is PickResult.Refused -> when {
            result.always -> throw result.error.exception()
            node() != null -> throw result.error.exception()
            else -> null
        }
    }

    private fun symbol(type: PickResultType): Any? = when (type) {
        PickResultType.Widget -> node()
        PickResultType.WidgetCollection -> target.collection(nodes())
        PickResultType.Widgets -> target.array(nodes())
        PickResultType.Content -> node()?.content() ?: ""
        PickResultType.Contents -> target.array(nodes().map { it.content() })
        PickResultType.Point -> node()?.let(target::point)
        PickResultType.Points -> target.array(nodes().mapNotNull(target::point))
        PickResultType.Selector -> target.selector()
        PickResultType.Existence -> if (request.compass === PickCompass.PassOn) target.exists() else node() != null
        else -> null
    }

}
