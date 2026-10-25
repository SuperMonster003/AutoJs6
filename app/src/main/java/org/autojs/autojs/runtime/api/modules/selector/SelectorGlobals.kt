package org.autojs.autojs.runtime.api.modules.selector

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiObjectActions
import org.autojs.autojs.core.automator.selector.SelectorRegistry
import java.lang.reflect.Modifier

/**
 * The `UiSelector` methods exported as global functions (`text("a")`, `findOne(3000)`, `click()`):
 * every registry property plus an explicit list of query and action methods. This replaces the
 * former reflection over all public methods, which also exported `toStringReadable` and the
 * mangled names of `internal` members (`plus` and `append` were already blacklisted).
 *
 * zh-CN: 作为全局函数导出的 `UiSelector` 方法 (`text("a")`, `findOne(3000)`, `click()`): 注册表的全部属性加上显式列出的
 * 查询与动作方法. 取代此前对全部 public 方法的反射, 后者连 `toStringReadable` 与 `internal` 成员的混淆名也一并导出了
 * (`plus` 与 `append` 原已在黑名单).
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
object SelectorGlobals {

    /** Query methods exported besides the registry. zh-CN: 注册表之外导出的查询方法. */
    val queryMethods: List<String> = listOf(
        "find", "findIterator", "findOnce", "findOne", "untilFind", "untilFindOne", "waitFor", "exists", "performAction",
    )

    /** The node actions of [UiObjectActions], as `UiSelector` performs them on what it finds. zh-CN: [UiObjectActions] 的节点动作, `UiSelector` 对找到的节点执行它们. */
    val actionMethods: List<String> = UiObjectActions::class.java.methods
        .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) && '$' !in it.name }
        .map { it.name }.distinct().sorted()

    /** Members that are public for other reasons and never exported. zh-CN: 出于其它原因 public 但从不导出的成员. */
    val excluded: List<String> = listOf(UiSelector::plus.name, UiSelector::append.name, UiSelector::toStringReadable.name)

    /** Every exported name, registry properties first, in a stable order. zh-CN: 全部导出名, 注册表属性在前, 顺序稳定. */
    val names: List<String> = (SelectorRegistry.names + queryMethods + actionMethods).distinct()

    fun isRegistryProperty(name: String): Boolean = name in SelectorRegistry

}
