package org.autojs.autojs.core.accessibility.codegen.rank

import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.plan.CompassText
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.automator.UiNode
import java.util.Collections

/**
 * Facts, not model-generated prose. Messages are kept as keys/arguments for localization by the
 * dialog; diagnostic rendering is available on the JVM. Every explanation ends with its scope.
 * zh-CN: 理由来自事实, 不由模型生成. 消息保留键与参数, 供对话框本地化, JVM 可输出诊断文字;
 * 每份理由均以验证范围结束.
 */
object Explanation {
    enum class Key {
        SELF_UNIQUE, SELF_FIRST_MATCH, SELF_COLLECTION, INDEXED_MATCH, INDEXED_MATCH_FROM_END,
        ANCHOR_PATH, ANCHOR_SEARCH_UNIQUE, ANCHOR_SEARCH_FIRST_MATCH, CONTAINER_ITEMS,
        RELATIVE_UNIQUE, RELATIVE_FIRST_MATCH, RELATIVE_COLLECTION,
        ORDER_DEPENDENT, DYNAMIC_TEXT, DYNAMIC_DESCRIPTION, STRUCTURAL_CONDITIONS,
        FIXED_PATH, FIXED_RELATION, LONG_PATH, LARGE_INDEX, RECEIVER_ANCESTOR, ACTIVE_WINDOW_ONLY,
    }

    class Message internal constructor(val key: Key, arguments: List<String> = emptyList()) {
        val arguments: List<String> = Collections.unmodifiableList(ArrayList(arguments))

        fun renderChinese(): String = when (key) {
            Key.SELF_UNIQUE -> "以 ${arguments[0]} 在当前快照中唯一定位"
            Key.SELF_FIRST_MATCH -> "以 ${arguments[0]} 匹配 ${arguments[1]} 个控件, 取首个作为目标"
            Key.SELF_COLLECTION -> "以 ${arguments[0]} 定位所选的 ${arguments[1]} 个控件"
            Key.INDEXED_MATCH -> "在 ${arguments[0]} 个匹配项中取第 ${arguments[1]} 个"
            Key.INDEXED_MATCH_FROM_END -> "在 ${arguments[0]} 个匹配项中取倒数第 ${arguments[1]} 个"
            Key.ANCHOR_PATH -> "通过唯一锚点与 ${arguments[0]} 步父子关系定位目标"
            Key.ANCHOR_SEARCH_UNIQUE -> "在唯一锚点的子树中唯一定位目标"
            Key.ANCHOR_SEARCH_FIRST_MATCH -> "在唯一锚点的子树中匹配 ${arguments[0]} 个控件, 取首个作为目标"
            Key.CONTAINER_ITEMS -> "在唯一容器的子树中定位所选的 ${arguments[0]} 个控件"
            Key.RELATIVE_UNIQUE -> "以 ${arguments[0]} 匹配 ${arguments[1]} 个控件, 其中罗盘 ${arguments[2]} 处含 ${arguments[3]} 的只有目标"
            Key.RELATIVE_FIRST_MATCH -> "以 ${arguments[0]} 匹配 ${arguments[1]} 个控件, 罗盘 ${arguments[2]} 处含 ${arguments[3]} 的有 ${arguments[4]} 个, 取首个作为目标"
            Key.RELATIVE_COLLECTION -> "以 ${arguments[0]} 定位罗盘 ${arguments[2]} 处含 ${arguments[3]} 的 ${arguments[1]} 个控件"
            Key.ORDER_DEPENDENT -> "依赖匹配顺序, 顺序变化时可能失效"
            Key.DYNAMIC_TEXT -> "依赖含数字的文本, 内容变化时可能失效"
            Key.DYNAMIC_DESCRIPTION -> "依赖含数字的描述, 内容变化时可能失效"
            Key.STRUCTURAL_CONDITIONS -> "依赖固定层级或控件状态"
            Key.FIXED_PATH -> "依赖固定父子结构"
            Key.FIXED_RELATION -> "依赖与邻近控件的固定相对关系"
            Key.LONG_PATH -> "父子关系路径较长 (${arguments[0]} 步)"
            Key.LARGE_INDEX -> "依赖较大的固定序号"
            Key.RECEIVER_ANCESTOR -> "动作由目标的第 ${arguments[0]} 层父节点接收"
            Key.ACTIVE_WINDOW_ONLY -> "仅在当前活动窗口上验证, 不代表其他窗口或后续页面状态"
        }
    }

    class Line internal constructor(messages: List<Message>) {
        val messages: List<Message> = Collections.unmodifiableList(ArrayList(messages))
        fun renderChinese(): String = messages.joinToString("; ") { it.renderChinese() }
    }

    fun <N : UiNode<N>> describe(request: CodeGenRequest<N>, candidate: Verified<N>): List<Line> {
        val plan = candidate.plan
        val facts = candidate.facts
        fun message(key: Key, vararg arguments: Any) = Message(key, arguments.map { it.toString() })
        fun label(selector: SelectorSpec) = selector.conditions.map { it.attr }.distinct().joinToString(" + ").ifEmpty { "selector()" }
        val selectorLabel = label(plan.selector)
        val summary = when (plan.family) {
            LocatePlan.Family.SELF -> when {
                plan.cardinality == LocatePlan.Cardinality.ALL -> message(Key.SELF_COLLECTION, selectorLabel, candidate.resolved.size)
                facts.selectorMatchCount == 1 -> message(Key.SELF_UNIQUE, selectorLabel)
                else -> message(Key.SELF_FIRST_MATCH, selectorLabel, facts.selectorMatchCount)
            }
            LocatePlan.Family.SELF_INDEX -> {
                val index = (plan.steps.single() as PlanStep.Index).index
                if (index < 0) message(Key.INDEXED_MATCH_FROM_END, facts.selectorMatchCount, -index.toLong())
                else message(Key.INDEXED_MATCH, facts.selectorMatchCount, index.toLong() + 1)
            }
            LocatePlan.Family.ANCHOR_PATH -> message(Key.ANCHOR_PATH, facts.pathUpLevels.toLong() + facts.pathChildSteps)
            LocatePlan.Family.ANCHOR_SEARCH -> if (facts.selectorMatchCount == 1) message(Key.ANCHOR_SEARCH_UNIQUE)
            else message(Key.ANCHOR_SEARCH_FIRST_MATCH, facts.selectorMatchCount)
            LocatePlan.Family.CONTAINER_ITEMS -> message(Key.CONTAINER_ITEMS, candidate.resolved.size)
            LocatePlan.Family.SELF_RELATIVE -> {
                val compass = CompassText.render(plan.relation)
                val relativeLabel = label(requireNotNull(plan.relativeMatch))
                when {
                    plan.cardinality == LocatePlan.Cardinality.ALL -> message(Key.RELATIVE_COLLECTION, selectorLabel, candidate.resolved.size, compass, relativeLabel)
                    facts.relativeMatchCount == 1 -> message(Key.RELATIVE_UNIQUE, selectorLabel, facts.selectorMatchCount, compass, relativeLabel)
                    else -> message(Key.RELATIVE_FIRST_MATCH, selectorLabel, facts.selectorMatchCount, compass, relativeLabel, facts.relativeMatchCount)
                }
            }
        }
        val risks = ArrayList<Message>()
        if (plan.family == LocatePlan.Family.SELF_INDEX ||
            plan.cardinality == LocatePlan.Cardinality.ONE && RuleRanker.orderedMatches(plan, facts) > 1
        ) risks += message(Key.ORDER_DEPENDENT)
        if (facts.dynamicText) risks += message(Key.DYNAMIC_TEXT)
        if (facts.dynamicDesc) risks += message(Key.DYNAMIC_DESCRIPTION)
        if (RuleRanker.conditions(plan).any(RuleRanker::isStructural)) risks += message(Key.STRUCTURAL_CONDITIONS)
        if (plan.family == LocatePlan.Family.ANCHOR_PATH) risks += message(Key.FIXED_PATH)
        if (plan.family == LocatePlan.Family.SELF_RELATIVE) risks += message(Key.FIXED_RELATION)
        val pathLength = RuleRanker.pathLength(facts)
        if (pathLength > RuleRanker.LONG_PATH_THRESHOLD) risks += message(Key.LONG_PATH, pathLength)
        if (RuleRanker.ordinalDistance(plan) >= RuleRanker.LARGE_INDEX_THRESHOLD) risks += message(Key.LARGE_INDEX)
        if (request.action != CodeGenRequest.Action.NONE && plan.receiver is PlanReceiver.Up) {
            risks += message(Key.RECEIVER_ANCESTOR, plan.receiver.levels)
        }
        return Collections.unmodifiableList(buildList {
            add(Line(listOf(summary)))
            if (risks.isNotEmpty()) add(Line(risks))
            add(Line(listOf(message(Key.ACTIVE_WINDOW_ONLY))))
        })
    }
}
