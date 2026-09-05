package org.autojs.autojs.core.automator

/**
 * The read-only view of an accessibility node that filters, search algorithms and the compass
 * walk against. Free of Android types, so the same code runs on the JVM over an in-memory tree
 * (`FakeUiNode` in the unit tests) and on a device over [UiObject]. [N] is the implementing type
 * itself, which keeps the static type of the caller through [parent] and [child]: a search rooted
 * at a [UiObject] yields [UiObject]s without casts.
 *
 * Member names and semantics follow the script-facing methods of [UiObject] (which implements this
 * interface with them), so a filter written against `UiNode` reads exactly like one written against
 * `UiObject`. Bounds are exposed as the screen coordinates the platform reports; [UiObject.bounds]
 * keeps returning the platform rectangle for scripts.
 *
 * zh-CN: 过滤器, 搜索算法与罗盘导航所面向的节点只读视图. 不含 Android 类型, 因而同一份代码既能在 JVM 上以内存节点树
 * (单元测试中的 `FakeUiNode`) 运行, 也能在设备上以 [UiObject] 运行. [N] 是实现类型自身, 使 [parent] / [child] 保持调用方的
 * 静态类型: 以 [UiObject] 为根的搜索得到的仍是 [UiObject], 无需强制转换.
 *
 * 成员名与语义沿用 [UiObject] 面向脚本的方法 (它以这些方法实现本接口), 因此面向 `UiNode` 写的过滤器与面向 `UiObject` 写的
 * 读起来完全一样. 边界以平台报告的屏幕坐标暴露; [UiObject.bounds] 仍为脚本返回平台的矩形对象.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
interface UiNode<N : UiNode<N>> {

    /* Tree. zh-CN: 树. */

    /** The parent node, null for a root. zh-CN: 父节点, 根节点为 null. */
    fun parent(): N?

    /**
     * The child at [i], null when out of range; negative indices count from the end, as in [UiObject.child].
     * zh-CN: 第 [i] 个子节点, 越界时为 null; 负数索引从末尾计, 同 [UiObject.child].
     */
    fun child(i: Int): N?

    fun childCount(): Int

    fun hasChildren(): Boolean = childCount() > 0

    /** Index among the parent's children, -1 when unknown. zh-CN: 在父节点子列表中的索引, 未知时为 -1. */
    fun indexInParent(): Int

    /** Distance from the root the node was reached from, 0 for that root. zh-CN: 距被搜索根节点的层数, 根节点为 0. */
    fun depth(): Int

    fun drawingOrder(): Int

    /* Identity and text. zh-CN: 标识与文本. */

    fun packageName(): String?

    fun className(): String?

    /** The view id resource name (`package:id/entry`) or null. zh-CN: 视图 id 资源名 (`package:id/entry`) 或 null. */
    fun id(): String?

    /** The view id as a hex resource id (`0x7f...`) or null. zh-CN: 十六进制资源 id (`0x7f...`) 形式的视图 id 或 null. */
    fun idHex(): String?

    /** The text, empty when absent. zh-CN: 文本, 无文本时为空串. */
    fun text(): String

    /** The content description or null. zh-CN: 内容描述或 null. */
    fun desc(): String?

    /** The content description, falling back to the text. zh-CN: 内容描述, 无则退回文本. */
    fun content(): String = desc() ?: text()

    /* Bounds on screen. zh-CN: 屏幕边界. */

    fun left(): Int

    fun top(): Int

    fun right(): Int

    fun bottom(): Int

    fun width(): Int

    fun height(): Int

    fun centerX(): Int

    fun centerY(): Int

    /* Boolean properties. zh-CN: 布尔属性. */

    fun checkable(): Boolean

    fun checked(): Boolean

    fun focusable(): Boolean

    fun focused(): Boolean

    fun visibleToUser(): Boolean

    fun accessibilityFocused(): Boolean

    fun selected(): Boolean

    fun clickable(): Boolean

    fun longClickable(): Boolean

    fun enabled(): Boolean

    fun password(): Boolean

    fun scrollable(): Boolean

    fun editable(): Boolean

    fun contentInvalid(): Boolean

    fun contextClickable(): Boolean

    fun multiLine(): Boolean

    fun dismissable(): Boolean

    /* Collection info, -1 (item) / 0 (collection) when absent. zh-CN: 集合信息, 缺失时为 -1 (条目) / 0 (集合). */

    fun row(): Int

    fun column(): Int

    fun rowSpan(): Int

    fun columnSpan(): Int

    fun rowCount(): Int

    fun columnCount(): Int

    /* Actions. zh-CN: 动作. */

    /** Symbolic names (`ACTION_CLICK`, ...) of the supported actions, see [UiNodeActions]. zh-CN: 支持的动作的符号名 (`ACTION_CLICK` 等), 见 [UiNodeActions]. */
    fun actionNames(): List<String>

    /**
     * Whether every entry of [actions] is supported: ints and doubles by id, strings by name with or
     * without the `ACTION_` prefix.
     * zh-CN: [actions] 的每一项是否都受支持: 整数与浮点数按 id, 字符串按名称 (可省略 `ACTION_` 前缀).
     */
    fun hasAction(vararg actions: Any): Boolean = UiNodeActions.hasAll(actionNames(), actions)

    fun performAction(action: Int, vararg arguments: ActionArgument): Boolean

    /* Lifecycle. zh-CN: 生命周期. */

    /**
     * Gives the platform node back once it is no longer needed. Only lifecycle helpers
     * (`NodeLifecycle`, the search algorithms) call this, and only where the platform requires it.
     * zh-CN: 不再需要时归还平台节点. 只有生命周期相关代码 (`NodeLifecycle`, 搜索算法) 调用, 且仅在平台要求时.
     */
    fun recycle()

}
