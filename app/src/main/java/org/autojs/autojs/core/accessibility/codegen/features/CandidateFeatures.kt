package org.autojs.autojs.core.accessibility.codegen.features

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.NodeTree
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.rank.RuleRanker
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.selector.SelectorRegistry
import java.util.Collections
import kotlin.math.abs
import kotlin.math.ln1p

/**
 * Features depend only on the captured request and its complete verified candidate group.
 * Prepare once per group to share the tree statistics and rule ordering across all candidates.
 * The schema's aggregation and missing-value rules are documented in tools/codegen-model/FEATURES.md.
 * zh-CN: 特征仅依赖捕获状态的请求与完整已验证候选组. 每组 prepare 一次, 供所有候选共用树统计与规则顺序.
 */
object CandidateFeatures {
    fun <N : UiNode<N>> extract(request: CodeGenRequest<N>, candidate: Verified<N>, group: List<Verified<N>>): FloatArray =
        prepare(request, group).extract(candidate)

    /** Pass the verifier's tree to retain a captured subtree boundary and reuse its index.
     * Without one, nodes must belong to a complete detached capture with a parentless root.
     * zh-CN: 传入验证器的树可保留捕获子树边界并复用索引; 未传入时要求节点属于根无父节点的完整独立捕获树. */
    fun <N : UiNode<N>> prepare(request: CodeGenRequest<N>, group: List<Verified<N>>, tree: NodeTree<N>? = null): Group<N> =
        Group(request, group, tree ?: NodeTree(detachedRoot(request.targets.first())))

    class Group<N : UiNode<N>> internal constructor(
        private val request: CodeGenRequest<N>,
        group: List<Verified<N>>,
        private val tree: NodeTree<N>,
    ) {
        private val candidates = Collections.unmodifiableList(ArrayList(group))
        private val byKey = candidates.associateBy { it.plan.planKey }
        private val statistics = WindowStatistics(tree)
        private val targets = request.targets.map { requireNotNull(tree.canonical(it)) { "Target outside capture" } }.distinct()
        private val targetSet = targets.toSet()
        private val targetFeatures: FloatArray
        private val rankByKey: Map<String, Int>
        private val familyRankByKey: Map<String, Int>
        private val costs: Map<String, Long>
        private val bestCost: Long
        private val anchors = HashMap<SelectorSpec, N>()

        init {
            require(targets.isNotEmpty()) { "Features require a target" }
            require(byKey.size == candidates.size) { "Candidate plan keys must be unique within a group" }
            require(candidates.all { it.resolved.toSet() == targetSet }) { "Candidates must belong to the same target request" }
            val rule = RuleRanker()
            val ranked = rule.rank(request, candidates)
            rankByKey = ranked.withIndex().associate { it.value.plan.planKey to it.index }
            val familyCounts = HashMap<LocatePlan.Family, Int>()
            familyRankByKey = ranked.associate {
                val familyRank = familyCounts.getOrDefault(it.plan.family, 0)
                familyCounts[it.plan.family] = familyRank + 1
                it.plan.planKey to familyRank
            }
            costs = candidates.associate { it.plan.planKey to rule.cost(it) }
            bestCost = costs.values.minOrNull() ?: 0L
            targetFeatures = floatArrayOf(
                count(targets.sumOf { tree.depth(it).toLong() }.toDouble() / targets.size),
                flag(targets.all { !it.id().isNullOrEmpty() }),
                flag(targets.all { it.text().isNotEmpty() }),
                flag(targets.all { it.clickable() }),
                flag(targets.all { statistics.inList.getValue(it) }),
                meanCount(targets.map { statistics.sameClassSiblings.getValue(it) }),
                count(targets.size),
            )
        }

        /** Corresponds to the supplied group's order; every call returns independent arrays.
         * zh-CN: 与输入候选组的顺序对应; 每次调用返回独立数组. */
        fun extractAll(): List<FloatArray> = candidates.map(::extract)

        fun extract(candidate: Verified<N>): FloatArray {
            val member = requireNotNull(byKey[candidate.plan.planKey]) { "Candidate is not in this group" }
            val plan = member.plan
            val facts = member.facts
            val result = FloatArray(FeatureSchema.names.size)
            var at = 0
            fun add(value: Float) { result[at++] = value }

            add(flag(request.scope == CodeGenRequest.Scope.COLLECTION))
            val actionIndex = when (request.action) {
                CodeGenRequest.Action.NONE -> 0
                CodeGenRequest.Action.CLICK -> 1
                CodeGenRequest.Action.LONG_CLICK -> 2
                is CodeGenRequest.Action.SET_TEXT -> 3
                CodeGenRequest.Action.SCROLL_FORWARD -> 4
                CodeGenRequest.Action.SCROLL_BACKWARD -> 5
            }
            repeat(6) { add(flag(it == actionIndex)) }
            CodeGenRequest.Attr.entries.forEach { add(flag(it in request.attributes)) }
            LocatePlan.Family.entries.forEach { add(flag(it == plan.family)) }
            add(count(facts.receiverUpLevels))

            val anchored = plan.family !in SELF_FAMILIES
            val selfSpec = if (anchored) (plan.steps.singleOrNull() as? PlanStep.Search)?.selector else plan.selector
            val anchorSpec = if (anchored) plan.selector else null
            selectorFeatures(selfSpec).forEach(::add)
            selectorFeatures(anchorSpec).forEach(::add)
            selectorFeatures(plan.relativeMatch).forEach(::add)

            val conditions = RuleRanker.conditions(plan)
            val ids = stringValues(conditions) { it == "id" }.map(::entryId).distinct()
            add(maxLength(ids))
            add(if (ids.isEmpty()) MISSING else flag(ids.any(::looksObfuscated)))
            add(if (ids.isEmpty()) MISSING else flag(ids.any { statistics.idPackages[it].orEmpty().size > 1 }))
            val text = stringValues(conditions) { it.startsWith("text") }
            val description = stringValues(conditions) { it.startsWith("desc") }
            add(maxLength(text))
            add(characterRatio(text, Character::isDigit))
            add(if (text.isEmpty()) MISSING else flag(facts.dynamicText || text.any(::dynamic)))
            add(characterRatio(text, ::isCjk))
            add(maxLength(description))
            add(if (description.isEmpty()) MISSING else flag(facts.dynamicDesc || description.any(::dynamic)))

            add(facts.index?.let { count(abs(it.toLong())) } ?: MISSING)
            add(facts.index?.let { flag(it < 0) } ?: MISSING)
            add(facts.resolvedIndex?.takeIf { facts.selectorMatchCount > 0 }?.let { it.toFloat() / facts.selectorMatchCount } ?: MISSING)
            add(count(facts.selectorMatchCount))
            if (plan.family == LocatePlan.Family.SELF_RELATIVE && facts.relativeMatchCount >= 0) {
                add(count(facts.relativeMatchCount))
                add(if (facts.selectorMatchCount > 0) facts.relativeMatchCount.toFloat() / facts.selectorMatchCount else MISSING)
            } else {
                add(MISSING)
                add(MISSING)
            }
            add(count(facts.pathSteps))
            add(count(facts.pathUpLevels))
            add(count(facts.pathChildSteps))
            add(count(facts.pathSiblingSteps))
            add(count(facts.pathMaxChildIndex))
            add(if (!anchored) MISSING else if (plan.family == LocatePlan.Family.CONTAINER_ITEMS) {
                val anchor = anchor(plan.selector)
                // A collection search is scoped to one anchor, so every target is its descendant.
                // zh-CN: 集合搜索仅限于一个锚点, 因而每个目标都是其后代.
                count(targets.sumOf { (tree.depth(it) - tree.depth(anchor)).toLong() }.toDouble() / targets.size)
            } else count(facts.anchorDistance))
            if (plan.family == LocatePlan.Family.CONTAINER_ITEMS) {
                add(count(facts.containerItemCount))
                add(statistics.uniformity(member.resolved))
            } else {
                add(MISSING)
                add(MISSING)
            }
            targetFeatures.forEach(::add)
            add(rankByKey.getValue(plan.planKey).toFloat() / candidates.size)
            add((costs.getValue(plan.planKey) - bestCost).toFloat())
            add(count(candidates.size))
            add(count(familyRankByKey.getValue(plan.planKey)))
            check(at == result.size) { "Feature/schema length mismatch" }
            check(result.all(Float::isFinite)) { "Non-finite candidate feature" }
            return result
        }

        private fun anchor(spec: SelectorSpec): N = anchors.getOrPut(spec) {
            val selector = UiSelector(null)
            spec.conditions.forEach { condition ->
                check(SelectorRegistry.apply(selector, condition.attr, listOf(condition.value)) == SelectorRegistry.Outcome.APPLIED)
            }
            requireNotNull(tree.nodes.singleOrNull { selector.selector.filter(it) }) { "Verified anchor must be unique in capture" }
        }
    }

    private data class Signature(val className: String?, val fullId: String?, val children: List<Int>)

    /** Interned bottom-up signatures keep time/memory linear even for a 5,000-node chain.
     * zh-CN: 自底向上驻留签名, 即使 5,000 节点链的时间与空间开销也保持线性. */
    private class WindowStatistics<N : UiNode<N>>(private val tree: NodeTree<N>) {
        val idPackages = HashMap<String, MutableSet<String>>()
        val inList = HashMap<N, Boolean>()
        val sameClassSiblings = HashMap<N, Int>()
        private val signatures = HashMap<N, Int>()

        init {
            val children = tree.nodes.associateWith(tree::children)
            val intern = HashMap<Signature, Int>()
            val repeated = HashSet<N>()
            for (node in tree.nodes.asReversed()) {
                val childNodes = children.getValue(node)
                val childSignatures = childNodes.map { signatures.getValue(it) }
                val signature = Signature(node.className(), node.id(), childSignatures)
                signatures[node] = intern.getOrPut(signature) { intern.size }
                if (childSignatures.size != childSignatures.toSet().size) repeated += node
                val classCounts = childNodes.groupingBy { it.className() }.eachCount()
                childNodes.forEach { child ->
                    sameClassSiblings[child] = child.className()?.let { classCounts.getValue(it) - 1 } ?: -1
                }
                node.id()?.takeIf(String::isNotEmpty)?.let { id ->
                    val owner = if (isFullId(id)) id.substringBefore(":id/") else node.packageName().orEmpty()
                    if (owner.isNotEmpty()) idPackages.getOrPut(entryId(id), ::HashSet).add(owner)
                }
            }
            sameClassSiblings[tree.root] = if (tree.root.className() == null) -1 else 0
            for (node in tree.nodes) {
                val parent = tree.parent(node)
                inList[node] = parent != null && (inList.getValue(parent) || parent.scrollable() && parent in repeated)
            }
        }

        fun uniformity(nodes: List<N>): Float {
            if (nodes.isEmpty()) return MISSING
            val frequencies = nodes.distinct().groupingBy { signatures.getValue(it) }.eachCount()
            return frequencies.values.max().toFloat() / frequencies.values.sum()
        }
    }

    private fun selectorFeatures(spec: SelectorSpec?): FloatArray {
        if (spec == null) return FloatArray(8) { MISSING }
        val conditions = spec.conditions
        return floatArrayOf(
            count(conditions.size),
            flag(conditions.any { it.attr == "id" && isFullId(it.value as? String) }),
            flag(conditions.any { it.attr == "id" && !isFullId(it.value as? String) }),
            flag(conditions.any { it.attr.startsWith("text") }),
            flag(conditions.any { it.attr.startsWith("desc") }),
            flag(conditions.any { it.attr == "className" }),
            flag(conditions.any { it.attr == "packageName" }),
            count(conditions.count(RuleRanker::isStructural)),
        )
    }

    private fun stringValues(conditions: List<Condition>, accepts: (String) -> Boolean): List<String> =
        conditions.filter { accepts(it.attr) }.mapNotNull { it.value as? String }.distinct().sorted()

    private fun <N : UiNode<N>> detachedRoot(target: N): N {
        var node = target
        val seen = HashSet<N>()
        while (true) {
            require(seen.add(node)) { "Parent cycle in captured tree" }
            node = node.parent() ?: return node
        }
    }

    private fun isFullId(value: String?): Boolean = value != null && value.indexOf(":id/") > 0 && value.substringAfter(":id/").isNotEmpty()
    private fun entryId(value: String): String = value.substringAfterLast(":id/")
    private fun looksObfuscated(value: String): Boolean = codePointLength(value) <= 2 ||
        value.length >= 3 && value.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } &&
        value.any { it in 'a'..'z' || it in 'A'..'Z' } && value.none { it.lowercaseChar() in "aeiou" }
    private fun dynamic(value: String): Boolean = value.codePoints().anyMatch(Character::isDigit)
    private fun codePointLength(value: String): Int = value.codePointCount(0, value.length)
    private fun maxLength(values: List<String>): Float = values.maxOfOrNull(::codePointLength)?.let(::count) ?: MISSING
    private fun characterRatio(values: List<String>, matches: (Int) -> Boolean): Float {
        if (values.isEmpty()) return MISSING
        var total = 0L
        var matched = 0L
        for (value in values) {
            var offset = 0
            while (offset < value.length) {
                val point = value.codePointAt(offset)
                total++
                if (matches(point)) matched++
                offset += Character.charCount(point)
            }
        }
        return if (total == 0L) 0f else (matched.toDouble() / total).toFloat()
    }
    private fun isCjk(point: Int): Boolean = point in 0x3400..0x4dbf || point in 0x4e00..0x9fff ||
        point in 0xf900..0xfaff || point in 0x20000..0x2ee5f || point in 0x2f800..0x2fa1f || point in 0x30000..0x323af
    private fun meanCount(values: List<Int>): Float {
        val present = values.filter { it >= 0 }
        return if (present.isEmpty()) MISSING else count(present.sumOf { it.toLong() }.toDouble() / present.size)
    }
    private fun flag(value: Boolean): Float = if (value) 1f else 0f
    private fun count(value: Int): Float = count(value.toDouble())
    private fun count(value: Long): Float = count(value.toDouble())
    private fun count(value: Double): Float = if (value < 0) MISSING else ln1p(value).toFloat()
    private const val MISSING = -1f
    private val SELF_FAMILIES = setOf(LocatePlan.Family.SELF, LocatePlan.Family.SELF_INDEX, LocatePlan.Family.SELF_RELATIVE)
}
