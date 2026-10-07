package org.autojs.autojs.core.accessibility.codegen.construction

import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Action
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Attr
import org.autojs.autojs.core.accessibility.codegen.construction.ConstructionGrammar.Operation
import org.autojs.autojs.core.accessibility.codegen.construction.ConstructionGrammar.Phase
import org.autojs.autojs.core.accessibility.codegen.construction.ConstructionGrammar.State
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.automator.UiNode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.ln

/** The score predicts one comparable completion value; it is never accumulated along a path.
 * zh-CN: 分数预测同尺度的完成价值, 不沿路径累加. */
fun interface ConstructionPolicy {
    fun score(features: FloatArray): Double
}

/** Independent state/action schema. It contains no future layout, truth id, completion label,
 * text/id bytes, raw node position or feature borrowed from the complete-candidate ranker.
 * zh-CN: 独立状态/动作特征, 不含未来布局, 真值 ID, 终局标签, 字符串原文或节点序号. */
object ConstructionFeatures {
    const val version = 1
    const val grammarVersion = 1
    val names: List<String> = buildList {
        addAll(Phase.entries.filter { it != Phase.DONE }.map { "phase_${it.name.lowercase()}" })
        addAll(listOf("family", "anchor", "outer", "inner", "end_outer", "index", "up", "child", "finish").map { "operation_$it" })
        addAll(Family.entries.map { "family_${it.name.lowercase()}" })
        addAll(listOf(
            "prefix_depth", "outer_count", "inner_count", "prefix_id", "prefix_text", "prefix_desc",
            "prefix_class", "prefix_package", "prefix_boolean", "prefix_depth_condition", "prefix_dynamic_text",
            "prefix_dynamic_desc", "prefix_id_digit_fraction", "prefix_id_generic", "prefix_id_long_token",
            "outer_matches_log", "outer_unique", "outer_first_desired", "outer_desired_present",
            "inner_matches_log", "inner_first_target", "inner_target_coverage", "inner_extra_fraction",
            "path_steps", "path_up_levels", "path_child_steps", "path_cursor_target", "path_remaining_distance",
            "anchor_distance", "anchor_depth", "target_depth", "tree_size_log", "target_count_log",
            "index_absolute_log", "index_negative", "receiver_up_levels",
            "condition_id", "condition_text", "condition_desc", "condition_class", "condition_package",
            "condition_boolean", "condition_depth", "condition_value_length_log", "condition_digit_fraction",
            "condition_id_generic", "condition_id_long_token", "condition_boolean_true",
            "operation_path_distance", "operation_up_levels", "operation_child_absolute_log", "operation_child_negative",
            "request_collection", "request_action_none", "request_action_click", "request_action_long_click",
            "request_action_set_text", "request_action_scroll_forward", "request_action_scroll_backward",
            "request_attr_id", "request_attr_text", "request_attr_desc", "request_receiver_auto",
            "request_mode_check", "request_mode_timeout", "request_style_flow",
        ))
    }
    val count: Int get() = names.size
    /** First 16 lowercase SHA-256 hex digits of UTF-8 names joined by LF, without a trailing LF. */
    val namesHash: String = MessageDigest.getInstance("SHA-256")
        .digest(names.joinToString("\n").toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }.take(16)
    private val indices = names.withIndex().associate { it.value to it.index }

    fun value(features: FloatArray, name: String): Float = features[indices.getValue(name)]

    internal fun <N : UiNode<N>> extract(
        grammar: ConstructionGrammar<N>, state: State, operation: Operation, next: State,
    ): FloatArray {
        val result = FloatArray(count)
        fun put(name: String, value: Number) { result[indices.getValue(name)] = value.toFloat() }
        fun flag(name: String, value: Boolean) = put(name, if (value) 1 else 0)
        flag("phase_${state.phase.name.lowercase()}", true)
        val operationName = when (operation) {
            is Operation.ChooseFamily -> "family"
            is Operation.ChooseAnchor -> "anchor"
            is Operation.AddCondition -> if (operation.inner) "inner" else "outer"
            Operation.EndOuter -> "end_outer"
            is Operation.Index -> "index"
            is Operation.Up -> "up"
            is Operation.Child -> "child"
            Operation.Finish -> "finish"
        }
        flag("operation_$operationName", true)
        next.family?.let { flag("family_${it.name.lowercase()}", true) }
        put("prefix_depth", next.depth)
        put("outer_count", next.outer.size)
        put("inner_count", next.inner.size)
        val outer = grammar.outerSpec(next).conditions
        val inner = grammar.innerSpec(next).conditions
        val all = outer + inner
        for ((attr, key) in listOf("id" to "id", "text" to "text", "desc" to "desc", "className" to "class", "packageName" to "package")) {
            put("prefix_$key", all.count { it.attr == attr })
        }
        put("prefix_boolean", all.count { it.value is Boolean })
        put("prefix_depth_condition", all.count { it.attr == "depth" })
        put("prefix_dynamic_text", all.count { it.attr == "text" && digitFraction(it.value) > 0 })
        put("prefix_dynamic_desc", all.count { it.attr == "desc" && digitFraction(it.value) > 0 })
        put("prefix_id_digit_fraction", all.filter { it.attr == "id" }.maxOfOrNull { digitFraction(it.value) } ?: 0.0)
        put("prefix_id_generic", all.count { it.attr == "id" && genericId(it.value) })
        put("prefix_id_long_token", all.count { it.attr == "id" && longId(it.value) })

        val desired = if (next.anchor >= 0) grammar.tree.nodes[next.anchor] else grammar.target
        if (next.family != null && (next.anchor >= 0 || next.family == Family.SELF || next.family == Family.SELF_INDEX)) {
            val matches = grammar.matches(grammar.outerSpec(next))
            put("outer_matches_log", logCount(matches.size))
            flag("outer_unique", matches.size == 1)
            flag("outer_first_desired", matches.firstOrNull() === desired)
            flag("outer_desired_present", desired in matches)
        }
        if (next.anchor >= 0 && (next.family == Family.ANCHOR_SEARCH || next.family == Family.CONTAINER_ITEMS)) {
            val matches = grammar.matches(grammar.innerSpec(next), grammar.tree.nodes[next.anchor])
            val targetSet = grammar.targets.toSet()
            put("inner_matches_log", logCount(matches.size))
            flag("inner_first_target", matches.firstOrNull() === grammar.target)
            put("inner_target_coverage", matches.count { it in targetSet }.toDouble() / targetSet.size.coerceAtLeast(1))
            put("inner_extra_fraction", matches.count { it !in targetSet }.toDouble() / matches.size.coerceAtLeast(1))
        }
        put("path_steps", next.path.size)
        put("path_up_levels", next.path.filterIsInstance<PlanStep.Up>().sumOf { it.levels })
        put("path_child_steps", next.path.count { it is PlanStep.Child })
        val target = grammar.target
        if (target != null) {
            put("target_depth", grammar.tree.depth(target))
            if (next.anchor >= 0) {
                val anchor = grammar.tree.nodes[next.anchor]
                put("anchor_distance", grammar.distance(anchor, target))
                put("anchor_depth", grammar.tree.depth(anchor))
            }
            if (next.cursor >= 0) {
                val cursor = grammar.tree.nodes[next.cursor]
                flag("path_cursor_target", cursor === target)
                put("path_remaining_distance", grammar.distance(cursor, target))
            }
        }
        put("tree_size_log", logCount(grammar.tree.nodes.size))
        put("target_count_log", logCount(grammar.targets.size))
        put("index_absolute_log", logCount(kotlin.math.abs((next.index ?: 0).toLong())))
        flag("index_negative", (next.index ?: 0) < 0)
        put("receiver_up_levels", (grammar.receiver as? PlanReceiver.Up)?.levels ?: 0)
        grammar.condition(state, operation)?.let { condition ->
            for ((attr, key) in listOf("id" to "id", "text" to "text", "desc" to "desc", "className" to "class", "packageName" to "package", "depth" to "depth")) {
                flag("condition_$key", condition.attr == attr)
            }
            flag("condition_boolean", condition.value is Boolean)
            flag("condition_boolean_true", condition.value == true)
            put("condition_value_length_log", logCount((condition.value as? String)?.length ?: 0))
            put("condition_digit_fraction", digitFraction(condition.value))
            flag("condition_id_generic", condition.attr == "id" && genericId(condition.value))
            flag("condition_id_long_token", condition.attr == "id" && longId(condition.value))
        }
        if (operation is Operation.Up || operation is Operation.Child) {
            put("operation_path_distance", value(result, "path_remaining_distance"))
        }
        put("operation_up_levels", (operation as? Operation.Up)?.levels ?: 0)
        val childIndex = (operation as? Operation.Child)?.index ?: 0
        put("operation_child_absolute_log", logCount(kotlin.math.abs(childIndex.toLong())))
        flag("operation_child_negative", childIndex < 0)
        val request = grammar.request
        flag("request_collection", request.scope == CodeGenRequest.Scope.COLLECTION)
        flag("request_action_none", request.action == Action.NONE)
        flag("request_action_click", request.action == Action.CLICK)
        flag("request_action_long_click", request.action == Action.LONG_CLICK)
        flag("request_action_set_text", request.action is Action.SET_TEXT)
        flag("request_action_scroll_forward", request.action == Action.SCROLL_FORWARD)
        flag("request_action_scroll_backward", request.action == Action.SCROLL_BACKWARD)
        flag("request_attr_id", Attr.ID in request.attributes)
        flag("request_attr_text", Attr.TEXT in request.attributes)
        flag("request_attr_desc", Attr.DESC in request.attributes)
        flag("request_receiver_auto", request.receiver == CodeGenRequest.Receiver.AUTO)
        flag("request_mode_check", request.mode == CodeGenRequest.Mode.CHECK)
        flag("request_mode_timeout", request.mode is CodeGenRequest.Mode.WAIT_TIMEOUT)
        flag("request_style_flow", request.style == CodeGenRequest.Style.FLOW)
        check(result.all(Float::isFinite))
        return result
    }

    private fun logCount(value: Number): Double = ln(1.0 + value.toDouble())
    private fun digitFraction(value: Any): Double = (value as? String)?.let {
        it.count(Char::isDigit).toDouble() / it.length.coerceAtLeast(1)
    } ?: 0.0
    private fun genericId(value: Any): Boolean = value is String && value.substringAfterLast(":id/").lowercase() in GENERIC_IDS
    private fun longId(value: Any): Boolean = value is String && value.substringAfterLast(":id/").let {
        it.length >= 24 && (digitFraction(it) > 0.2 || it.none { char -> char == '_' })
    }
    private val GENERIC_IDS = setOf("button", "text", "title", "view", "item", "label", "layout", "container", "content")
}

/** Fixed baseline policy, shared grammar and budgets with the learned policy. The constants are
 * deliberately visible; they are not tuned on the held-out experiment cases.
 * zh-CN: 固定启发式对照, 与学习策略共用语法和预算, 常量不根据留出任务调整. */
object ConstructionHeuristicPolicy : ConstructionPolicy {
    override fun score(features: FloatArray): Double {
        fun f(name: String) = ConstructionFeatures.value(features, name).toDouble()
        val conditions = f("outer_count") + f("inner_count")
        var score = 1.0 + 0.8 * f("outer_unique") + 0.4 * f("outer_first_desired")
        score += 1.2 * f("prefix_id") + 0.7 * f("prefix_desc") + 0.5 * f("prefix_text")
        score += 0.18 * f("prefix_class") + 0.04 * f("prefix_boolean")
        score -= 0.65 * f("prefix_dynamic_text") + 0.5 * f("prefix_dynamic_desc")
        score -= 0.7 * f("prefix_id_digit_fraction") + 0.3 * f("prefix_id_generic") + 0.6 * f("prefix_id_long_token")
        score -= 0.26 * conditions + 0.10 * f("outer_matches_log")
        score -= 0.08 * f("path_steps") + 0.12 * f("index_absolute_log")
        score -= 0.10 * f("family_self_index") + 0.08 * f("family_anchor_path")
        if (f("family_anchor_search") + f("family_container_items") > 0) {
            score += 0.5 * f("inner_target_coverage") + 0.3 * f("inner_first_target") - 0.8 * f("inner_extra_fraction")
        }
        if (f("phase_path") > 0) score += 0.8 * f("path_cursor_target") - 0.12 * f("path_remaining_distance")
        score += 0.12 * f("operation_finish") + 0.06 * f("operation_end_outer")
        return score
    }
}
