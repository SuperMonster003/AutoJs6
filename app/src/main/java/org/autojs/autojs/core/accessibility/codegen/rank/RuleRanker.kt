package org.autojs.autojs.core.accessibility.codegen.rank

import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.verify.VerificationFacts
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.automator.UiNode
import java.util.Collections
import kotlin.math.max

/**
 * A deterministic, model-free baseline. Costs are additive: a cheaper family can still lose when
 * it relies on changing content or long positional paths. The plan key breaks every score tie.
 * zh-CN: 确定且无模型的基线. 代价可累加, 因而较简单的族在依赖动态内容或长位置路径时仍可落后;
 * 所有同分候选均以计划键定序.
 */
class RuleRanker : Ranker {
    override fun <N : UiNode<N>> rank(request: CodeGenRequest<N>, verified: List<Verified<N>>): List<Ranked<N>> {
        val ranked = verified.map { Ranked(it, -cost(it).toDouble()) }
            .sortedWith(compareByDescending<Ranked<N>> { it.score }.thenBy { it.plan.planKey })
        return Collections.unmodifiableList(ranked)
    }

    internal fun <N : UiNode<N>> cost(candidate: Verified<N>): Long {
        val plan = candidate.plan
        val facts = candidate.facts
        val familyCost = when (plan.family) {
            LocatePlan.Family.SELF -> 0L
            LocatePlan.Family.ANCHOR_SEARCH -> 40L
            LocatePlan.Family.SELF_RELATIVE -> 60L
            LocatePlan.Family.ANCHOR_PATH -> 80L
            LocatePlan.Family.CONTAINER_ITEMS -> 120L
            LocatePlan.Family.SELF_INDEX -> 160L
        }
        val conditionCost = conditions(plan).sumOf(::conditionCost)
        val dynamicCost = (if (facts.dynamicText) 32L else 0L) + (if (facts.dynamicDesc) 32L else 0L)
        val pathLength = pathLength(facts)
        // Sibling offsets assume the order of neighbours, so they cost slightly more than levels.
        // zh-CN: 兄弟偏移假定邻居的顺序, 因而比层级步骤略贵.
        val pathCost = pathLength * 2L + facts.pathSiblingSteps + max(0L, pathLength - LONG_PATH_THRESHOLD) * 6L
        val ordinalCost = ordinalDistance(plan) * 2L
        val effectiveMatches = orderedMatches(plan, facts)
        val firstMatchCost = if (plan.cardinality == LocatePlan.Cardinality.ONE &&
            plan.family != LocatePlan.Family.SELF_INDEX && effectiveMatches > 1
        ) 64L + (effectiveMatches - 1).toLong().coerceAtMost(100L) else 0L
        return familyCost + conditionCost + dynamicCost + pathCost + ordinalCost + firstMatchCost
    }

    companion object {
        internal const val LONG_PATH_THRESHOLD = 2L
        internal const val LARGE_INDEX_THRESHOLD = 8L

        /**
         * The number of alternatives whose order a single-target plan depends on. A first-of-many SELF is
         * valid, but more sensitive to order than a unique selector. A relative plan is judged by the matches
         * its relation keeps, not by its deliberately broad entry selector, and also by how often its nested
         * content occurs in the capture: repeated content only says which kind of neighbour is nearby, so a
         * relation that keeps one entry then still depends on the position of that neighbour.
         * zh-CN: 单目标计划所依赖其顺序的备选数. SELF 取多个匹配中的首项虽然有效, 但比唯一选择器更依赖顺序.
         * 相对计划按关系保留的匹配数判断, 而非按其刻意宽泛的入口选择器, 同时考虑嵌套内容在捕获中的出现次数:
         * 重复的内容只说明附近是哪类邻居, 此时即使关系只保留一个入口, 仍依赖该邻居的位置.
         */
        internal fun orderedMatches(plan: LocatePlan, facts: VerificationFacts): Int =
            if (plan.family == LocatePlan.Family.SELF_RELATIVE) max(facts.relativeMatchCount, facts.relativeContentMatchCount)
            else facts.selectorMatchCount

        internal fun conditions(plan: LocatePlan): List<Condition> = plan.selector.conditions + plan.steps.flatMap { step ->
            when (step) {
                is PlanStep.Search -> step.selector.conditions
                is PlanStep.Match -> step.selector.conditions
                else -> emptyList()
            }
        }

        /** Verified relation steps plus the receiver ascent. zh-CN: 已验证的关系步数加动作接收者的上行层数. */
        internal fun pathLength(facts: VerificationFacts): Long =
            facts.pathUpLevels.toLong() + facts.pathChildSteps + facts.pathSiblingSteps + facts.receiverUpLevels

        internal fun isStructural(condition: Condition): Boolean =
            condition.attr !in setOf("id", "desc", "descStartsWith", "text", "textStartsWith", "className", "packageName")

        // A first-line prefix is slightly broader than the exact value, but escapes changing later lines.
        // zh-CN: 首行前缀比精确值略宽泛, 但不受后续行变化的影响.
        private fun conditionCost(condition: Condition): Long = when (condition.attr) {
            "id" -> if (isFullId(condition.value)) 1L else 2L
            "desc" -> 4L
            "descStartsWith" -> 5L
            "text" -> 6L
            "textStartsWith" -> 7L
            "packageName" -> 7L
            "className" -> 8L
            else -> 16L
        }

        private fun isFullId(value: Any): Boolean {
            val text = value as? String ?: return false
            val separator = text.indexOf(":id/")
            return separator > 0 && separator + 4 < text.length
        }

        /** Distance from the specified edge, so child(-1) and child(0) have equal cost.
         * zh-CN: 自指定边缘的距离, 因而 child(-1) 与 child(0) 代价相同. */
        internal fun ordinalDistance(plan: LocatePlan): Long = plan.steps.sumOf { step ->
            val index = when (step) {
                is PlanStep.Index -> step.index
                is PlanStep.Child -> step.index
                // A sibling offset of 1 is the nearest neighbour in either direction.
                // zh-CN: 兄弟偏移 1 即任一方向上最近的邻居.
                is PlanStep.Sibling -> return@sumOf kotlin.math.abs(step.offset.toLong()) - 1L
                else -> return@sumOf 0L
            }
            if (index >= 0) index.toLong() else -index.toLong() - 1L
        }
    }
}
